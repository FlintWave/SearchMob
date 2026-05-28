# theming Specification

## Purpose
TBD - created by archiving change add-search-ui-and-theming. Update Purpose after archive.
## Requirements
### Requirement: Light, dark, and follow-system theming
The app SHALL support a light color scheme and a dark color scheme, and SHALL support a Follow-system
mode in which the active scheme tracks the device's system light/dark setting. Every app surface
SHALL render correctly in both light and dark schemes.

#### Scenario: Follow-system tracks the system setting
- **WHEN** the theme mode is Follow system and the device switches between light and dark
- **THEN** the app's active color scheme switches to match the system setting

#### Scenario: Both schemes render correctly
- **WHEN** the app is rendered in either the light or the dark scheme
- **THEN** all surfaces render with the corresponding scheme's colors

### Requirement: User theme override takes precedence over the system
When the user has explicitly chosen Light or Dark as the theme mode, the app SHALL use that scheme
regardless of the device's system light/dark setting. The explicit user override MUST take
precedence over the system setting.

#### Scenario: Explicit dark overrides a light system
- **WHEN** the user has selected Dark and the device system setting is light
- **THEN** the app renders in the dark scheme

#### Scenario: Explicit light overrides a dark system
- **WHEN** the user has selected Light and the device system setting is dark
- **THEN** the app renders in the light scheme

### Requirement: Material You dynamic color on API 31+
On Android API 31 and above, when the dynamic-color preference is enabled, the app SHALL derive its
color scheme from the system's Material You dynamic palette. On devices below API 31, or when
dynamic color is disabled, the app SHALL fall back to its built-in light/dark color schemes without
error.

#### Scenario: Dynamic color applied on API 31+
- **WHEN** the device is API 31+ and dynamic color is enabled
- **THEN** the app's color scheme is derived from the system dynamic palette

#### Scenario: Graceful fallback below API 31
- **WHEN** the device is below API 31 (e.g. API 26) and dynamic color is enabled
- **THEN** the app falls back to its built-in light/dark scheme without crashing

#### Scenario: Light/dark selection still governs dynamic color
- **WHEN** dynamic color is enabled and the user has selected Light or Dark
- **THEN** the dynamic palette is applied in the user-selected light or dark variant

### Requirement: Adequate contrast and accessibility
Both the built-in light and dark color schemes SHALL meet adequate text/background contrast for
accessibility (at least WCAG AA contrast for body text), and the UI SHALL respect the system font
scale.

#### Scenario: Contrast meets accessibility threshold
- **WHEN** body text is rendered in either the light or dark scheme
- **THEN** the text/background contrast meets at least the WCAG AA threshold

#### Scenario: System font scale is respected
- **WHEN** the user has increased the system font scale
- **THEN** the app's text scales accordingly without clipping critical controls

