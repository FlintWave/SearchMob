package org.searchmob.data.crypto

/**
 * Wraps (encrypts) and unwraps the DEK with a key-encryption key the wrapper controls. [unwrap]
 * returns null when authentication fails (wrong key / tampered blob), never throwing.
 *
 * Implementations:
 * - Keystore-backed (Android): KEK is a hardware-backed AndroidKeyStore key, no prompt, recoverable.
 * - [PassphraseDekWrapper]: KEK is derived from a user passphrase, zero-knowledge, unrecoverable.
 * - [SecretKeyDekWrapper]: KEK is an in-memory AES key, the JVM-testable reference for both.
 */
interface DekWrapper {
    fun wrap(dek: ByteArray): ByteArray

    fun unwrap(blob: ByteArray): ByteArray?
}

/** Wraps the DEK with a caller-provided AES key. Mirrors the Keystore wrap on the JVM for tests. */
class SecretKeyDekWrapper(private val kek: ByteArray) : DekWrapper {
    override fun wrap(dek: ByteArray): ByteArray = AesGcm.encrypt(kek, dek)

    override fun unwrap(blob: ByteArray): ByteArray? = AesGcm.decrypt(kek, blob)
}

/**
 * Zero-knowledge wrapper: derives the KEK from a passphrase + salt via [Kdf] and AES-256-GCM-wraps the
 * DEK with it. An incorrect passphrase yields a different KEK and therefore a failed GCM auth (null);
 * the data is unrecoverable without the exact passphrase.
 */
class PassphraseDekWrapper(
    private val kdf: Kdf,
    private val passphrase: CharArray,
    private val salt: ByteArray,
) : DekWrapper {
    override fun wrap(dek: ByteArray): ByteArray = AesGcm.encrypt(kek(), dek)

    override fun unwrap(blob: ByteArray): ByteArray? = AesGcm.decrypt(kek(), blob)

    private fun kek(): ByteArray = kdf.derive(passphrase, salt, Dek.SIZE_BYTES)
}
