package org.searchmob.data.prefs

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * [PreferencesStore] backed by a Jetpack [DataStore] whose payload is encrypted with the DEK by
 * [EncryptedPreferencesSerializer]. The DataStore is created with that serializer (see
 * [StorageProvider]); this class just exposes typed read/write over it.
 */
class EncryptedDataStorePreferences(
    private val dataStore: DataStore<Preferences>,
) : PreferencesStore {
    override fun observe(): Flow<Preferences> = dataStore.data

    override suspend fun getAll(): Preferences = dataStore.data.first()

    override suspend fun get(key: String): String? = dataStore.data.map { it[key] }.first()

    override suspend fun put(
        key: String,
        value: String,
    ) {
        dataStore.updateData { it + (key to value) }
    }

    override suspend fun remove(key: String) {
        dataStore.updateData { it - key }
    }

    override suspend fun clear() {
        dataStore.updateData { emptyMap() }
    }
}
