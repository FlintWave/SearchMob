## ADDED Requirements

### Requirement: Network mode is an opt-in toggle that is off by default
The application SHALL provide a single boolean preference `networkAccessEnabled` that controls whether
the local search server is reachable from the device's network. It SHALL default to OFF (false) and
SHALL be persisted so it survives app restarts and reboots. The preference SHALL be observable so the
server binding and the UI track it without a relaunch.

#### Scenario: Default is off
- **WHEN** the app is launched for the first time (no stored value)
- **THEN** network mode is off and the server is reachable only from the device

#### Scenario: Choice persists
- **WHEN** the user enables network mode and later relaunches the app
- **THEN** the stored value is still enabled

### Requirement: The server binds to loopback by default and to all interfaces when network mode is on
The embedded HTTP server SHALL bind to loopback ("127.0.0.1") when network mode is off and SHALL bind
to all interfaces ("0.0.0.0") when network mode is on. When the preference changes while the service is
running, the service SHALL rebind the embedded server to the new host without dropping the foreground
service.

#### Scenario: Loopback when off
- **WHEN** network mode is off
- **THEN** the server binds to 127.0.0.1 and is not reachable from other machines

#### Scenario: All interfaces when on
- **WHEN** network mode is on
- **THEN** the server binds to 0.0.0.0 and is reachable at the device's network address on the bound port

#### Scenario: Rebind on change
- **WHEN** the user toggles network mode while the service is running
- **THEN** the embedded server is restarted on the new host and the foreground service stays up

### Requirement: Enabling network mode requires confirming an explicit warning
Turning network mode ON SHALL first present a warning dialog and SHALL persist the preference as ON
only if the user confirms. Cancelling the dialog SHALL leave network mode OFF. Turning network mode
OFF SHALL NOT require confirmation. The warning SHALL state that the feature is only for protected,
trusted networks, must not be used on open or public Wi-Fi, and that the server has no password.

#### Scenario: Confirm enables
- **WHEN** the user turns the toggle on and confirms the warning
- **THEN** network mode is persisted as enabled

#### Scenario: Cancel leaves it off
- **WHEN** the user turns the toggle on and cancels the warning
- **THEN** network mode remains disabled and nothing is persisted as enabled

#### Scenario: Turning off is immediate
- **WHEN** the user turns the toggle off
- **THEN** network mode is persisted as disabled with no confirmation dialog

### Requirement: The reachable address is shown with copy when network mode is on
When network mode is on, Settings SHALL show the reachable URL `http://<device-LAN-IP>:<port>/` using
a non-loopback, site-local IPv4 address discovered via `NetworkInterface`, with a tap-to-copy control
and a copy confirmation. When no such address is available, Settings SHALL show a clear no-address
state instead. Discovery SHALL NOT require any new dangerous permission.

#### Scenario: Address shown when reachable
- **WHEN** network mode is on and the device has a site-local IPv4 address on the bound port
- **THEN** Settings shows http://<that-ip>:<port>/ with a copy control

#### Scenario: No-address state
- **WHEN** network mode is on but no site-local IPv4 address can be determined
- **THEN** Settings shows that no network address is available

### Requirement: Network mode has no authentication
When network mode is on, the server SHALL serve any client that can reach the device on the bound port,
without authentication. This caveat SHALL be documented in the in-app warning and in `SECURITY.md`.

#### Scenario: No auth challenge
- **WHEN** network mode is on and another machine on the network requests a search
- **THEN** the server serves the request without prompting for any credential
