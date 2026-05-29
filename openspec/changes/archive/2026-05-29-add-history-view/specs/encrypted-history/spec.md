## ADDED Requirements

### Requirement: In-app history viewing and pruning
The application SHALL provide an in-app screen, reachable from Settings, that lists the saved search
history newest-first with each entry's time, while history is enabled. It SHALL let the user delete a
single entry and clear all entries. Reads and writes SHALL run off the main thread. When history is
disabled the screen SHALL show an empty state rather than any stored queries (the store-nothing default
is unchanged).

#### Scenario: View saved queries
- **WHEN** history is enabled and the user has run searches, and they open the History screen
- **THEN** their past queries are listed newest-first

#### Scenario: Delete a single entry
- **WHEN** the user deletes one entry
- **THEN** that entry is removed from the encrypted store and no longer listed, and the others remain

#### Scenario: Clear all
- **WHEN** the user clears history
- **THEN** all entries are removed

#### Scenario: Disabled shows nothing
- **WHEN** history is disabled
- **THEN** the screen lists no queries

### Requirement: History export and import for backup and device transfer
The application SHALL export the saved history to a JSON file and import it from a JSON file via the
system document picker (Storage Access Framework), so a user can back it up or move it to a new device.
Export SHALL write the non-expired entries. Import SHALL merge the entries from the chosen file into the
store and SHALL require history to be enabled, consistent with the store-nothing default. No history
data SHALL be sent over the network; the only I/O is to the user-chosen local file. No additional
Android storage permission SHALL be required.

#### Scenario: Export then import round-trips
- **WHEN** the user exports history to a file and later imports that file
- **THEN** the same queries are present in the store

#### Scenario: Import requires history enabled
- **WHEN** the user imports a file while history is disabled
- **THEN** no entries are stored (history must be turned on first)

#### Scenario: Local file only
- **WHEN** the user exports or imports history
- **THEN** the data is read from or written to the chosen local file and nothing is sent over the network
