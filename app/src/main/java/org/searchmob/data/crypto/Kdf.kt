package org.searchmob.data.crypto

import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Derives a key-encryption key from a user passphrase + random salt. The production zero-knowledge
 * mode uses Argon2id (argon2kt, Android); [Pbkdf2Kdf] is a portable JCE implementation used as a
 * fallback and as the deterministic reference exercised in unit tests.
 */
interface Kdf {
    fun derive(
        passphrase: CharArray,
        salt: ByteArray,
        lengthBytes: Int = 32,
    ): ByteArray
}

/** PBKDF2-HMAC-SHA256. Portable (JVM + Android); high iteration count. */
class Pbkdf2Kdf(private val iterations: Int = 210_000) : Kdf {
    override fun derive(
        passphrase: CharArray,
        salt: ByteArray,
        lengthBytes: Int,
    ): ByteArray {
        val spec = PBEKeySpec(passphrase, salt, iterations, lengthBytes * 8)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
