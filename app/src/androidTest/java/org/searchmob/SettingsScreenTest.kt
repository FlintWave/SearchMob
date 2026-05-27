package org.searchmob

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.searchmob.data.history.InMemoryHistoryStore
import org.searchmob.ui.AppDependencies
import org.searchmob.ui.prefs.InMemoryPreferencesStore
import org.searchmob.ui.prefs.PreferencesStore
import org.searchmob.ui.settings.SettingsScreen
import org.searchmob.ui.settings.SettingsTestTags
import org.searchmob.ui.settings.SettingsViewModel
import org.searchmob.ui.theme.SearchMobTheme
import org.searchmob.ui.theme.ThemeMode

/** Compose UI tests for the settings surface: toggle persistence, theme override, store-nothing. */
class SettingsScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun deps(store: PreferencesStore = InMemoryPreferencesStore()) = AppDependencies(preferencesStore = store)

    private fun setSettings(deps: AppDependencies): SettingsViewModel {
        val viewModel =
            SettingsViewModel(
                preferences = deps.preferencesRepository,
                historyStore = deps.historyStore,
                engineCatalog = deps.engineCatalog,
                engineEnabledSink = deps.engineEnabled,
                apiKeysSink = deps.apiKeys,
            )
        composeTestRule.setContent {
            SettingsScreen(viewModel = viewModel, onBack = {})
        }
        return viewModel
    }

    @Test
    fun historyToggle_persistsAndReappliesAcrossRestart() {
        val store = InMemoryPreferencesStore()
        // First "process": flip history on via the screen.
        setSettings(deps(store))
        composeTestRule.onNodeWithTag(SettingsTestTags.HISTORY_SWITCH).assertIsOff()
        composeTestRule.onNodeWithTag(SettingsTestTags.HISTORY_SWITCH).performClick()
        composeTestRule.waitForIdle()

        // Simulate restart by rebuilding the repository over the SAME store.
        val restored = runBlocking { deps(store).preferencesRepository.preferences.first() }
        assertTrue(restored.historyEnabled)
    }

    @Test
    fun engineToggle_persistsToStore() {
        val store = InMemoryPreferencesStore()
        val deps = deps(store)
        setSettings(deps)
        val first = deps.engineCatalog.first { !it.requiresApiKey }
        composeTestRule.onNodeWithTag(SettingsTestTags.engineSwitch(first.id)).performClick()
        composeTestRule.waitForIdle()
        val restored = runBlocking { deps.preferencesRepository.preferences.first() }
        assertEquals(false, restored.isEngineEnabled(first.id))
    }

    @Test
    fun historyOff_storeNothing_recordingIsNoOp() {
        val history = InMemoryHistoryStore()
        val deps = AppDependencies(historyStore = history)
        val viewModel = setSettings(deps)
        // History defaults off; recording a query must persist nothing.
        viewModel.recordQuery("secret query")
        assertTrue(history.list(System.currentTimeMillis()).isEmpty())
    }

    @Test
    fun themeOverride_lightBeatsDarkSystem() {
        var surface = Color.Unspecified
        composeTestRule.setContent {
            SearchMobTheme(themeMode = ThemeMode.LIGHT, dynamicColor = false) {
                surface = MaterialTheme.colorScheme.background
            }
        }
        composeTestRule.waitForIdle()
        // Built-in light background is the near-white surface.
        assertEquals(Color(0xFFFDFBFF), surface)
    }

    @Test
    fun themeOverride_darkBeatsLightSystem() {
        var background = Color.Unspecified
        composeTestRule.setContent {
            SearchMobTheme(themeMode = ThemeMode.DARK, dynamicColor = false) {
                background = MaterialTheme.colorScheme.background
            }
        }
        composeTestRule.waitForIdle()
        assertEquals(Color(0xFF1B1B1F), background)
    }

    @Test
    fun appliedImmediately_themeFlowEmitsOnChange() {
        val deps = deps()
        val viewModel = setSettings(deps)
        viewModel.setThemeMode(ThemeMode.DARK)
        composeTestRule.waitForIdle()
        val mode = runBlocking { deps.preferencesRepository.preferences.first().themeMode }
        assertEquals(ThemeMode.DARK, mode)
    }
}
