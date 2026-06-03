package org.searchmob.server

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.testApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Served-page infinite scroll: the whole pool is rendered, results past the first window start
 * collapsed, and a sentinel + reveal script unhide them on scroll. A small pool renders no reveal
 * machinery, and with JS off every result is still in the markup.
 */
class PagingRevealTest {
    // The first-window size mirrors `REVEAL_SIZE` in SearchServer.kt (file-private there).
    private val window = 10

    private class ManyProvider(private val count: Int) : SearchResultProvider {
        override suspend fun search(query: String): List<SearchResult> =
            (0 until count).map {
                SearchResult(title = "Result $it", url = "https://e.test/$it", engine = "fake")
            }
    }

    private fun countOf(
        haystack: String,
        needle: String,
    ): Int = haystack.split(needle).size - 1

    @Test
    fun largePoolCollapsesResultsPastTheWindowAndRevealsOnScroll() =
        testApplication {
            application { searchModule(ManyProvider(window + 8)) { DEFAULT_PORT } }
            val body = client.get("/search?q=x").bodyAsText()
            val visible = countOf(body, "<div class=\"result\">")
            val collapsed = countOf(body, "<div class=\"result is-collapsed\">")
            // Every result is in the DOM (nothing dropped); only the ones past the window collapse.
            assertEquals(window + 8, visible + collapsed)
            assertEquals(window, visible)
            assertEquals(8, collapsed)
            // The sentinel and reveal script are present so scrolling unhides the rest.
            assertTrue(body.contains("reveal-sentinel"))
            assertTrue(body.contains("IntersectionObserver"))
        }

    @Test
    fun smallPoolHasNoRevealMachinery() =
        testApplication {
            application { searchModule(ManyProvider(window)) { DEFAULT_PORT } }
            val body = client.get("/search?q=x").bodyAsText()
            // Assert on the rendered markup, not bare strings: the CSS always names the classes.
            assertFalse(body.contains("<div class=\"result is-collapsed\">"))
            assertFalse(body.contains("<div class=\"reveal-sentinel\""))
            assertFalse(body.contains("IntersectionObserver"))
        }
}
