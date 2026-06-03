## Why

Search relevance is the product. Today ranking is RRF (engine-consensus) over de-duplicated results,
then sort and the user's domain rules. Nothing asks "does this result actually match the query?", so
with mostly single-engine results the fused scores are near-tied and off-topic or wrong-language
results slip into the top. Users reported results "very far from relevant" and results "in different
languages than the request". This change adds the missing query-match signal without breaking the
store-nothing, on-device, owner-safe posture, and it brings Android to parity with the desktop app
(shipped 26.06.04). It must work in any query language ahead of the upcoming localization pass (not
be English-specific).

## What Changes

- Add a deterministic, on-device relevance signal folded into the aggregator's final ordering,
  between RRF/dedup and `ResultSorter`.
- Lexical query-match: stopword-filtered, lightly (ASCII-gated) stemmed content-term coverage over
  title and snippet, weighted toward the title, with a head-term (subject) penalty and a small
  exact-phrase bonus.
- Language affinity: script-relative demotion of results whose dominant script differs from the
  query's (works in any language; same-script results are never penalized).
- Demotion-only blend: the relevance factor is capped at 1.0, so a weak or wrong-language match is
  sunk toward a floor but a strong match never outranks engine consensus (no keyword-stuffing wins).
- Multilingual-ready: Unicode-aware tokenizer (not ASCII-only, since Java/Kotlin regex `\w` is
  ASCII-only by default), English stemming gated to ASCII so non-Latin words are never corrupted,
  English stopwords degrade harmlessly (nothing stripped) for other languages and are a per-language
  hook for the localization pass.

## Capabilities

### New Capabilities
- `relevance-ranking`: a query-match and language-affinity signal that demotes off-topic and
  wrong-language results in the aggregated ranking, on-device, deterministic, and language-agnostic.

### Modified Capabilities
<!-- None. This adds a new pass to the aggregator; the RRF/dedup, sort, and domain-rule passes are
unchanged in contract. -->

## Non-goals

- Semantic / embedding relevance (synonyms, "ai" vs "artificial intelligence"): out of scope; the
  bounded demotion-only blend deliberately keeps consensus results phrased differently from the
  query rather than deleting them.
- Result quality/reputation (a low-quality but on-topic page): that is the AI-slop filter and engine
  consensus, a separate axis, not relevance-match.
- Per-language stopword lists and stemmers: hooks are left for the localization pass; not built here.

## Impact

- New code: `engine/aggregate/Relevance.kt` (pure `contentTerms` / `lexicalScore` / `languageAffinity`
  / `blendedScore`). Mirrors desktop `engines/relevance.py` with the same constants and scoring.
- Modified: `engine/aggregate/Aggregator.kt` (apply the blend to the final sort key).
- No new dependencies, no new outbound calls, no stored data, no LAN-facing surface. Owner and
  network paths rank identically (this is base relevance, not personalization).
