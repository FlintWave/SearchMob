package org.searchmob.engine

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient
import org.searchmob.engine.aggregate.AggregatedResult
import org.searchmob.engine.aggregate.Aggregator
import org.searchmob.engine.aggregate.EngineOutcome
import org.searchmob.engine.correct.NoopSpellCorrector
import org.searchmob.engine.correct.SpellCorrector
import org.searchmob.engine.http.HttpClientFactory
import org.searchmob.engine.query.QueryOperators
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
 * The raw query is run through [QueryOperators] before anything else: Google-fu style operators
 * (`site:`, `-exclude`, `"phrase"`, `intitle:`/`inurl:`, `filetype:`/`ext:`, `before:`/`after:`,
 * `OR`/`|`) are split off into an operator-free `cleanText` (for relevance/correction/the contextual
 * summary) and an `engineQuery` sent upstream; whatever no engine can be trusted to honor is enforced
 * locally as a post-filter over the aggregated results.
 *
 * It also surfaces a "did you mean" correction: it prefers the consensus correction the upstream
 * engines reported (parsed from the responses already fetched, no extra request) and otherwise falls
 * back to the offline [corrector] (skipped entirely for an operator-laden query, whose syntax the
 * corrector would just mangle). When the original query returns results, the correction is offered as
 * a non-binding suggestion; when it returns nothing and a confident correction exists, the correction
 * is searched automatically and reported via [SearchOutcome.showingResultsFor].
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
    // Whether the media actions row + canonical-platform promotion are on. Defaults off so callers
    // and tests behave exactly as before.
    private val mediaActionsEnabled: suspend () -> Boolean = { false },
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
        mediaActionsEnabled: suspend () -> Boolean = { false },
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
        mediaActionsEnabled,
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

            // Google-fu style operators (site:/intitle:/before:/etc., see QueryOperators) are parsed
            // once here so the operator-free text can drive anything that reasons about "what is the
            // user asking about" rather than "how do I fetch it".
            val parsed = QueryOperators.parse(query)
            // Fetch the contextual summary concurrently with the metasearch so it adds no latency. Uses
            // the operator-free text: an operator-laden query would never resolve a Wikipedia entity.
            val summaryDeferred = async { runCatching { summaryFetcher(parsed.cleanText) }.getOrNull() }
            // An inline `+name` scope token (parsed by the route) overrides the saved active scope
            // for this one search only; the persisted selection is never written.
            val rules =
                rankingRules().let { if (activeLensOverride != null) it.copy(activeLens = activeLensOverride) else it }
            val slopMode = aiSlopMode()
            val slop = if (slopMode == "off") emptySet() else slopDomains()
            // The learned model is applied only when the caller allows it (in-app always; the server
            // only for the loopback owner) and only when personalization is enabled.
            val model = if (personalize) runCatching { personalization() }.getOrNull() else null
            val mediaOn = mediaActionsEnabled()
            val (results, upstreamRaw, engineStatus) =
                aggregateRanked(
                    query,
                    vertical,
                    rules,
                    sortMode,
                    slop,
                    slopMode,
                    model,
                    summaryDeferred = summaryDeferred.takeIf { mediaOn },
                )

            val upstreamCorrection = upstreamRaw?.takeIf { !it.equals(query, ignoreCase = true) }
            // The on-device corrector reasons about English word spelling, not query syntax: an
            // operator-laden query (parsed.engineQuery differing from parsed.cleanText, or any
            // locally-enforced filter) would just get its operators mangled, so it is skipped and the
            // suggestion relies on whatever the upstream engines themselves reported.
            val hasOperators = parsed.engineQuery != parsed.cleanText || parsed.hasFilters
            val onDevice = if (hasOperators) null else corrector.suggest(query)
            val suggestion =
                upstreamCorrection ?: onDevice?.corrected?.takeIf { !it.equals(query, ignoreCase = true) }
            val summary = summaryDeferred.await()
            // The "Listen/Watch/Read/Play on" actions row for a resolved media entity (toggle on);
            // links are built locally from the entity name.
            val actionsRow =
                if (mediaOn && summary != null) {
                    MediaIntent.detectCategory(summary.description)
                        ?.let { MediaIntent.buildActionsRow(it, summary.title, summary.url) }
                } else {
                    null
                }

            if (results.isNotEmpty()) {
                return@coroutineScope SearchOutcome(
                    results,
                    didYouMean = suggestion,
                    summary = summary,
                    engineStatus = engineStatus,
                    actionsRow = actionsRow,
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
                    actionsRow = actionsRow,
                )
            }
            val (retryResults, _, _) =
                aggregateRanked(autoCorrection, vertical, rules, sortMode, slop, slopMode, model)
            if (retryResults.isEmpty()) {
                SearchOutcome(
                    results,
                    didYouMean = suggestion,
                    summary = summary,
                    engineStatus = engineStatus,
                    actionsRow = actionsRow,
                )
            } else {
                SearchOutcome(
                    retryResults,
                    showingResultsFor = autoCorrection,
                    summary = summary,
                    engineStatus = engineStatus,
                    actionsRow = actionsRow,
                )
            }
        }

    /**
     * Aggregate [query] (parsing any Google-fu operators it contains — see [QueryOperators]), sort,
     * apply [rules] locally; return (results, correction, per-engine status).
     */
    private suspend fun aggregateRanked(
        query: String,
        vertical: Vertical,
        rules: RankingRules,
        sortMode: SortMode,
        slopDomains: Set<String>,
        slopMode: String,
        personalization: PersonalizationModel?,
        // The contextual-summary task, already in flight, when media promotion is enabled. Awaited
        // here so a resolved media entity's canonical platforms are lifted before the user's domain
        // rules (pin/raise/block still win); null disables promotion.
        summaryDeferred: Deferred<WikiSummary?>? = null,
    ): Triple<List<SearchResult>, String?, List<EngineOutcome>> {
        val parsed = QueryOperators.parse(query)
        // Scope the query for the chosen vertical (a `site:` OR group the engines understand), on top
        // of the already-parsed engine query (operators forwarded as-is; intitle:/inurl: turned into a
        // bare recall hint). The operator-free text still drives sort/summary/correction so freshness
        // keywords are detected without a `site:` clause throwing that off.
        val scoped = Verticals.transformQuery(parsed.engineQuery, vertical)
        // Tailor results to the active UI language (region-neutral when English/unset).
        val region = languageRegionFor(languageProvider())
        val aggregated =
            aggregator.aggregate(
                SearchQuery(scoped, rankingTerms = parsed.cleanText),
                registryProvider().activeEngines(httpClient, region),
            )
        // Operators the engines cannot be trusted to honor (intitle:/inurl:/filetype:/before:/after:/
        // -exclusions/site: scoping) are enforced locally here, before sort/personalization/rules see
        // the list.
        val filtered =
            if (parsed.hasFilters) {
                aggregated.results.filter { parsed.matches(it.title, it.url, it.snippet, it.publishedMillis) }
            } else {
                aggregated.results
            }
        // Sort first (relevance/date/freshness blend), then nudge by the owner's learned click model
        // (between sort and rules, so PIN/RAISE/BLOCK still win), then bucket by rules.
        val sorted =
            ResultSorter.sort(
                filtered,
                sortMode,
                parsed.cleanText,
                System.currentTimeMillis(),
                relevanceOf = { it.score },
                publishedOf = { it.publishedMillis },
            )
        val personalizedBase =
            if (personalization != null) {
                Personalizer.reorder(
                    sorted,
                    { DomainRanker.host(it.url) },
                    parsed.cleanText,
                    personalization,
                    System.currentTimeMillis(),
                )
            } else {
                sorted
            }
        // Media promotion: for a resolved media entity, lift its canonical platforms (bounded), after
        // relevance/personalization and before the rules pass so pin/raise/block still win.
        val category = summaryDeferred?.await()?.let { MediaIntent.detectCategory(it.description) }
        val personalized =
            if (category != null) {
                MediaIntent.promoteMedia(personalizedBase, category, urlOf = { it.url })
            } else {
                personalizedBase
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
