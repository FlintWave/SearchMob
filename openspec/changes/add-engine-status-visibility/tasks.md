# Tasks: engine-status-visibility (Android)

## Engine

- [x] `engine/aggregate/Aggregator.kt`: capture per-engine outcome (contributed n / empty / failed),
      a timeout/throw is FAILED; `AggregationResult.engineStatus: List<EngineOutcome>`. Engine label
      is the adapter id.
- [x] `engine/MetaSearchResultProvider.kt`: thread the outcome through `aggregateRanked` into
      `SearchOutcome.engineStatus`.

## UI

- [x] `ui/search/SearchUiState.kt` + `SearchViewModel.kt`: carry `engineStatus` to the results state.
- [x] `ui/search/SearchScreen.kt`: an unobtrusive "N of M engines responded" row, tap to expand
      per-engine detail (always shown in-app; the owner's own results).
- [x] `server/SearchServer.kt`: the same line as a `<details>` disclosure, gated on `editable`
      (loopback owner only); `res/values/strings.xml` (+ authored locales).

## Tests

- [x] `AggregatorTest`: outcome distinguishes contributed / empty / failed; a timeout is FAILED.
- [x] `EngineStatusRouteTest`: served line shows for the loopback owner, absent on a read-only server.

## Verify + ship

- [x] `ktlintCheck`, `:app:lintDebug`, `:app:testDebugUnitTest`, `:app:assembleDebug` green.
- [x] `openspec validate add-engine-status-visibility --strict` passes.
- [ ] Emulator spot-check: a forced failure shows as failed (not empty); own PR to `main`.
