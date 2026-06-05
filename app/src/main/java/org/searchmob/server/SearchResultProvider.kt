package org.searchmob.server

import org.searchmob.engine.ActionsRow
import org.searchmob.engine.aggregate.EngineOutcome
import org.searchmob.engine.sort.SortMode
import org.searchmob.engine.summary.WikiSummary
import org.searchmob.engine.vertical.Vertical

/**
 * Results plus an optional spelling correction. [didYouMean] is a suggestion to offer while still
 * showing the original query's results. [showingResultsFor] is set instead when the original query
 * returned nothing and a confident correction was searched automatically, so [results] are for the
 * correction and the UI can link back to the original. [summary] is an optional contextual Wikipedia
 * summary shown above the results.
 */
data class SearchOutcome(
    val results: List<SearchResult>,
    val didYouMean: String? = null,
    val showingResultsFor: String? = null,
    val summary: WikiSummary? = null,
    // Per-engine outcome for this search (contributed / empty / failed), for owner-facing diagnostics.
    // Computed on-device; the served page shows it to the loopback owner only. Empty for the stub.
    val engineStatus: List<EngineOutcome> = emptyList(),
    // The "Listen/Watch/Read/Play on" actions row for a resolved media entity, or null. Links are
    // built locally; rendered in-app and on the served page when the media toggle is on.
    val actionsRow: ActionsRow? = null,
)

/**
 * Source of search results behind the HTTP routes. The routes depend only on this abstraction so a
 * later change (`add-metasearch-engine-core`) can substitute the real engine without route changes.
 */
interface SearchResultProvider {
    suspend fun search(query: String): List<SearchResult>

    /**
     * Search and also report any spelling correction, ordering results per [sortMode] and scoping to
     * [vertical] (a `site:` filter over the same engines). Defaults to results with no correction
     * (and ignores the sort/vertical, which only the real provider implements).
     */
    suspend fun searchWithCorrection(
        query: String,
        sortMode: SortMode = SortMode.FRESH_RELEVANT,
        vertical: Vertical = Vertical.WEB,
        // Whether to apply the owner's learned personalization model. The server passes this true
        // only for the loopback owner, so a network visitor never gets the owner's personalized order.
        personalize: Boolean = true,
        // An inline `+name` scope token (parsed from the query by the route) overrides the saved
        // active scope for this one search only; the persisted selection is never written. Null
        // leaves the saved scope in effect.
        activeLensOverride: String? = null,
    ): SearchOutcome = SearchOutcome(search(query))
}

/** Deterministic placeholder provider used until the metasearch engine lands. */
class StubSearchResultProvider : SearchResultProvider {
    override suspend fun search(query: String): List<SearchResult> =
        listOf(
            SearchResult(
                title = "Stub result for \"$query\"",
                url = "https://example.com/?q=$query",
                snippet = "Placeholder result. The metasearch engine replaces this provider in a later phase.",
                engine = "stub",
            ),
        )
}
