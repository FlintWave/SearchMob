## 1. Branch & dependencies

- [ ] 1.1 Create branch `feat/add-search-ui-and-theming` off `main`
- [ ] 1.2 Add UI dependencies: Compose Material 3 (with dynamic-color APIs), `androidx.lifecycle` ViewModel/Compose integration, `androidx.navigation:navigation-compose`, `androidx.datastore:datastore-preferences`, and `androidx.compose.ui:ui-test-junit4` (no analytics SDKs)

## 2. Preferences store (DataStore)

- [ ] 2.1 Define a `Preferences` model (theme mode, dynamic-color flag, per-engine enabled set, search-history flag) with first-run defaults (Follow-system, dynamic-color on where supported, all default engines enabled, history off)
- [ ] 2.2 Implement a `PreferencesRepository` over Jetpack DataStore exposing read `Flow`s and write functions, so changes persist across restarts/reboots and emit immediately
- [ ] 2.3 Route Brave/Mojeek API-key read/write/clear through the encrypted-preferences mechanism from `add-encrypted-storage` (never DataStore plaintext, never logs)

## 3. Theming layer

- [ ] 3.1 Define built-in light and dark `ColorScheme`s meeting WCAG AA body-text contrast
- [ ] 3.2 Implement a single `resolveColorScheme(themeMode, dynamicColorEnabled, sdkInt, systemDark)` enforcing: explicit Light/Dark beats system, Follow-system tracks system, dynamic color on API 31+ supplies the palette in the resolved variant, fallback to built-in below API 31
- [ ] 3.3 Wire the app `Theme` composable to observe the preference `Flow`s so theme/dynamic-color changes apply immediately; ensure the system font scale is respected

## 4. Search UI

- [ ] 4.1 Define a sealed `SearchUiState` (`Idle`/`Loading`/`Empty`/`Error`/`Results`) and a `SearchViewModel` backed by a `StateFlow`
- [ ] 4.2 Implement a results repository interface consuming the in-process aggregator (`add-metasearch-engine-core`), keeping session state in memory only; allow the localhost endpoint (`add-local-search-server`) as an alternate backing source behind the same interface
- [ ] 4.3 Build the query input + submit composable; ignore empty/whitespace-only submissions
- [ ] 4.4 Build the results screen rendering title, snippet, and source-engine attribution per result, with tap-to-open via `ACTION_VIEW`
- [ ] 4.5 Implement the loading, empty, and error states (visually distinct) with retry control and pull-to-refresh that re-dispatches the most recent query
- [ ] 4.6 Gate any query/result persistence on the history toggle, routed through `add-encrypted-storage`; emit no analytics/telemetry and add no outbound network path

## 5. Settings UI

- [ ] 5.1 Build the settings screen scaffold and `navigation-compose` wiring between search and settings
- [ ] 5.2 Add theme-mode control (Light / Dark / Follow system) and Material You dynamic-color toggle bound to the preferences store
- [ ] 5.3 Add per-engine enable/disable toggles that exclude disabled engines from the fan-out
- [ ] 5.4 Add BYO API-key entry (enter/replace/clear) for Brave and Mojeek via encrypted preferences
- [ ] 5.5 Add search-history on/off switch (default off) and a "clear history" action wired to `add-encrypted-storage`
- [ ] 5.6 Add the zero-knowledge passphrase setup entry point (hand off to `add-encrypted-storage`) with an explicit data-unrecoverable-if-lost warning
- [ ] 5.7 Add the entry point that opens the battery/OEM device-setup guidance from `add-foreground-service`

## 6. Unit tests

- [ ] 6.1 Unit-test `resolveColorScheme`: explicit Light/Dark beats the system setting, Follow-system tracks it, dynamic color applies the resolved variant on API 31+, and falls back without crashing at API 26
- [ ] 6.2 Unit-test the `SearchViewModel` state machine: submit -> Loading, success-with-results -> Results, success-empty -> Empty, failure -> Error, retry -> Loading; empty/whitespace submit is ignored
- [ ] 6.3 Unit-test the preferences repository round-trips values and that disabled engines are excluded from the fan-out request

## 7. Compose UI tests

- [ ] 7.1 Results render: assert title, snippet, and source-engine attribution appear for a returned result set
- [ ] 7.2 State transitions: assert loading, empty, and error states each render distinctly and that retry/pull-to-refresh returns to loading
- [ ] 7.3 Settings toggles persist and re-apply: flip a theme/engine/history toggle, simulate process restart, and assert the value is restored and applied
- [ ] 7.4 Theme override beats system: set system dark, select Light, assert the app renders light; set system light, select Dark, assert the app renders dark
- [ ] 7.5 Store-nothing: with history off, assert no query/result is persisted to disk

## 8. On-device / VM verification

- [ ] 8.1 Build and install the debug APK on the Android VM/emulator
- [ ] 8.2 Run a real query end-to-end and confirm aggregated results render with source-engine attribution and that tapping a result opens its URL
- [ ] 8.3 Flip the theme mode (and dynamic-color toggle on an API 31+ image) and confirm the UI re-themes immediately with the user override beating the system setting
- [ ] 8.4 Disable an engine and confirm subsequent searches exclude it
- [ ] 8.5 Reboot-persistence check: change preferences, reboot the VM/device, relaunch, and confirm the preferences are restored and applied
- [ ] 8.6 Privacy check: capture network traffic during a query and confirm traffic only to the engine fan-out (no analytics/third-party); confirm history off writes nothing to disk

## 9. Validate & merge

- [ ] 9.1 Run `openspec validate add-search-ui-and-theming --strict` and fix any issues
- [ ] 9.2 Open PR against `main`, confirm CI green
- [ ] 9.3 Merge to `main`, then run `openspec archive add-search-ui-and-theming`
