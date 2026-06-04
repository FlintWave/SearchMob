# Tasks: theming (Android)

## Theme registry + model

- [x] `ui/theme/Themes.kt`: add an `AppTheme` registry of thirteen themes (id, display name, mode,
      role colors, optional credit) and `colorSchemeFor(theme)` building a Material 3 `ColorScheme`.
      Copy the locked hex palettes verbatim. (Registry lives in a new `Themes.kt` rather than
      `Color.kt`, keeping the existing `Color.kt` palette values intact.)
- [x] `ui/theme/Theme.kt`: `SearchMobTheme` resolves the active theme via the two-slot rule
      (`resolveActiveTheme(mode, lightId, darkId, systemDark)`) and uses its scheme unless dynamic
      color is on. `ThemeMode` enum unchanged.
- [x] `data` prefs: add `PreferenceKeys.LIGHT_THEME` / `DARK_THEME` / `FONT_POINT_SIZE`;
      `PreferencesRepository.setLightTheme/setDarkTheme/setFontPointSize`; expose `lightThemeId`
      (default `searchmob-light`), `darkThemeId` (default `searchmob-dark`), `fontPointSize`
      (default 12, bounds 8-24) on `UserPreferences`. Defaults reuse the existing SearchMob look so
      the default appearance is unchanged.

## Font size

- [x] `ui/theme/Type.kt` + `Theme.kt`: build a scaled `Typography` from the base by `fontPointSize/12f`
      and pass it to `MaterialTheme`, so result and interface text scale together.

## Compose controls

- [x] `ui/settings/SettingsScreen.kt`: keep the Light/Dark/Follow-system radios + Material You toggle;
      add a "Light theme" selector, a "Dark theme" selector (each listing only that mode's themes),
      and A-/A+ font-size step buttons (2pt steps, 12pt default, bounded) showing the current size.
      Add test tags. Wire to the ViewModel/repository.

## Served page

- [x] `server/SearchServer.kt`: emit one `[data-theme="<id>"]` block per theme; update `THEME_INIT_JS`
      and `THEME_TOGGLE_JS` to use `sm-theme` (mode) + `sm-light-theme` + `sm-dark-theme` and resolve
      slot -> id pre-paint; add a theme picker + A-/A+ to `renderSettingsPage`. The home-page scope
      select now nests inside the search box via a hidden `/scope` form (`form=`), matching desktop.
- [x] `server/SearchServer.kt`: set root font size in points, convert component px font-sizes to `rem`
      so font scale cascades; persist + restore `sm-font` pre-paint.

## Credits

- [x] Add/extend a CREDITS (or third-party licenses) notice listing the nine reused palettes and their
      upstream licenses; the two accessibility themes are noted as original.

## Verify + ship

- [x] `./gradlew ktlintCheck :app:lintDebug :app:testDebugUnitTest :app:assembleDebug` green.
- [x] New tests: `resolveActiveTheme` truth table; `colorSchemeFor` role mapping; scaled `Typography`
      grows with `fontPointSize`; each served theme id emits its `[data-theme]` block; a11y themes meet
      the AA/AAA contrast threshold; theme-id list matches desktop.
- [ ] Emulator: cycle the themes in the app and on the served page; confirm every element recolours,
      the quick toggle swaps slots, font sizes reflow cleanly, and the a11y themes read as high
      contrast. Screenshot-review per the release-verification procedure.
- [ ] Ship as part of the RC feature pile (theming + i18n + engine-status + media-intent); one `-rc`
      tag for the pile, not a per-feature GA.
- [x] Add a `## [Unreleased]` CHANGELOG entry.
