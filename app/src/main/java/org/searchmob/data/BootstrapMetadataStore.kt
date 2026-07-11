package org.searchmob.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream

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
        // Atomic replace, not an in-place truncate-and-write: this file holds the ONLY copy of the
        // wrapped DEK, so a crash or power loss mid-write must leave either the old blob or the new
        // one, never a torn file. Write to a scratch sibling, fsync it, then rename over the target
        // (a same-directory rename is atomic on the filesystems Android uses).
        val scratch = File(file.parentFile, file.name + ".tmp")
        FileOutputStream(scratch).use { out ->
            out.write(json.encodeToString(metadata).toByteArray(Charsets.UTF_8))
            out.fd.sync()
        }
        if (!scratch.renameTo(file)) {
            // Rename can fail if the target is on another mount (never the case here) - fall back to
            // a delete+rename so the write still lands rather than silently keeping stale metadata.
            file.delete()
            check(scratch.renameTo(file)) { "Failed to persist bootstrap metadata" }
        }
    }

    fun delete() {
        if (file.exists()) file.delete()
    }

    companion object {
        const val FILE_NAME = "searchmob-bootstrap.json"
    }
}
