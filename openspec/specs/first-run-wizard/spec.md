# first-run-wizard Specification

## Purpose
TBD - created by archiving change add-onboarding-and-widget. Update Purpose after archive.
## Requirements
### Requirement: First-run wizard appears once and is skippable
The application SHALL show a multi-page onboarding wizard on first launch and SHALL NOT show it on
subsequent launches once it has been completed or skipped. The user SHALL be able to skip the wizard
at any page. Completion/skip state SHALL be persisted so it survives app restarts and reboots.

#### Scenario: Wizard shows on first launch
- **WHEN** the app is launched for the first time (no completion flag stored)
- **THEN** the onboarding wizard is shown

#### Scenario: Wizard does not reappear after completion
- **WHEN** the user completes or skips the wizard and later relaunches the app
- **THEN** the wizard is not shown again and the app opens to the home screen

#### Scenario: Wizard can be skipped
- **WHEN** the user taps "Skip" on any wizard page
- **THEN** the wizard closes and the completion flag is persisted

### Requirement: Wizard guides through permissions
The wizard SHALL include a step for the notifications permission (Android 13+) and a step for the
battery-optimization exemption, each with a button that triggers the respective system prompt and
reflects the current granted/exempt state. It SHALL never auto-request; the user taps to proceed.

#### Scenario: Notifications step requests permission on tap
- **WHEN** the user taps the grant control on the notifications step on Android 13+
- **THEN** the system notifications permission prompt is shown

#### Scenario: Battery step offers the exemption on tap
- **WHEN** the user taps the control on the battery-optimization step and is not already exempt
- **THEN** the system battery-optimization exemption prompt is shown

#### Scenario: Already-granted state is reflected
- **WHEN** a permission/exemption is already granted when its step is shown
- **THEN** the step indicates it is already set and does not prompt again

### Requirement: Wizard guides setting the default search engine
The wizard SHALL include a step that presents the browser-setup guidance (see the
`browser-setup-guide` capability) for making SearchMob the browser's default search engine, including
a control to open the device browser at the SearchMob page.

#### Scenario: Default-search step opens the guide
- **WHEN** the user reaches the default-search step
- **THEN** the step shows the copyable SearchMob URL(s) and per-browser instructions

#### Scenario: Open-in-browser launches the page
- **WHEN** the user taps "Open in browser" on the default-search step
- **THEN** the device browser is launched at the SearchMob home URL on the bound loopback port

