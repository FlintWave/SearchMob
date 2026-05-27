package org.searchmob.server

/**
 * Source of search results behind the HTTP routes. The routes depend only on this abstraction so a
 * later change (`add-metasearch-engine-core`) can substitute the real engine without route changes.
 */
interface SearchResultProvider {
    suspend fun search(query: String): List<SearchResult>
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
