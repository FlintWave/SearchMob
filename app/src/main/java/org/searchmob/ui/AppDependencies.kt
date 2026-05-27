package org.searchmob.ui

import kotlinx.coroutines.flow.MutableStateFlow
import org.searchmob.data.history.HistoryStore
import org.searchmob.data.history.InMemoryHistoryStore
import org.searchmob.engine.EngineAdapter
import org.searchmob.engine.EngineConfig
import org.searchmob.engine.EngineRegistry
import org.searchmob.engine.adapters.BraveApiAdapter
import org.searchmob.engine.adapters.DuckDuckGoAdapter
import org.searchmob.engine.adapters.MarginaliaAdapter
import org.searchmob.engine.adapters.MojeekAdapter
import org.searchmob.engine.adapters.MojeekApiAdapter
import org.searchmob.engine.adapters.MwmblAdapter
import org.searchmob.engine.adapters.WikipediaAdapter
import org.searchmob.ui.prefs.InMemoryPreferencesStore
import org.searchmob.ui.prefs.PreferencesRepository
import org.searchmob.ui.prefs.PreferencesStore
import org.searchmob.ui.search.InProcessSearchResultsRepository
import org.searchmob.ui.search.SearchResultsRepository

/**
 * The single composition root / injection point for the UI layer. It owns the in-process engine
 * adapters, the preference state, the results repository, and the history store, and rebuilds the
 * [EngineRegistry] on demand from the current per-engine toggles and BYO API keys.
 *
 * INJECTION POINTS (owned by other phases, wired here, not reimplemented):
 * - [PreferencesStore]: defaults to the in-memory store; the storage phase binds an encrypted
 *   DataStore implementation here for reboot persistence.
 * - [HistoryStore]: defaults to the in-memory reference; the storage phase binds the SQLCipher store.
 * - API keys: held in [apiKeys] in memory by default; the storage phase routes read/write/clear
 *   through `EncryptedPreferencesCodec` + `Vault` (never DataStore plaintext, never logs).
 */
class AppDependencies(
    val preferencesStore: PreferencesStore = InMemoryPreferencesStore(),
    val historyStore: HistoryStore = InMemoryHistoryStore(),
    private val adapters: List<EngineAdapter> = defaultAdapters(),
) {
    val preferencesRepository: PreferencesRepository =
        PreferencesRepository(preferencesStore, knownEngineIds = adapters.map { it.id })

    /** Engine ids and display names, for the per-engine settings toggles. */
    val engineCatalog: List<EngineDescriptor> =
        adapters.map {
            EngineDescriptor(
                it.id,
                it.displayName,
                it.requiresApiKey,
            )
        }

    /**
     * In-memory BYO API keys keyed by engine id. TODO(storage phase): replace this with read/write
     * through `EncryptedPreferencesCodec` + `Vault`; never persist keys as DataStore plaintext.
     */
    val apiKeys: MutableStateFlow<Map<String, String>> = MutableStateFlow(emptyMap())

    /** Latest per-engine enabled snapshot, updated by the settings layer; used to build the registry. */
    val engineEnabled: MutableStateFlow<Map<String, Boolean>> = MutableStateFlow(emptyMap())

    val searchRepository: SearchResultsRepository =
        InProcessSearchResultsRepository(registryProvider = ::buildRegistry)

    /** Build a fresh registry from the current toggle + key state. */
    fun buildRegistry(): EngineRegistry {
        val enabled = engineEnabled.value
        val keys = apiKeys.value
        val configs =
            adapters.associate { adapter ->
                adapter.id to
                    EngineConfig(
                        engineId = adapter.id,
                        enabled = enabled[adapter.id] ?: !adapter.requiresApiKey,
                        apiKey = keys[adapter.id],
                    )
            }
        return EngineRegistry(adapters = adapters, configs = configs)
    }

    companion object {
        /** All in-process engine adapters: free scrapers/APIs + the two BYO-key APIs. */
        fun defaultAdapters(): List<EngineAdapter> =
            listOf(
                DuckDuckGoAdapter(),
                MojeekAdapter(),
                MarginaliaAdapter(),
                MwmblAdapter(),
                WikipediaAdapter(),
                BraveApiAdapter(),
                MojeekApiAdapter(),
            )
    }
}

/** A search engine the user can toggle / supply a key for in settings. */
data class EngineDescriptor(
    val id: String,
    val displayName: String,
    val requiresApiKey: Boolean,
)

/** Engine ids that accept a bring-your-own API key, surfaced in settings. */
object ApiKeyEngines {
    const val BRAVE = "brave-api"
    const val MOJEEK = "mojeek-api"
}
