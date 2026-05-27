package org.searchmob

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.searchmob.ui.setup.BrowserSetupBody
import org.searchmob.ui.setup.BrowserSetupScreenContent
import org.searchmob.ui.setup.SetupTestTags

/** Compose UI tests for the browser-setup guide: URL display, copy payload, not-running state. */
class BrowserSetupScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun running_showsLiveLoopbackUrls() {
        composeTestRule.setContent {
            BrowserSetupScreenContent(port = 8080, onBack = {}, onOpenUrl = {}, onStartService = {})
        }
        composeTestRule.onNodeWithText("http://127.0.0.1:8080/").assertIsDisplayed()
        composeTestRule.onNodeWithText("http://127.0.0.1:8080/search?q=%s").assertIsDisplayed()
    }

    @Test
    fun copyTemplate_putsExactTemplateOnClipboard() {
        var copied: String? = null
        composeTestRule.setContent {
            // Drive the copy callback directly via the body to assert the exact payload.
            Column {
                BrowserSetupBody(
                    port = 8080,
                    onOpenUrl = {},
                    onStartService = {},
                    onCopy = { copied = it },
                )
            }
        }
        composeTestRule.onNodeWithTag(SetupTestTags.TEMPLATE_COPY).performClick()
        composeTestRule.waitForIdle()
        assertEquals("http://127.0.0.1:8080/search?q=%s", copied)
    }

    @Test
    fun copyVisit_putsExactVisitUrlOnClipboard() {
        var copied: String? = null
        composeTestRule.setContent {
            Column {
                BrowserSetupBody(
                    port = 8080,
                    onOpenUrl = {},
                    onStartService = {},
                    onCopy = { copied = it },
                )
            }
        }
        composeTestRule.onNodeWithTag(SetupTestTags.VISIT_COPY).performClick()
        composeTestRule.waitForIdle()
        assertEquals("http://127.0.0.1:8080/", copied)
    }

    @Test
    fun openBrowser_usesVisitUrl() {
        var opened: String? = null
        composeTestRule.setContent {
            BrowserSetupScreenContent(port = 4321, onBack = {}, onOpenUrl = { opened = it }, onStartService = {})
        }
        composeTestRule.onNodeWithTag(SetupTestTags.OPEN_BROWSER).performClick()
        composeTestRule.waitForIdle()
        assertEquals("http://127.0.0.1:4321/", opened)
    }

    @Test
    fun notRunning_showsStartPromptAndCanStart() {
        var started = false
        composeTestRule.setContent {
            BrowserSetupScreenContent(port = null, onBack = {}, onOpenUrl = {}, onStartService = { started = true })
        }
        composeTestRule.onNodeWithTag(SetupTestTags.NOT_RUNNING).assertIsDisplayed()
        composeTestRule.onNodeWithText("Start service").performClick()
        composeTestRule.waitForIdle()
        assertEquals(true, started)
    }
}
