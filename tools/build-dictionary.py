#!/usr/bin/env python3
"""Build the bundled correction dictionary for SearchMob's on-device spell corrector.

It fetches free-licensed word-frequency and name lists, merges them into a compact
`word<TAB>weight` table, and writes a gzipped, sorted (reproducible) asset to
app/src/main/assets/dict/words.txt.gz.

Sources (see app/src/main/assets/dict/NOTICE):
  - English word frequencies: hermitdave/FrequencyWords en_50k (MIT; derived from OpenSubtitles)
  - First names: dominictarr/random-name first-names.txt (public domain)
  - Surnames:    dominictarr/random-name names.txt (public domain)

Run from the repo root:  python3 tools/build-dictionary.py
Requires network access; the generated asset is committed so the app builds offline.
"""
import gzip
import re
import sys
import urllib.request
from pathlib import Path

WORDS_URL = "https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/en/en_50k.txt"
FIRST_NAMES_URL = "https://raw.githubusercontent.com/dominictarr/random-name/master/first-names.txt"
SURNAMES_URL = "https://raw.githubusercontent.com/dominictarr/random-name/master/names.txt"

# Names carry no real frequency, so give them a moderate baseline so they are plausible
# correction candidates without outranking genuinely common words.
NAME_WEIGHT = 20_000
TOKEN = re.compile(r"^[a-z]{2,20}$")

OUT = Path("app/src/main/assets/dict/words.txt.gz")


def fetch(url: str) -> str:
    with urllib.request.urlopen(url, timeout=60) as r:
        return r.read().decode("utf-8", "replace")


def main() -> int:
    weights: dict[str, int] = {}

    def add(token: str, weight: int) -> None:
        t = token.strip().lower()
        if TOKEN.match(t):
            if weight > weights.get(t, 0):
                weights[t] = weight

    print("fetching word frequencies...", file=sys.stderr)
    for line in fetch(WORDS_URL).splitlines():
        parts = line.split()
        if len(parts) == 2 and parts[1].isdigit():
            add(parts[0], int(parts[1]))

    print("fetching names...", file=sys.stderr)
    for url in (FIRST_NAMES_URL, SURNAMES_URL):
        for line in fetch(url).splitlines():
            add(line, NAME_WEIGHT)

    # Sort by descending weight then word for a deterministic, reproducible file.
    rows = sorted(weights.items(), key=lambda kv: (-kv[1], kv[0]))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    payload = "".join(f"{w}\t{c}\n" for w, c in rows).encode("utf-8")
    # mtime=0 so the gzip output is byte-stable across runs.
    with gzip.GzipFile(filename="", mode="wb", fileobj=open(OUT, "wb"), mtime=0) as gz:
        gz.write(payload)

    print(f"wrote {OUT} ({OUT.stat().st_size} bytes, {len(rows)} entries)", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
