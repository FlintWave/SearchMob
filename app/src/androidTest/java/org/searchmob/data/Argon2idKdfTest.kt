package org.searchmob.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.searchmob.data.crypto.Argon2idKdf
import org.searchmob.data.crypto.Dek
import org.searchmob.data.crypto.PassphraseDekWrapper
import java.security.SecureRandom

/**
 * On-device Argon2id (native `argon2kt`) derivation: deterministic for the same inputs, different for a
 * different passphrase, and a correct/incorrect passphrase succeeds/fails the GCM unwrap. Uses reduced
 * cost so the instrumentation run stays fast; production uses t=3 / m=64 MiB.
 */
@RunWith(AndroidJUnit4::class)
class Argon2idKdfTest {
    // Reduced cost for test speed (still real Argon2id).
    private val kdf = Argon2idKdf(iterations = 1, memoryKib = 8 * 1024, parallelism = 1)

    @Test
    fun deriveIsDeterministicForSameInputs() {
        val salt = randomSalt()
        val a = kdf.derive("hunter2".toCharArray(), salt, 32)
        val b = kdf.derive("hunter2".toCharArray(), salt, 32)
        assertEquals(32, a.size)
        assertArrayEquals(a, b)
    }

    @Test
    fun differentPassphraseDerivesDifferentKey() {
        val salt = randomSalt()
        val a = kdf.derive("right".toCharArray(), salt, 32)
        val b = kdf.derive("wrong".toCharArray(), salt, 32)
        assertFalse(a.contentEquals(b))
    }

    @Test
    fun wrapUnwrapRoundTripsWithCorrectPassphraseAndRejectsWrong() {
        val salt = randomSalt()
        val dek = Dek.generate()
        val wrapped = PassphraseDekWrapper(kdf, "correct".toCharArray(), salt).wrap(dek)
        assertArrayEquals(dek, PassphraseDekWrapper(kdf, "correct".toCharArray(), salt).unwrap(wrapped))
        assertNull(PassphraseDekWrapper(kdf, "incorrect".toCharArray(), salt).unwrap(wrapped))
    }

    private fun randomSalt(): ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }
}
