package org.searchmob

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.searchmob.ui.home.HomeScreen

/** Instrumentation test proving the Compose UI test harness runs and the home screen renders. */
class HomeScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun homeScreen_showsAppName() {
        composeTestRule.setContent {
            HomeScreen(onOpenSearch = {}, onOpenSettings = {})
        }
        composeTestRule.onNodeWithText("SearchMob").assertIsDisplayed()
    }
}
