package org.searchmob.server

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.xml.parsers.DocumentBuilderFactory

class SearchRoutesTest {
    private class FakeProvider(private val title: String) : SearchResultProvider {
        override suspend fun search(query: String): List<SearchResult> =
            listOf(SearchResult(title = title, url = "https://fake.test/$query", engine = "fake"))
    }

    /** Echoes a single result whose URL is controlled by the query, so href rendering can be inspected. */
    private class UrlProvider(private val url: String) : SearchResultProvider {
        override suspend fun search(query: String): List<SearchResult> =
            listOf(SearchResult(title = "HOSTILE_TITLE", url = url, engine = "fake"))
    }

    @Test
    fun healthzReturnsOk() =
        testApplication {
            application { searchModule(StubSearchResultProvider()) { DEFAULT_PORT } }
            val response = client.get("/healthz")
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("ok", response.bodyAsText())
        }

    @Test
    fun searchReturnsHtmlWithResults() =
        testApplication {
            application { searchModule(StubSearchResultProvider()) { DEFAULT_PORT } }
            val response = client.get("/search?q=privacy")
            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue((response.headers["Content-Type"] ?: "").contains("text/html"))
            val body = response.bodyAsText()
            assertTrue(body.contains("privacy"))
            assertTrue(body.contains("Stub result"))
        }

    @Test
    fun searchWithoutQueryReturnsValidPage() =
        testApplication {
            application { searchModule(StubSearchResultProvider()) { DEFAULT_PORT } }
            val response = client.get("/search")
            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.bodyAsText().contains("Enter a query"))
        }

    @Test
    fun apiSearchReturnsJson() =
        testApplication {
            application { searchModule(StubSearchResultProvider()) { DEFAULT_PORT } }
            val response = client.get("/api/search?q=privacy&format=json")
            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue((response.headers["Content-Type"] ?: "").contains("application/json"))
            val body = response.bodyAsText()
            assertTrue(body.contains("privacy"))
            assertTrue(body.contains("results"))
        }

    @Test
    fun providerIsSubstitutableBehindRoutes() =
        testApplication {
            application { searchModule(FakeProvider("INJECTED_TITLE")) { DEFAULT_PORT } }
            assertTrue(client.get("/api/search?q=x").bodyAsText().contains("INJECTED_TITLE"))
            assertTrue(client.get("/search?q=x").bodyAsText().contains("INJECTED_TITLE"))
        }

    @Test
    fun openSearchDescriptorIsWellFormedAndUsesBoundPort() =
        testApplication {
            application { searchModule(StubSearchResultProvider()) { 9123 } }
            val response = client.get("/opensearch.xml")
            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue((response.headers["Content-Type"] ?: "").contains("opensearchdescription+xml"))
            val body = response.bodyAsText()
            assertTrue(body.contains("{searchTerms}"))
            assertTrue(body.contains(":9123/search"))

            // Must be well-formed XML with the OpenSearch root element.
            val doc =
                DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(body.byteInputStream())
            assertEquals("OpenSearchDescription", doc.documentElement.tagName)
        }

    @Test
    fun isSafeHttpUrlAllowsOnlyHttpAndHttps() {
        assertTrue(isSafeHttpUrl("http://example.com/"))
        assertTrue(isSafeHttpUrl("https://example.com/path?q=1"))
        assertFalse(isSafeHttpUrl("javascript:alert(1)"))
        assertFalse(isSafeHttpUrl("JavaScript:alert(1)"))
        assertFalse(isSafeHttpUrl("data:text/html,<script>alert(1)</script>"))
        assertFalse(isSafeHttpUrl("file:///etc/passwd"))
        assertFalse(isSafeHttpUrl("/relative/path"))
        assertFalse(isSafeHttpUrl(""))
        // Malformed URLs must be treated as unsafe rather than throwing.
        assertFalse(isSafeHttpUrl("ht tp://broken url"))
    }

    @Test
    fun unsafeResultUrlIsNotRenderedAsHref() =
        testApplication {
            application { searchModule(UrlProvider("javascript:alert(1)")) { DEFAULT_PORT } }
            val body = client.get("/search?q=x").bodyAsText()
            // Title still shown, but never inside an href to the hostile scheme.
            assertTrue(body.contains("HOSTILE_TITLE"))
            assertFalse(body.contains("href=\"javascript:"))
        }

    @Test
    fun safeResultUrlIsRenderedAsHref() =
        testApplication {
            application { searchModule(UrlProvider("https://safe.test/page")) { DEFAULT_PORT } }
            val body = client.get("/search?q=x").bodyAsText()
            assertTrue(body.contains("href=\"https://safe.test/page\""))
        }

    @Test
    fun overlongQueryIsCappedBeforeReachingProvider() =
        testApplication {
            val seen = StringBuilder()
            val recorder =
                object : SearchResultProvider {
                    override suspend fun search(query: String): List<SearchResult> {
                        seen.append(query)
                        return emptyList()
                    }
                }
            application { searchModule(recorder) { DEFAULT_PORT } }
            val longQuery = "a".repeat(MAX_QUERY_LENGTH + 100)
            client.get("/search?q=$longQuery")
            assertEquals(MAX_QUERY_LENGTH, seen.length)

            seen.clear()
            client.get("/api/search?q=$longQuery")
            assertEquals(MAX_QUERY_LENGTH, seen.length)
        }
}
