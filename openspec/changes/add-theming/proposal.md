## Why

The app ships a single light palette and a single dark palette, chosen by a light/dark/system control
(plus optional Material You dynamic color). Users who spend hours in a search tool want their own look,
and some need a high-contrast or larger-text option to read comfortably. Today there is no way to pick
a different palette, size the text, or get a vetted accessibility theme. This adds a real theme
library, a picker, and two quality-of-life controls (font size and an accessibility high-contrast
option) without disturbing the store-nothing, owner-first posture. It mirrors the desktop app's
`add-theming` change so both apps share the same themes and behaviour.

## What Changes

- Add a library of named themes (a slate of nine plus two accessibility themes), each a full palette
  applied through the existing Material 3 `ColorScheme` (Compose) and CSS-variable (served page)
  layers. The two accessibility themes are verified to meet WCAG AA/AAA contrast and serve as the
  app's high-contrast option.
- Keep the existing light/dark/system control unchanged, and add a full theme picker that chooses
  which named theme fills the light slot and which fills the dark slot (a "two-slot" model layered on
  the existing `ThemeMode`). The quick toggle keeps swapping between those two slots.
- Add a font-size control: a comfortable 12pt-equivalent base with step buttons that raise or lower
  the size by 2pt (bounded), resizing interface and result text together. The choice is remembered.
- Apply all of the above to the Compose app and the served page, matching the desktop GUI and desktop
  served page so the look is consistent across all four surfaces.
- Reused third-party palettes are credited with their upstream licenses in a credits notice.

## Capabilities

### New Capabilities
- `theming`: a library of named themes plus font-size and accessibility controls, selectable per
  light/dark slot, applied consistently across the Compose app and the served page (and matching the
  desktop surfaces), and persisted as a local UI preference.

### Modified Capabilities
<!-- None in contract. The existing light/dark/system control and Material You toggle keep their
meaning; the named-theme slot selection and font size are additive preferences. -->

## Non-goals

- User-authored or importable custom themes: the library is a fixed, curated slate this change.
- Per-element or syntax-style theming: SearchMob has no code editor; themes color the app chrome and
  result list.
- Computing a high-contrast variant of every theme: high contrast is delivered by the two dedicated
  accessibility themes, not a per-theme contrast booster.
- Replacing Material You dynamic color: it remains an independent option; named themes apply when
  dynamic color is off.
- Syncing the chosen theme between the app and a LAN browser client: each surface persists its own
  local preference.

## Impact

- Modified: `ui/theme/` (`Color.kt`/`Theme.kt`/`ThemeMode.kt`/`Type.kt`: a theme registry feeding
  Material 3 `ColorScheme`s, font-scaled `Typography`), `ui/settings/SettingsScreen.kt` (theme picker
  + font-size control next to the existing radios), `data` preferences (`PreferencesRepository`,
  `PreferenceKeys`, `UserPreferences`: add `lightThemeId`, `darkThemeId`, `fontPointSize`),
  `server/SearchServer.kt` (a `[data-theme="<id>"]` block per theme + the picker + font-scale). A
  credits/licenses notice lists each reused palette.
- No new dependencies, no new outbound calls, no telemetry. Theme/font choices are local UI prefs
  (DataStore / served-page localStorage), consistent with store-nothing-by-default. Owner/LAN gating
  unchanged.
