## ADDED Requirements

### Requirement: Zero-knowledge mode is an optional opt-in
The system SHALL provide an optional zero-knowledge mode, OFF by default, that wraps the DEK with a
Key-Encryption-Key (KEK) derived from a user passphrase instead of with the Android Keystore key.
Enabling or disabling the mode SHALL re-wrap the same existing DEK and MUST NOT re-encrypt or destroy
already-stored data.

#### Scenario: Zero-knowledge mode off by default
- **WHEN** the app is freshly installed and the user has changed no settings
- **THEN** zero-knowledge mode is disabled and the DEK is Keystore-wrapped

#### Scenario: Enabling re-wraps the same DEK
- **WHEN** the user enables zero-knowledge mode and sets a passphrase
- **THEN** the existing DEK is re-wrapped with the passphrase-derived KEK and previously stored data
  remains intact and readable while unlocked

### Requirement: KEK derived from passphrase via Argon2id with per-install salt
The system SHALL derive the KEK from the user passphrase using Argon2id (via `argon2kt`) with a
random per-install salt, and SHALL wrap the DEK with that KEK using AES-256-GCM. The per-install salt
SHALL be a random value and MUST NOT be a device identifier.

#### Scenario: Derive then unwrap with correct passphrase succeeds
- **WHEN** the DEK is wrapped with a KEK derived from a passphrase and is later unwrapped using the
  same passphrase and stored salt
- **THEN** the unwrapped DEK bytes are byte-identical to the original DEK

#### Scenario: Salt is random, not a device identifier
- **WHEN** zero-knowledge mode is enabled
- **THEN** the stored salt is a randomly generated value and is not derived from any device identifier

### Requirement: Wrong passphrase is rejected, never silently accepted
The system SHALL reject an incorrect passphrase: a wrong passphrase yields a wrong KEK and the
AES-256-GCM unwrap SHALL fail authentication rather than returning incorrect or partial key material.

#### Scenario: Wrong passphrase fails unwrap
- **WHEN** an unwrap is attempted with an incorrect passphrase
- **THEN** the GCM authentication fails, no DEK is produced, and data remains locked

### Requirement: Unrecoverable-data warning is required before enabling
The system SHALL require an explicit, unmissable confirmation before enabling zero-knowledge mode,
stating that data becomes permanently UNRECOVERABLE if the passphrase is lost and is unreadable until
unlock. There SHALL be no passphrase recovery, escrow, or reset path.

#### Scenario: Enabling requires explicit warning confirmation
- **WHEN** the user attempts to enable zero-knowledge mode
- **THEN** an explicit warning that lost-passphrase data is permanently unrecoverable is shown and the
  user must confirm it before the mode is enabled

#### Scenario: No recovery path exists
- **WHEN** the passphrase is lost
- **THEN** the system provides no recovery, escrow, or reset path and the data remains unrecoverable

### Requirement: Unlocked DEK is held in memory only and evicted
In zero-knowledge mode the system SHALL hold the decrypted DEK in process memory only while unlocked
and SHALL evict it (zeroing the key bytes and closing the encrypted database handle) on explicit lock,
on app moving to background, and on an inactivity timeout. The plaintext DEK and the derived KEK SHALL
NOT be persisted.

#### Scenario: DEK evicted when app goes to background
- **WHEN** the app is unlocked in zero-knowledge mode and then moves to the background
- **THEN** the in-memory DEK is zeroed and dropped and the encrypted database handle is closed

#### Scenario: DEK evicted on inactivity timeout
- **WHEN** the app is unlocked and the inactivity timeout elapses
- **THEN** the in-memory DEK is evicted and the app returns to a locked state

#### Scenario: Locked state blocks history access
- **WHEN** the app is locked in zero-knowledge mode
- **THEN** history reads and writes are unavailable until the user re-enters the passphrase to unlock
