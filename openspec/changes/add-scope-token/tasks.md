# Tasks: inline scope token (Android)

## Parser

- [x] `engine/rank/ScopeToken.kt`: pure `parse(query, rules) -> Pair<cleaned, scopeName?>` with
      first-word match (case-insensitive), normalized full-name fallback, first-token-wins, and a
      no-`+` fast path. 1:1 with the desktop `scope_token.py`.

## Served wiring

- [x] `server/SearchResultProvider.kt` + `engine/MetaSearchResultProvider.kt`: add an
      `activeLensOverride: String?` to `searchWithCorrection`; apply `rules.copy(activeLens=override)`
      transiently when set.
- [x] `server/SearchServer.kt`: `/search` and `/api/search` parse the query, search the cleaned text
      with the override, and echo the original (search box / JSON `query`).

## Tests

- [x] `ScopeTokenTest`: parser truth table (mirrors desktop).
- [x] `ScopeTokenRouteTest`: served routes apply + strip + echo; unmatched passes through; saved
      active scope untouched.

## Verify

- [x] `./gradlew ktlintCheck :app:lintDebug :app:testDebugUnitTest :app:assembleDebug` green.
- [x] `openspec validate add-scope-token --strict` passes.
- [ ] Ship in the RC feature pile (own PR); desktop CLI + served covered by the desktop repo's
      `add-scope-token` change.
