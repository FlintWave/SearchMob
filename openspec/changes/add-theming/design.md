# Design: theming (Android)

Mirrors the desktop `add-theming` change (same slate, same two-slot model, same font control). This
doc covers the Android specifics. Exact hex palettes are the locked slate in
`~/.claude/plans/searchmob-theming-research.md`, copied verbatim into the registries.

## Theme model (two-slot, layered on the existing mode)

Keep `ThemeMode` = `LIGHT` | `DARK` | `SYSTEM` (unchanged). Add `lightThemeId` (default `github-light`)
and `darkThemeId` (default `one-dark`) to `UserPreferences`. Resolved theme: `SYSTEM` ->
(systemDark ? darkThemeId : lightThemeId); `LIGHT` -> lightThemeId; `DARK` -> darkThemeId. The quick
light/dark/system control is unchanged; it now swaps between the two chosen themes. Picking a theme in
the settings picker sets its slot and switches `ThemeMode` to that slot's mode so it shows at once.
Material You dynamic color stays an independent toggle: when on, it overrides the named theme (existing
behaviour); when off, the resolved named theme applies.

## The slate (ids, mode)

Light: `github-light`, `catppuccin-latte`, `rose-pine-dawn`, `paper-white`. Dark: `one-dark`,
`dracula`, `tokyo-night`, `catppuccin-mocha`, `gruvbox`, `nord`, `obsidian-slate`. Each theme defines
six roles; map them onto a Material 3 `ColorScheme` (background/surface from bg/surface; onBackground/
onSurface from primary text; outline + surfaceVariant from border/muted; primary + the link color from
accent; derive the remaining scheme slots consistently). `obsidian-slate`/`paper-white` are custom AAA
palettes (no attribution); the other nine reuse upstream palettes and are credited.

## Per-surface implementation

### Compose (`ui/theme/`, `ui/settings/SettingsScreen.kt`, `data` prefs)
- `ui/theme/Color.kt` + `Theme.kt`: add a `Theme` registry (id, display name, mode, the role colors,
  optional credit) and a `colorSchemeFor(theme)` that builds a Material 3 `ColorScheme`. `ThemeMode`
  enum unchanged. `SearchMobTheme(...)` resolves the active theme via the two-slot rule and uses its
  scheme unless dynamic color is on.
- Font size: `fontPointSize` int (default 12, bounds 8-24). Build a scaled `Typography` from
  `Type.kt`'s base by multiplying each style's `fontSize`/`lineHeight` by `fontPointSize / 12f`, and
  pass it to `MaterialTheme`. (Keep honoring the system fontScale; this multiplies on top.)
- `PreferenceKeys`: add `LIGHT_THEME`, `DARK_THEME`, `FONT_POINT_SIZE`. `PreferencesRepository`:
  `setLightTheme`, `setDarkTheme`, `setFontPointSize`; expose on `UserPreferences`.
- `SettingsScreen` Theme section: keep the Light/Dark/Follow-system radios and the Material You toggle;
  add a "Light theme" selector (light themes), a "Dark theme" selector (dark themes), and A-/A+
  font-size step buttons showing the current pt. Test tags for each.

### Served page (`server/SearchServer.kt`)
- `PAGE_CSS` already uses CSS custom properties with `[data-theme="light"]`/`[data-theme="dark"]`.
  Generate one `[data-theme="<id>"]` block per theme from the shared palette table.
- `THEME_INIT_JS` / `THEME_TOGGLE_JS`: store `sm-theme` (mode) + `sm-light-theme` + `sm-dark-theme` in
  localStorage; init resolves mode -> slot -> id and sets `data-theme="<id>"` on `<html>` pre-paint;
  the toggle flips mode and applies the slot id. Add a theme picker + A-/A+ to `renderSettingsPage`.
- Font size: set the root font size in points (`html{font-size:<n>pt}`), convert component px font
  sizes to `rem`, persist `sm-font`, restore pre-paint.

## Persistence / privacy

Mode, the two slot ids, and font size are local UI preferences (DataStore / served-page localStorage),
never transmitted, never search data. Consistent with store-nothing-by-default. No new network calls or
dependencies.

## Credits

The nine reused palettes are credited with their upstream licenses in a CREDITS/third-party notice;
the two accessibility palettes are original to SearchMob.

## Testing

- Compose/unit: `resolveActiveTheme(mode, lightId, darkId, systemDark)` truth table; `colorSchemeFor`
  maps the role colors onto the scheme; the scaled `Typography` grows with `fontPointSize`; the two
  a11y themes meet the AA/AAA contrast threshold (relative-luminance check from hex).
- Served: each theme id emits a matching `[data-theme="<id>"]` block; init/toggle JS present; the
  font-scale attribute and rem conversion render; `/settings` exposes the picker.
- Parity: the theme id list matches the desktop registry.
- UI (Compose test): the settings selectors switch the active theme and persist (via test tags).
