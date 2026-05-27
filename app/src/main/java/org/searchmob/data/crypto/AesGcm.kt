package org.searchmob.data.crypto

import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM authenticated encryption (JCE). Output blob is `iv (12 bytes) || ciphertext+tag`.
 * [decrypt] returns null on authentication failure (tampering or wrong key) rather than throwing.
 */
object AesGcm {
    private const val IV_LENGTH = 12
    private const val TAG_BITS = 128

    fun encrypt(
        key: ByteArray,
        plaintext: ByteArray,
    ): ByteArray {
        val iv = ByteArray(IV_LENGTH).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        return iv + cipher.doFinal(plaintext)
    }

    fun decrypt(
        key: ByteArray,
        blob: ByteArray,
    ): ByteArray? =
        try {
            val iv = blob.copyOfRange(0, IV_LENGTH)
            val ciphertext = blob.copyOfRange(IV_LENGTH, blob.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
            cipher.doFinal(ciphertext)
        } catch (_: GeneralSecurityException) {
            null
        } catch (_: IndexOutOfBoundsException) {
            null
        }
}
