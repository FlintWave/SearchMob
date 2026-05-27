package org.searchmob.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.searchmob.data.crypto.Dek
import org.searchmob.data.prefs.EncryptedPreferencesCodec
import org.searchmob.data.prefs.EncryptedPreferencesSerializer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * The DataStore serializer writes only AES-GCM ciphertext and round-trips through the DEK codec. This
 * is the disk format used by the encrypted DataStore (no `EncryptedSharedPreferences`).
 */
class EncryptedPreferencesSerializerTest {
    @Test
    fun writesCiphertextAndRoundTrips() =
        runTest {
            val dek = Dek.generate()
            val serializer = EncryptedPreferencesSerializer(EncryptedPreferencesCodec { dek })
            val values = mapOf("apiKey.brave" to "top-secret", "engine.ddg.enabled" to "true")

            val out = ByteArrayOutputStream()
            serializer.writeTo(values, out)
            val onDisk = out.toByteArray()

            // On-disk bytes must not contain the plaintext secret.
            assertFalse(onDisk.decodeToString().contains("top-secret"))

            val read = serializer.readFrom(ByteArrayInputStream(onDisk))
            assertEquals(values, read)
        }

    @Test
    fun emptyFileReadsAsDefault() =
        runTest {
            val serializer = EncryptedPreferencesSerializer(EncryptedPreferencesCodec { Dek.generate() })
            assertEquals(emptyMap<String, String>(), serializer.readFrom(ByteArrayInputStream(ByteArray(0))))
        }
}
