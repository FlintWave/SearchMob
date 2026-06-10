package org.searchmob.server

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.searchmob.data.history.HistoryEntry
import org.searchmob.data.history.InMemoryHistoryStore
import org.searchmob.data.prefs.PersonalizationPreferences
import org.searchmob.data.prefs.Preferences
import org.searchmob.data.prefs.PreferencesStore
import org.searchmob.data.prefs.RankingPreferences
import org.searchmob.ui.prefs.InMemoryPreferencesStore
import org.searchmob.ui.prefs.PreferencesRepository
import java.net.HttpURLConnection
import java.net.URL

/**
 * The served Settings page and its loopback-only preference / personalization routes, against a real
 * loopback server (requests genuinely originate from 127.0.0.1, i.e. the owner). Mirrors the desktop
 * settings-route tests.
 */
class WebUiSettingsTest {
    private class FakeStore : PreferencesStore {
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

    private class OneResultProvider : SearchResultProvider {
        override suspend fun search(query: String): List<SearchResult> =
            listOf(SearchResult(title = "A page", url = "https://news.example/x", snippet = "s", engine = "e"))
    }

    private fun waitForHealthz(
        port: Int,
        attempts: Int = 30,
    ): Int {
        repeat(attempts) {
            try {
                val c = URL("http://$LOOPBACK_HOST:$port/healthz").openConnection() as HttpURLConnection
                c.connectTimeout = 500
                c.readTimeout = 500
                val code = c.responseCode
                c.disconnect()
                return code
            } catch (_: Exception) {
                Thread.sleep(100)
            }
        }
        throw AssertionError("server did not respond on $port")
    }

    private fun get(
        port: Int,
        path: String,
    ): Pair<Int, String> {
        val c = URL("http://$LOOPBACK_HOST:$port$path").openConnection() as HttpURLConnection
        c.instanceFollowRedirects = false
        val code = c.responseCode
        val body = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.readText().orEmpty()
        c.disconnect()
        return code to body
    }

    private fun postForm(
        port: Int,
        path: String,
        form: String,
    ): Int {
        val c = URL("http://$LOOPBACK_HOST:$port$path").openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.instanceFollowRedirects = false
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        c.outputStream.use { it.write(form.toByteArray()) }
        val code = c.responseCode
        c.disconnect()
        return code
    }

    private fun fullServer(
        ranking: RankingPreferences,
        prefs: PreferencesRepository,
        history: InMemoryHistoryStore = InMemoryHistoryStore(),
        personalization: PersonalizationPreferences = PersonalizationPreferences(FakeStore()),
    ) = SearchServer(
        provider = OneResultProvider(),
        rankingPreferences = ranking,
        userPreferences = prefs,
        historyStore = history,
        personalizationPreferences = personalization,
    )

    @Test
    fun settingsPageRendersAllSectionsForOwner() {
        val ranking = RankingPreferences(FakeStore())
        val prefs = PreferencesRepository(InMemoryPreferencesStore())
        val server = fullServer(ranking, prefs)
        val port = server.start(freeLoopbackPort())
        try {
            assertEquals(200, waitForHealthz(port))
            val (code, html) = get(port, "/settings")
            assertEquals(200, code)
            assertTrue(html.contains("Settings"))
            assertTrue(html.contains("Default sort"))
            assertTrue(html.contains("Domain rules"))
            assertTrue(html.contains("Scopes"))
            assertTrue(html.contains("Goggles"))
            assertTrue(html.contains("Search history"))
            // Parity controls with the in-app Settings (added so the browser page is not behind).
            assertTrue(html.contains("""name="media_actions_enabled""""))
            assertTrue(html.contains("""name="history_enabled""""))
            assertTrue(html.contains("""name="update_check_enabled""""))
            assertTrue(html.contains("""name="language""""))
            assertTrue(html.contains("""name="personalization_enabled""""))
            assertTrue(html.contains("Result personalization"))
            assertTrue(html.contains("/settings/personalization/export"))
        } finally {
            server.stop()
        }
    }

    @Test
    fun postPrefsUpdatesPreferences() {
        val ranking = RankingPreferences(FakeStore())
        val prefs = PreferencesRepository(InMemoryPreferencesStore())
        val server = fullServer(ranking, prefs)
        val port = server.start(freeLoopbackPort())
        try {
            assertEquals(200, waitForHealthz(port))
            val code =
                postForm(
                    port,
                    "/settings/prefs",
                    "sort_mode=date&ai_slop_mode=hide&summary_enabled=on" +
                        "&media_actions_enabled=on&history_enabled=on&update_check_enabled=on" +
                        "&personalization_enabled=on&language=es",
                )
            assertTrue("expected redirect, got $code", code in 300..399)
            runBlocking {
                assertEquals("date", prefs.sortMode.first())
                assertEquals("hide", prefs.aiSlopMode())
                assertTrue(prefs.summaryEnabled())
                // upstream_suggestions_enabled omitted -> unchecked.
                assertFalse(prefs.upstreamSuggestionsEnabled.first())
                // New parity controls persist too.
                assertTrue(prefs.mediaActionsEnabled())
                assertTrue(prefs.preferences.first().historyEnabled)
                assertTrue(prefs.updateCheckEnabled())
                assertTrue(prefs.personalizationEnabled())
                assertEquals("es", prefs.language())
            }
        } finally {
            server.stop()
        }
    }

    @Test
    fun postLensCreatesAndDeletes() {
        val ranking = RankingPreferences(FakeStore())
        val prefs = PreferencesRepository(InMemoryPreferencesStore())
        val server = fullServer(ranking, prefs)
        val port = server.start(freeLoopbackPort())
        try {
            assertEquals(200, waitForHealthz(port))
            postForm(port, "/settings/lens", "name=Research&include_domains=Arxiv.org,%20.edu")
            // Added alongside the seeded sample scopes (a fresh profile has them by default).
            val lens = runBlocking { ranking.load() }.lenses.single { it.name == "Research" }
            assertEquals(listOf("arxiv.org", ".edu"), lens.includeDomains)

            postForm(port, "/settings/lens/delete", "name=Research")
            assertFalse(runBlocking { ranking.load() }.lenses.any { it.name == "Research" })
        } finally {
            server.stop()
        }
    }

    @Test
    fun postGogglesAppendsAndClears() {
        val ranking = RankingPreferences(FakeStore())
        val prefs = PreferencesRepository(InMemoryPreferencesStore())
        val server = fullServer(ranking, prefs)
        val port = server.start(freeLoopbackPort())
        try {
            assertEquals(200, waitForHealthz(port))
            postForm(port, "/settings/goggles", "goggles=%24discard%2Csite%3Dads.example")
            assertTrue(runBlocking { ranking.load() }.goggles.any { it.site == "ads.example" })

            postForm(port, "/settings/goggles/clear", "")
            assertTrue(runBlocking { ranking.load() }.goggles.isEmpty())
        } finally {
            server.stop()
        }
    }

    @Test
    fun postHistoryClearEmptiesHistory() {
        val ranking = RankingPreferences(FakeStore())
        val prefs = PreferencesRepository(InMemoryPreferencesStore())
        val history = InMemoryHistoryStore()
        history.setEnabled(true)
        history.add(HistoryEntry("rust borrow checker", System.currentTimeMillis()))
        val server = fullServer(ranking, prefs, history)
        val port = server.start(freeLoopbackPort())
        try {
            assertEquals(200, waitForHealthz(port))
            assertTrue(history.list(System.currentTimeMillis()).isNotEmpty())
            postForm(port, "/settings/history/clear", "")
            assertTrue(history.list(System.currentTimeMillis()).isEmpty())
        } finally {
            server.stop()
        }
    }

    @Test
    fun personalizationExportImportResetRoutesWork() {
        val ranking = RankingPreferences(FakeStore())
        val prefs = PreferencesRepository(InMemoryPreferencesStore())
        val personalization = PersonalizationPreferences(FakeStore())
        val server = fullServer(ranking, prefs, personalization = personalization)
        val port = server.start(freeLoopbackPort())
        try {
            assertEquals(200, waitForHealthz(port))
            // Export returns the model JSON (an empty model is still valid JSON).
            val (code, body) = get(port, "/settings/personalization/export")
            assertEquals(200, code)
            assertTrue("export should be JSON, was: $body", body.trim().startsWith("{"))
            // Re-importing that JSON is accepted (redirects back to settings).
            val imported =
                postForm(port, "/settings/personalization/import", "model=" + java.net.URLEncoder.encode(body, "UTF-8"))
            assertTrue("import should redirect, got $imported", imported in 300..399)
            // Reset is accepted too.
            assertTrue(postForm(port, "/settings/personalization/reset", "") in 300..399)
        } finally {
            server.stop()
        }
    }

    @Test
    fun settingsUnavailableWithoutStores() {
        // No userPreferences wired -> the page 404s and the prefs route reports unavailable.
        val server = SearchServer(provider = OneResultProvider(), rankingPreferences = RankingPreferences(FakeStore()))
        val port = server.start(freeLoopbackPort())
        try {
            assertEquals(200, waitForHealthz(port))
            assertEquals(404, get(port, "/settings").first)
            assertEquals(503, postForm(port, "/settings/prefs", "sort_mode=date"))
        } finally {
            server.stop()
        }
    }
}
