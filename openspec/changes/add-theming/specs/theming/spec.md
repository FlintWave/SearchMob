## ADDED Requirements

### Requirement: Named theme library

The system SHALL provide a fixed library of named themes consisting of a curated slate plus two
accessibility themes (one light, one dark). Each theme SHALL define a complete palette covering at
least background, surface, primary text, muted/secondary text, accent/link, and border roles, and
SHALL be applied through the surface's existing styling layer so every interface element honours it.

#### Scenario: Selecting a theme recolours the whole interface

- **WHEN** the user selects a named theme
- **THEN** the background, panels, result cards, text, links, and borders all change to that theme's
  palette, with no element left on the previous colours

### Requirement: Quick light/dark toggle preserved with per-slot theme selection

The system SHALL keep the existing light/dark/system control, and SHALL let the user choose which
named theme fills the light slot and which fills the dark slot. The resolved theme SHALL be the light
slot when the mode resolves to light and the dark slot when it resolves to dark, so toggling swaps
between the two chosen themes.

#### Scenario: Toggling swaps between the chosen light and dark themes

- **WHEN** the user has chosen a light-slot theme and a dark-slot theme and uses the quick toggle
- **THEN** the interface switches to the other slot's chosen theme rather than to a single fixed
  light or dark palette

#### Scenario: System mode follows the OS appearance

- **WHEN** the mode is set to follow the system and the OS appearance is dark
- **THEN** the dark-slot theme is applied, and the light-slot theme is applied when the OS appearance
  is light

### Requirement: Font-size control

The system SHALL provide a font-size control with a comfortable default base size (12pt) and step
buttons that raise or lower the size by a fixed 2pt step within a bounded range. Changing it SHALL
resize interface and result text together without truncating or breaking the layout, and the chosen
size SHALL be remembered between uses.

#### Scenario: Increasing the font size enlarges the text

- **WHEN** the user presses the increase-size button
- **THEN** interface labels and result text render 2pt larger and the layout reflows to remain usable

#### Scenario: The size is bounded

- **WHEN** the size is already at the smallest or largest allowed value
- **THEN** the corresponding step button does not reduce or enlarge the text further

### Requirement: Accessibility themes meet contrast standards

The system SHALL include a light and a dark accessibility theme whose primary text meets at least WCAG
AA contrast (targeting AAA for body text) against its background, and these themes SHALL be the app's
high-contrast option.

#### Scenario: The accessibility theme is high contrast

- **WHEN** the user selects an accessibility theme
- **THEN** primary text is rendered at high contrast against the background, meeting WCAG AA or better

### Requirement: Theme preferences persist locally

The system SHALL persist the chosen mode, light-slot theme, dark-slot theme, and font size as local UI
preferences, and SHALL restore them on next launch. These preferences SHALL NOT be transmitted off the
device and SHALL NOT be treated as search data.

#### Scenario: Choices survive a restart

- **WHEN** the user sets a theme and font size and restarts the app
- **THEN** the same theme and font size are applied on launch without re-selection

### Requirement: Consistent theming across surfaces

The system SHALL offer the same theme library, picker, and font-size control on the desktop GUI, the
desktop served page, the Android app, and the Android served page, so the available themes and
behaviour match across surfaces.

#### Scenario: The same themes are available on the served page

- **WHEN** the user opens the served page
- **THEN** the same named themes and font-size control are available there as in the Android app

### Requirement: Third-party palette attribution

The system SHALL credit each reused third-party palette together with its upstream license in a
credits or licenses notice shipped with the app.

#### Scenario: Reused palettes are credited

- **WHEN** a theme reuses a third-party palette
- **THEN** that palette and its license are listed in the app's credits/licenses notice
