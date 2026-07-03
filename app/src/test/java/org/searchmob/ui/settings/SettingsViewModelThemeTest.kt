package org.searchmob.ui.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.searchmob.data.history.InMemoryHistoryStore
import org.searchmob.ui.prefs.InMemoryPreferencesStore
import org.searchmob.ui.prefs.PreferencesRepository

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelThemeTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var preferences: PreferencesRepository
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        preferences = PreferencesRepository(InMemoryPreferencesStore())
        viewModel =
            SettingsViewModel(
                preferences = preferences,
                historyStore = InMemoryHistoryStore(),
                engineCatalog = emptyList(),
                engineEnabledSink = MutableStateFlow(emptyMap()),
                apiKeysSink = MutableStateFlow(emptyMap()),
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `picking a light theme switches Material You off so the pick takes effect`() =
        runTest(dispatcher) {
            assertTrue(preferences.preferences.first().dynamicColor) // default on

            viewModel.setLightTheme("github-light")
            advanceUntilIdle()

            val prefs = preferences.preferences.first()
            assertEquals("github-light", prefs.lightThemeId)
            assertFalse(prefs.dynamicColor)
        }

    @Test
    fun `picking a dark theme switches Material You off so the pick takes effect`() =
        runTest(dispatcher) {
            viewModel.setDarkTheme("dracula")
            advanceUntilIdle()

            val prefs = preferences.preferences.first()
            assertEquals("dracula", prefs.darkThemeId)
            assertFalse(prefs.dynamicColor)
        }

    @Test
    fun `re-enabling Material You after a pick is respected`() =
        runTest(dispatcher) {
            viewModel.setDarkTheme("dracula")
            advanceUntilIdle()
            viewModel.setDynamicColor(true)
            advanceUntilIdle()

            val prefs = preferences.preferences.first()
            assertTrue(prefs.dynamicColor)
            assertEquals("dracula", prefs.darkThemeId) // the slot choice is kept
        }
}
