package org.searchmob.ui.search

import okhttp3.OkHttpClient
import org.searchmob.engine.EngineRegistry
import org.searchmob.engine.MetaSearchResultProvider
import org.searchmob.engine.correct.NoopSpellCorrector
import org.searchmob.engine.correct.SpellCorrector
import org.searchmob.engine.http.HttpClientFactory
import org.searchmob.engine.rank.RankingRules
import org.searchmob.engine.sort.SortMode
import org.searchmob.engine.vertical.Vertical
import org.searchmob.server.SearchOutcome
import org.searchmob.server.SearchResult

/**
 * Source of aggregated results for the UI. Session state is kept in memory only by the caller
 * (the ViewModel); this repository performs no disk writes, so the store-nothing default is honored
 * regardless of the history toggle.
 *
 * The interface lets either the in-process aggregator (default, clean path) or the localhost endpoint
 * back the UI without UI changes.
 */
fun interface SearchResultsRepository {
    suspend fun search(query: String): List<SearchResult>

    /** Search and report any correction, ordering per [sortMode] and scoping to [vertical]. */
    suspend fun searchWithCorrection(
        query: String,
        sortMode: SortMode = SortMode.FRESH_RELEVANT,
        vertical: Vertical = Vertical.WEB,
    ): SearchOutcome = SearchOutcome(search(query))
}

/**
 * Default repository: runs the metasearch aggregator in-process via [MetaSearchResultProvider],
 * avoiding a redundant loopback round-trip. The [EngineRegistry] is supplied per call so the current
 * per-engine enable/disable state and BYO keys are always reflected. The [corrector] supplies the
 * offline "did you mean" fallback.
 */
class InProcessSearchResultsRepository(
    private val registryProvider: () -> EngineRegistry,
    private val httpClient: OkHttpClient = HttpClientFactory.create(),
    private val corrector: SpellCorrector = NoopSpellCorrector,
    private val rankingRules: suspend () -> RankingRules = { RankingRules.EMPTY },
    private val slopDomains: suspend () -> Set<String> = { emptySet() },
    private val aiSlopMode: suspend () -> String = { "off" },
    // Contextual Wikipedia summary for the in-app results, gated by the user preference upstream.
    // Defaults to none so callers/tests without it behave exactly as before.
    private val summaryFetcher: suspend (String) -> org.searchmob.engine.summary.WikiSummary? = { null },
) : SearchResultsRepository {
    private fun provider() =
        MetaSearchResultProvider(
            registry = registryProvider(),
            httpClient = httpClient,
            corrector = corrector,
            rankingRules = rankingRules,
            summaryFetcher = summaryFetcher,
            slopDomains = slopDomains,
            aiSlopMode = aiSlopMode,
        )

    override suspend fun search(query: String): List<SearchResult> = provider().search(query)

    override suspend fun searchWithCorrection(
        query: String,
        sortMode: SortMode,
        vertical: Vertical,
    ): SearchOutcome = provider().searchWithCorrection(query, sortMode, vertical)
}
