package org.searchmob.server

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.searchmob.engine.summary.WikiSummary

/** The commercial-parity features on the served routes: !bangs, instant answers, favicon, /img proxy. */
class ParityFeaturesRouteTest {
    private class SummaryProvider(private val thumbnailUrl: String?) : SearchResultProvider {
        override suspend fun search(query: String): List<SearchResult> =
            listOf(SearchResult(title = "T", url = "https://example.com/", engine = "fake"))

        override suspend fun searchWithCorrection(
            query: String,
            sortMode: org.searchmob.engine.sort.SortMode,
            vertical: org.searchmob.engine.vertical.Vertical,
            personalize: Boolean,
            activeLensOverride: String?,
        ): SearchOutcome =
            SearchOutcome(
                results = search(query),
                summary =
                    WikiSummary(
                        title = "Ada Lovelace",
                        description = "Mathematician",
                        extract = "Extract.",
                        url = "https://en.wikipedia.org/wiki/Ada_Lovelace",
                        thumbnailUrl = thumbnailUrl,
                    ),
            )
    }

    @Test
    fun bangQueryRedirectsToSiteSearch() =
        testApplication {
            application { searchModule(StubSearchResultProvider()) { DEFAULT_PORT } }
            val client = createClient { followRedirects = false }
            val response = client.get("/search?q=%21gh%20kotlin%20coroutines")
            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("https://github.com/search?q=kotlin%20coroutines", response.headers["Location"])
        }

    @Test
    fun unknownBangTokenIsANormalSearch() =
        testApplication {
            application { searchModule(StubSearchResultProvider()) { DEFAULT_PORT } }
            val client = createClient { followRedirects = false }
            val response = client.get("/search?q=%21important%20css")
            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.bodyAsText().contains("Stub result"))
        }

    @Test
    fun calculatorInstantAnswerRendersAboveResults() =
        testApplication {
            application { searchModule(StubSearchResultProvider()) { DEFAULT_PORT } }
            val body = client.get("/search?q=2%2B2").bodyAsText()
            assertTrue(body.contains("class=\"instant\""))
            assertTrue(body.contains("<p class=\"ival\">4</p>"))
        }

    @Test
    fun unitConversionInstantAnswerRenders() =
        testApplication {
            application { searchModule(StubSearchResultProvider()) { DEFAULT_PORT } }
            val body = client.get("/search?q=10%20km%20to%20miles").bodyAsText()
            assertTrue(body.contains("class=\"instant\""))
            assertTrue(body.contains("6.213711922 miles"))
        }

    @Test
    fun ordinaryQueryHasNoInstantAnswer() =
        testApplication {
            application { searchModule(StubSearchResultProvider()) { DEFAULT_PORT } }
            val body = client.get("/search?q=privacy").bodyAsText()
            assertFalse(body.contains("class=\"instant\""))
        }

    @Test
    fun metaLineShowsResultCountAndTiming() =
        testApplication {
            application { searchModule(StubSearchResultProvider()) { DEFAULT_PORT } }
            val body = client.get("/search?q=privacy").bodyAsText()
            assertTrue(body.contains("1 results"))
            assertTrue(Regex("· \\d+\\.\\d s").containsMatchIn(body))
        }

    @Test
    fun faviconIsServed() =
        testApplication {
            application { searchModule(StubSearchResultProvider()) { DEFAULT_PORT } }
            val response = client.get("/favicon.ico")
            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue((response.headers["Content-Type"] ?: "").contains("image/svg+xml"))
        }

    @Test
    fun sortFormKeepsTheActiveVertical() =
        testApplication {
            application { searchModule(StubSearchResultProvider()) { DEFAULT_PORT } }
            val body = client.get("/search?q=x&vertical=news").bodyAsText()
            // The hidden field appears in both the topbar form and the sort form.
            assertTrue(body.contains("name=\"vertical\" value=\"news\""))
        }

    @Test
    fun cspAllowsSameOriginFetchAndImagesOnly() =
        testApplication {
            application { searchModule(StubSearchResultProvider()) { DEFAULT_PORT } }
            val csp = client.get("/").headers["Content-Security-Policy"].orEmpty()
            assertTrue(csp.contains("connect-src 'self'"))
            assertTrue(csp.contains("img-src 'self' data:"))
            assertFalse(csp.contains("img-src https:"))
        }

    @Test
    fun imgProxyRequiresAllowedHostAndWiredFetcher() =
        testApplication {
            application {
                searchModule(
                    StubSearchResultProvider(),
                    imageProxy = { ProxiedImage(byteArrayOf(1, 2, 3), "image/png") },
                ) { DEFAULT_PORT }
            }
            val ok = client.get("/img?u=https%3A%2F%2Fupload.wikimedia.org%2Fx.png")
            assertEquals(HttpStatusCode.OK, ok.status)
            assertTrue((ok.headers["Content-Type"] ?: "").contains("image/png"))
            // A non-Wikimedia URL is refused even though a fetcher is wired (SSRF guard).
            val refused = client.get("/img?u=https%3A%2F%2Fevil.example%2Fx.png")
            assertEquals(HttpStatusCode.NotFound, refused.status)
        }

    @Test
    fun imgProxyIsNotFoundWhenNoFetcherWired() =
        testApplication {
            application { searchModule(StubSearchResultProvider()) { DEFAULT_PORT } }
            val response = client.get("/img?u=https%3A%2F%2Fupload.wikimedia.org%2Fx.png")
            assertEquals(HttpStatusCode.NotFound, response.status)
        }

    @Test
    fun summaryThumbnailGoesThroughLoopbackProxyWhenWired() =
        testApplication {
            application {
                searchModule(
                    SummaryProvider("https://upload.wikimedia.org/thumb/ada.png"),
                    imageProxy = { ProxiedImage(byteArrayOf(1), "image/png") },
                ) { DEFAULT_PORT }
            }
            val body = client.get("/search?q=ada%20lovelace").bodyAsText()
            assertTrue(body.contains("/img?u=https%3A%2F%2Fupload.wikimedia.org%2Fthumb%2Fada.png"))
            assertFalse(body.contains("src=\"https://upload.wikimedia.org"))
        }

    @Test
    fun summaryThumbnailIsOmittedEntirelyWithoutProxy() =
        testApplication {
            application { searchModule(SummaryProvider("https://upload.wikimedia.org/thumb/ada.png")) { DEFAULT_PORT } }
            val body = client.get("/search?q=ada%20lovelace").bodyAsText()
            // The card renders text-only: the browser must never be pointed at the third-party host.
            assertTrue(body.contains("Ada Lovelace"))
            assertFalse(body.contains("upload.wikimedia.org"))
        }

    @Test
    fun searchPagesAdvertiseTheFaviconAndSuggestScript() =
        testApplication {
            application { searchModule(StubSearchResultProvider()) { DEFAULT_PORT } }
            val body = client.get("/").bodyAsText()
            assertTrue(body.contains("rel=\"icon\""))
            assertTrue(body.contains("/suggest?q="))
        }
}
