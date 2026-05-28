# app-shell Specification

## Purpose
TBD - created by archiving change bootstrap-project-scaffold. Update Purpose after archive.
## Requirements
### Requirement: Application launches to a home screen
The application SHALL install on Android 8.0 (API 26) and above and, when launched, display a
Compose-based home screen without crashing. The home screen is a placeholder in this phase and
serves as the surface later phases extend.

#### Scenario: Cold launch shows the home screen
- **WHEN** the user installs the debug build and taps the app icon
- **THEN** the app process starts and renders the home screen content within the activity

#### Scenario: Launch on minimum supported OS
- **WHEN** the app is launched on a device running Android 8.0 (API 26)
- **THEN** the app launches successfully and does not crash

### Requirement: Material 3 theme honors the system light/dark setting
The application SHALL apply a Material 3 theme whose color scheme follows the system dark-mode
setting. On Android 12 (API 31) and above it SHALL use dynamic color when available; below API 31
it SHALL fall back to a bundled static light and dark color scheme.

#### Scenario: System dark mode yields a dark UI
- **WHEN** the device system setting is dark mode and the app is launched
- **THEN** the app renders using the dark color scheme

#### Scenario: System light mode yields a light UI
- **WHEN** the device system setting is light mode and the app is launched
- **THEN** the app renders using the light color scheme

#### Scenario: Dynamic color on supported OS
- **WHEN** the app runs on Android 12+ with dynamic color available
- **THEN** the theme derives its colors from the system dynamic palette

### Requirement: App requests no permissions and performs no network or storage I/O
In this phase the application SHALL declare no runtime or dangerous permissions, make no network
requests, and persist no user data, preserving the privacy baseline until features are added
deliberately in later phases.

#### Scenario: Manifest declares no dangerous permissions
- **WHEN** the built APK manifest is inspected
- **THEN** it declares no `dangerous`-level permissions and no `INTERNET` usage beyond what the
  toolchain requires for debug tooling

