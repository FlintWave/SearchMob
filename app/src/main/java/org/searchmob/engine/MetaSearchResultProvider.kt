package org.searchmob.engine

import okhttp3.OkHttpClient
import org.searchmob.engine.aggregate.AggregatedResult
import org.searchmob.engine.aggregate.Aggregator
import org.searchmob.engine.correct.NoopSpellCorrector
import org.searchmob.engine.correct.SpellCorrector
import org.searchmob.engine.http.HttpClientFactory
import org.searchmob.server.SearchOutcome
import org.searchmob.server.SearchResult
import org.searchmob.server.SearchResultProvider

/**
 * Bridges the metasearch engine to the server's [SearchResultProvider] contract, so the engine drops
 * in behind the unchanged HTTP routes.
 *
 * It also surfaces a "did you mean" correction: it prefers the consensus correction the upstream
 * engines reported (parsed from the responses already fetched, no extra request) and otherwise falls
 * back to the offline [corrector]. When the original query returns results, the correction is offered
 * as a non-binding suggestion; when it returns nothing and a confident correction exists, the
 * correction is searched automatically and reported via [SearchOutcome.showingResultsFor].
 */
class MetaSearchResultProvider(
    private val registry: EngineRegistry,
    private val aggregator: Aggregator = Aggregator(),
    private val httpClient: OkHttpClient = HttpClientFactory.create(),
    private val corrector: SpellCorrector = NoopSpellCorrector,
) : SearchResultProvider {
    override suspend fun search(query: String): List<SearchResult> = searchWithCorrection(query).results

    override suspend fun searchWithCorrection(query: String): SearchOutcome {
        if (query.isBlank()) return SearchOutcome(emptyList())

        val aggregated = aggregator.aggregate(SearchQuery(query), registry.activeEngines(httpClient))
        val results = aggregated.results.map(::toSearchResult)

        val upstream = aggregated.correction?.takeIf { !it.equals(query, ignoreCase = true) }
        val onDevice = corrector.suggest(query)
        val suggestion = upstream ?: onDevice?.corrected?.takeIf { !it.equals(query, ignoreCase = true) }

        if (results.isNotEmpty()) {
            return SearchOutcome(results, didYouMean = suggestion)
        }

        // The original query found nothing: auto-search a confident correction (an upstream correction,
        // or a high-confidence on-device one) and report what we searched instead.
        val autoCorrection =
            upstream ?: onDevice?.takeIf { it.confidence >= AUTO_SEARCH_CONFIDENCE }?.corrected
        if (autoCorrection == null || autoCorrection.equals(query, ignoreCase = true)) {
            return SearchOutcome(results, didYouMean = suggestion)
        }
        val retry = aggregator.aggregate(SearchQuery(autoCorrection), registry.activeEngines(httpClient))
        val retryResults = retry.results.map(::toSearchResult)
        return if (retryResults.isEmpty()) {
            SearchOutcome(results, didYouMean = suggestion)
        } else {
            SearchOutcome(retryResults, showingResultsFor = autoCorrection)
        }
    }

    private fun toSearchResult(result: AggregatedResult): SearchResult =
        SearchResult(
            title = result.title,
            url = result.url,
            snippet = result.snippet,
            engine = result.engines.joinToString(","),
        )

    private companion object {
        /** On-device confidence required to auto-search a correction when the original query is empty. */
        const val AUTO_SEARCH_CONFIDENCE = 0.9
    }
}
