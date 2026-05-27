package org.searchmob.data.prefs

import androidx.datastore.core.Serializer
import java.io.InputStream
import java.io.OutputStream

/** The decrypted preferences document: a flat string→string map (engine config + BYO API keys). */
typealias Preferences = Map<String, String>

/**
 * A DataStore [Serializer] whose on-disk form is always AES-256-GCM ciphertext produced by
 * [EncryptedPreferencesCodec] (whole-payload encryption with the DEK; fresh IV per write). We do NOT
 * use `androidx.security:security-crypto` / `EncryptedSharedPreferences`.
 *
 * [codec] reads the DEK lazily, so reads/writes throw while the vault is locked (zero-knowledge):
 * preferences are simply unavailable until unlock, matching the spec. An empty/undecryptable file
 * decodes to the empty map rather than crashing.
 */
class EncryptedPreferencesSerializer(
    private val codec: EncryptedPreferencesCodec,
) : Serializer<Preferences> {
    override val defaultValue: Preferences = emptyMap()

    override suspend fun readFrom(input: InputStream): Preferences = codec.decode(input.readBytes())

    override suspend fun writeTo(
        t: Preferences,
        output: OutputStream,
    ) {
        output.write(codec.encode(t))
    }
}
