package org.searchmob.server

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
