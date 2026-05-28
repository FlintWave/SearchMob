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

/**
 * The KDF parameters recorded alongside a passphrase-wrapped DEK so the exact same KEK can be derived
 * on later unlocks even if the compile-time defaults change. Defaults to the current Argon2id profile.
 */
data class KdfParams(
    val algorithm: String = Argon2idKdf.ALGORITHM,
    val iterations: Int = Argon2idKdf.DEFAULT_ITERATIONS,
    val memoryKib: Int = Argon2idKdf.DEFAULT_MEMORY_KIB,
    val parallelism: Int = Argon2idKdf.DEFAULT_PARALLELISM,
)

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
