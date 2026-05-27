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
import org.searchmob.server.SearchResult
import org.searchmob.ui.search.SearchResultsRepository
import org.searchmob.ui.search.SearchUiState
import org.searchmob.ui.search.SearchViewModel

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val dispatcher = StandardTestDispatcher()

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
    fun submit_emptyResults_goesEmpty() =
        runTest(dispatcher) {
            val viewModel = vm({ emptyList() })
            viewModel.onQueryChange("zzz")
            viewModel.submit()
            advanceUntilIdle()
            assertEquals(SearchUiState.Empty, viewModel.state.value)
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
