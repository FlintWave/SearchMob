package org.searchmob.ui.search

import org.searchmob.server.SearchResult

/**
 * The four explicit, required states of the results surface (plus [Idle] before the first search).
 * A sealed type keeps the states mutually exclusive and directly testable.
 */
sealed interface SearchUiState {
    /** No search has been dispatched yet. */
    data object Idle : SearchUiState

    /** A search is in flight. */
    data object Loading : SearchUiState

    /** A search completed successfully but returned zero results. */
    data object Empty : SearchUiState

    /** A search failed (all engines errored / request failed). */
    data class Error(val message: String) : SearchUiState

    /** A search completed with one or more results. */
    data class Results(val results: List<SearchResult>) : SearchUiState
}
