package org.searchmob.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.searchmob.engine.adapters.BraveApiAdapter
import org.searchmob.engine.adapters.MojeekApiAdapter

class ApiAdaptersTest {
    private fun fixture(name: String): String =
        checkNotNull(this::class.java.getResourceAsStream("/fixtures/$name")) { "missing fixture $name" }
            .readBytes()
            .decodeToString()

    @Test
    fun braveApiParsesWebResults() {
        val adapter = BraveApiAdapter()
        assertTrue(adapter.requiresApiKey)
        val items = adapter.parse(fixture("brave_api.json"))
        assertEquals(2, items.size)
        assertEquals("Privacy Tools", items.first().title)
        assertEquals("https://www.privacytools.io/", items.first().url)
        assertEquals("brave-api", items.first().engineId)
    }

    @Test
    fun mojeekApiParsesResultsAndSupersedesFreeMojeek() {
        val adapter = MojeekApiAdapter()
        assertTrue(adapter.requiresApiKey)
        assertTrue(adapter.supersedes.contains("mojeek"))
        val items = adapter.parse(fixture("mojeek_api.json"))
        assertEquals(2, items.size)
        assertEquals("https://www.privacytools.io/", items.first().url)
        assertEquals("mojeek-api", items.first().engineId)
    }
}
