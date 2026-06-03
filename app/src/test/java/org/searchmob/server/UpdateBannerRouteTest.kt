package org.searchmob.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

/**
 * The owner-only "update available" banner on the served home + results pages, against a real
 * loopback server (requests originate from 127.0.0.1 = the owner). The route gates the banner with
 * `isOwnerRequest`; a network visitor never reaches it (covered by the isLoopbackHost helper tests).
 */
class UpdateBannerRouteTest {
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

    private fun body(
        port: Int,
        path: String,
    ): String {
        val c = URL("http://$LOOPBACK_HOST:$port$path").openConnection() as HttpURLConnection
        c.instanceFollowRedirects = false
        val text = c.inputStream.bufferedReader().readText()
        c.disconnect()
        return text
    }

    private fun server(updateBanner: suspend () -> Pair<String, String>?) =
        SearchServer(provider = OneResultProvider(), updateBanner = updateBanner)

    @Test
    fun ownerSeesBannerOnHomeAndResults() {
        val server = server { "26.07.00" to "https://example.test/r/v26.07.00" }
        val port = server.start(freeLoopbackPort())
        try {
            assertEquals(200, waitForHealthz(port))
            val home = body(port, "/")
            assertTrue(home.contains("class=\"updatebar\""))
            assertTrue(home.contains("https://example.test/r/v26.07.00"))
            assertTrue(home.contains("SearchMob 26.07.00 is available."))

            val results = body(port, "/search?q=hi")
            assertTrue(results.contains("class=\"updatebar\""))
            assertTrue(results.contains("https://example.test/r/v26.07.00"))
        } finally {
            server.stop()
        }
    }

    @Test
    fun noBannerWhenProviderReturnsNull() {
        val server = server { null }
        val port = server.start(freeLoopbackPort())
        try {
            assertEquals(200, waitForHealthz(port))
            assertFalse(body(port, "/").contains("class=\"updatebar\""))
            assertFalse(body(port, "/search?q=hi").contains("class=\"updatebar\""))
        } finally {
            server.stop()
        }
    }
}
