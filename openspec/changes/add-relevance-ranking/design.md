# Design: relevance-ranking

## Where it sits in the pipeline

Adapters -> Aggregator (RRF + URL-dedup) -> **relevance blend (new)** -> `ResultSorter` ->
`Personalizer` -> `DomainRanker`. The blend changes only the aggregator's final sort key.
`ResultSorter` keys off the resulting index (`1.0 / (RRF_K + index)`, see `engine/sort/ResultSorter.kt`),
and the personalization and domain-rule passes consume the order positionally too, so all of them
inherit the improved order unchanged. `AggregatedResult.score` stays the raw RRF value; the blend is
a local sort key, not stored.

## Algorithm

- `contentTerms(query)`: Unicode word tokens (maximal `Character.isLetterOrDigit` runs over code
  points, the language-agnostic twin of Python's `[^\W_]+`), length >= 2, distinct, stopwords removed;
  falls back to all tokens when every token is a stopword.
- `lexicalScore(title, snippet, terms)` in [0, 1]: `0.5*coverage + 0.4*titleCoverage + 0.1*phrase`,
  on lightly stemmed whole-word membership; halved when the head term is absent everywhere.
- `languageAffinity(query, title, snippet)`: 1.0 when the result's dominant script equals the query's
  (or the query has no letters), else 0.4. Script buckets: latin, cyrillic, greek, hebrew, arabic,
  devanagari, thai, cjk, other.
- `blendedScore(rrf, lexical, affinity) = rrf * minOf(1.0, BASE + GAIN*lexical) * affinity`, with
  `BASE=0.5`, `GAIN=1.0`. Capping at 1.0 makes it demotion-only.

## Privacy / owner / parity

- Pure string work on data already fetched. No new outbound calls, no stored state, no vault use.
- Base relevance is identical for the owner and for LAN clients (this is not personalization); it
  does not interact with owner-only gating.
- Parity: Android `engine/aggregate/Relevance.kt` and desktop `engines/relevance.py` use the same
  constants and the same scoring so both apps rank equivalently. Shared concept, same names.

## Porting note (Android-specific)

The single correctness trap in the port is tokenization: Java/Kotlin regex `\w` is ASCII-only unless
`UNICODE_CHARACTER_CLASS` is set, so a literal port of `re.compile(r"[^\W_]+")` would silently drop
every non-Latin character and make the signal a no-op for Cyrillic/Greek/Arabic/CJK queries. The
Kotlin port scans code points with `Character.isLetterOrDigit` instead (Unicode-aware; underscore
naturally excluded), which is what the non-Latin unit test pins. Script bucketing and stemming
iterate code points for the same reason.

## Tuning rationale

Inherited from the desktop change, which was verified empirically against live queries
(keyboard/news/musical/tie). A first naive multiplicative blend regressed quality (dropped a relevant
result on a plural mismatch, promoted keyword-stuffed blogspam), which is why the final design is
demotion-only and lightly stemmed. The bounded floor keeps differently-phrased consensus results
(e.g. "artificial intelligence" for "ai") alive.

## Multilingual readiness (for the localization pass)

- Tokenizer and affinity are language-agnostic already.
- English stopwords and the ASCII stemmer are the only English-specific pieces; both degrade
  harmlessly and are the documented hooks for per-language stopword/stemmer tables.
- Open follow-up for i18n (tracked separately): make the engine `Accept-Language` reflect the
  selected UI/query language so engines return same-language results in the first place.
