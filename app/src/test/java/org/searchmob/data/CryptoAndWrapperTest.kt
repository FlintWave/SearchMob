package org.searchmob.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.searchmob.data.crypto.AesGcm
import org.searchmob.data.crypto.Dek
import org.searchmob.data.crypto.PassphraseDekWrapper
import org.searchmob.data.crypto.Pbkdf2Kdf
import org.searchmob.data.crypto.SecretKeyDekWrapper

class CryptoAndWrapperTest {
    @Test
    fun aesGcmRoundTrips() {
        val key = Dek.generate()
        val plaintext = "secret query".encodeToByteArray()
        val blob = AesGcm.encrypt(key, plaintext)
        assertFalse("blob must not equal plaintext", blob.contentEquals(plaintext))
        assertArrayEquals(plaintext, AesGcm.decrypt(key, blob))
    }

    @Test
    fun aesGcmRejectsTamperedBlob() {
        val key = Dek.generate()
        val blob = AesGcm.encrypt(key, "data".encodeToByteArray())
        blob[blob.size - 1] = (blob[blob.size - 1] + 1).toByte()
        assertNull(AesGcm.decrypt(key, blob))
    }

    @Test
    fun secretKeyWrapperRoundTripsAndDetectsTampering() {
        val kek = Dek.generate()
        val dek = Dek.generate()
        val wrapper = SecretKeyDekWrapper(kek)
        val wrapped = wrapper.wrap(dek)
        assertArrayEquals(dek, wrapper.unwrap(wrapped))
        wrapped[wrapped.size - 1] = (wrapped[wrapped.size - 1] + 1).toByte()
        assertNull(wrapper.unwrap(wrapped))
    }

    @Test
    fun passphraseWrapperUnwrapsWithCorrectPassphraseOnly() {
        val kdf = Pbkdf2Kdf(iterations = 1_000)
        val salt = Dek.generate()
        val dek = Dek.generate()

        val wrapped = PassphraseDekWrapper(kdf, "correct horse".toCharArray(), salt).wrap(dek)
        // Correct passphrase recovers the exact DEK.
        assertArrayEquals(dek, PassphraseDekWrapper(kdf, "correct horse".toCharArray(), salt).unwrap(wrapped))
        // Wrong passphrase fails GCM auth → null (data is unrecoverable).
        assertNull(PassphraseDekWrapper(kdf, "wrong".toCharArray(), salt).unwrap(wrapped))
    }

    @Test
    fun saltsAreRandomNotDerivedFromAnIdentifier() {
        assertFalse(Dek.generate().contentEquals(Dek.generate()))
    }

    @Test
    fun dekHolderZeroesOnLock() {
        val vault = Vault()
        val dek = Dek.generate()
        vault.unlock(dek)
        assertTrue(vault.isUnlocked)
        assertArrayEquals(dek, vault.dek())
        vault.lock()
        assertFalse(vault.isUnlocked)
        // dek bytes were zeroed in place
        assertTrue(dek.all { it == 0.toByte() })
    }
}
