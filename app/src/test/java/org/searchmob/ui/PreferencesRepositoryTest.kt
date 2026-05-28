package org.searchmob.ui

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.searchmob.engine.EngineConfig
import org.searchmob.engine.EngineRegistry
import org.searchmob.engine.adapters.DuckDuckGoAdapter
import org.searchmob.engine.adapters.MojeekAdapter
import org.searchmob.engine.http.HttpClientFactory
import org.searchmob.ui.prefs.InMemoryPreferencesStore
import org.searchmob.ui.prefs.PreferencesRepository
import org.searchmob.ui.theme.ThemeMode

@OptIn(ExperimentalCoroutinesApi::class)
class PreferencesRepositoryTest {
    private fun repo(): PreferencesRepository =
        PreferencesRepository(
            InMemoryPreferencesStore(),
            knownEngineIds = listOf("duckduckgo", "mojeek"),
        )

    @Test
    fun defaults_matchLockedFirstRunValues() =
        runTest {
            val prefs = repo().preferences.first()
            assertEquals(ThemeMode.SYSTEM, prefs.themeMode)
            assertTrue(prefs.dynamicColor)
            assertFalse(prefs.historyEnabled)
            assertFalse(prefs.networkAccessEnabled)
            // All known engines default to enabled.
            assertTrue(prefs.isEngineEnabled("duckduckgo"))
            assertTrue(prefs.isEngineEnabled("mojeek"))
        }

    @Test
    fun themeMode_roundTrips() =
        runTest {
            val r = repo()
            r.setThemeMode(ThemeMode.DARK)
            assertEquals(ThemeMode.DARK, r.preferences.first().themeMode)
        }

    @Test
    fun history_roundTrips() =
        runTest {
            val r = repo()
            r.setHistoryEnabled(true)
            assertTrue(r.preferences.first().historyEnabled)
        }

    @Test
    fun networkAccess_defaultsOffAndRoundTrips() =
        runTest {
            val r = repo()
            assertFalse(r.preferences.first().networkAccessEnabled)
            assertFalse(r.networkAccessEnabled.first())
            r.setNetworkAccessEnabled(true)
            assertTrue(r.preferences.first().networkAccessEnabled)
            assertTrue(r.networkAccessEnabled.first())
            r.setNetworkAccessEnabled(false)
            assertFalse(r.networkAccessEnabled.first())
        }

    @Test
    fun engineToggle_roundTrips() =
        runTest {
            val r = repo()
            r.setEngineEnabled("mojeek", enabled = false, current = emptyMap())
            val prefs = r.preferences.first()
            assertFalse(prefs.isEngineEnabled("mojeek"))
            assertTrue(prefs.isEngineEnabled("duckduckgo"))
        }

    @Test
    fun disabledEngine_excludedFromFanOut() {
        val adapters = listOf(DuckDuckGoAdapter(), MojeekAdapter())
        val configs =
            mapOf(
                "duckduckgo" to EngineConfig("duckduckgo", enabled = true),
                "mojeek" to EngineConfig("mojeek", enabled = false),
            )
        val registry = EngineRegistry(adapters = adapters, configs = configs)
        val activeIds = registry.activeEngines(HttpClientFactory.create()).map { it.first.id }
        assertEquals(listOf("duckduckgo"), activeIds)
    }
}
