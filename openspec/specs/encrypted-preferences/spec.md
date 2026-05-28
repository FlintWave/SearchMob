# encrypted-preferences Specification

## Purpose
TBD - created by archiving change add-encrypted-storage. Update Purpose after archive.
## Requirements
### Requirement: Preferences are encrypted at rest with a DEK
The system SHALL persist application preferences via a Jetpack DataStore whose serialized contents
are encrypted with a random 256-bit Data Encryption Key (DEK) using AES-256-GCM. No preference value
(including BYO API keys and engine configuration) SHALL be written to disk in plaintext. The DEK
itself SHALL NOT be persisted in plaintext; only a wrapped (encrypted) DEK blob SHALL be stored.

#### Scenario: Preference values are unreadable on disk
- **WHEN** a preference value such as a BYO API key is written and the underlying DataStore file is
  inspected
- **THEN** the value does not appear in plaintext and the file contents are AES-256-GCM ciphertext

#### Scenario: Fresh DEK generated on first run
- **WHEN** the app starts for the first time with no existing DEK
- **THEN** a random 256-bit DEK is generated and a wrapped DEK blob is persisted, and the plaintext
  DEK is never written to disk

#### Scenario: Round-trip read returns the original value
- **WHEN** a preference is written and later read back in the same install
- **THEN** the decrypted value equals the originally written value

### Requirement: DEK is wrapped by an AES-256-GCM Android Keystore key
The system SHALL wrap the DEK using an AES-256-GCM key held in the `AndroidKeyStore`. The system MUST
NOT use the deprecated `androidx.security:security-crypto` library or `EncryptedSharedPreferences`;
it SHALL use the Android Keystore directly. Unwrapping a DEK blob that has been tampered with SHALL
fail (GCM authentication) rather than return incorrect key material.

#### Scenario: DEK wrap then unwrap yields the same key
- **WHEN** the DEK is wrapped with the Keystore key and subsequently unwrapped
- **THEN** the unwrapped DEK bytes are byte-identical to the original DEK

#### Scenario: Tampered wrapped blob is rejected
- **WHEN** a stored wrapped-DEK blob is modified and an unwrap is attempted
- **THEN** the unwrap fails with an authentication error and no key material is returned

#### Scenario: Deprecated EncryptedSharedPreferences is not used
- **WHEN** the storage layer is built
- **THEN** it depends on the Android Keystore directly and does not reference
  `androidx.security:security-crypto` or `EncryptedSharedPreferences`

### Requirement: StrongBox preferred with verified TEE fallback
The system SHALL request a StrongBox-backed Keystore key when
`PackageManager.FEATURE_STRONGBOX_KEYSTORE` is present, and SHALL fall back to a TEE-backed key on
`StrongBoxUnavailableException`. After generation the system SHALL verify and record the achieved
security level via `KeyInfo.getSecurityLevel()` (API 31+) or `isInsideSecureHardware()` on older
API levels.

#### Scenario: StrongBox used when feature present
- **WHEN** the device reports `FEATURE_STRONGBOX_KEYSTORE` and key generation succeeds
- **THEN** the wrapping key is StrongBox-backed and the recorded security level reflects StrongBox

#### Scenario: Fallback to TEE when StrongBox unavailable
- **WHEN** key generation with StrongBox throws `StrongBoxUnavailableException`
- **THEN** the system retries without StrongBox, produces a TEE-backed key, and does not crash

#### Scenario: Achieved security level is verified at runtime
- **WHEN** the wrapping key has been generated
- **THEN** the system reads `KeyInfo.getSecurityLevel()` (or `isInsideSecureHardware()` on older APIs)
  and records the achieved level

### Requirement: Optional binding of key use to device unlock or biometric
The system SHALL support an optional setting that binds use of the Keystore wrapping key to a recent
device unlock or biometric authentication via `setUserAuthenticationParameters` (API 31+) or the
legacy validity-duration API on older levels. This binding SHALL be OFF by default.

#### Scenario: Auth binding off by default
- **WHEN** the app is installed and no user-authentication binding has been enabled
- **THEN** the wrapping key does not require user authentication and the DEK unwraps without a prompt

#### Scenario: Unwrap requires authentication when binding enabled
- **WHEN** user-authentication binding is enabled and the unwrap is attempted without a recent unlock
- **THEN** the unwrap requires device-credential or biometric authentication before succeeding

### Requirement: Encrypted preferences persist across reboot
The system SHALL ensure that preferences remain decryptable after a device reboot, because the
Keystore wrapping key and the persisted wrapped DEK survive reboot.

#### Scenario: Preferences readable after reboot
- **WHEN** a preference is written, the device reboots, and the app reads the preference
- **THEN** the wrapped DEK is unwrapped with the persisted Keystore key and the original preference
  value is returned

