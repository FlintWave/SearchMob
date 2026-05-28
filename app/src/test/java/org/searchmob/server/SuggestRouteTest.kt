package org.searchmob.server

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.searchmob.server.suggest.SuggestionsProvider

class SuggestRouteTest {
    /** Returns a fixed list, capturing the query and limit it was asked for. */
    private class FakeSuggestions(private val items: List<String>) : SuggestionsProvider {
        var lastQuery: String? = null
        var lastLimit: Int = -1

        override suspend fun suggest(
            query: String,
            limit: Int,
        ): List<String> {
            lastQuery = query
            lastLimit = limit
            return items
        }
    }

    @Test
    fun suggestReturnsOpenSearchShapeAndContentType() =
        testApplication {
            val fake = FakeSuggestions(listOf("kotlin", "kotlinx"))
            application { searchModule(StubSearchResultProvider(), suggestionsProvider = fake) { DEFAULT_PORT } }
            val response = client.get("/suggest?q=kot")
            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue((response.headers["Content-Type"] ?: "").contains("application/x-suggestions+json"))
            assertEquals("[\"kot\",[\"kotlin\",\"kotlinx\"]]", response.bodyAsText())
        }

    @Test
    fun blankQueryReturnsEmptyTwoElementArrayAndDoesNotCallProvider() =
        testApplication {
            val fake = FakeSuggestions(listOf("should-not-appear"))
            application { searchModule(StubSearchResultProvider(), suggestionsProvider = fake) { DEFAULT_PORT } }
            assertEquals("[\"\",[]]", client.get("/suggest?q=").bodyAsText())
            assertEquals("[\"\",[]]", client.get("/suggest").bodyAsText())
            assertEquals("[\"\",[]]", client.get("/suggest?q=%20%20").bodyAsText())
            // Provider must not be consulted for a blank query.
            assertEquals(null, fake.lastQuery)
        }

    @Test
    fun queryIsLengthCappedBeforeReachingProvider() =
        testApplication {
            val fake = FakeSuggestions(emptyList())
            application { searchModule(StubSearchResultProvider(), suggestionsProvider = fake) { DEFAULT_PORT } }
            val longQuery = "a".repeat(MAX_QUERY_LENGTH + 100)
            client.get("/suggest?q=$longQuery")
            assertEquals(MAX_QUERY_LENGTH, fake.lastQuery?.length)
            // The cap passed to the provider is the suggestions cap, not the query-length cap.
            assertEquals(MAX_SUGGESTIONS, fake.lastLimit)
        }

    @Test
    fun jsonEscapesSpecialCharactersInQueryAndSuggestions() =
        testApplication {
            val fake = FakeSuggestions(listOf("a\"b", "c\\d"))
            application { searchModule(StubSearchResultProvider(), suggestionsProvider = fake) { DEFAULT_PORT } }
            // q="x containing a quote; serialization must escape it correctly in both array elements.
            val body = client.get("/suggest?q=he%22llo").bodyAsText()
            assertEquals("[\"he\\\"llo\",[\"a\\\"b\",\"c\\\\d\"]]", body)
        }

    @Test
    fun defaultProviderYieldsNoSuggestions() =
        testApplication {
            application { searchModule(StubSearchResultProvider()) { DEFAULT_PORT } }
            assertEquals("[\"hi\",[]]", client.get("/suggest?q=hi").bodyAsText())
        }

    @Test
    fun descriptorAdvertisesSuggestionsUrl() {
        val xml = openSearchDescriptor(9123)
        assertTrue(xml.contains("application/x-suggestions+json"))
        assertTrue(xml.contains(":9123/suggest?q={searchTerms}"))
    }
}
