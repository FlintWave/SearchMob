## ADDED Requirements

### Requirement: Per-engine search outcome

The system SHALL compute, for each search, a per-engine outcome distinguishing engines that
contributed results (with a count), engines that returned empty, and engines that failed (error or
timeout). This outcome SHALL be computed locally and SHALL NOT be sent off the device.

#### Scenario: A failing engine is recorded as failed, not silently empty

- **WHEN** one engine raises or times out while the others return results
- **THEN** the search still succeeds with the other engines' results AND the failing engine is
  recorded as failed rather than indistinguishable from an engine that simply found nothing

### Requirement: Owner-only engine status display

The system SHALL surface the per-engine outcome to the owner in the app and on the served page for
the loopback owner only. Network/LAN clients SHALL NOT receive engine status. The status SHALL NOT be
persisted.

#### Scenario: Owner sees degraded coverage

- **WHEN** fewer engines than configured respond for a search
- **THEN** the owner sees an unobtrusive indication that some engines did not respond

#### Scenario: A network client sees no engine status

- **WHEN** a LAN client loads the served results page
- **THEN** no per-engine status is shown to that client
