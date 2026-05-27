package org.searchmob.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.searchmob.data.prefs.EngineConfigPreferences
import org.searchmob.data.prefs.Preferences
import org.searchmob.data.prefs.PreferencesStore

/** In-memory [PreferencesStore] standing in for the encrypted DataStore in JVM tests. */
private class FakePreferencesStore(initial: Preferences = emptyMap()) : PreferencesStore {
    private val map = initial.toMutableMap()

    override fun observe(): Flow<Preferences> = flowOf(map.toMap())

    override suspend fun getAll(): Preferences = map.toMap()

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

class EngineConfigPreferencesTest {
    @Test
    fun defaultsFreeEnginesOnAndKeyedEnginesOffWhenNoPrefs() =
        runTest {
            val sut = EngineConfigPreferences(FakePreferencesStore())
            val configs =
                sut.configs(
                    engineIds = listOf("duckduckgo", "brave"),
                    keyRequired = { it == "brave" },
                )
            assertTrue(configs.getValue("duckduckgo").enabled)
            assertFalse(configs.getValue("brave").enabled)
            assertNull(configs.getValue("brave").apiKey)
        }

    @Test
    fun storedApiKeyAndEnabledFlagRoundTrip() =
        runTest {
            val store = FakePreferencesStore()
            val sut = EngineConfigPreferences(store)
            sut.setApiKey("brave", "secret-byok")
            sut.setEnabled("brave", true)

            val configs = sut.configs(listOf("brave"), keyRequired = { true })
            assertEquals("secret-byok", configs.getValue("brave").apiKey)
            assertTrue(configs.getValue("brave").enabled)
            assertEquals("secret-byok", sut.apiKey("brave"))
        }

    @Test
    fun blankApiKeyClearsAndIsTreatedAsAbsent() =
        runTest {
            val sut = EngineConfigPreferences(FakePreferencesStore())
            sut.setApiKey("mojeek", "k")
            sut.setApiKey("mojeek", "  ")
            assertNull(sut.apiKey("mojeek"))
        }
}
