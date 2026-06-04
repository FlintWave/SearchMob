#!/usr/bin/env python3
"""Author the per-locale Android string catalogs offline with the local `translategemma` model.

SearchMob's English UI strings live in `app/src/main/res/values/strings.xml`. Android resolves the
right translation at runtime from a `values-<qualifier>/strings.xml` sibling, falling back to the
base (English) file for any missing key. This tool translates the base file into the nine shipped
target languages ONCE and the result is committed; the app never calls a model at runtime, keeping
the offline / store-nothing posture and shipping no model in the apk.

    python tools/i18n_author.py translate                 # all nine target locales
    python tools/i18n_author.py translate --locale es ar  # just these
    python tools/i18n_author.py translate --model translategemma:12b

It is INCREMENTAL and RESUMABLE: an existing `values-<q>/strings.xml` is read back, only keys it is
missing are fetched, and progress is flushed every few strings.

Two model failure modes are guarded, mirroring the desktop authoring tool. Placeholders (`%s`,
`%d`, positional `%1$s` / `%1$d`, and any `{name}` token) are masked to opaque `{pN}` tokens before
translation and restored after, so the model neither echoes a sentence containing a bare `%s` nor
translates a placeholder's name. An exact echo of the input is treated as a failed translation:
one firmer retry, then the English source is kept (Android then falls back to it anyway).
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path

_REPO_ROOT = Path(__file__).resolve().parent.parent
_RES_DIR = _REPO_ROOT / "app" / "src" / "main" / "res"
_BASE_STRINGS = _RES_DIR / "values" / "strings.xml"
_OLLAMA_URL = "http://localhost:11434/api/generate"
_DEFAULT_MODEL = "translategemma:27b"

# (tag, English name, resource-folder qualifier) for the nine authored locales. English is the
# source and is never translated. The qualifier gotchas: Indonesian must use the BCP-47 `b+id`
# form because Android maps the legacy code `id` -> `in`, so `values-id` silently fails to match;
# Simplified Chinese is region-qualified `zh-rCN`.
_TARGETS: tuple[tuple[str, str, str], ...] = (
    ("zh", "Chinese (Simplified)", "zh-rCN"),
    ("hi", "Hindi", "hi"),
    ("es", "Spanish", "es"),
    ("ar", "Arabic", "ar"),
    ("fr", "French", "fr"),
    ("bn", "Bengali", "bn"),
    ("pt", "Portuguese", "pt"),
    ("id", "Indonesian", "b+id"),
    ("ur", "Urdu", "ur"),
)

# Keys whose value is a brand or otherwise must not be translated.
_NO_TRANSLATE: frozenset[str] = frozenset({"app_name"})

# Placeholders that must survive translation untouched: named `{token}` tokens and printf specs
# including Android's positional form (`%1$s`, `%2$d`) as well as the bare `%s` / `%d`.
_PROTECT_TOKEN = re.compile(r"\{[a-zA-Z_][a-zA-Z0-9_]*\}|%\d*\$?[sd]")
_PLACEHOLDER = re.compile(r"\{[a-zA-Z_][a-zA-Z0-9_]*\}")

# Trailing sentence punctuation the model sometimes appends to a fragment that had none, including
# the CJK and Arabic full stops/commas so it is stripped for those scripts too.
_TRAILING_PUNCT = ".。!！?？:：،"  # noqa: RUF001


def _ollama(model: str, prompt: str) -> str:
    payload = json.dumps({"model": model, "prompt": prompt, "stream": False}).encode("utf-8")
    request = urllib.request.Request(
        _OLLAMA_URL, data=payload, headers={"Content-Type": "application/json"}
    )
    with urllib.request.urlopen(request, timeout=120) as response:
        body = json.loads(response.read().decode("utf-8"))
    return str(body.get("response", "")).strip()


def _protect(text: str) -> tuple[str, list[str]]:
    """Mask every placeholder as an opaque `{p0}`, `{p1}`, ... token; return (masked, originals)."""
    specs: list[str] = []

    def _mask(match: re.Match[str]) -> str:
        specs.append(match.group(0))
        return f"{{p{len(specs) - 1}}}"

    return _PROTECT_TOKEN.sub(_mask, text), specs


def _restore(text: str, specs: list[str]) -> str:
    for index, spec in enumerate(specs):
        text = text.replace(f"{{p{index}}}", spec)
    return text


def _prompt(name: str, tag: str, text: str, *, insist: bool = False) -> str:
    nudge = (
        " The previous attempt returned the text unchanged; this string DOES need translating, so "
        "render its meaning in the target language while preserving the tokens."
        if insist
        else ""
    )
    return (
        f"Translate the following Android user-interface text from English (en) to {name} ({tag}). "
        f"Keep any {{placeholder}} tokens exactly as written, and keep any digits as Western "
        f"numerals (0-9).{nudge} Reply with only the translation, no quotes or notes."
        f"\n\n{text}"
    )


def _strip_trailing(source: str, out: str) -> str:
    if source and source[-1] not in _TRAILING_PUNCT and out and out[-1] in _TRAILING_PUNCT:
        return out[:-1].strip()
    return out


def _placeholders_ok(source: str, out: str) -> bool:
    return set(_PLACEHOLDER.findall(source)) == set(_PLACEHOLDER.findall(out))


def _clean(response: str) -> str:
    return response.strip().strip('"').strip()


def _translate_text(model: str, name: str, tag: str, rendered: str) -> str:
    """Translate one string, masking placeholders and retrying once if the model echoes the input.

    Returns the translation, or the input `rendered` unchanged when both attempts fail (an exact
    echo) or mangle a placeholder; the caller then drops the key so Android falls back to English.
    """
    masked, specs = _protect(rendered)
    for insist in (False, True):
        response = _clean(_ollama(model, _prompt(name, tag, masked, insist=insist)))
        out = _restore(_strip_trailing(masked, response), specs)
        if out and _placeholders_ok(rendered, out) and out != rendered:
            return out
        if not specs and out == rendered and not insist:
            continue  # an echo of a token-free string: one firmer retry before giving up
        if out and _placeholders_ok(rendered, out):
            return out  # a legitimately identical translation (e.g. a loanword) — keep it
    return rendered


# --- Android XML string (de)serialization ------------------------------------------------------
#
# The value of an Android <string> uses aapt escaping, NOT XML escaping, for quotes: an apostrophe
# is written `\'` and a double quote `\"` (or the whole value wrapped in "..."). Ampersand and angle
# brackets use XML entities. We unescape to a plain Python string for the model, then re-escape on
# the way out so the emitted file is valid for both the XML parser and aapt.


def _unescape(value: str) -> str:
    """aapt-escaped <string> inner text -> plain text for translation."""
    return value.replace("\\'", "'").replace('\\"', '"').replace("\\n", "\n").replace("\\t", "\t")


def _escape(value: str) -> str:
    """Plain text -> aapt + XML escaped <string> inner text."""
    value = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    value = value.replace("\n", "\\n").replace("\t", "\\t")
    return value.replace("'", "\\'").replace('"', "\\\"")


def _load_base() -> list[tuple[str, str]]:
    """Return [(name, plain-text value), ...] for translatable base strings, in document order."""
    tree = ET.parse(_BASE_STRINGS)
    items: list[tuple[str, str]] = []
    for node in tree.getroot():
        if node.tag != "string":
            continue  # plurals/string-array are not used by the base file today
        key = node.get("name")
        if key is None or node.get("translatable") == "false" or key in _NO_TRANSLATE:
            continue
        # ElementTree already turns &amp; etc. back into characters; only aapt escapes remain.
        items.append((key, _unescape(node.text or "")))
    return items


def _load_existing(path: Path) -> dict[str, str]:
    if not path.exists():
        return {}
    try:
        tree = ET.parse(path)
    except ET.ParseError:
        return {}
    return {
        node.get("name", ""): _unescape(node.text or "")
        for node in tree.getroot()
        if node.tag == "string" and node.get("name")
    }


def _write_locale(path: Path, translations: dict[str, str], order: list[str]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        "<!-- Machine-authored from values/strings.xml; English is the source of truth. -->",
        "<resources>",
    ]
    lines.extend(
        f'    <string name="{key}">{_escape(translations[key])}</string>'
        for key in order
        if key in translations
    )
    lines.append("</resources>")
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def translate(tags: list[str], model: str) -> None:
    base = _load_base()
    order = [key for key, _ in base]
    sources = dict(base)
    for tag, name, qualifier in _TARGETS:
        if tags and tag not in tags:
            continue
        path = _RES_DIR / f"values-{qualifier}" / "strings.xml"
        translations = {k: v for k, v in _load_existing(path).items() if k in sources}
        missing = [k for k in order if k not in translations]
        print(f"\n{tag} ({name}) -> values-{qualifier}: {len(missing)} to translate, "
              f"{len(translations)} done")
        try:
            for index, key in enumerate(missing, start=1):
                out = _translate_text(model, name, tag, sources[key])
                if out != sources[key]:  # keep only genuine translations; else fall back to English
                    translations[key] = out
                if index % 10 == 0:
                    _write_locale(path, translations, order)
                    print(f"  {index}/{len(missing)}")
        except (urllib.error.URLError, TimeoutError, OSError) as exc:
            _write_locale(path, translations, order)
            sys.exit(f"\nollama call failed ({exc}); saved progress, re-run to resume")
        _write_locale(path, translations, order)
        print(f"{tag}: done ({len(translations)}/{len(order)} keys)")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    translate_parser = sub.add_parser("translate", help="fill missing per-locale translations")
    translate_parser.add_argument("--locale", nargs="*", default=[], help="subset of target tags")
    translate_parser.add_argument("--model", default=_DEFAULT_MODEL, help="ollama model name")
    args = parser.parse_args()
    translate(args.locale, args.model)


if __name__ == "__main__":
    main()
