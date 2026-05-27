package org.searchmob.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.searchmob.data.history.HistoryEntry
import org.searchmob.data.history.HistoryStore
import org.searchmob.ui.EngineDescriptor
import org.searchmob.ui.prefs.PreferencesRepository
import org.searchmob.ui.prefs.UserPreferences
import org.searchmob.ui.theme.ThemeMode

/**
 * Backs the settings screen. Reads the live [UserPreferences] flow and exposes writers that persist
 * immediately. It keeps the [HistoryStore] and the in-memory engine-enabled / API-key state in sync
 * with the persisted preferences so searches reflect changes without a relaunch.
 */
class SettingsViewModel(
    private val preferences: PreferencesRepository,
    private val historyStore: HistoryStore,
    private val engineCatalog: List<EngineDescriptor>,
    private val engineEnabledSink: MutableStateFlow<Map<String, Boolean>>,
    private val apiKeysSink: MutableStateFlow<Map<String, String>>,
) : ViewModel() {
    val preferencesState: StateFlow<UserPreferences> =
        preferences.preferences.stateIn(viewModelScope, SharingStarted.Eagerly, UserPreferences())

    val engines: List<EngineDescriptor> = engineCatalog

    private val mutableApiKeys = MutableStateFlow<Map<String, String>>(emptyMap())

    /** Which engines currently have a key set (the key value itself is never exposed to the UI). */
    val apiKeyPresence: StateFlow<Set<String>> =
        mutableApiKeys
            .map { it.keys.toSet() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    init {
        // Mirror persisted prefs into the live engine-enabled + history sinks so the in-process
        // registry and history store always reflect the user's choices immediately.
        preferences.preferences
            .onEach { prefs ->
                engineEnabledSink.value = engineCatalog.associate { it.id to prefs.isEngineEnabled(it.id) }
                if (historyStore.enabled != prefs.historyEnabled) historyStore.setEnabled(prefs.historyEnabled)
            }.launchIn(viewModelScope)
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { preferences.setDynamicColor(enabled) }
    }

    fun setEngineEnabled(
        engineId: String,
        enabled: Boolean,
    ) {
        viewModelScope.launch {
            preferences.setEngineEnabled(engineId, enabled, preferencesState.value.engineEnabled)
        }
    }

    fun setHistoryEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setHistoryEnabled(enabled) }
    }

    fun clearHistory() {
        historyStore.clear()
    }

    /** Record a query if history is enabled. Wired into the search flow as the recorder callback. */
    fun recordQuery(query: String) {
        historyStore.add(HistoryEntry(query, System.currentTimeMillis()))
    }

    /**
     * Save (or replace) a BYO API key. TODO(storage phase): route through `EncryptedPreferencesCodec`
     * + `Vault` instead of the in-memory sink. Never log the key.
     */
    fun setApiKey(
        engineId: String,
        key: String,
    ) {
        val trimmed = key.trim()
        val next = mutableApiKeys.value.toMutableMap()
        if (trimmed.isEmpty()) next.remove(engineId) else next[engineId] = trimmed
        mutableApiKeys.value = next
        apiKeysSink.value = next
    }

    fun clearApiKey(engineId: String) = setApiKey(engineId, "")

    fun hasApiKey(engineId: String): Boolean = mutableApiKeys.value.containsKey(engineId)
}
