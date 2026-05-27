package org.searchmob.data.crypto

import java.security.SecureRandom

/** Generates the 256-bit Data Encryption Key that protects all at-rest data. */
object Dek {
    const val SIZE_BYTES = 32

    fun generate(): ByteArray = ByteArray(SIZE_BYTES).also { SecureRandom().nextBytes(it) }
}

/**
 * Holds the unwrapped DEK in process memory only. [zero] wipes the key bytes so a locked vault leaves
 * no plaintext key resident. The DEK is never written to disk in unwrapped form.
 */
class DekHolder {
    private var bytes: ByteArray? = null

    val isUnlocked: Boolean get() = bytes != null

    fun set(dek: ByteArray) {
        bytes = dek
    }

    fun get(): ByteArray = bytes ?: error("vault is locked: DEK not present in memory")

    fun zero() {
        bytes?.fill(0)
        bytes = null
    }
}
