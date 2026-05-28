# update-check Specification

## Purpose
TBD - created by archiving change add-update-check. Update Purpose after archive.
## Requirements
### Requirement: The update check is on by default and can be turned off
The application SHALL provide a single boolean preference `updateCheckEnabled` that controls whether the
launch-time update check runs. It SHALL default to ON (true) and SHALL be persisted so the choice
survives app restarts and reboots. The preference SHALL be observable so the Settings toggle and the
launch-time check track it without a relaunch. When it is off, the application SHALL NOT make the update
network call.

#### Scenario: Default is on
- **WHEN** the app is launched for the first time (no stored value)
- **THEN** the update check is enabled

#### Scenario: Choice persists
- **WHEN** the user turns the update check off and later relaunches the app
- **THEN** the stored value is still off

#### Scenario: Off disables the network call
- **WHEN** the update check preference is off and the app launches
- **THEN** no request is made to GitHub and the check stamps no timestamp

### Requirement: The check queries GitHub Releases through the privacy proxy and is fail-soft
When enabled and due, the application SHALL GET the GitHub Releases "latest" endpoint through the shared
privacy-proxy HTTP client (no cookies, stripped headers, rotated User-Agent), with a short timeout
(about 4 seconds) and a bounded response body. It SHALL parse the release `tag_name` and `html_url`. Any
HTTP error, timeout, malformed JSON, or malformed tag SHALL result in no update being reported, without
throwing, and SHALL NOT block app startup or the search UI. The call SHALL send no query and no user or
device identifier.

#### Scenario: Newer release parsed
- **WHEN** the endpoint returns a valid release whose tag parses to a version code greater than the
  running build's
- **THEN** the app reports that an update is available with the new version name and release URL

#### Scenario: HTTP error fails soft
- **WHEN** the endpoint returns an HTTP error
- **THEN** no update is reported and nothing throws

#### Scenario: Timeout fails soft
- **WHEN** the endpoint does not respond within the short timeout
- **THEN** no update is reported and nothing throws

#### Scenario: Malformed response fails soft
- **WHEN** the endpoint returns malformed JSON or a malformed tag
- **THEN** no update is reported and nothing throws

### Requirement: The latest version is compared by the build's version-code formula
The application SHALL convert the release tag `YY.MM.VV` (optionally prefixed with `v`) into a version
code using the same formula the build uses, `YY*10000 + MM*100 + VV`, and SHALL report an update as
available only when that code is strictly greater than the running build's version code, read from the
platform `PackageInfo`.

#### Scenario: Newer tag is an update
- **WHEN** the latest tag's version code is greater than the running build's
- **THEN** an update is available

#### Scenario: Equal or older tag is not an update
- **WHEN** the latest tag's version code is equal to or less than the running build's
- **THEN** no update is available

### Requirement: The check runs at most about once a day
The application SHALL store the timestamp of the last update-check attempt and SHALL perform a network
check only when at least about 24 hours have elapsed since that timestamp. It SHALL update the timestamp
after each attempt, whether the attempt succeeds or fails, so a failing check does not repeat on every
launch. A never-checked state (timestamp 0) SHALL be treated as due.

#### Scenario: Due when never checked
- **WHEN** no check has ever run
- **THEN** a check is due

#### Scenario: Not due within the interval
- **WHEN** less than about 24 hours have elapsed since the last attempt
- **THEN** no network check is made

#### Scenario: Throttle advances on failure
- **WHEN** an enabled, due check is attempted and fails
- **THEN** the last-check timestamp is still updated so the next launch does not retry immediately

### Requirement: An available update prompts the user without auto-installing
When an update is available and the user is past first-run onboarding, the application SHALL show an
in-app prompt stating the new version, with a control that opens the release page via an external view
intent and a control that dismisses the prompt. The application SHALL NOT open the browser without an
explicit tap and SHALL NOT auto-download or auto-install any artifact. When the release URL is missing,
the prompt SHALL fall back to the project's releases page.

#### Scenario: Prompt shown for an available update
- **WHEN** the launch-time check finds a strictly newer release
- **THEN** the app shows an "Update available" prompt with the new version

#### Scenario: Open releases requires a tap
- **WHEN** the user taps "Open releases page"
- **THEN** the app opens the release URL externally and dismisses the prompt

#### Scenario: Dismiss without updating
- **WHEN** the user taps "Not now"
- **THEN** the prompt is dismissed and nothing is downloaded or installed

### Requirement: The update check is disclosed in the privacy copy
The in-app About/privacy text, the README, and SECURITY.md SHALL state that, in addition to the searches
the user runs, the app makes an optional, once-a-day update check to GitHub that is on by default,
routed through the privacy proxy, and can be turned off in Settings. The no-telemetry, no-analytics, and
no-identifier claims SHALL remain accurate.

#### Scenario: Outbound-traffic claim is truthful
- **WHEN** a user reads the About/privacy copy, the README, or SECURITY.md
- **THEN** the description of outbound traffic includes the optional update check, and the
  no-telemetry/no-analytics/no-identifier claims are still stated

