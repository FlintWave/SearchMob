## 1. Branch & preference

- [x] 1.1 Create branch `feat/add-search-suggestions` off `main`
- [x] 1.2 Add a `upstreamSuggestionsEnabled` boolean preference (default FALSE) and key to `UserPreferences`
- [x] 1.3 Expose it as an observable `Flow` plus a suspend setter on `PreferencesRepository`
- [x] 1.4 Confirm no new permission is added (INTERNET already present; upstream reuses the proxy client)

## 2. History source

- [x] 2.1 Add a `suggestSince(prefix, cutoffMs, limit)` DAO query (distinct, NOCASE prefix, newest first)
- [x] 2.2 Add `HistoryStore.suggest(prefix, limit, nowMs)` with an in-memory reference implementation
- [x] 2.3 Make the SQLCipher store fail-soft (locked/empty -> empty list, never throws)

## 3. Suggestions providers

- [x] 3.1 Add the `SuggestionsProvider` interface + `NoSuggestionsProvider` default
- [x] 3.2 `HistorySuggestionsProvider` over `HistoryStore.suggest`
- [x] 3.3 `UpstreamSuggestionsProvider` (DDG ac/?q=&type=list) via the proxy client, short timeout, bounded body, fail-soft
- [x] 3.4 `CompositeSuggestionsProvider`: always history, upstream only when opted in, local-first dedup + cap

## 4. Endpoint & descriptor

- [x] 4.1 Add `GET /suggest?q=` returning `["q",[...]]` as `application/x-suggestions+json`, q clamped, blank -> `["", []]`
- [x] 4.2 Build the JSON with kotlinx.serialization (correct escaping)
- [x] 4.3 Add the `application/x-suggestions+json` Url entry to the OpenSearch descriptor on the bound port
- [x] 4.4 Wire the composite provider into `SearchServer`/`searchModule` and have the service observe the prefs

## 5. Settings UI

- [x] 5.1 Add a "Suggestions" section with a "Live suggestions from the web" toggle, OFF by default
- [x] 5.2 Add a subtitle explaining the trade-off (no blocking warning dialog)

## 6. Tests

- [x] 6.1 `/suggest` shape + content type, blank -> `["", []]`, length cap, JSON escaping
- [x] 6.2 History provider: prefix match, case-insensitive, distinct, newest-first, limit, empty when off/empty
- [x] 6.3 Upstream provider: parses DDG ac JSON, empty on error/timeout/malformed, not called when off
- [x] 6.4 Composite: local-first ordering, case-insensitive dedup, total cap, upstream gated by opt-in
- [x] 6.5 Preference defaults to false and round-trips

## 7. Verify

- [x] 7.1 `./gradlew --no-daemon ktlintFormat ktlintCheck lint test assembleDebug` BUILD SUCCESSFUL
- [x] 7.2 `openspec validate add-search-suggestions --strict`
