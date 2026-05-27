package org.searchmob.data

import org.searchmob.data.crypto.DekHolder

/**
 * Unlock/lock state for the encrypted store. While unlocked the DEK lives only in [DekHolder] (process
 * memory). [lock] wipes it — call on explicit lock, on `ON_STOP` (app backgrounded), and on inactivity
 * timeout. Accessing [dek] while locked throws, so encrypted data is inaccessible until re-unlock.
 */
class Vault(private val holder: DekHolder = DekHolder()) {
    val isUnlocked: Boolean get() = holder.isUnlocked

    fun unlock(dek: ByteArray) {
        holder.set(dek)
    }

    fun dek(): ByteArray = holder.get()

    fun lock() {
        holder.zero()
    }
}
