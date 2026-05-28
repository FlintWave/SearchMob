# settings-and-preferences Specification

## Purpose
TBD - created by archiving change add-search-ui-and-theming. Update Purpose after archive.
## Requirements
### Requirement: Preferences persist across restarts and reboots
The app SHALL persist all user preferences (theme mode, dynamic-color toggle, per-engine
enable/disable, search-history on/off) via Jetpack DataStore so they survive both app restarts and
device reboots. On launch the app SHALL restore the persisted preferences before rendering the
search and settings surfaces.

#### Scenario: Preference survives app restart
- **WHEN** the user changes a preference, fully closes the app, and relaunches it
- **THEN** the changed preference is restored from DataStore and reflected in the UI

#### Scenario: Preference survives device reboot
- **WHEN** the user changes a preference and the device is rebooted
- **THEN** the changed preference is restored from DataStore on next launch

### Requirement: Preferences apply immediately
When the user changes a preference, the app SHALL apply it immediately to the running UI without
requiring an app relaunch.

#### Scenario: Theme mode applies immediately
- **WHEN** the user changes the theme mode in settings
- **THEN** the running UI re-themes immediately without a relaunch

#### Scenario: Engine toggle applies immediately
- **WHEN** the user disables an engine in settings
- **THEN** subsequent searches exclude that engine without a relaunch

### Requirement: Theme and dynamic-color controls
The settings screen SHALL provide a theme-mode control with the choices Light, Dark, and Follow
system, and a Material You dynamic-color toggle. These controls SHALL drive the theming behavior and
persist as preferences.

#### Scenario: User selects a theme mode
- **WHEN** the user selects Light, Dark, or Follow system
- **THEN** the selection is persisted and applied to the running UI

#### Scenario: User toggles dynamic color
- **WHEN** the user toggles the Material You dynamic-color control
- **THEN** the toggle is persisted and the active color scheme updates accordingly

### Requirement: Per-engine enable/disable toggles
The settings screen SHALL present a toggle for each available search engine allowing the user to
enable or disable it. Disabled engines MUST be excluded from the fan-out on subsequent searches.

#### Scenario: User disables an engine
- **WHEN** the user disables a given engine and performs a search
- **THEN** that engine is not queried and contributes no results

#### Scenario: Engine toggles persist
- **WHEN** the user changes engine toggles and relaunches the app
- **THEN** the same engines are enabled/disabled as before the relaunch

### Requirement: Bring-your-own API key entry
The settings screen SHALL allow the user to enter, replace, and clear bring-your-own API keys for
Brave and Mojeek. Entered keys SHALL be persisted through the encrypted-preferences mechanism from
`add-encrypted-storage` and MUST NOT be written to logs or plaintext storage. When a key is present,
the corresponding engine SHALL use it for subsequent searches.

#### Scenario: User enters an API key
- **WHEN** the user enters a Brave or Mojeek API key and saves it
- **THEN** the key is persisted via encrypted preferences and used for that engine's subsequent searches

#### Scenario: User clears an API key
- **WHEN** the user clears a previously saved API key
- **THEN** the key is removed from encrypted storage and the engine reverts to its keyless behavior

#### Scenario: Keys are not exposed
- **WHEN** an API key is saved
- **THEN** the key is not written to logs or plaintext storage

### Requirement: Search-history controls
The settings screen SHALL provide a search-history on/off switch (defaulting to off, per the
store-nothing default) and a "clear history" action. Enabling history SHALL cause subsequent
searches to be recorded via `add-encrypted-storage`; disabling it SHALL stop new recording.
Triggering "clear history" SHALL purge all stored history entries.

#### Scenario: History defaults to off
- **WHEN** the app is installed and launched for the first time
- **THEN** search history is off and no searches are recorded

#### Scenario: User enables history
- **WHEN** the user turns on search history and performs a search
- **THEN** the search is recorded via the encrypted-storage history store

#### Scenario: User clears history
- **WHEN** the user triggers "clear history"
- **THEN** all stored history entries are purged

### Requirement: Zero-knowledge passphrase setup hand-off
The settings screen SHALL provide an entry point to set up zero-knowledge mode, handing off to
`add-encrypted-storage` to capture the passphrase. The UI SHALL explicitly warn the user that data
becomes unrecoverable if the passphrase is lost.

#### Scenario: User starts zero-knowledge setup
- **WHEN** the user selects the zero-knowledge setup entry point
- **THEN** the app hands off to the encrypted-storage passphrase setup flow

#### Scenario: Unrecoverability warning is shown
- **WHEN** the user is setting up the zero-knowledge passphrase
- **THEN** the UI explicitly warns that data is unrecoverable if the passphrase is lost

### Requirement: Device-setup-guidance entry point
The settings screen SHALL provide an entry point that opens the battery / OEM device-setup guidance
surface owned by `add-foreground-service`.

#### Scenario: User opens device-setup guidance
- **WHEN** the user selects the device-setup guidance entry point in settings
- **THEN** the app opens the battery/OEM guidance surface from `add-foreground-service`

### Requirement: Settings privacy
The settings surface SHALL NOT include any analytics, telemetry, or device-identifier collection.

#### Scenario: No analytics in settings
- **WHEN** the user interacts with the settings screen
- **THEN** no analytics or telemetry events are emitted and no device identifiers are collected

