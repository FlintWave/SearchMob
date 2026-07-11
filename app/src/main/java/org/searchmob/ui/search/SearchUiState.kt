package org.searchmob.ui.search

import org.searchmob.engine.ActionsRow
import org.searchmob.engine.aggregate.EngineOutcome
import org.searchmob.engine.instant.InstantAnswer
import org.searchmob.engine.summary.WikiSummary
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

    /**
     * A search completed with one or more results. [didYouMean] is a correction to offer while showing
     * results for the original query; [showingResultsFor] is set instead when the original query was
     * empty and these results are for that auto-searched correction.
     */
    data class Results(
        val results: List<SearchResult>,
        val didYouMean: String? = null,
        val showingResultsFor: String? = null,
        val summary: WikiSummary? = null,
        // Per-engine outcome for this search (contributed / empty / failed). In-app results are always
        // the owner's, so this is shown; computed on-device and never persisted or transmitted.
        val engineStatus: List<EngineOutcome> = emptyList(),
        // The "Listen/Watch/Read/Play on" actions row for a resolved media entity, or null.
        val actionsRow: ActionsRow? = null,
        // On-device instant answer (calculator / unit / base conversion), or null for most queries.
        val instantAnswer: InstantAnswer? = null,
    ) : SearchUiState
}
