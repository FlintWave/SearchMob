package org.searchmob.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.searchmob.data.crypto.AesGcm
import org.searchmob.data.crypto.Dek
import org.searchmob.data.prefs.EncryptedPreferencesCodec

class EncryptedPreferencesCodecTest {
    @Test
    fun roundTripsAndStoresCiphertext() {
        val dek = Dek.generate()
        val codec = EncryptedPreferencesCodec { dek }
        val values = mapOf("engine.duckduckgo.enabled" to "true", "apiKey.brave" to "secret-key")

        val blob = codec.encode(values)
        // On-disk form is ciphertext: the plaintext key/value must not appear verbatim.
        val asText = blob.decodeToString()
        assertFalse(asText.contains("secret-key"))
        assertFalse(asText.contains("duckduckgo"))

        assertEquals(values, codec.decode(blob))
    }

    @Test
    fun decodingWithWrongKeyYieldsEmptyNotCrash() {
        val codec = EncryptedPreferencesCodec { Dek.generate() }
        val blob = codec.encode(mapOf("a" to "b"))
        // A different DEK cannot decrypt → empty map (no crash).
        val other = EncryptedPreferencesCodec { Dek.generate() }
        assertTrue(other.decode(blob).isEmpty())
    }

    @Test
    fun emptyBlobDecodesToEmptyMap() {
        val codec = EncryptedPreferencesCodec { Dek.generate() }
        assertTrue(codec.decode(ByteArray(0)).isEmpty())
    }

    @Test
    fun malformedButDecryptablePayloadYieldsEmptyNotCrash() {
        val dek = Dek.generate()
        val codec = EncryptedPreferencesCodec { dek }
        // A payload that authenticates under the DEK (valid GCM) but is NOT valid JSON for a string
        // map: GCM decrypt succeeds, the JSON parse must degrade to an empty map rather than throwing.
        val blob = AesGcm.encrypt(dek, "not-json-at-all".encodeToByteArray())
        assertTrue(codec.decode(blob).isEmpty())
    }
}
