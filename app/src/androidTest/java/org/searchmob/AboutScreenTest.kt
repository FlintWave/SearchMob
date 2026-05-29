package org.searchmob

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.searchmob.ui.about.AboutScreenContent
import org.searchmob.ui.about.AboutTestTags
import org.searchmob.ui.theme.SearchMobTheme

/** Compose UI tests for the About / Privacy screen: version display, repo-open intent, copy accuracy. */
class AboutScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun showsDynamicVersionInFooter() {
        composeTestRule.setContent {
            SearchMobTheme {
                AboutScreenContent(version = "4.2.0", onBack = {}, onOpenRepo = {})
            }
        }
        composeTestRule.onNodeWithTag(AboutTestTags.VERSION).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Version 4.2.0").assertIsDisplayed()
    }

    @Test
    fun repoButton_invokesOpenRepoCallback() {
        var opened = false
        composeTestRule.setContent {
            SearchMobTheme {
                AboutScreenContent(version = "1.0.0", onBack = {}, onOpenRepo = { opened = true })
            }
        }
        composeTestRule.onNodeWithTag(AboutTestTags.REPO_BUTTON).performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertTrue(opened)
    }

    @Test
    fun backButton_invokesBackCallback() {
        var backCount = 0
        composeTestRule.setContent {
            SearchMobTheme {
                AboutScreenContent(version = "1.0.0", onBack = { backCount++ }, onOpenRepo = {})
            }
        }
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, backCount)
    }
}
