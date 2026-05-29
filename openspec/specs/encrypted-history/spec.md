# encrypted-history Specification

## Purpose
TBD - created by archiving change add-encrypted-storage. Update Purpose after archive.
## Requirements
### Requirement: Search history is off by default
The system SHALL NOT persist any search history unless the user has explicitly opted in. With history
disabled, current-session search data SHALL remain in memory only and no history database file SHALL
be created or opened.

#### Scenario: No history stored when disabled
- **WHEN** the user performs searches with history disabled (the default)
- **THEN** no history database file exists and no query is persisted to disk

#### Scenario: Default install does not opt in
- **WHEN** the app is freshly installed and the user has changed no settings
- **THEN** history is disabled and the app stores nothing about past queries

#### Scenario: Enabling history creates the encrypted database
- **WHEN** the user opts in to search history
- **THEN** an encrypted history database is created lazily and subsequent searches are recorded

### Requirement: History database is SQLCipher-encrypted via Room
When history is enabled, the system SHALL store it in a SQLCipher-encrypted database accessed through
Room using a `SupportFactory`, keyed with the shared DEK. The system SHALL use the
`net.zetetic:sqlcipher-android` artifact and MUST NOT use the deprecated
`android-database-sqlcipher` artifact. The entire database file (including indices) SHALL be
encrypted at rest.

#### Scenario: History contents unreadable on disk
- **WHEN** history is enabled, queries are recorded, and the database file is inspected
- **THEN** the recorded queries do not appear in plaintext in the file

#### Scenario: History requires the correct DEK to open
- **WHEN** the database is opened with the correct DEK as the SQLCipher passphrase
- **THEN** the history rows are readable, and opening with an incorrect key fails

#### Scenario: Uses the supported SQLCipher artifact
- **WHEN** the history storage is built
- **THEN** it depends on `net.zetetic:sqlcipher-android` and not on `android-database-sqlcipher`

### Requirement: History entries expire by TTL
The system SHALL apply a time-to-live to history entries so that entries older than the configured
TTL are treated as expired. Expired entries SHALL NOT be returned by reads, and the system SHALL
remove expired entries opportunistically (e.g. on open or insert) without using a background timer or
wake-lock.

#### Scenario: Expired entries are not returned
- **WHEN** a history entry's age exceeds the TTL and history is read
- **THEN** the expired entry is not included in the results

#### Scenario: Within-TTL entries are returned
- **WHEN** a history entry's age is within the TTL and history is read
- **THEN** the entry is included in the results

#### Scenario: Expiry needs no background job
- **WHEN** expired entries are removed
- **THEN** removal occurs inline with a user-triggered operation (open/insert/read) and no background
  timer or wake-lock is used

### Requirement: User can purge history
The system SHALL provide an explicit "clear history" operation that deletes all stored history
entries. The system SHALL also delete the history database file when the user disables history.

#### Scenario: Clear history removes all entries
- **WHEN** the user invokes "clear history"
- **THEN** all history entries are deleted and a subsequent read returns no entries

#### Scenario: Disabling history deletes the database file
- **WHEN** the user disables history after it was enabled
- **THEN** the history database file is deleted from the device

### Requirement: History is never synced off-device
The system SHALL keep all history local to the device and MUST NOT sync, back up, upload, or
otherwise transmit history off-device. No new network permission SHALL be required for history.

#### Scenario: No off-device transmission of history
- **WHEN** history is enabled and entries are recorded
- **THEN** no history data is sent off-device and no cloud backup or sync of history occurs

#### Scenario: No new permission for history
- **WHEN** the history feature is added
- **THEN** it introduces no new network or storage permission

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

