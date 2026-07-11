package org.searchmob.ui.prefs

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory default [PreferencesStore]. Values live for the process lifetime only; nothing is written
 * to disk. It is the reference implementation that makes the UI fully runnable and unit-testable, and
 * gives apply-immediately semantics for free via [MutableStateFlow].
 *
 * The storage phase replaces this binding with an encrypted-DataStore implementation that adds
 * reboot persistence behind the same [PreferencesStore] interface.
 */
class InMemoryPreferencesStore : PreferencesStore {
    private val strings = mutableMapOf<String, MutableStateFlow<String>>()
    private val booleans = mutableMapOf<String, MutableStateFlow<Boolean>>()
    private val maps = mutableMapOf<String, MutableStateFlow<Map<String, Boolean>>>()

    @Synchronized
    private fun stringFlow(
        key: String,
        default: String,
    ): MutableStateFlow<String> = strings.getOrPut(key) { MutableStateFlow(default) }

    @Synchronized
    private fun boolFlow(
        key: String,
        default: Boolean,
    ): MutableStateFlow<Boolean> = booleans.getOrPut(key) { MutableStateFlow(default) }

    @Synchronized
    private fun mapFlow(key: String): MutableStateFlow<Map<String, Boolean>> =
        maps.getOrPut(key) { MutableStateFlow(emptyMap()) }

    override fun getString(
        key: String,
        default: String,
    ): Flow<String> = stringFlow(key, default)

    override fun getBoolean(
        key: String,
        default: Boolean,
    ): Flow<Boolean> = boolFlow(key, default)

    override fun getBooleanMap(
        key: String,
        defaults: Map<String, Boolean>,
    ): Flow<Map<String, Boolean>> = mapFlow(key).map { stored -> defaults + stored }

    override suspend fun setString(
        key: String,
        value: String,
    ) {
        stringFlow(key, value).value = value
    }

    override suspend fun setBoolean(
        key: String,
        value: Boolean,
    ) {
        boolFlow(key, value).value = value
    }

    override suspend fun setBooleanMap(
        key: String,
        value: Map<String, Boolean>,
    ) {
        mapFlow(key).value = value
    }

    override suspend fun updateBooleanMap(
        key: String,
        transform: (Map<String, Boolean>) -> Map<String, Boolean>,
    ) {
        // MutableStateFlow.update is a compare-and-set loop over the stored value itself, giving the
        // same lost-update-free semantics the persistent store provides via its edit transaction.
        mapFlow(key).update(transform)
    }
}
