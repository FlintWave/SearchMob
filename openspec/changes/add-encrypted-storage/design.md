## Context

By phase 4 (`add-metasearch-engine-core`), SearchMob can search, but it has no place to persist
anything: BYO API keys and per-engine config are injected in-memory, and there is no search history.
The project's storage/privacy posture is **store nothing by default**, with **opt-in, local-only,
encrypted, purgeable** history. This change supplies the encryption-at-rest layer that later phases
(settings UI, history UI) build on.

The cryptographic building blocks are **locked decisions** from the project context: a random DEK
wrapped by an Android Keystore key (StrongBox-preferred, TEE fallback); SQLCipher
(`net.zetetic:sqlcipher-android`) for any database; Jetpack DataStore for preferences;
`EncryptedSharedPreferences` is forbidden (deprecated); and an optional Argon2id-passphrase
zero-knowledge mode whose data is unrecoverable if the passphrase is lost. This design does not
relitigate those; it decides the **internal architecture**: the DEK lifecycle, the two pluggable
wrappers over one shared storage layer, the SQLCipher+Room wiring, TTL/purge mechanics, and the
unlock/eviction state machine.

## Goals / Non-Goals

**Goals:**
- One **storage layer** keyed by a single random 256-bit DEK, with two interchangeable
  **DEK-wrapping** strategies (Keystore-wrapped = transparent/recoverable default; Argon2id-wrapped =
  zero-knowledge) so the encrypted DataStore and SQLCipher DB are identical in both modes.
- Encrypted preferences via DataStore that survive reboot and never touch the deprecated
  `EncryptedSharedPreferences`.
- Opt-in search history that is **OFF by default**, TTL-expiring, user-purgeable, and never synced.
- A zero-knowledge mode whose **unrecoverable** nature is surfaced explicitly, whose key lives in
  memory only while unlocked, and which evicts the key on lock/background/timeout.
- Hardware-backed keys when available (StrongBox), with a verified, graceful TEE fallback.

**Non-Goals:**
- No Compose settings/history UI; this change exposes storage APIs and toggle state, and screens are
  `add-search-ui-and-theming`.
- No off-device sync, backup, or cloud export of any encrypted data; permanently out of scope.
- No passphrase recovery / escrow / "forgot passphrase" path for zero-knowledge mode; its absence is
  the security property, not a gap to fill later.
- No use of `androidx.security:security-crypto` / `EncryptedSharedPreferences` (deprecated).
- No network exposure; encryption and storage are entirely on-device, with no new permissions.

## Decisions

- **One DEK, two wrappers, one storage layer.** A single random 256-bit Data Encryption Key
  encrypts both the DataStore payload and the SQLCipher database. The DEK itself is never stored in
  plaintext; only a *wrapped* (encrypted) DEK blob is persisted. Two `DekWrapper` implementations
  produce/consume that blob: `KeystoreDekWrapper` (default) and `Argon2idDekWrapper` (zero-knowledge).
  Switching modes re-wraps the *same* DEK, so no data is re-encrypted and history survives a mode
  switch. *Alternative (separate keys per store, or re-encrypting all data on mode switch) rejected:*
  more failure surface and a slow, risky migration on every toggle.
- **Keystore wrapping = AES-256-GCM, StrongBox-preferred, TEE fallback, verified.** The wrapping key
  is an AES-256-GCM `KeyGenParameterSpec` in the `AndroidKeyStore`. When
  `PackageManager.FEATURE_STRONGBOX_KEYSTORE` is present we call `setIsStrongBoxBacked(true)`; on
  `StrongBoxUnavailableException` we retry once without StrongBox (TEE). After generation we read back
  `KeyInfo.getSecurityLevel()` (API 31+) / `isInsideSecureHardware()` (older) and record the achieved
  level so it can be surfaced and asserted in tests. GCM gives authenticated encryption, so a
  tampered wrapped-DEK blob fails to unwrap rather than silently returning garbage. *Alternative
  (RSA-OAEP key, or storing the DEK directly in Keystore) rejected:* AES-GCM symmetric wrapping is
  simpler and StrongBox-friendly; the Keystore cannot store an importable raw AES key we also use as
  a software SQLCipher passphrase, so we wrap rather than store.
- **Optional user-authentication binding.** When the user enables it, the Keystore wrapping key is
  generated with `setUserAuthenticationRequired(true)` + `setUserAuthenticationParameters(timeout,
  AUTH_DEVICE_CREDENTIAL or AUTH_BIOMETRIC_STRONG)`, so unwrapping the DEK requires a recent device
  unlock/biometric (via `androidx.biometric`). This is independent of zero-knowledge mode and off by
  default. *Alternative (always require auth) rejected:* would break the transparent, always-on default.
- **Encrypted DataStore, not EncryptedSharedPreferences.** Preferences use Jetpack DataStore with a
  custom `Serializer` that AES-256-GCM-encrypts the serialized bytes with the DEK (fresh random IV
  per write, IV prepended to ciphertext). The deprecated `androidx.security:security-crypto` path is
  not used. The wrapped-DEK blob, the achieved security level, the per-install salt, and the
  zero-knowledge/auth-binding flags are bootstrap metadata stored *unencrypted* (they reveal no user
  data and are needed before the DEK is available). *Alternative (Proto DataStore with field-level
  crypto) rejected* as needless; whole-payload encryption is simpler and leaks no structure.
- **History = SQLCipher via Room `SupportFactory`, using `net.zetetic:sqlcipher-android`.** Room is
  built with `openHelperFactory(SupportFactory(dekBytes))` from the **new** SQLCipher artifact
  (`net.zetetic:sqlcipher-android` + `androidx.sqlite`), never the deprecated
  `android-database-sqlcipher`. The SQLCipher passphrase is the DEK bytes. History is **OFF by
  default**: when disabled, no DB file is created/opened and search runs in-memory only; enabling it
  creates the encrypted DB lazily. *Alternative (plain Room + column encryption) rejected:* whole-file
  SQLCipher encrypts indices and the WAL too, leaving nothing in plaintext on disk.
- **TTL / auto-expiry + explicit purge.** Each history row stores a creation timestamp; a TTL
  (user-configurable, sane default) makes rows older than the cutoff expire. Expiry is enforced
  **on read** (expired rows are never returned) and swept opportunistically (e.g. on open/insert) so
  no background timer/wake-lock is needed. "Clear history" deletes all rows; "disable history"
  additionally deletes the DB file. *Alternative (a periodic background purge job) rejected:* it
  would cost battery and contradict the event-driven, no-idle-work discipline.
- **Zero-knowledge wrapping = Argon2id + per-install salt.** `Argon2idDekWrapper` derives a 32-byte
  KEK from the user passphrase via `argon2kt` Argon2id (default cost t=4, m=128 MiB, p=1) with a
  random per-install salt (random bytes, not a device identifier), then AES-256-GCM-wraps the DEK
  with that KEK. The wrong passphrase produces a wrong KEK, so GCM authentication fails on unwrap:
  i.e. wrong passphrase is detectably rejected, never silently accepted. The salt and Argon2
  parameters are stored as bootstrap metadata so the same KEK is reproducible. *Alternative (PBKDF2/
  scrypt) rejected:* Argon2id is the locked, memory-hard choice and resists GPU/ASIC cracking.
- **Unlocked-DEK lives in memory only; eviction state machine.** In zero-knowledge mode the unwrapped
  DEK is held only in a process-memory holder while *unlocked*. A lifecycle observer evicts it (zeroes
  the byte array, drops references, closes the SQLCipher handle) on app background (`ON_STOP`), on an
  inactivity timeout, and on explicit lock. While locked, history reads/writes are unavailable and the
  UI must prompt for the passphrase. *Alternative (caching the derived KEK to skip re-deriving)
  rejected:* defeats the zero-knowledge property; we re-derive on each unlock.
- **Unmissable unrecoverable warning.** Enabling zero-knowledge mode SHALL require an explicit
  confirmation that states data becomes permanently unrecoverable if the passphrase is lost and
  unreadable until unlock. This is a normative spec requirement, not just UX copy guidance.

## Risks / Trade-offs

- [Battery: no idle work] → All crypto and TTL sweeps happen *inline* with a user action (open,
  read, insert, unlock); there is **no background purge job, no timer, no wake-lock**. This preserves
  the project's event-driven, near-zero-idle-battery discipline. Argon2id at m=128 MiB runs only on
  explicit unlock, not in the hot search path.
- [Privacy: plaintext leak on disk] → Whole-payload DataStore encryption + whole-file SQLCipher mean
  no user query/history/key is ever written in plaintext; the only unencrypted on-disk bytes are
  non-sensitive bootstrap metadata (wrapped DEK, salt, flags, security level). Nothing is synced; no
  permissions are added; the per-install salt is random, not a device identifier.
- [Zero-knowledge data loss] → Lost passphrase = permanently unrecoverable data, by design. Mitigation
  is informational only: a required, unmissable warning at enable time and a clear locked-state UX;
  there is intentionally no recovery path.
- [StrongBox absent or buggy on some OEMs] → Generation prefers StrongBox but catches
  `StrongBoxUnavailableException` and falls back to TEE; the achieved `KeyInfo.getSecurityLevel()` is
  read back and surfaced, so the app degrades gracefully instead of crashing on StrongBox-less
  devices (common below high-end hardware and on emulators).
- [Android-version restrictions] → minSdk 26 supports `AndroidKeyStore` AES-GCM and SQLCipher.
  `setUserAuthenticationParameters` and the `KeyInfo.getSecurityLevel()` enum are API 31+, so on
  API 26-30 we use the legacy `setUserAuthenticationValidityDurationSeconds` /
  `isInsideSecureHardware()` paths behind a version check. No `targetSdk 35` storage restrictions
  apply since all files are app-internal (no scoped-storage/`MANAGE_EXTERNAL_STORAGE` concerns).
- [DB key rotation on mode switch] → Because both wrappers wrap the *same* DEK, switching modes only
  re-wraps the DEK blob; the SQLCipher DB and DataStore payload are untouched, avoiding a full,
  failure-prone re-encryption.
- [Forgetting to zero key material] → Eviction explicitly zeroes DEK/KEK byte arrays and closes
  handles; tests assert the in-memory DEK is cleared after `ON_STOP`/timeout/lock.

## Migration Plan

- Greenfield storage layer; there is no existing persisted data to migrate. First run generates a
  fresh DEK, wraps it with the default `KeystoreDekWrapper`, and writes bootstrap metadata.
- `add-metasearch-engine-core` consumers are repointed from in-memory injected config/keys to the
  encrypted DataStore via DI; rollback is repointing the binding back to in-memory config (no schema
  to revert).
- Enabling history creates the SQLCipher DB lazily; disabling deletes the file. Enabling
  zero-knowledge mode re-wraps the existing DEK with the Argon2id wrapper after the warning is
  confirmed; disabling re-wraps it back to the Keystore wrapper while unlocked. No data is
  re-encrypted in either direction.
- Ship with history OFF and zero-knowledge OFF by default so the default install still "stores
  nothing" until the user opts in.

## Open Questions

- Final Argon2id cost parameters: start at t=4, m=128 MiB, p=1 (32-byte output) and tune on real
  low-end devices during instrumentation testing so unlock stays acceptably fast without weakening
  the KDF.
- Default history TTL value and whether to expose it as a user setting now or hard-code a default:
  lean toward a sane default (e.g. 30 days) with the setting deferred to the UI phase.
- Default inactivity-timeout duration for DEK eviction in zero-knowledge mode: pick a conservative
  default (e.g. evict on background immediately + a short foreground idle timeout); not blocking.
