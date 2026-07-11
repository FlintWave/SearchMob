package org.searchmob.ui.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.searchMobPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "searchmob_prefs")

/**
 * Persistent [PreferencesStore] backed by Jetpack Preferences DataStore. Used for non-sensitive,
 * reboot-persistent settings (theme mode, dynamic-color flag, per-engine flags, history flag,
 * onboarding-completed flag). API keys are NOT stored here; they go through the encrypted path.
 */
class DataStorePreferencesStore(context: Context) : PreferencesStore {
    private val dataStore = context.applicationContext.searchMobPrefsDataStore

    override fun getString(
        key: String,
        default: String,
    ): Flow<String> = dataStore.data.map { it[stringPreferencesKey(key)] ?: default }

    override fun getBoolean(
        key: String,
        default: Boolean,
    ): Flow<Boolean> = dataStore.data.map { it[booleanPreferencesKey(key)] ?: default }

    override fun getBooleanMap(
        key: String,
        defaults: Map<String, Boolean>,
    ): Flow<Map<String, Boolean>> =
        dataStore.data.map { prefs ->
            val stored =
                prefs[stringPreferencesKey(key)]
                    ?.let { runCatching { Json.decodeFromString<Map<String, Boolean>>(it) }.getOrNull() }
                    ?: emptyMap()
            defaults + stored
        }

    override suspend fun setString(
        key: String,
        value: String,
    ) {
        dataStore.edit { it[stringPreferencesKey(key)] = value }
    }

    override suspend fun setBoolean(
        key: String,
        value: Boolean,
    ) {
        dataStore.edit { it[booleanPreferencesKey(key)] = value }
    }

    override suspend fun setBooleanMap(
        key: String,
        value: Map<String, Boolean>,
    ) {
        dataStore.edit { it[stringPreferencesKey(key)] = Json.encodeToString(value) }
    }

    override suspend fun updateBooleanMap(
        key: String,
        transform: (Map<String, Boolean>) -> Map<String, Boolean>,
    ) {
        // edit() serializes writers and hands the transform a freshly-read snapshot, so decoding the
        // current map and persisting the transformed one is one atomic transaction: a concurrent
        // update cannot slip in between (unlike composing setBooleanMap over an observed value).
        dataStore.edit { prefs ->
            val current =
                prefs[stringPreferencesKey(key)]
                    ?.let { runCatching { Json.decodeFromString<Map<String, Boolean>>(it) }.getOrNull() }
                    ?: emptyMap()
            prefs[stringPreferencesKey(key)] = Json.encodeToString(transform(current))
        }
    }
}
