## 1. Branch & dependencies

- [ ] 1.1 Create branch `feat/add-encrypted-storage` off `main`
- [ ] 1.2 Add `androidx.datastore` (DataStore), `androidx.room` (+ `room-ktx`) and the `ksp` plugin,
      `net.zetetic:sqlcipher-android`, and `androidx.sqlite` to `gradle/libs.versions.toml` and the
      `app` build script; confirm NO dependency on the deprecated `android-database-sqlcipher` or
      `androidx.security:security-crypto`
- [ ] 1.3 Add `argon2kt` and (optional) `androidx.biometric` dependencies
- [ ] 1.4 Add test-only AndroidX instrumentation + Room/SQLite test artifacts; confirm `AndroidManifest.xml`
      gains NO new permissions (no INTERNET/storage changes)

## 2. DEK lifecycle & in-memory holder

- [ ] 2.1 Implement the random 256-bit `Dek` generator and a `DekHolder` that keeps the unwrapped DEK
      in process memory only and can zero its key bytes
- [ ] 2.2 Define the `DekWrapper` interface (`wrap(dek) -> blob`, `unwrap(blob) -> dek`) and the
      persisted bootstrap-metadata model (wrapped-DEK blob, salt, flags, achieved security level)
      stored unencrypted

## 3. Keystore-wrapped DEK

- [ ] 3.1 Implement `KeystoreDekWrapper`: AES-256-GCM `AndroidKeyStore` key generation, StrongBox
      requested when `FEATURE_STRONGBOX_KEYSTORE` is present, fallback to TEE on
      `StrongBoxUnavailableException`, fresh random IV per wrap (IV prepended to ciphertext)
- [ ] 3.2 Read back and record `KeyInfo.getSecurityLevel()` (API 31+) / `isInsideSecureHardware()`
      (API 26–30) after key generation
- [ ] 3.3 Implement optional user-authentication binding via `setUserAuthenticationParameters`
      (API 31+) / legacy validity-duration (older), OFF by default, using `androidx.biometric`

## 4. Encrypted preferences (DataStore)

- [ ] 4.1 Implement a DataStore `Serializer` that AES-256-GCM-encrypts the serialized payload with the
      DEK; do NOT use `EncryptedSharedPreferences`
- [ ] 4.2 First-run bootstrap: generate the DEK, wrap with `KeystoreDekWrapper`, persist metadata; on
      later runs unwrap and decrypt
- [ ] 4.3 Repoint `add-metasearch-engine-core` engine config + injected BYO API keys to read/write
      encrypted preferences (DI swap behind the existing config surface)

## 5. Opt-in encrypted history (SQLCipher + Room)

- [ ] 5.1 Define the Room history entity (query, timestamp, etc.) + DAO; build Room with
      `SupportFactory(dekBytes)` from `net.zetetic:sqlcipher-android`
- [ ] 5.2 Implement OFF-by-default behavior: no DB file created/opened while disabled; lazy creation on
      opt-in; in-memory-only session otherwise
- [ ] 5.3 Implement TTL/auto-expiry enforced on read + opportunistic sweep on open/insert (no
      background timer/wake-lock)
- [ ] 5.4 Implement "clear history" (delete all rows) and "disable history" (delete the DB file)

## 6. Zero-knowledge mode (Argon2id)

- [ ] 6.1 Implement `Argon2idDekWrapper`: derive a 32-byte KEK via `argon2kt` Argon2id
      (t=4, m=128 MiB, p=1) with a random per-install salt, AES-256-GCM-wrap the DEK with it
- [ ] 6.2 Implement enable/disable as a re-wrap of the SAME DEK between `KeystoreDekWrapper` and
      `Argon2idDekWrapper` (no data re-encryption)
- [ ] 6.3 Implement the required, unmissable unrecoverable-data warning gate before enabling, with no
      recovery/escrow/reset path
- [ ] 6.4 Implement the unlock/lock state machine: hold the DEK in memory only while unlocked; evict
      (zero bytes, close DB handle) on explicit lock, `ON_STOP` background, and inactivity timeout

## 7. Unit & instrumentation tests

- [ ] 7.1 DEK wrap/unwrap round-trip returns byte-identical key; tampered wrapped blob fails GCM auth
- [ ] 7.2 StrongBox-absent fallback: simulate `StrongBoxUnavailableException`, assert a TEE-backed key
      is produced without crashing and the recorded security level is correct
- [ ] 7.3 Preferences: written values are ciphertext on disk, round-trip read matches, and decryptable
      after a simulated reboot
- [ ] 7.4 History off-by-default: no DB file exists and nothing persists until opt-in
- [ ] 7.5 History TTL expiry: expired rows are not returned and are swept inline (no background job)
- [ ] 7.6 History purge: "clear history" empties rows; "disable history" deletes the DB file
- [ ] 7.7 Argon2id derive + wrap/unwrap with correct passphrase succeeds; WRONG passphrase fails unwrap
      (no key returned); salt is random, not a device identifier
- [ ] 7.8 Key eviction: after `ON_STOP`/timeout/lock the in-memory DEK is zeroed/dropped, the DB handle
      is closed, and history access is blocked until re-unlock
- [ ] 7.9 Assert no dependency on `EncryptedSharedPreferences` / `androidx.security:security-crypto` or
      `android-database-sqlcipher`, and that no new permission was added

## 8. Verify, validate, PR & archive

- [ ] 8.1 Run `./gradlew lint test` and the instrumentation suite (on-device/VM); confirm green
- [ ] 8.2 Run `openspec validate add-encrypted-storage --strict` and fix any issues
- [ ] 8.3 Open PR `feat/add-encrypted-storage`, confirm CI green, merge to `main`
- [ ] 8.4 Run `openspec archive add-encrypted-storage`
