package org.searchmob.server

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.xml.parsers.DocumentBuilderFactory

class SearchRoutesTest {
    private class FakeProvider(private val title: String) : SearchResultProvider {
        override suspend fun search(query: String): List<SearchResult> =
            listOf(SearchResult(title = title, url = "https://fake.test/$query", engine = "fake"))
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
}
