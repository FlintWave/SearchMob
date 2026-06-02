package org.searchmob.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.searchmob.engine.sort.SortMode
import org.searchmob.engine.summary.WikiSummary
import org.searchmob.server.SearchOutcome
import org.searchmob.server.SearchResult
import org.searchmob.ui.search.SearchResultsRepository
import org.searchmob.ui.search.SearchUiState
import org.searchmob.ui.search.SearchViewModel

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    // Minimal in-memory data-layer store for the encrypted-model side (PersonalizationPreferences).
    private class FakeModelStore : org.searchmob.data.prefs.PreferencesStore {
        private val map = mutableMapOf<String, String>()

        override fun observe() = kotlinx.coroutines.flow.flowOf(map.toMap())

        override suspend fun getAll(): org.searchmob.data.prefs.Preferences = map.toMap()

        override suspend fun get(key: String): String? = map[key]

        override suspend fun put(
            key: String,
            value: String,
        ) {
            map[key] = value
        }

        override suspend fun remove(key: String) {
            map.remove(key)
        }

        override suspend fun clear() = map.clear()
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm(
        repo: SearchResultsRepository,
        onRecord: (String) -> Unit = {},
    ) = SearchViewModel(repository = repo, ioDispatcher = dispatcher, onRecordQuery = onRecord)

    @Test
    fun submit_withResults_goesLoadingThenResults() =
        runTest(dispatcher) {
            val results = listOf(SearchResult("T", "https://e.com", "snip", "duckduckgo"))
            val viewModel = vm({ results })
            viewModel.onQueryChange("kotlin")
            viewModel.submit()
            assertEquals(SearchUiState.Loading, viewModel.state.value)
            advanceUntilIdle()
            assertEquals(SearchUiState.Results(results), viewModel.state.value)
        }

    @Test
    fun submit_withSummary_includesSummaryInResultsState() =
        runTest(dispatcher) {
            val box =
                WikiSummary(
                    title = "Mount Everest",
                    description = "Earth's highest mountain",
                    extract = "Mount Everest is Earth's highest mountain.",
                    url = "https://en.wikipedia.org/wiki/Mount_Everest",
                )
            val repo =
                object : SearchResultsRepository {
                    override suspend fun search(query: String): List<SearchResult> =
                        listOf(SearchResult("T", "https://e.com", "s", "ddg"))

                    override suspend fun searchWithCorrection(
                        query: String,
                        sortMode: org.searchmob.engine.sort.SortMode,
                        vertical: org.searchmob.engine.vertical.Vertical,
                    ): SearchOutcome = SearchOutcome(results = search(query), summary = box)
                }
            val viewModel = vm(repo)
            viewModel.onQueryChange("everest")
            viewModel.submit()
            advanceUntilIdle()
            val state = viewModel.state.value
            assertTrue(state is SearchUiState.Results)
            assertEquals(box, (state as SearchUiState.Results).summary)
        }

    @Test
    fun setSortMode_reDispatchesWithNewMode() =
        runTest(dispatcher) {
            var lastSort: SortMode? = null
            val repo =
                object : SearchResultsRepository {
                    override suspend fun search(query: String): List<SearchResult> =
                        listOf(SearchResult("T", "https://e", "s", "ddg"))

                    override suspend fun searchWithCorrection(
                        query: String,
                        sortMode: SortMode,
                        vertical: org.searchmob.engine.vertical.Vertical,
                    ): SearchOutcome {
                        lastSort = sortMode
                        return SearchOutcome(results = search(query))
                    }
                }
            val viewModel = vm(repo)
            viewModel.onQueryChange("everest")
            viewModel.submit()
            advanceUntilIdle()
            assertEquals(SortMode.FRESH_RELEVANT, lastSort) // default

            viewModel.setSortMode(SortMode.DATE)
            advanceUntilIdle()
            assertEquals(SortMode.DATE, lastSort) // re-dispatched with the new mode
            assertEquals(SortMode.DATE, viewModel.sortMode.value)
        }

    @Test
    fun submit_emptyResults_goesEmpty() =
        runTest(dispatcher) {
            val viewModel = vm({ emptyList() })
            viewModel.onQueryChange("zzz")
            viewModel.submit()
            advanceUntilIdle()
            assertEquals(SearchUiState.Empty, viewModel.state.value)
        }

    @Test
    fun onResultOpened_whenEnabled_learnsFromTheClick() =
        runTest(dispatcher) {
            val store = org.searchmob.ui.prefs.InMemoryPreferencesStore()
            val prefs = org.searchmob.ui.prefs.PreferencesRepository(store)
            prefs.setPersonalizationEnabled(true)
            val personalization = org.searchmob.data.prefs.PersonalizationPreferences(FakeModelStore())
            val results =
                listOf(
                    SearchResult("A", "https://a.com/1", "s", "e"),
                    SearchResult("B", "https://b.com/2", "s", "e"),
                    SearchResult("C", "https://so.com/3", "s", "e"),
                )
            val viewModel =
                SearchViewModel(
                    repository = { results },
                    ioDispatcher = dispatcher,
                    preferences = prefs,
                    personalizationPreferences = personalization,
                )
            viewModel.onQueryChange("python list")
            viewModel.submit()
            advanceUntilIdle()

            // Click the third result; the two above it are skipped.
            viewModel.onResultOpened("https://so.com/3")
            advanceUntilIdle()

            val model = personalization.load()
            assertEquals(1, model.totalClickedQueries)
            assertEquals(model.config.alphaPrior + 1, model.domains["so.com"]!!.alpha, 1e-9)
            assertEquals(model.config.betaPrior + 1, model.domains["a.com"]!!.beta, 1e-9)
        }

    @Test
    fun onResultOpened_whenDisabled_recordsNothing() =
        runTest(dispatcher) {
            val store = org.searchmob.ui.prefs.InMemoryPreferencesStore()
            val prefs = org.searchmob.ui.prefs.PreferencesRepository(store) // personalization off
            val personalization = org.searchmob.data.prefs.PersonalizationPreferences(FakeModelStore())
            val results = listOf(SearchResult("A", "https://a.com/1", "s", "e"))
            val viewModel =
                SearchViewModel(
                    repository = { results },
                    ioDispatcher = dispatcher,
                    preferences = prefs,
                    personalizationPreferences = personalization,
                )
            viewModel.onQueryChange("q")
            viewModel.submit()
            advanceUntilIdle()
            viewModel.onResultOpened("https://a.com/1")
            advanceUntilIdle()
            assertTrue(personalization.load().isEmpty())
        }

    @Test
    fun submit_emptyResultsWithCorrection_keepsTheDidYouMean() =
        runTest(dispatcher) {
            val repo =
                object : SearchResultsRepository {
                    override suspend fun search(query: String): List<SearchResult> = emptyList()

                    override suspend fun searchWithCorrection(
                        query: String,
                        sortMode: SortMode,
                        vertical: org.searchmob.engine.vertical.Vertical,
                    ): SearchOutcome = SearchOutcome(results = emptyList(), didYouMean = "kotlin")
                }
            val viewModel = vm(repo)
            viewModel.onQueryChange("kotln")
            viewModel.submit()
            advanceUntilIdle()
            // No results, but the correction is surfaced via a Results state (not dropped to Empty).
            val state = viewModel.state.value
            assertTrue(state is SearchUiState.Results)
            assertEquals("kotlin", (state as SearchUiState.Results).didYouMean)
            assertTrue(state.results.isEmpty())
        }

    @Test
    fun submit_failure_goesError() =
        runTest(dispatcher) {
            val viewModel = vm({ throw RuntimeException("boom") })
            viewModel.onQueryChange("kotlin")
            viewModel.submit()
            advanceUntilIdle()
            assertTrue(viewModel.state.value is SearchUiState.Error)
        }

    @Test
    fun submit_whitespaceOnly_isIgnored() =
        runTest(dispatcher) {
            val viewModel = vm({ error("should not be called") })
            viewModel.onQueryChange("   ")
            viewModel.submit()
            advanceUntilIdle()
            assertEquals(SearchUiState.Idle, viewModel.state.value)
        }

    @Test
    fun retry_reDispatchesLastQuery() =
        runTest(dispatcher) {
            var calls = 0
            val viewModel =
                vm({
                    calls++
                    emptyList()
                })
            viewModel.onQueryChange("kotlin")
            viewModel.submit()
            advanceUntilIdle()
            viewModel.retry()
            assertEquals(SearchUiState.Loading, viewModel.state.value)
            advanceUntilIdle()
            assertEquals(2, calls)
        }

    @Test
    fun record_calledOnlyOnNonEmptySuccess() =
        runTest(dispatcher) {
            val recorded = mutableListOf<String>()
            val viewModel =
                vm(
                    repo = { listOf(SearchResult("T", "https://e.com")) },
                    onRecord = { recorded.add(it) },
                )
            viewModel.onQueryChange("kotlin")
            viewModel.submit()
            advanceUntilIdle()
            assertEquals(listOf("kotlin"), recorded)
        }

    @Test
    fun record_notCalledOnEmptyResults() =
        runTest(dispatcher) {
            val recorded = mutableListOf<String>()
            val viewModel = vm(repo = { emptyList() }, onRecord = { recorded.add(it) })
            viewModel.onQueryChange("kotlin")
            viewModel.submit()
            advanceUntilIdle()
            assertTrue(recorded.isEmpty())
        }
}
