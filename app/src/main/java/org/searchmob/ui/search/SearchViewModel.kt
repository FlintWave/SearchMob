package org.searchmob.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Drives the search surface state machine:
 * submit -> [SearchUiState.Loading]; success-with-results -> [SearchUiState.Results];
 * success-empty -> [SearchUiState.Empty]; failure -> [SearchUiState.Error]; retry -> Loading.
 *
 * Empty/whitespace-only submissions are ignored. The query text and results are held in memory only;
 * recording to encrypted history (when enabled) is delegated to [onRecordQuery], which the host wires
 * to `add-encrypted-storage`. Nothing is persisted by this ViewModel itself.
 *
 * The actual fan-out runs off the main thread on [ioDispatcher].
 */
class SearchViewModel(
    private val repository: SearchResultsRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val onRecordQuery: (String) -> Unit = {},
) : ViewModel() {
    private val mutableState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val state: StateFlow<SearchUiState> = mutableState.asStateFlow()

    private val mutableQuery = MutableStateFlow("")
    val query: StateFlow<String> = mutableQuery.asStateFlow()

    private var lastDispatched: String = ""
    private var inFlight: Job? = null

    fun onQueryChange(text: String) {
        mutableQuery.value = text
    }

    /** Dispatch the current query. No-op for empty/whitespace-only input. */
    fun submit() = dispatch(mutableQuery.value)

    /** Re-dispatch the most recently dispatched query (retry / pull-to-refresh). */
    fun retry() {
        if (lastDispatched.isNotBlank()) dispatch(lastDispatched)
    }

    /** Accept a "did you mean" suggestion: put it in the query field and search for it. */
    fun searchCorrected(corrected: String) {
        mutableQuery.value = corrected
        dispatch(corrected)
    }

    private fun dispatch(raw: String) {
        val terms = raw.trim()
        if (terms.isEmpty()) return
        lastDispatched = terms
        inFlight?.cancel()
        mutableState.value = SearchUiState.Loading
        inFlight =
            viewModelScope.launch {
                val outcome =
                    runCatching {
                        withContext(ioDispatcher) { repository.searchWithCorrection(terms) }
                    }
                mutableState.value =
                    outcome.fold(
                        onSuccess = { result ->
                            if (result.results.isEmpty()) {
                                SearchUiState.Empty
                            } else {
                                // Record the query actually answered (the correction when we auto-searched
                                // it), so a typo never pollutes history. The recorder gates on the history
                                // toggle, so store-nothing is honored when history is off.
                                onRecordQuery(result.showingResultsFor ?: terms)
                                SearchUiState.Results(
                                    results = result.results,
                                    didYouMean = result.didYouMean,
                                    showingResultsFor = result.showingResultsFor,
                                )
                            }
                        },
                        onFailure = { error ->
                            SearchUiState.Error(error.message ?: "Search failed")
                        },
                    )
            }
    }
}
