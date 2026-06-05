# Tasks: media-intent (Android)

User decisions (confirmed at build time): the proposed platform lists plus the entity's Wikipedia
article leading each row; the actions row appears for resolved entities only (no cue-only rows).

## Detection + module

- [x] `engine/MediaIntent.kt`: `detectCategory` from the Wikipedia short description (no extra
      network call); per-category curated platform lists (free/open first) + `buildActionsRow`
      (Wikipedia leads) + `hostInCategory` + bounded stable `promoteMedia`. Ported 1:1 from desktop.

## Threading + surfaces

- [x] `MetaSearchResultProvider`: promote (bounded, before the rules pass) using the concurrently
      fetched summary; build the row onto `SearchOutcome.actionsRow`; gated by a `mediaActionsEnabled`
      lambda wired to the pref (in-app + served).
- [x] `ui/search` (`SearchUiState`, `SearchViewModel`, `SearchScreen`): carry + render the actions row.
- [x] `server/SearchServer.kt`: render the served actions row + CSS; verb label localized.
- [x] `media_actions_enabled` pref (DataStore) + Settings toggle (`SettingsScreen` + `SettingsViewModel`).
- [x] `res/values/strings.xml`: verb labels + the Settings label/supporting (+ authored locales).

## Tests

- [x] `MediaIntentTest`: mapping (+ non-media null; "video game" beats "game"); actions-row build
      (Wikipedia first, free/open first, URL-encoded); host-in-category; promotion bounded + stable.
- [x] `MediaIntentRouteTest`: served row renders for an outcome with one, absent otherwise.

## Verify + ship

- [x] `ktlintCheck`, `:app:lintDebug`, `:app:testDebugUnitTest`, `:app:assembleDebug` green.
- [x] `openspec validate add-media-intent --strict`; own PR to `main`.
