package org.searchmob.engine.adapters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KagiApiAdapterTest {
    @Test
    fun parsesSearchResultsAndDropsRelatedSearches() {
        val body =
            """
            {"data":[
              {"t":0,"url":"https://a.com","title":"A","snippet":"sa"},
              {"t":0,"url":"https://b.com","title":"B","snippet":"sb"},
              {"t":1,"list":["related one","related two"]}
            ]}
            """.trimIndent()
        val items = KagiApiAdapter().parse(body)
        assertEquals(2, items.size)
        assertEquals(listOf("https://a.com", "https://b.com"), items.map { it.url })
        assertEquals("kagi-api", items[0].engineId)
        assertEquals(listOf(0, 1), items.map { it.position })
    }

    @Test
    fun emptyWhenNoData() {
        assertTrue(KagiApiAdapter().parse("{}").isEmpty())
    }

    @Test
    fun skipsResultsWithoutUrl() {
        val body = """{"data":[{"t":0,"title":"no url"},{"t":0,"url":"https://ok.com","title":"ok"}]}"""
        assertEquals(listOf("https://ok.com"), KagiApiAdapter().parse(body).map { it.url })
    }
}
