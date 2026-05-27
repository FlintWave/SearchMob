## Why

SearchMob stores nothing by default, but the metasearch core (`add-metasearch-engine-core`) already
needs somewhere to keep user-supplied BYO API keys and per-engine config, and users want an
opt-in, local-only search history — both of which must be encrypted at rest to honour the
**private** goal. This change builds the encryption-at-rest foundation: encrypted preferences for
settings/keys, an opt-in encrypted history database that is off by default and purgeable, and an
optional zero-knowledge passphrase mode — all on-device, never synced.

## What Changes

- Establish a single **storage layer** built around a random 256-bit **Data Encryption Key (DEK)**.
  Two interchangeable **DEK-wrapping** strategies share that one storage layer: a Keystore-wrapped
  DEK (seamless, recoverable — the default) and an Argon2id-passphrase-wrapped DEK (zero-knowledge,
  unrecoverable). Only the wrapping differs; the encrypted DataStore and SQLCipher database are the
  same in both modes.
- Add **encrypted preferences** via Jetpack **DataStore** whose serialized contents are encrypted
  with the DEK. The DEK is wrapped by an **AES-256-GCM Android Keystore** key. Prefer **StrongBox**
  when `PackageManager.FEATURE_STRONGBOX_KEYSTORE` is present; fall back to **TEE** on
  `StrongBoxUnavailableException`; verify the achieved security level at runtime via
  `KeyInfo.getSecurityLevel()`. Optionally bind key use to device unlock/biometric via
  `setUserAuthenticationParameters`. Preferences survive reboot.
- **Do NOT** use `androidx.security:security-crypto` / `EncryptedSharedPreferences` (deprecated as of
  1.1.0) — use the Android Keystore directly.
- Add **opt-in encrypted search history**: a **SQLCipher** database (artifact
  `net.zetetic:sqlcipher-android`, NOT the deprecated `android-database-sqlcipher`) accessed through
  **Room** via a `SupportFactory`. History is **OFF by default**, **user-purgeable** ("clear
  history"), and subject to a **TTL / auto-expiry**. Nothing is ever synced off-device.
- Add an optional **zero-knowledge mode** (user toggle): derive a **Key-Encryption-Key (KEK)** from a
  user passphrase with **Argon2id** (`argon2kt`; e.g. t=4, m=128 MiB, p=1, 32-byte output) plus a
  per-install salt, and wrap the DEK with that KEK instead of the Keystore key. The decrypted DEK is
  held **in memory only while unlocked** and **evicted on lock, app background, or inactivity
  timeout**. Because no recovery path exists, the spec REQUIRES an explicit, unmissable warning that
  data becomes **unrecoverable** if the passphrase is lost and **unreadable** until unlock.

## Capabilities

### New Capabilities
- `encrypted-preferences`: a Jetpack DataStore encrypted with a random 256-bit DEK that is wrapped by
  an AES-256-GCM Android Keystore key, with StrongBox-preferred / TEE-fallback key generation,
  runtime `KeyInfo.getSecurityLevel()` verification, optional user-authentication binding, and
  reboot-persistent decryptability — without using the deprecated `EncryptedSharedPreferences`.
- `encrypted-history`: an opt-in (OFF by default) SQLCipher-encrypted search-history database accessed
  through Room/`SupportFactory`, keyed by the shared DEK, with TTL/auto-expiry, explicit user purge
  ("clear history"), and a guarantee that history is never synced off-device.
- `zero-knowledge-mode`: an optional user toggle that derives a KEK from a passphrase via Argon2id +
  per-install salt and wraps the DEK with it, surfaces an explicit unrecoverable-data warning, holds
  the decrypted DEK in memory only while unlocked, and evicts it on lock/background/timeout.

### Modified Capabilities
<!-- None. This change introduces the storage layer; the metasearch core's config/key surface
     (add-metasearch-engine-core) consumes encrypted preferences without a spec-level requirement
     change there. -->

## Impact

- New code (a `storage/` package): the `Dek` lifecycle + in-memory holder; a `KeystoreDekWrapper`
  (AES-256-GCM Keystore key, StrongBox→TEE fallback, security-level check, optional auth binding); an
  encrypted DataStore serializer; a SQLCipher-backed Room database + DAO for history with TTL/purge;
  an `Argon2idDekWrapper` for zero-knowledge mode; and an unlock/lock state machine that evicts the
  DEK on background/timeout.
- Consumers: `add-metasearch-engine-core`'s engine config + injected BYO API keys move from in-memory
  injection to encrypted preferences; the settings/history UI is the later
  `add-search-ui-and-theming` change (this change exposes the storage APIs and toggles, not the
  Compose screens).
- **Dependencies introduced**: `androidx.datastore` (DataStore), `androidx.room` (+ `room-ktx` and
  the `ksp` plugin), `net.zetetic:sqlcipher-android`, `androidx.sqlite`, `argon2kt`, and optional
  `androidx.biometric` (only if user-authentication binding is enabled). Test-only: Room/SQLite
  in-instrumentation test artifacts and Robolectric/AndroidX test runners.
- **Permissions**: NO new permissions. No `INTERNET` need beyond what the metasearch core already
  declares; encryption and storage are entirely on-device. No telemetry, no analytics, no device
  identifiers; any install id remains a locally-generated, user-wipeable UUID. The per-install salt
  is a random value, not a device identifier.
