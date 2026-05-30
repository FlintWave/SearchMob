package org.searchmob.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.searchmob.data.history.HistoryEntry
import org.searchmob.data.history.HistoryStore
import org.searchmob.data.prefs.EngineConfigPreferences
import org.searchmob.data.prefs.RankingPreferences
import org.searchmob.engine.rank.DEFAULT_SAMPLE_LENSES
import org.searchmob.engine.rank.Goggles
import org.searchmob.engine.rank.Lens
import org.searchmob.engine.rank.RankRule
import org.searchmob.engine.rank.RankingRules
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
    private val engineConfig: EngineConfigPreferences? = null,
    private val rankingPreferences: RankingPreferences? = null,
) : ViewModel() {
    val preferencesState: StateFlow<UserPreferences> =
        preferences.preferences.stateIn(viewModelScope, SharingStarted.Eagerly, UserPreferences())

    val engines: List<EngineDescriptor> = engineCatalog

    private val mutableApiKeys = MutableStateFlow<Map<String, String>>(emptyMap())

    private val mutableShowNetworkWarning = MutableStateFlow(false)

    /** Which engines currently have a key set (the key value itself is never exposed to the UI). */
    val apiKeyPresence: StateFlow<Set<String>> =
        mutableApiKeys
            .map { it.keys.toSet() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private val mutableRankingRules = MutableStateFlow(RankingRules.EMPTY)

    /** The current personalization rules (domain rules, lenses, active lens, goggles) for the UI. */
    val rankingRules: StateFlow<RankingRules> = mutableRankingRules

    init {
        // Mirror persisted prefs into the live engine-enabled + history sinks so the in-process
        // registry and history store always reflect the user's choices immediately.
        preferences.preferences
            .onEach { prefs ->
                engineEnabledSink.value = engineCatalog.associate { it.id to prefs.isEngineEnabled(it.id) }
                // setEnabled(false) deletes the encrypted DB file, so keep it off the main thread.
                if (historyStore.enabled != prefs.historyEnabled) {
                    withContext(Dispatchers.IO) { historyStore.setEnabled(prefs.historyEnabled) }
                }
            }.launchIn(viewModelScope)

        // Load the persisted (encrypted) BYO API keys so key presence and the registry reflect what is
        // already stored, without exposing the key values to the UI beyond this view model.
        viewModelScope.launch {
            val stored =
                engineCatalog
                    .filter { it.requiresApiKey }
                    .mapNotNull { engine ->
                        runCatching { engineConfig?.apiKey(engine.id) }.getOrNull()?.let { engine.id to it }
                    }.toMap()
            if (stored.isNotEmpty()) {
                mutableApiKeys.value = stored
                apiKeysSink.value = stored
            }
        }

        refreshRankingRules()
    }

    private fun refreshRankingRules() {
        val prefs = rankingPreferences ?: return
        viewModelScope.launch { mutableRankingRules.value = prefs.load() }
    }

    /** Clear a per-domain rule (set it to Normal) from the Settings list. */
    fun clearDomainRule(domain: String) {
        val prefs = rankingPreferences ?: return
        viewModelScope.launch {
            prefs.setDomainRule(domain, RankRule.NORMAL)
            mutableRankingRules.value = prefs.load()
        }
    }

    /** Create or replace a lens (matched by name). */
    fun saveLens(lens: Lens) {
        val prefs = rankingPreferences ?: return
        viewModelScope.launch {
            prefs.upsertLens(lens)
            mutableRankingRules.value = prefs.load()
        }
    }

    fun deleteLens(name: String) {
        val prefs = rankingPreferences ?: return
        viewModelScope.launch {
            prefs.removeLens(name)
            mutableRankingRules.value = prefs.load()
        }
    }

    /** Add the built-in sample scopes (skipping any whose name already exists). */
    fun addSampleLenses() {
        val prefs = rankingPreferences ?: return
        viewModelScope.launch {
            val existing = prefs.load().lenses.map { it.name }.toSet()
            DEFAULT_SAMPLE_LENSES.filter { it.name !in existing }.forEach { prefs.upsertLens(it) }
            mutableRankingRules.value = prefs.load()
        }
    }

    /** Select the active lens, or null to clear it. */
    fun selectLens(name: String?) {
        val prefs = rankingPreferences ?: return
        viewModelScope.launch {
            prefs.setActiveLens(name)
            mutableRankingRules.value = prefs.load()
        }
    }

    /** Import goggle rules from pasted/file text; replaces any previously imported goggles. */
    fun importGoggles(text: String) {
        val prefs = rankingPreferences ?: return
        viewModelScope.launch {
            prefs.importGoggles(Goggles.parse(text))
            mutableRankingRules.value = prefs.load()
        }
    }

    fun clearGoggles() {
        val prefs = rankingPreferences ?: return
        viewModelScope.launch {
            prefs.importGoggles(emptyList())
            mutableRankingRules.value = prefs.load()
        }
    }

    /** Replace all rules from an exported JSON document; returns true on success. */
    fun importRulesJson(
        text: String,
        onResult: (Boolean) -> Unit = {},
    ) {
        val prefs = rankingPreferences ?: return onResult(false)
        viewModelScope.launch {
            val ok = prefs.importJson(text)
            if (ok) mutableRankingRules.value = prefs.load()
            onResult(ok)
        }
    }

    /** The full rule set as a JSON document for export, delivered via [onReady]. */
    fun exportRulesJson(onReady: (String) -> Unit) {
        val prefs = rankingPreferences ?: return onReady("{}")
        viewModelScope.launch { onReady(prefs.exportJson()) }
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

    /**
     * Toggle the opt-in upstream (web) suggestions source. Unlike network mode there is no blocking
     * warning dialog: the subtitle explains the trade-off and it persists immediately either way.
     */
    fun setUpstreamSuggestionsEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setUpstreamSuggestionsEnabled(enabled) }
    }

    /** Toggle the contextual Wikipedia summary box. ON by default; persists immediately. */
    fun setSummaryEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setSummaryEnabled(enabled) }
    }

    /** Set the AI-slop filter mode ("downrank"/"hide"/"off"). Default "downrank"; persists immediately. */
    fun setAiSlopMode(mode: String) {
        viewModelScope.launch { preferences.setAiSlopMode(mode) }
    }

    /**
     * Toggle the opt-out launch-time update check. ON by default; the subtitle discloses the GitHub
     * call and that it routes through the privacy proxy. Persists immediately with no warning dialog.
     */
    fun setUpdateCheckEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setUpdateCheckEnabled(enabled) }
    }

    /**
     * Whether the network-mode warning dialog is currently shown. Turning the toggle ON opens it;
     * confirming or cancelling closes it. Turning OFF never opens it.
     */
    val showNetworkWarning: StateFlow<Boolean> = mutableShowNetworkWarning

    /**
     * Handle a network-mode toggle. Enabling is gated: it only opens the warning dialog and does NOT
     * persist yet. Disabling persists immediately with no confirmation.
     */
    fun onNetworkAccessToggle(requestedOn: Boolean) {
        if (requestedOn) {
            mutableShowNetworkWarning.value = true
        } else {
            mutableShowNetworkWarning.value = false
            viewModelScope.launch { preferences.setNetworkAccessEnabled(false) }
        }
    }

    /** User confirmed the warning: persist network mode ON and close the dialog. */
    fun confirmNetworkAccess() {
        mutableShowNetworkWarning.value = false
        viewModelScope.launch { preferences.setNetworkAccessEnabled(true) }
    }

    /** User cancelled the warning: leave network mode OFF and close the dialog. */
    fun cancelNetworkAccess() {
        mutableShowNetworkWarning.value = false
    }

    fun clearHistory() {
        // The SQLCipher-backed store touches the database, which must not run on the main thread.
        viewModelScope.launch(Dispatchers.IO) { historyStore.clear() }
    }

    /**
     * Record a query if history is enabled. Wired into the search flow as the recorder callback. Runs
     * off the main thread because the encrypted store performs Room/SQLCipher I/O.
     */
    fun recordQuery(query: String) {
        val entry = HistoryEntry(query, System.currentTimeMillis())
        viewModelScope.launch(Dispatchers.IO) { historyStore.add(entry) }
    }

    /**
     * Save (or replace) a BYO API key. The durable copy is written through [engineConfig], where it is
     * AES-256-GCM-encrypted at rest; the in-memory caches keep the live registry in sync. The key is
     * never logged.
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
        viewModelScope.launch { engineConfig?.setApiKey(engineId, trimmed.ifEmpty { null }) }
    }

    fun clearApiKey(engineId: String) = setApiKey(engineId, "")

    fun hasApiKey(engineId: String): Boolean = mutableApiKeys.value.containsKey(engineId)
}
