package org.searchmob.data.crypto

import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2Mode
import java.nio.charset.StandardCharsets

/**
 * Android [Kdf] backed by Argon2id (via `argon2kt`). Memory-hard, resists GPU/ASIC cracking — the
 * locked choice for zero-knowledge mode. Defaults follow the design: t=4 iterations, m=128 MiB,
 * p=1, 32-byte output. The cost is paid only on explicit unlock, never in the hot search path.
 *
 * The passphrase is encoded to UTF-8 bytes and that scratch buffer is zeroed after derivation so no
 * plaintext passphrase lingers longer than necessary.
 */
class Argon2idKdf(
    private val iterations: Int = DEFAULT_ITERATIONS,
    private val memoryKib: Int = DEFAULT_MEMORY_KIB,
    private val parallelism: Int = DEFAULT_PARALLELISM,
    private val argon2Kt: Argon2Kt = Argon2Kt(),
) : Kdf {
    override fun derive(
        passphrase: CharArray,
        salt: ByteArray,
        lengthBytes: Int,
    ): ByteArray {
        val passwordBytes = encodeUtf8(passphrase)
        return try {
            argon2Kt
                .hash(
                    mode = Argon2Mode.ARGON2_ID,
                    password = passwordBytes,
                    salt = salt,
                    tCostInIterations = iterations,
                    mCostInKibibyte = memoryKib,
                    parallelism = parallelism,
                    hashLengthInBytes = lengthBytes,
                ).rawHashAsByteArray()
        } finally {
            passwordBytes.fill(0)
        }
    }

    private fun encodeUtf8(chars: CharArray): ByteArray {
        val buffer = StandardCharsets.UTF_8.encode(java.nio.CharBuffer.wrap(chars))
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return bytes
    }

    companion object {
        const val DEFAULT_ITERATIONS = 4
        const val DEFAULT_MEMORY_KIB = 128 * 1024 // 128 MiB
        const val DEFAULT_PARALLELISM = 1
    }
}
