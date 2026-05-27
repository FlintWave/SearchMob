package org.searchmob.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.searchmob.engine.adapters.DuckDuckGoAdapter

class DuckDuckGoAdapterTest {
    private fun fixture(name: String): String =
        checkNotNull(this::class.java.getResourceAsStream("/fixtures/$name")) { "missing fixture $name" }
            .readBytes()
            .decodeToString()

    @Test
    fun parsesWebResultsDecodesUrlsAndExcludesAds() {
        val items = DuckDuckGoAdapter().parse(fixture("ddg.html"))

        // The fixture has 10 web results plus one ad; the ad must be excluded.
        assertTrue("expected several results, got ${items.size}", items.size >= 8)
        assertEquals("duckduckgo", items.first().engineId)
        assertTrue("first result should be the decoded real URL", items.first().url.contains("privacytools.io"))

        // No ad/redirect leakage: real destination URLs only.
        assertTrue(items.none { it.url.contains("duckduckgo.com/y.js") })
        assertTrue(items.none { it.url.contains("bing.com/aclick") })
        assertTrue(items.none { it.url.contains("capterra.com") })
        assertTrue(items.all { it.url.startsWith("http") && it.title.isNotBlank() })
    }
}
