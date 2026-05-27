package org.searchmob.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Persists [BootstrapMetadata] as an UNENCRYPTED JSON file in app-internal storage. This is
 * deliberately plaintext: it holds only the *wrapped* (encrypted) DEK, the random salt, the wrap mode,
 * and the achieved Keystore security level, none of which reveal user data, and all of which are
 * needed before the DEK is available. The plaintext DEK and passphrase are never written here.
 */
class BootstrapMetadataStore(private val file: File) {
    private val json = Json { encodeDefaults = true }

    fun exists(): Boolean = file.exists()

    fun read(): BootstrapMetadata? {
        if (!file.exists()) return null
        return runCatching { json.decodeFromString<BootstrapMetadata>(file.readText()) }.getOrNull()
    }

    fun write(metadata: BootstrapMetadata) {
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(metadata))
    }

    fun delete() {
        if (file.exists()) file.delete()
    }

    companion object {
        const val FILE_NAME = "searchmob-bootstrap.json"
    }
}
