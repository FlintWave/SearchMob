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
import org.searchmob.data.prefs.PersonalizationPreferences
import org.searchmob.data.prefs.RankingPreferences
import org.searchmob.engine.rank.DomainRanker
import org.searchmob.engine.rank.Personalizer
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
    private val personalizationPreferences: PersonalizationPreferences? = null,
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

    // The available scopes (lenses) and the active one, for the scope selector. The sample scopes are
    // present by default (seeded by the ranking store), so the selector is useful before any search.
    private val mutableLenses = MutableStateFlow<List<String>>(emptyList())
    val lenses: StateFlow<List<String>> = mutableLenses.asStateFlow()

    private val mutableActiveLens = MutableStateFlow<String?>(null)
    val activeLens: StateFlow<String?> = mutableActiveLens.asStateFlow()

    private var lastDispatched: String = ""
    private var inFlight: Job? = null

    init {
        // Adopt the persisted sort order once on construction (default stays FRESH_RELEVANT).
        preferences?.let {
                p ->
            viewModelScope.launch { mutableSortMode.value = SortMode.fromValue(p.sortMode.first()) }
        }
        // Load the available scopes + the active one so the selector is populated before any search.
        refreshScopes()
    }

    private fun refreshScopes() {
        val prefs = rankingPreferences ?: return
        viewModelScope.launch {
            val rules = prefs.load()
            mutableLenses.value = rules.lenses.map { it.name }
            mutableActiveLens.value = rules.activeLens
        }
    }

    /** Set the active scope (lens) and re-run the last query so the results reflect it. */
    fun setActiveScope(name: String?) {
        val prefs = rankingPreferences ?: return
        if (name == mutableActiveLens.value) return
        viewModelScope.launch {
            prefs.setActiveLens(name)
            mutableActiveLens.value = name
            if (lastDispatched.isNotBlank()) dispatch(lastDispatched)
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

    /**
     * A result was opened: learn from the click (clicked over skipped-above) when personalization is
     * enabled. Resolves the clicked position from the displayed list so the model sees the same order
     * the user saw. Best-effort and off the main thread; a missing/locked vault simply records nothing.
     */
    fun onResultOpened(url: String) {
        val prefs = preferences ?: return
        val store = personalizationPreferences ?: return
        val current = mutableState.value as? SearchUiState.Results ?: return
        val results = current.results
        val position = results.indexOfFirst { it.url == url }
        if (position < 0) return
        val query = lastDispatched
        viewModelScope.launch {
            if (!prefs.personalizationEnabled()) return@launch
            withContext(ioDispatcher) {
                runCatching {
                    val model = store.load()
                    val hosts = results.map { DomainRanker.host(it.url) }
                    Personalizer.updateFromClick(
                        model,
                        hosts,
                        position,
                        Personalizer.queryTerms(query),
                        System.currentTimeMillis(),
                    )
                    store.save(model)
                }
            }
        }
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
                            if (result.results.isEmpty() && result.didYouMean == null) {
                                SearchUiState.Empty
                            } else if (result.results.isEmpty()) {
                                // No results, but the corrector has a suggestion: keep it visible
                                // (a Results state with an empty list) instead of dropping it to the
                                // bare Empty state, mirroring the desktop "No results / did you mean".
                                SearchUiState.Results(
                                    results = emptyList(),
                                    didYouMean = result.didYouMean,
                                    showingResultsFor = result.showingResultsFor,
                                    summary = result.summary,
                                )
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
