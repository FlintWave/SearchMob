package org.searchmob.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.searchmob.data.prefs.RankingPreferences
import org.searchmob.engine.rank.DomainRanker
import org.searchmob.engine.rank.RankRule
import org.searchmob.engine.sort.SortMode
import org.searchmob.engine.vertical.Vertical
import org.searchmob.ui.prefs.PreferencesRepository

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
    private val rankingPreferences: RankingPreferences? = null,
    private val preferences: PreferencesRepository? = null,
) : ViewModel() {
    private val mutableState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val state: StateFlow<SearchUiState> = mutableState.asStateFlow()

    private val mutableQuery = MutableStateFlow("")
    val query: StateFlow<String> = mutableQuery.asStateFlow()

    private val mutableSortMode = MutableStateFlow(SortMode.FRESH_RELEVANT)
    val sortMode: StateFlow<SortMode> = mutableSortMode.asStateFlow()

    // The active search vertical (Web / News / Forums / Academic). Per-session, defaulting to Web;
    // not persisted, matching the desktop app.
    private val mutableVertical = MutableStateFlow(Vertical.WEB)
    val vertical: StateFlow<Vertical> = mutableVertical.asStateFlow()

    private var lastDispatched: String = ""
    private var inFlight: Job? = null

    init {
        // Adopt the persisted sort order once on construction (default stays FRESH_RELEVANT).
        preferences?.let {
                p ->
            viewModelScope.launch { mutableSortMode.value = SortMode.fromValue(p.sortMode.first()) }
        }
    }

    /** Change the result sort order: persist it and re-run the last query so the order updates. */
    fun setSortMode(mode: SortMode) {
        if (mode == mutableSortMode.value) return
        mutableSortMode.value = mode
        preferences?.let { p -> viewModelScope.launch { p.setSortMode(mode.value) } }
        if (lastDispatched.isNotBlank()) dispatch(lastDispatched)
    }

    /** Switch the search vertical and re-run the last query scoped to it. */
    fun setVertical(vertical: Vertical) {
        if (vertical == mutableVertical.value) return
        mutableVertical.value = vertical
        if (lastDispatched.isNotBlank()) dispatch(lastDispatched)
    }

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

    /**
     * Set a per-domain ranking rule from the inline result menu. Persists it to the encrypted store and
     * re-ranks the currently shown results immediately (no re-fetch), so a Block hides the domain now.
     */
    fun setDomainRule(
        domain: String,
        rule: RankRule,
    ) {
        val prefs = rankingPreferences ?: return
        viewModelScope.launch {
            prefs.setDomainRule(domain, rule)
            val rules = withContext(ioDispatcher) { prefs.load() }
            val current = mutableState.value as? SearchUiState.Results ?: return@launch
            val reranked =
                DomainRanker.apply(
                    items = current.results,
                    rules = rules,
                    hostOf = { DomainRanker.host(it.url) },
                    textOf = { "${it.title} ${it.snippet}" },
                )
            mutableState.value =
                if (reranked.isEmpty()) SearchUiState.Empty else current.copy(results = reranked)
        }
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
                        withContext(ioDispatcher) {
                            repository.searchWithCorrection(terms, mutableSortMode.value, mutableVertical.value)
                        }
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
                                    summary = result.summary,
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
