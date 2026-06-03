# Tasks: relevance-ranking (Android)

## Implementation

- [x] Add `engine/aggregate/Relevance.kt`: `contentTerms`, `stem` (ASCII-gated), code-point script
      helpers, `languageAffinity`, `lexicalScore` (head-term penalty), `blendedScore` (demotion-only).
      Unicode-aware tokenizer via `Character.isLetterOrDigit` (not regex `\w`, which is ASCII-only).
- [x] Wire the blend into `engine/aggregate/Aggregator.kt` final ordering, keeping the existing
      deterministic tie-breakers (normalized URL, then engine set).
- [x] Unit tests `engine/aggregate/RelevanceTest.kt` mirroring the desktop cases (coverage, stemming,
      head penalty, script affinity both directions, demotion-only cap, non-Latin tokenization).
- [x] ktlint + lintDebug + unit tests + assembleDebug green; existing `AggregatorTest` unchanged.

## Verify + ship

- [ ] Run representative queries on the `searchmob` emulator (including a non-Latin query) and confirm
      off-topic and wrong-language intrusions are demoted without regressing good results.
- [ ] Ship as a standalone Android GA at parity with desktop 26.06.04; RC bypassed per the user:
      local verification + CI green, then a direct GA.
