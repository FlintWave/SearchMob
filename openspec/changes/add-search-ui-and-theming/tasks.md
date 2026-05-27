## 1. Branch & dependencies

- [x] 1.1 Create branch `feat/add-search-ui-and-theming` off `main` (implemented on `feat/p6-ui` off HEAD per phase brief)
- [x] 1.2 Add UI dependencies: `androidx.navigation:navigation-compose`, `androidx.lifecycle:lifecycle-viewmodel-compose` + `lifecycle-runtime-compose`, and `androidx.compose.ui:ui-test-junit4` (already present) — no analytics SDKs. (DataStore intentionally NOT added: the encrypted-DataStore impl is owned by the storage phase and binds to the `PreferencesStore` interface defined here.)

## 2. Preferences store (DataStore)

- [x] 2.1 Define a preferences model (`UserPreferences`: theme mode, dynamic-color flag, per-engine enabled map, search-history flag) with first-run defaults (Follow-system, dynamic-color on, all engines enabled, history off)
- [x] 2.2 Define a `PreferencesStore` interface (get/set string/bool/map) with an `InMemoryPreferencesStore` default + a `PreferencesRepository` exposing read `Flow`s and write functions that emit immediately. INJECTION POINT + TODO left for the storage phase to bind the encrypted DataStore for reboot persistence (DataStore itself NOT implemented here, per scope).
- [x] 2.3 Route Brave/Mojeek API-key read/write/clear through an in-memory sink with a clear TODO to bind `EncryptedPreferencesCodec` + `Vault` (never DataStore plaintext, never logs)

## 3. Theming layer

- [x] 3.1 Define built-in light and dark `ColorScheme`s with WCAG-AA body-text contrast (on-colors added)
- [x] 3.2 Implement the single, pure precedence logic (`resolveDarkTheme` + `resolvePaletteSource`): explicit Light/Dark beats system, Follow-system tracks system, dynamic color on API 31+ in the resolved variant, fallback below API 31
- [x] 3.3 Wire `SearchMobTheme(themeMode, dynamicColor, ...)` and observe the preference `Flow`s in `SearchMobApp` so theme changes apply immediately; font scale is respected (not overridden)

## 4. Search UI

- [x] 4.1 Define sealed `SearchUiState` (`Idle`/`Loading`/`Empty`/`Error`/`Results`) and a `SearchViewModel` backed by a `StateFlow`
- [x] 4.2 Implement `SearchResultsRepository` consuming the in-process aggregator (`InProcessSearchResultsRepository` -> `MetaSearchResultProvider`), session state in memory only; localhost endpoint can back the same interface
- [x] 4.3 Build the query input + submit composable; empty/whitespace-only submissions ignored
- [x] 4.4 Build the results screen rendering title, snippet, and source-engine attribution per result, tap-to-open via `ACTION_VIEW`
- [x] 4.5 Implement loading, empty, and error states (visually distinct, test-tagged) with a retry control re-dispatching the most recent query
- [x] 4.6 Gate query recording on the history toggle (routed via the history store recorder); no analytics/telemetry, no new outbound path

## 5. Settings UI

- [x] 5.1 Build the settings screen scaffold + `navigation-compose` wiring (Home/Search/Settings)
- [x] 5.2 Add theme-mode control (Light/Dark/Follow system) + Material You dynamic-color toggle bound to the store
- [x] 5.3 Add per-engine enable/disable toggles that exclude disabled engines from the fan-out
- [x] 5.4 Add BYO API-key entry (enter/replace/clear) for Brave and Mojeek (masked input; in-memory sink with TODO to bind encrypted prefs)
- [x] 5.5 Add search-history on/off switch (default off) + "clear history" wired to the history store
- [x] 5.6 Add the zero-knowledge passphrase setup entry point (hand-off dialog with the data-unrecoverable warning; TODO to launch `add-encrypted-storage` capture flow)
- [x] 5.7 Add the entry point opening the battery exemption + OEM device-setup guidance from `add-foreground-service`

## 6. Unit tests

- [x] 6.1 Unit-test the theme precedence: explicit Light/Dark beats system, Follow-system tracks it, dynamic color on API 31+, fallback at API 26 without crashing
- [x] 6.2 Unit-test the `SearchViewModel` state machine: submit -> Loading, results -> Results, empty -> Empty, failure -> Error, retry -> Loading; whitespace submit ignored; recording only on non-empty success
- [x] 6.3 Unit-test `PreferencesRepository` round-trips values and that disabled engines are excluded from the fan-out

## 7. Compose UI tests

- [x] 7.1 Results render: title, snippet, and source-engine attribution appear for a returned result set
- [x] 7.2 State transitions: loading, empty, and error render distinctly and retry returns to loading
- [x] 7.3 Settings toggles persist and re-apply: flip a theme/engine/history toggle, rebuild over the same store, assert restored
- [x] 7.4 Theme override beats system: select Light asserts light background, select Dark asserts dark background
- [x] 7.5 Store-nothing: with history off, recording a query persists nothing

## 8. On-device / VM verification

- [ ] 8.1 Build and install the debug APK on the Android VM/emulator (DEFERRED — phase brief forbids adb/emulator; central run later)
- [ ] 8.2 Run a real query end-to-end and confirm aggregated results render with attribution and tap opens the URL (DEFERRED)
- [ ] 8.3 Flip theme mode + dynamic color and confirm immediate re-theming with override beating system (DEFERRED)
- [ ] 8.4 Disable an engine and confirm subsequent searches exclude it (DEFERRED)
- [ ] 8.5 Reboot-persistence check (DEFERRED — depends on the storage phase binding the encrypted DataStore)
- [ ] 8.6 Privacy check: capture network traffic, confirm engine-fan-out only; confirm history off writes nothing (DEFERRED)

## 9. Validate & merge

- [x] 9.1 Run `openspec validate add-search-ui-and-theming --strict` and fix any issues
- [ ] 9.2 Open PR against `main`, confirm CI green (DEFERRED — no push per phase brief)
- [ ] 9.3 Merge to `main`, then run `openspec archive add-search-ui-and-theming` (DEFERRED)
