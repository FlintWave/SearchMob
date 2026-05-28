package org.searchmob.server

import kotlinx.serialization.Serializable

/** A single search result. The HTTP contract depends on this, not on any specific provider. */
@Serializable
data class SearchResult(
    val title: String,
    val url: String,
    val snippet: String = "",
    val engine: String = "",
)

/** JSON envelope returned by the search API. */
@Serializable
data class SearchResponse(
    val query: String,
    val results: List<SearchResult>,
    /** A spelling correction to offer ("did you mean"); results are still for [query]. */
    val didYouMean: String? = null,
    /** Set when [query] returned nothing and results are for this auto-searched correction instead. */
    val showingResultsFor: String? = null,
)
