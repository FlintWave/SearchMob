package org.searchmob

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.searchmob.ui.onboarding.OnboardingStep
import org.searchmob.ui.onboarding.OnboardingTestTags
import org.searchmob.ui.onboarding.OnboardingWizard

/** Compose UI tests for the first-run wizard: paging, skip/finish, embedded guide. */
class OnboardingWizardTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun wizard(
        port: Int? = 8080,
        onComplete: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            OnboardingWizard(
                port = port,
                onComplete = onComplete,
                onOpenUrl = {},
                onStartService = {},
                onOpenPrivacySettings = {},
            )
        }
    }

    @Test
    fun startsOnWelcome() {
        wizard()
        composeTestRule.onNodeWithText("Welcome to SearchMob").assertIsDisplayed()
    }

    @Test
    fun skip_completesFromFirstPage() {
        var completed = false
        wizard(onComplete = { completed = true })
        composeTestRule.onNodeWithTag(OnboardingTestTags.SKIP).performClick()
        composeTestRule.waitForIdle()
        assertTrue(completed)
    }

    @Test
    fun next_advancesToPermissions() {
        wizard()
        composeTestRule.onNodeWithTag(OnboardingTestTags.NEXT).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Permissions").assertIsDisplayed()
    }

    @Test
    fun finish_completesOnLastPage() {
        var completed = false
        wizard(onComplete = { completed = true })
        // Page through every step to the last one (PERSONALIZE at the time of writing); deriving the
        // click count from the step list keeps this test honest when the wizard gains a page.
        repeat(OnboardingStep.entries.lastIndex) {
            composeTestRule.onNodeWithTag(OnboardingTestTags.NEXT).performClick()
            composeTestRule.waitForIdle()
        }
        composeTestRule.onNodeWithTag(OnboardingTestTags.FINISH).performClick()
        composeTestRule.waitForIdle()
        assertEquals(true, completed)
    }

    @Test
    fun back_returnsToPreviousPage() {
        wizard()
        composeTestRule.onNodeWithTag(OnboardingTestTags.NEXT).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(OnboardingTestTags.BACK).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Welcome to SearchMob").assertIsDisplayed()
    }
}
