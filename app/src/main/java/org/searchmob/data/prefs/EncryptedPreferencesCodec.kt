package org.searchmob.data.prefs

import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.searchmob.data.crypto.AesGcm

/**
 * Encodes/decodes a preferences map as an AES-256-GCM-encrypted blob using the DEK. The on-disk form is
 * always ciphertext (this is what a DataStore Serializer persists). Used for engine config and the
 * optional BYO API keys. Decoding a tampered/undecryptable blob yields an empty map rather than crashing.
 */
class EncryptedPreferencesCodec(private val dek: () -> ByteArray) {
    fun encode(values: Map<String, String>): ByteArray =
        AesGcm.encrypt(dek(), Json.encodeToString(values).encodeToByteArray())

    fun decode(blob: ByteArray): Map<String, String> {
        if (blob.isEmpty()) return emptyMap()
        val plaintext = AesGcm.decrypt(dek(), blob) ?: return emptyMap()
        // The payload authenticated under GCM but could still be structurally corrupt; degrade to an
        // empty map rather than letting a SerializationException escape the codec.
        return try {
            Json.decodeFromString<Map<String, String>>(plaintext.decodeToString())
        } catch (_: SerializationException) {
            emptyMap()
        }
    }
}
