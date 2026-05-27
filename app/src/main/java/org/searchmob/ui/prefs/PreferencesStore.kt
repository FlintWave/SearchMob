package org.searchmob.ui.prefs

import kotlinx.coroutines.flow.Flow

/**
 * Reactive, apply-immediately key/value preference store for the UI layer.
 *
 * The [Flow] reads emit the current value on collection and re-emit on every change, so the UI
 * re-themes / re-fans-out without a relaunch. Writes persist for the implementation that backs this
 * interface.
 *
 * INJECTION POINT: the in-app default is [InMemoryPreferencesStore]. The storage phase
 * (`add-encrypted-storage`) owns the encrypted-DataStore implementation and is expected to provide a
 * `DataStorePreferencesStore` that implements this same interface; bind it in
 * [org.searchmob.ui.AppDependencies] without touching any UI code.
 *
 * Plaintext-safe keys only (theme mode, dynamic-color flag, per-engine flags, history flag) belong
 * here. API keys MUST NOT be stored through this interface; they are routed through the
 * encrypted-preferences mechanism (`EncryptedPreferencesCodec` / `Vault`), never DataStore plaintext.
 */
interface PreferencesStore {
    fun getString(
        key: String,
        default: String,
    ): Flow<String>

    fun getBoolean(
        key: String,
        default: Boolean,
    ): Flow<Boolean>

    /**
     * A string->boolean map preference (used for the per-engine enabled set). The emitted map is
     * merged on top of [defaults]: any key absent from storage falls back to its default.
     */
    fun getBooleanMap(
        key: String,
        defaults: Map<String, Boolean>,
    ): Flow<Map<String, Boolean>>

    suspend fun setString(
        key: String,
        value: String,
    )

    suspend fun setBoolean(
        key: String,
        value: Boolean,
    )

    suspend fun setBooleanMap(
        key: String,
        value: Map<String, Boolean>,
    )
}
