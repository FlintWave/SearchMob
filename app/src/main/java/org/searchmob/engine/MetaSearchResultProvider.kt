package org.searchmob.engine

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient
import org.searchmob.engine.aggregate.AggregatedResult
import org.searchmob.engine.aggregate.Aggregator
import org.searchmob.engine.aggregate.EngineOutcome
import org.searchmob.engine.correct.NoopSpellCorrector
import org.searchmob.engine.correct.SpellCorrector
import org.searchmob.engine.http.HttpClientFactory
import org.searchmob.engine.rank.DomainRanker
import org.searchmob.engine.rank.PersonalizationModel
import org.searchmob.engine.rank.Personalizer
import org.searchmob.engine.rank.RankingRules
import org.searchmob.engine.sort.ResultSorter
import org.searchmob.engine.sort.SortMode
import org.searchmob.engine.summary.WikiSummary
import org.searchmob.engine.vertical.Vertical
import org.searchmob.engine.vertical.Verticals
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
    // The owner's learned click model, or null when personalization is off. Applied (when the caller
    // allows it) as a bounded boost between the sort and the rule pass. Defaults to none.
    private val personalization: suspend () -> PersonalizationModel? = { null },
    // The active UI language tag (or null/empty for English), used to tailor results to that language
    // via per-engine region params. Defaults to none so callers/tests behave region-neutrally.
    private val languageProvider: suspend () -> String? = { null },
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
        personalization: suspend () -> PersonalizationModel? = { null },
        languageProvider: suspend () -> String? = { null },
    ) : this(
        { registry },
        aggregator,
        httpClient,
        corrector,
        rankingRules,
        summaryFetcher,
        slopDomains,
        aiSlopMode,
        personalization,
        languageProvider,
    )

    override suspend fun search(query: String): List<SearchResult> = searchWithCorrection(query).results

    override suspend fun searchWithCorrection(
        query: String,
        sortMode: SortMode,
        vertical: Vertical,
        personalize: Boolean,
        activeLensOverride: String?,
    ): SearchOutcome =
        coroutineScope {
            if (query.isBlank()) return@coroutineScope SearchOutcome(emptyList())

            // Fetch the contextual summary concurrently with the metasearch so it adds no latency.
            val summaryDeferred = async { runCatching { summaryFetcher(query) }.getOrNull() }
            // An inline `+name` scope token (parsed by the route) overrides the saved active scope
            // for this one search only; the persisted selection is never written.
            val rules =
                rankingRules().let { if (activeLensOverride != null) it.copy(activeLens = activeLensOverride) else it }
            val slopMode = aiSlopMode()
            val slop = if (slopMode == "off") emptySet() else slopDomains()
            // The learned model is applied only when the caller allows it (in-app always; the server
            // only for the loopback owner) and only when personalization is enabled.
            val model = if (personalize) runCatching { personalization() }.getOrNull() else null
            val (results, upstreamRaw, engineStatus) =
                aggregateRanked(query, vertical, rules, sortMode, slop, slopMode, model)

            val upstreamCorrection = upstreamRaw?.takeIf { !it.equals(query, ignoreCase = true) }
            val onDevice = corrector.suggest(query)
            val suggestion =
                upstreamCorrection ?: onDevice?.corrected?.takeIf { !it.equals(query, ignoreCase = true) }
            val summary = summaryDeferred.await()

            if (results.isNotEmpty()) {
                return@coroutineScope SearchOutcome(
                    results,
                    didYouMean = suggestion,
                    summary = summary,
                    engineStatus = engineStatus,
                )
            }

            // The original query found nothing: auto-search a confident correction (an upstream
            // correction, or a high-confidence on-device one) and report what we searched instead.
            val autoCorrection =
                upstreamCorrection ?: onDevice?.takeIf { it.confidence >= AUTO_SEARCH_CONFIDENCE }?.corrected
            if (autoCorrection == null || autoCorrection.equals(query, ignoreCase = true)) {
                return@coroutineScope SearchOutcome(
                    results,
                    didYouMean = suggestion,
                    summary = summary,
                    engineStatus = engineStatus,
                )
            }
            val (retryResults, _, _) =
                aggregateRanked(autoCorrection, vertical, rules, sortMode, slop, slopMode, model)
            if (retryResults.isEmpty()) {
                SearchOutcome(results, didYouMean = suggestion, summary = summary, engineStatus = engineStatus)
            } else {
                SearchOutcome(
                    retryResults,
                    showingResultsFor = autoCorrection,
                    summary = summary,
                    engineStatus = engineStatus,
                )
            }
        }

    /** Aggregate [query], sort, apply [rules] locally; return (results, correction, per-engine status). */
    private suspend fun aggregateRanked(
        query: String,
        vertical: Vertical,
        rules: RankingRules,
        sortMode: SortMode,
        slopDomains: Set<String>,
        slopMode: String,
        personalization: PersonalizationModel?,
    ): Triple<List<SearchResult>, String?, List<EngineOutcome>> {
        // Scope the query for the chosen vertical (a `site:` OR group the engines understand). The
        // original query still drives sort/summary/correction so freshness keywords are detected.
        val scoped = Verticals.transformQuery(query, vertical)
        // Tailor results to the active UI language (region-neutral when English/unset).
        val region = languageRegionFor(languageProvider())
        val aggregated =
            aggregator.aggregate(SearchQuery(scoped), registryProvider().activeEngines(httpClient, region))
        // Sort first (relevance/date/freshness blend), then nudge by the owner's learned click model
        // (between sort and rules, so PIN/RAISE/BLOCK still win), then bucket by rules.
        val sorted =
            ResultSorter.sort(
                aggregated.results,
                sortMode,
                query,
                System.currentTimeMillis(),
                publishedOf = { it.publishedMillis },
            )
        val personalized =
            if (personalization != null) {
                Personalizer.reorder(
                    sorted,
                    { DomainRanker.host(it.url) },
                    query,
                    personalization,
                    System.currentTimeMillis(),
                )
            } else {
                sorted
            }
        val ranked =
            DomainRanker.apply(
                items = personalized,
                rules = rules,
                hostOf = { DomainRanker.host(it.url) },
                textOf = { "${it.title} ${it.snippet}" },
                slopDomains = slopDomains,
                slopMode = slopMode,
            )
        return Triple(ranked.map(::toSearchResult), aggregated.correction, aggregated.engineStatus)
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
