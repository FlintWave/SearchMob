package org.searchmob.ui

import kotlinx.coroutines.flow.MutableStateFlow
import org.searchmob.data.history.HistoryStore
import org.searchmob.data.history.InMemoryHistoryStore
import org.searchmob.data.prefs.EngineConfigPreferences
import org.searchmob.data.prefs.RankingPreferences
import org.searchmob.engine.EngineAdapter
import org.searchmob.engine.EngineConfig
import org.searchmob.engine.EngineRegistry
import org.searchmob.engine.adapters.BraveApiAdapter
import org.searchmob.engine.adapters.DuckDuckGoAdapter
import org.searchmob.engine.adapters.KagiApiAdapter
import org.searchmob.engine.adapters.MarginaliaAdapter
import org.searchmob.engine.adapters.MojeekAdapter
import org.searchmob.engine.adapters.MojeekApiAdapter
import org.searchmob.engine.adapters.MwmblAdapter
import org.searchmob.engine.adapters.WikipediaAdapter
import org.searchmob.engine.correct.NoopSpellCorrector
import org.searchmob.engine.correct.SpellCorrector
import org.searchmob.engine.rank.RankingRules
import org.searchmob.i18n.SupportedLocales
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
 * INJECTION POINTS:
 * - [PreferencesStore]: the non-secret UI prefs (theme, toggles). Defaults to the in-memory store;
 *   the real app binds the plaintext DataStore (these values are not sensitive).
 * - [HistoryStore]: defaults to the in-memory reference; the real app binds the shared SQLCipher store
 *   from [org.searchmob.SearchMobApplication].
 * - [engineConfig]: the DEK-encrypted preferences bridge that persists BYO API keys at rest. Null in
 *   tests, where keys stay in the in-memory [apiKeys] cache only.
 */
class AppDependencies(
    val preferencesStore: PreferencesStore = InMemoryPreferencesStore(),
    val historyStore: HistoryStore = InMemoryHistoryStore(),
    val engineConfig: EngineConfigPreferences? = null,
    val spellCorrector: SpellCorrector = NoopSpellCorrector,
    val rankingPreferences: RankingPreferences? = null,
    val personalizationPreferences: org.searchmob.data.prefs.PersonalizationPreferences? = null,
    // Cached AI-slop blocklist domains, supplied by the process-wide loader; empty in tests.
    private val slopDomains: () -> Set<String> = { emptySet() },
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
     * Runtime cache of BYO API keys keyed by engine id, read synchronously by [buildRegistry]. The
     * durable copy lives encrypted in [engineConfig]; this cache is hydrated from it at startup (see
     * [hydrateApiKeys]) and kept in sync by the settings layer on each write.
     */
    val apiKeys: MutableStateFlow<Map<String, String>> = MutableStateFlow(emptyMap())

    /**
     * Load the persisted (encrypted) BYO API keys into the [apiKeys] runtime cache. Call once at
     * startup so the engine registry picks them up before the user opens settings. A no-op when no
     * [engineConfig] is bound (tests) or while the vault is locked (the read fails soft).
     */
    suspend fun hydrateApiKeys() {
        val config = engineConfig ?: return
        val loaded =
            engineCatalog
                .filter { it.requiresApiKey }
                .mapNotNull { descriptor ->
                    runCatching { config.apiKey(descriptor.id) }.getOrNull()?.let { descriptor.id to it }
                }.toMap()
        if (loaded.isNotEmpty()) apiKeys.value = loaded
    }

    /** Latest per-engine enabled snapshot, updated by the settings layer; used to build the registry. */
    val engineEnabled: MutableStateFlow<Map<String, Boolean>> = MutableStateFlow(emptyMap())

    // Single contextual-summary provider (its own privacy HTTP client), reused across in-app searches.
    private val wikiSummaryProvider by lazy {
        org.searchmob.engine.summary.WikiSummaryProvider(
            org.searchmob.engine.http.HttpClientFactory.create(),
        )
    }

    val searchRepository: SearchResultsRepository =
        InProcessSearchResultsRepository(
            registryProvider = ::buildRegistry,
            corrector = spellCorrector,
            rankingRules = { rankingPreferences?.load() ?: RankingRules.EMPTY },
            slopDomains = { slopDomains() },
            aiSlopMode = { preferencesRepository.aiSlopMode() },
            // Show the Wikipedia summary card in the in-app results too (was server-only); gated by
            // the same preference the served page uses.
            summaryFetcher = { query ->
                if (preferencesRepository.summaryEnabled()) wikiSummaryProvider.fetch(query) else null
            },
            // In-app results are always the owner's, so apply the learned model whenever it is on.
            personalization = {
                if (preferencesRepository.personalizationEnabled()) personalizationPreferences?.load() else null
            },
            // Tailor results to the chosen UI language (region-neutral when English / following the OS).
            languageProvider = { SupportedLocales.effectiveTag(preferencesRepository.language()) },
            // Media actions row + canonical-platform promotion, gated by the user's toggle.
            mediaActionsEnabled = { preferencesRepository.mediaActionsEnabled() },
        )

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
        /** All in-process engine adapters: free scrapers/APIs + the BYO-key APIs. */
        fun defaultAdapters(): List<EngineAdapter> =
            listOf(
                DuckDuckGoAdapter(),
                MojeekAdapter(),
                MarginaliaAdapter(),
                MwmblAdapter(),
                WikipediaAdapter(),
                BraveApiAdapter(),
                MojeekApiAdapter(),
                KagiApiAdapter(),
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
    const val KAGI = "kagi-api"
}
