package org.searchmob

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test
import org.searchmob.server.SearchResult
import org.searchmob.ui.search.SearchResultsRepository
import org.searchmob.ui.search.SearchScreen
import org.searchmob.ui.search.SearchTestTags
import org.searchmob.ui.search.SearchViewModel

/** Compose UI tests for the search surface states and result rendering. */
class SearchScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setContent(repo: SearchResultsRepository): SearchViewModel {
        val viewModel = SearchViewModel(repository = repo)
        composeTestRule.setContent {
            SearchScreen(viewModel = viewModel, onOpenSettings = {})
        }
        return viewModel
    }

    @Test
    fun results_renderTitleSnippetAndAttribution() {
        val results =
            listOf(
                SearchResult(
                    title = "Kotlin Programming",
                    url = "https://kotlinlang.org",
                    snippet = "A modern programming language",
                    engine = "duckduckgo,wikipedia",
                ),
            )
        setContent { results }
        composeTestRule.onNodeWithTag(SearchTestTags.QUERY_FIELD).performTextInput("kotlin")
        composeTestRule.onNodeWithTag(SearchTestTags.SUBMIT).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Kotlin Programming").assertIsDisplayed()
        composeTestRule.onNodeWithText("A modern programming language").assertIsDisplayed()
        composeTestRule.onNodeWithText("via duckduckgo,wikipedia").assertIsDisplayed()
    }

    @Test
    fun loadingState_isShownWhileInFlight() {
        val gate = CompletableDeferred<List<SearchResult>>()
        setContent { gate.await() }
        composeTestRule.onNodeWithTag(SearchTestTags.QUERY_FIELD).performTextInput("kotlin")
        composeTestRule.onNodeWithTag(SearchTestTags.SUBMIT).performClick()
        composeTestRule.onNodeWithTag(SearchTestTags.LOADING).assertIsDisplayed()
        gate.complete(emptyList())
    }

    @Test
    fun emptyState_isShownForNoResults() {
        setContent { emptyList() }
        composeTestRule.onNodeWithTag(SearchTestTags.QUERY_FIELD).performTextInput("zzz")
        composeTestRule.onNodeWithTag(SearchTestTags.SUBMIT).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(SearchTestTags.EMPTY).assertIsDisplayed()
    }

    @Test
    fun errorState_isShownAndRetryReturnsToLoading() {
        var attempts = 0
        val gate = CompletableDeferred<List<SearchResult>>()
        setContent {
            attempts++
            if (attempts == 1) throw RuntimeException("network down") else gate.await()
        }
        composeTestRule.onNodeWithTag(SearchTestTags.QUERY_FIELD).performTextInput("kotlin")
        composeTestRule.onNodeWithTag(SearchTestTags.SUBMIT).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(SearchTestTags.ERROR).assertIsDisplayed()

        composeTestRule.onNodeWithTag(SearchTestTags.RETRY).performClick()
        composeTestRule.onNodeWithTag(SearchTestTags.LOADING).assertIsDisplayed()
        gate.complete(emptyList())
    }
}
