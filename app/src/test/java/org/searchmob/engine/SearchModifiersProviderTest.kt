package org.searchmob.engine

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * End-to-end coverage of Google-fu style search operators (see
 * [org.searchmob.engine.query.QueryOperators]) through [MetaSearchResultProvider]: the query the
 * engines actually receive, and the local post-filter applied to the aggregated results. Mirrors the
 * fake-adapter pattern in `AggregatorTest`.
 */
class SearchModifiersProviderTest {
    private class CapturingEngine(
        override val id: String,
        private val items: List<EngineResultItem>,
    ) : EngineAdapter {
        override val displayName = id
        override val categories = setOf(SearchCategory.GENERAL)

        /** The last [SearchQuery] this adapter was asked to search, for asserting the transformed query. */
        var lastQuery: SearchQuery? = null
            private set

        override suspend fun search(
            query: SearchQuery,
            ctx: EngineContext,
        ): EngineResult {
            lastQuery = query
            return EngineResult.Success(items)
        }
    }

    private fun item(
        url: String,
        title: String = "Title",
        snippet: String = "",
        position: Int = 0,
    ) = EngineResultItem(title = title, url = url, snippet = snippet, engineId = "e", position = position)

    private fun provider(engine: EngineAdapter) = MetaSearchResultProvider(EngineRegistry(listOf(engine)))

    @Test
    fun siteOperatorDropsResultsFromOtherHosts() =
        runTest {
            val engine =
                CapturingEngine(
                    "e",
                    listOf(
                        item("https://example.com/a", position = 0),
                        item("https://other.com/b", position = 1),
                    ),
                )
            val results = provider(engine).search("foo site:example.com")
            assertEquals(listOf("https://example.com/a"), results.map { it.url })
        }

    @Test
    fun negatedTermExclusionWorksEndToEnd() =
        runTest {
            val engine =
                CapturingEngine(
                    "e",
                    listOf(
                        item("https://a.com/1", title = "About cats", position = 0),
                        item("https://b.com/1", title = "About dogs", position = 1),
                    ),
                )
            val results = provider(engine).search("pets -cats")
            assertEquals(listOf("https://b.com/1"), results.map { it.url })
        }

    @Test
    fun engineReceivesTransformedQueryWithIntitleReplacedByBareValue() =
        runTest {
            val engine = CapturingEngine("e", listOf(item("https://a.com/1")))
            provider(engine).search("mouse intitle:review")
            // intitle: is not implemented consistently upstream, so it becomes a bare recall hint; the
            // title constraint itself is enforced locally by ParsedQuery.matches.
            assertEquals("mouse review", engine.lastQuery?.terms)
            assertEquals("mouse review", engine.lastQuery?.rankingTerms)
        }

    @Test
    fun filetypeOperatorKeepsOnlyMatchingExtension() =
        runTest {
            val engine =
                CapturingEngine(
                    "e",
                    listOf(
                        item("https://a.com/manual.pdf", position = 0),
                        item("https://a.com/manual.docx", position = 1),
                    ),
                )
            val results = provider(engine).search("manual filetype:pdf")
            assertEquals(listOf("https://a.com/manual.pdf"), results.map { it.url })
        }
}
