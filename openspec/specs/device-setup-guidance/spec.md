# device-setup-guidance Specification

## Purpose
TBD - created by archiving change add-foreground-service. Update Purpose after archive.
## Requirements
### Requirement: Battery-optimization exemption is detected and surfaced
The application SHALL detect whether it is exempt from battery optimization using
`PowerManager.isIgnoringBatteryOptimizations()` and SHALL surface the result to the user. When the
app is not exempt, it SHALL inform the user that battery optimization can cause the OS to kill the
always-on service.

#### Scenario: Not-exempt state is surfaced
- **WHEN** the app is not exempt from battery optimization
- **THEN** the app reports the non-exempt state and explains the always-on service may be killed

#### Scenario: Exempt state is surfaced
- **WHEN** the app is already exempt from battery optimization
- **THEN** the app reports the exempt state and does not prompt the user again

### Requirement: Battery-optimization exemption is user-initiated, never auto-granted
The application SHALL only request the battery-optimization exemption in response to an explicit
user action, by firing the `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` system intent. The
application SHALL NOT auto-grant, silently request, or assume the exemption. The app declares the
`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` permission for this prompt.

#### Scenario: User taps to request the exemption
- **WHEN** the user explicitly taps the control to grant the exemption
- **THEN** the app launches the `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` system prompt

#### Scenario: No automatic exemption request
- **WHEN** the app starts or the service starts without any user action
- **THEN** the app does not request or grant the battery-optimization exemption on its own

#### Scenario: User declines the exemption
- **WHEN** the user dismisses or declines the system exemption prompt
- **THEN** the app remains in the non-exempt state and does not retry without a new user action

### Requirement: Per-OEM autostart and never-sleep guidance
The application SHALL provide an in-app guidance surface that links to dontkillmyapp.com with
manufacturer-specific instructions for autostart / never-sleep settings, covering at least Samsung,
Xiaomi, OnePlus, and Huawei. The guidance SHALL warn the user that these vendor settings can reset
on firmware updates.

#### Scenario: Manufacturer-specific guidance is offered
- **WHEN** the user opens the device-setup guidance on a device whose manufacturer is one of
  Samsung, Xiaomi, OnePlus, or Huawei
- **THEN** the app surfaces guidance linking to the dontkillmyapp.com page for that manufacturer

#### Scenario: Firmware-reset warning is shown
- **WHEN** the user views the OEM autostart/never-sleep guidance
- **THEN** the guidance warns that these settings can be reset by firmware/OS updates

#### Scenario: Generic guidance for unlisted manufacturers
- **WHEN** the device manufacturer is not one of the explicitly covered vendors
- **THEN** the app still surfaces generic dontkillmyapp.com guidance rather than failing

