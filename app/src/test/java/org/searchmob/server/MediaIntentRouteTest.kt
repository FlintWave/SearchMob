package org.searchmob.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.searchmob.engine.MediaCategory
import org.searchmob.engine.MediaIntent
import org.searchmob.engine.sort.SortMode
import org.searchmob.engine.vertical.Vertical
import java.net.HttpURLConnection
import java.net.URL

/** Served media actions row: the "Watch/Listen/Read/Play on" row renders when the outcome carries it. */
class MediaIntentRouteTest {
    private class FilmProvider(private val withRow: Boolean) : SearchResultProvider {
        override suspend fun search(query: String): List<SearchResult> =
            listOf(SearchResult(title = "A page", url = "https://a.example/x", snippet = "s", engine = "e"))

        override suspend fun searchWithCorrection(
            query: String,
            sortMode: SortMode,
            vertical: Vertical,
            personalize: Boolean,
            activeLensOverride: String?,
        ): SearchOutcome =
            SearchOutcome(
                results = search(query),
                actionsRow =
                    if (withRow) {
                        MediaIntent.buildActionsRow(
                            MediaCategory.FILM_TV,
                            "Inception",
                            "https://en.wikipedia.org/wiki/Inception",
                        )
                    } else {
                        null
                    },
            )
    }

    @Test
    fun actionsRowRendersForAMediaEntity() {
        val server = SearchServer(provider = FilmProvider(withRow = true))
        val port = server.start(freeLoopbackPort())
        try {
            assertEquals(200, waitForHealthz(port))
            val (code, html) = get(port, "/search?q=inception")
            assertEquals(200, code)
            assertTrue(html.contains("class=\"actions-row\""))
            assertTrue(html.contains("Watch on"))
            assertTrue(html.contains("imdb.com")) // a film platform deep link
            assertTrue(html.contains("en.wikipedia.org/wiki/Inception")) // Wikipedia leads
        } finally {
            server.stop()
        }
    }

    @Test
    fun noActionsRowWhenOutcomeHasNone() {
        val server = SearchServer(provider = FilmProvider(withRow = false))
        val port = server.start(freeLoopbackPort())
        try {
            assertEquals(200, waitForHealthz(port))
            val (_, html) = get(port, "/search?q=inception")
            assertFalse(html.contains("class=\"actions-row\""))
        } finally {
            server.stop()
        }
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
}
