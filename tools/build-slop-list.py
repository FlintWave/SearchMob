#!/usr/bin/env python3
"""Build the bundled AI-slop / low-quality domain blocklist asset.

Downloads two actively-maintained, CC0-1.0 (public-domain) community lists, parses each into bare
registrable domains (dropping path-scoped, regex, comment, and header lines so a stray entry can
never block a whole legit domain), dedupes + sorts, and writes a gzipped one-domain-per-line asset
plus a NOTICE. Mirrors `tools/build-dictionary.py` for the correction dictionary: the generated
asset is committed for reproducibility, and this script documents exactly how it was produced. The
desktop client ships the identical asset built by its own copy of this script.

Run from the repo root:  python tools/build-slop-list.py
"""

from __future__ import annotations

import gzip
import re
import urllib.request
from pathlib import Path

# (name, raw URL, format). Both are CC0-1.0 -> safe to bundle/redistribute in an AGPL app.
_SOURCES = [
    (
        "laylavish/uBlockOrigin-HUGE-AI-Blocklist",
        "https://raw.githubusercontent.com/laylavish/uBlockOrigin-HUGE-AI-Blocklist/main/noai_hosts.txt",
        "hosts",
    ),
    (
        "agsimmons/ai-content-blocklist",
        "https://raw.githubusercontent.com/agsimmons/ai-content-blocklist/refs/heads/main/uBlacklist.txt",
        "ublacklist",
    ),
]

_OUT_DIR = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "assets" / "blocklist"
_DOMAIN_RE = re.compile(r"^[a-z0-9.-]+\.[a-z]{2,}$")


def _parse_hosts(text: str) -> set[str]:
    out: set[str] = set()
    for raw in text.splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        parts = line.split()
        # "0.0.0.0 domain.tld" / "127.0.0.1 domain.tld"; a bare domain line is also accepted.
        domain = parts[1] if len(parts) >= 2 else parts[0]
        out.add(domain.lower().removeprefix("www."))
    return out


def _parse_ublacklist(text: str) -> set[str]:
    out: set[str] = set()
    for raw in text.splitlines():
        line = raw.strip()
        # Only match-pattern lines have "://"; comments (#/!), allowlist (@), regex (/.../), and
        # YAML-ish header (key: value) lines do not, so they are skipped here.
        if not line or line.startswith(("#", "!", "@")) or "://" not in line:
            continue
        # "*://*.domain.tld/*" -> take the host between "://" and the first path "/".
        host = line.split("://", 1)[1].removeprefix("*.").removeprefix("www.").split("/", 1)[0]
        if "*" in host or not host:
            continue
        out.add(host.lower())
    return out


def main() -> None:
    domains: set[str] = set()
    for name, url, fmt in _SOURCES:
        print(f"fetching {name} ...")
        with urllib.request.urlopen(url, timeout=30) as resp:
            text = resp.read().decode("utf-8", "replace")
        parsed = _parse_hosts(text) if fmt == "hosts" else _parse_ublacklist(text)
        # Keep only things that look like real domains.
        parsed = {d for d in parsed if _DOMAIN_RE.match(d)}
        print(f"  {len(parsed)} domains")
        domains |= parsed

    ordered = sorted(domains)
    _OUT_DIR.mkdir(parents=True, exist_ok=True)
    asset = _OUT_DIR / "ai-slop-domains.txt.gz"
    with gzip.open(asset, "wt", encoding="utf-8") as f:
        f.write("\n".join(ordered) + "\n")
    print(f"wrote {len(ordered)} domains -> {asset}")

    notice = _OUT_DIR / "NOTICE"
    sources_md = "\n".join(
        f"- {name}\n  {url}\n  License: CC0-1.0 (public domain)" for name, url, _ in _SOURCES
    )
    notice.write_text(
        "AI-slop / low-quality domain blocklist (ai-slop-domains.txt.gz)\n"
        "==============================================================\n\n"
        "A merged snapshot of community-maintained AI-content-farm / low-quality domain lists,\n"
        "parsed to bare domains. Applied entirely on-device to downrank or hide listed domains;\n"
        "no query leaves the device for filtering.\n\n"
        "Sources (both CC0-1.0, public domain -> compatible with AGPL-3.0 redistribution):\n\n"
        f"{sources_md}\n\n"
        "Regenerate with:  python tools/build-slop-list.py\n",
        encoding="utf-8",
    )
    print(f"wrote {notice}")


if __name__ == "__main__":
    main()
