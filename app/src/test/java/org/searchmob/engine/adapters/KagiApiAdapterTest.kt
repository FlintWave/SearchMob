package org.searchmob.engine.adapters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KagiApiAdapterTest {
    @Test
    fun parsesWebSearchResults() {
        val body =
            """
            {"meta":{"ms":12},"data":{"search":[
              {"url":"https://a.com","title":"A","snippet":"sa"},
              {"url":"https://b.com","title":"B","snippet":"sb"}
            ]}}
            """.trimIndent()
        val items = KagiApiAdapter().parse(body)
        assertEquals(2, items.size)
        assertEquals(listOf("https://a.com", "https://b.com"), items.map { it.url })
        assertEquals("kagi-api", items[0].engineId)
        assertEquals(listOf(0, 1), items.map { it.position })
    }

    @Test
    fun emptyWhenNoSearchResults() {
        assertTrue(KagiApiAdapter().parse("""{"meta":{},"data":{"image":[]}}""").isEmpty())
        assertTrue(KagiApiAdapter().parse("{}").isEmpty())
    }

    @Test
    fun skipsResultsWithoutUrl() {
        val body = """{"data":{"search":[{"title":"no url"},{"url":"https://ok.com","title":"ok"}]}}"""
        assertEquals(listOf("https://ok.com"), KagiApiAdapter().parse(body).map { it.url })
    }
}
