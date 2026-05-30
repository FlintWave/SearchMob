package org.searchmob.engine

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient
import org.searchmob.engine.aggregate.AggregatedResult
import org.searchmob.engine.aggregate.Aggregator
import org.searchmob.engine.correct.NoopSpellCorrector
import org.searchmob.engine.correct.SpellCorrector
import org.searchmob.engine.http.HttpClientFactory
import org.searchmob.engine.rank.DomainRanker
import org.searchmob.engine.rank.RankingRules
import org.searchmob.engine.sort.ResultSorter
import org.searchmob.engine.sort.SortMode
import org.searchmob.engine.summary.WikiSummary
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
    private val registryProvider: suspend () -> EngineRegistry,
    private val aggregator: Aggregator = Aggregator(),
    private val httpClient: OkHttpClient = HttpClientFactory.create(),
    private val corrector: SpellCorrector = NoopSpellCorrector,
    private val rankingRules: suspend () -> RankingRules = { RankingRules.EMPTY },
    // Optional contextual Wikipedia summary, fetched concurrently with the metasearch. Returns null
    // when disabled or not warranted; never fails the search.
    private val summaryFetcher: suspend (String) -> WikiSummary? = { null },
    // On-device AI-slop blocklist (bare domains) and the user's filter mode ("off"/"downrank"/"hide").
    // Both default inert so callers without the filter behave exactly as before.
    private val slopDomains: suspend () -> Set<String> = { emptySet() },
    private val aiSlopMode: suspend () -> String = { "off" },
) : SearchResultProvider {
    /** Convenience constructor for a fixed registry (tests and callers without dynamic config). */
    constructor(
        registry: EngineRegistry,
        aggregator: Aggregator = Aggregator(),
        httpClient: OkHttpClient = HttpClientFactory.create(),
        corrector: SpellCorrector = NoopSpellCorrector,
        rankingRules: suspend () -> RankingRules = { RankingRules.EMPTY },
        summaryFetcher: suspend (String) -> WikiSummary? = { null },
        slopDomains: suspend () -> Set<String> = { emptySet() },
        aiSlopMode: suspend () -> String = { "off" },
    ) : this(
        { registry },
        aggregator,
        httpClient,
        corrector,
        rankingRules,
        summaryFetcher,
        slopDomains,
        aiSlopMode,
    )

    override suspend fun search(query: String): List<SearchResult> = searchWithCorrection(query).results

    override suspend fun searchWithCorrection(
        query: String,
        sortMode: SortMode,
    ): SearchOutcome =
        coroutineScope {
            if (query.isBlank()) return@coroutineScope SearchOutcome(emptyList())

            // Fetch the contextual summary concurrently with the metasearch so it adds no latency.
            val summaryDeferred = async { runCatching { summaryFetcher(query) }.getOrNull() }
            val rules = rankingRules()
            val slopMode = aiSlopMode()
            val slop = if (slopMode == "off") emptySet() else slopDomains()
            val (results, upstreamRaw) = aggregateRanked(query, rules, sortMode, slop, slopMode)

            val upstreamCorrection = upstreamRaw?.takeIf { !it.equals(query, ignoreCase = true) }
            val onDevice = corrector.suggest(query)
            val suggestion =
                upstreamCorrection ?: onDevice?.corrected?.takeIf { !it.equals(query, ignoreCase = true) }
            val summary = summaryDeferred.await()

            if (results.isNotEmpty()) {
                return@coroutineScope SearchOutcome(results, didYouMean = suggestion, summary = summary)
            }

            // The original query found nothing: auto-search a confident correction (an upstream
            // correction, or a high-confidence on-device one) and report what we searched instead.
            val autoCorrection =
                upstreamCorrection ?: onDevice?.takeIf { it.confidence >= AUTO_SEARCH_CONFIDENCE }?.corrected
            if (autoCorrection == null || autoCorrection.equals(query, ignoreCase = true)) {
                return@coroutineScope SearchOutcome(results, didYouMean = suggestion, summary = summary)
            }
            val (retryResults, _) = aggregateRanked(autoCorrection, rules, sortMode, slop, slopMode)
            if (retryResults.isEmpty()) {
                SearchOutcome(results, didYouMean = suggestion, summary = summary)
            } else {
                SearchOutcome(retryResults, showingResultsFor = autoCorrection, summary = summary)
            }
        }

    /** Aggregate [query], sort, apply the personalization [rules] locally; return (results, correction). */
    private suspend fun aggregateRanked(
        query: String,
        rules: RankingRules,
        sortMode: SortMode,
        slopDomains: Set<String>,
        slopMode: String,
    ): Pair<List<SearchResult>, String?> {
        val aggregated = aggregator.aggregate(SearchQuery(query), registryProvider().activeEngines(httpClient))
        // Sort first (relevance/date/freshness blend), then bucket by rules so PIN/RAISE preserve
        // the chosen order within each bucket.
        val sorted =
            ResultSorter.sort(
                aggregated.results,
                sortMode,
                query,
                System.currentTimeMillis(),
                publishedOf = { it.publishedMillis },
            )
        val ranked =
            DomainRanker.apply(
                items = sorted,
                rules = rules,
                hostOf = { DomainRanker.host(it.url) },
                textOf = { "${it.title} ${it.snippet}" },
                slopDomains = slopDomains,
                slopMode = slopMode,
            )
        return ranked.map(::toSearchResult) to aggregated.correction
    }

    private fun toSearchResult(result: AggregatedResult): SearchResult =
        SearchResult(
            title = result.title,
            url = result.url,
            snippet = result.snippet,
            engine = result.engines.joinToString(","),
            publishedMillis = result.publishedMillis,
        )

    private companion object {
        /** On-device confidence required to auto-search a correction when the original query is empty. */
        const val AUTO_SEARCH_CONFIDENCE = 0.9
    }
}
