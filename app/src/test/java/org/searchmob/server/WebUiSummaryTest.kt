package org.searchmob.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.searchmob.engine.summary.WikiSummary
import java.net.HttpURLConnection
import java.net.URL

/** The contextual Wikipedia summary box on the served results page. */
class WebUiSummaryTest {
    private class SummaryProvider(private val summary: WikiSummary?) : SearchResultProvider {
        override suspend fun search(query: String): List<SearchResult> =
            listOf(SearchResult(title = "A page", url = "https://news.example/x", snippet = "s", engine = "e"))

        override suspend fun searchWithCorrection(
            query: String,
            sortMode: org.searchmob.engine.sort.SortMode,
            vertical: org.searchmob.engine.vertical.Vertical,
            personalize: Boolean,
        ): SearchOutcome = SearchOutcome(results = search(query), summary = summary)
    }

    private val box =
        WikiSummary(
            title = "Mount Everest",
            description = "Earth's highest mountain",
            extract = "Mount Everest is Earth's highest mountain above sea level.",
            url = "https://en.wikipedia.org/wiki/Mount_Everest",
        )

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

    private fun html(
        port: Int,
        query: String,
    ): String {
        val c = URL("http://$LOOPBACK_HOST:$port/search?q=$query").openConnection() as HttpURLConnection
        val body = c.inputStream.bufferedReader().readText()
        c.disconnect()
        return body
    }

    @Test
    fun summaryBoxRenderedWhenPresent() {
        val server = SearchServer(provider = SummaryProvider(box))
        val port = server.start(freeLoopbackPort())
        try {
            assertEquals(200, waitForHealthz(port))
            val page = html(port, "everest")
            assertTrue(page.contains("class=\"summary\""))
            assertTrue(page.contains("Mount Everest"))
            assertTrue(page.contains("highest mountain"))
            assertTrue(page.contains("From Wikipedia"))
            assertTrue(page.contains("en.wikipedia.org/wiki/Mount_Everest"))
        } finally {
            server.stop()
        }
    }

    @Test
    fun noSummaryBoxWhenAbsent() {
        val server = SearchServer(provider = SummaryProvider(null))
        val port = server.start(freeLoopbackPort())
        try {
            assertEquals(200, waitForHealthz(port))
            assertFalse(html(port, "everest").contains("class=\"summary\""))
        } finally {
            server.stop()
        }
    }
}
