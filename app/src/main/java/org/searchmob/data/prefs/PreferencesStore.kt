package org.searchmob.data.prefs

import kotlinx.coroutines.flow.Flow

/**
 * The storage layer's preferences surface (engine config + BYO API keys + storage toggles). Phase 6
 * (UI) had not defined a `PreferencesStore` when this was written, so this interface is provided here
 * and backed by [EncryptedDataStorePreferences]. If the UI phase later introduces its own interface,
 * point its implementation at the same encrypted DataStore (see the integration notes in the PR).
 *
 * All values are stored AES-256-GCM-encrypted with the DEK; nothing is in plaintext on disk.
 */
interface PreferencesStore {
    /** Observe all decrypted preferences. Collection fails while the vault is locked. */
    fun observe(): Flow<Preferences>

    /** Read all decrypted preferences once. Throws while the vault is locked. */
    suspend fun getAll(): Preferences

    /** Read a single value (or null). */
    suspend fun get(key: String): String?

    /** Write a single value. */
    suspend fun put(
        key: String,
        value: String,
    )

    /** Remove a single value. */
    suspend fun remove(key: String)

    /** Remove all values (does not destroy the DEK). */
    suspend fun clear()
}
