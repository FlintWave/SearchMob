package org.searchmob.server

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.searchmob.data.prefs.Preferences
import org.searchmob.data.prefs.PreferencesStore
import org.searchmob.data.prefs.RankingPreferences
import org.searchmob.ui.prefs.InMemoryPreferencesStore
import org.searchmob.ui.prefs.PreferencesRepository
import org.searchmob.ui.theme.APP_THEMES
import org.searchmob.ui.theme.DEFAULT_DARK_ID
import org.searchmob.ui.theme.DEFAULT_LIGHT_ID
import org.searchmob.ui.theme.ThemePaletteMode

/**
 * Served-page theming: the generated per-theme CSS variable blocks, the two-slot + font init/toggle
 * JS, and the Appearance settings picker. The served themes are browser-local (localStorage), so
 * these are markup assertions; nothing touches the server's stored state. Mirrors the desktop
 * `tests/server/test_theme_markup.py`.
 */
class ThemeMarkupTest {
    private class FakeRankingStore : PreferencesStore {
        private val map = mutableMapOf<String, String>()

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

    private fun ownerModule(): io.ktor.server.application.Application.() -> Unit =
        {
            searchModule(
                provider = StubSearchResultProvider(),
                rankingPreferences = RankingPreferences(FakeRankingStore()),
                userPreferences = PreferencesRepository(InMemoryPreferencesStore()),
            ) { DEFAULT_PORT }
        }

    @Test
    fun everyTheme_emitsADataThemeBlock() =
        testApplication {
            application { searchModule(StubSearchResultProvider()) { DEFAULT_PORT } }
            val html = client.get("/").bodyAsText()
            APP_THEMES.forEach { theme ->
                assertTrue("${theme.id} block missing", html.contains("[data-theme=\"${theme.id}\"]{"))
            }
            // The two defaults drive :root and the dark media query.
            assertTrue(html.contains(":root{"))
            assertTrue(html.contains("@media (prefers-color-scheme:dark){:root{"))
        }

    @Test
    fun dataThemeBlock_mapsPaletteFields() =
        testApplication {
            application { searchModule(StubSearchResultProvider()) { DEFAULT_PORT } }
            val html = client.get("/").bodyAsText()
            val theme = APP_THEMES.first { it.id == "dracula" }
            val start = html.indexOf("[data-theme=\"dracula\"]{")
            val chunk = html.substring(start, html.indexOf("}", start))
            assertTrue(chunk.contains("--bg:${theme.background};"))
            assertTrue(chunk.contains("--card:${theme.surface};")) // --card maps to surface
            assertTrue(chunk.contains("--url:${theme.accent};"))
            assertTrue(chunk.contains("--topbar:${theme.background}ee;")) // 8-digit hex with alpha
        }

    @Test
    fun rootFontRule_presentAndContentScalesInRem() =
        testApplication {
            application { searchModule(StubSearchResultProvider()) { DEFAULT_PORT } }
            val home = client.get("/").bodyAsText()
            assertTrue(home.contains("html{font-size:12pt}"))
            assertTrue(home.contains(".home .brand{font-size:3rem;"))
            assertTrue(home.contains("font-size:1rem;padding:13px 18px}")) // the search input
            val results = client.get("/search?q=hi").bodyAsText()
            assertTrue(results.contains(".result .title{display:block;font-size:1.25rem;"))
        }

    @Test
    fun initJs_referencesTheTwoSlotsAndFont() =
        testApplication {
            application { searchModule(StubSearchResultProvider()) { DEFAULT_PORT } }
            val html = client.get("/").bodyAsText()
            assertTrue(html.contains("sm-theme"))
            assertTrue(html.contains("sm-light-theme"))
            assertTrue(html.contains("sm-dark-theme"))
            assertTrue(html.contains("sm-font"))
            // Defaults mirror the two-slot defaults.
            assertTrue(html.contains(DEFAULT_LIGHT_ID))
            assertTrue(html.contains(DEFAULT_DARK_ID))
        }

    @Test
    fun resultsPage_alsoCarriesTheThemeBlocks() =
        testApplication {
            application { searchModule(StubSearchResultProvider()) { DEFAULT_PORT } }
            val html = client.get("/search?q=hi").bodyAsText()
            APP_THEMES.forEach { theme ->
                assertTrue("${theme.id} block missing", html.contains("[data-theme=\"${theme.id}\"]{"))
            }
        }

    @Test
    fun settingsPage_hasTheAppearancePicker() =
        testApplication {
            application(ownerModule())
            val html = client.get("/settings").bodyAsText()
            assertTrue(html.contains("Appearance"))
            assertTrue(html.contains("id=\"sm-mode\""))
            assertTrue(html.contains("id=\"sm-light-theme\""))
            assertTrue(html.contains("id=\"sm-dark-theme\""))
            assertTrue(html.contains("Follow system"))
        }

    @Test
    fun settingsLightAndDarkSelects_listTheirThemes() =
        testApplication {
            application(ownerModule())
            val html = client.get("/settings").bodyAsText()
            val lightStart = html.indexOf("id=\"sm-light-theme\"")
            val lightChunk = html.substring(lightStart, html.indexOf("</select>", lightStart))
            val darkStart = html.indexOf("id=\"sm-dark-theme\"")
            val darkChunk = html.substring(darkStart, html.indexOf("</select>", darkStart))
            APP_THEMES.forEach { theme ->
                if (theme.mode == ThemePaletteMode.LIGHT) {
                    assertTrue("${theme.id} in light", lightChunk.contains("value=\"${theme.id}\""))
                    assertFalse("${theme.id} not in dark", darkChunk.contains("value=\"${theme.id}\""))
                } else {
                    assertTrue("${theme.id} in dark", darkChunk.contains("value=\"${theme.id}\""))
                    assertFalse("${theme.id} not in light", lightChunk.contains("value=\"${theme.id}\""))
                }
            }
        }

    @Test
    fun settingsPage_hasTheTextSizeStepper() =
        testApplication {
            application(ownerModule())
            val html = client.get("/settings").bodyAsText()
            assertTrue(html.contains("id=\"sm-font-dec\""))
            assertTrue(html.contains("id=\"sm-font-inc\""))
            assertTrue(html.contains("id=\"sm-font-val\""))
            assertTrue(html.contains(">A-<"))
            assertTrue(html.contains(">A+<"))
        }

    @Test
    fun controlsJs_emittedOnlyOnSettings() =
        testApplication {
            application(ownerModule())
            val settings = client.get("/settings").bodyAsText()
            val home = client.get("/").bodyAsText()
            assertTrue(settings.contains("sm-font-inc"))
            assertFalse(home.contains("sm-font-inc"))
        }
}
