package org.searchmob.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.searchmob.engine.adapters.MojeekAdapter

class MojeekAdapterTest {
    private fun fixture(name: String): String =
        checkNotNull(this::class.java.getResourceAsStream("/fixtures/$name")) { "missing fixture $name" }
            .readBytes()
            .decodeToString()

    @Test
    fun parsesResultsWithDirectUrlsAndSnippets() {
        val items = MojeekAdapter().parse(fixture("mojeek.html"))
        assertTrue("expected results, got ${items.size}", items.size >= 5)
        assertEquals("https://www.privacytools.io/", items.first().url)
        assertTrue(items.first().title.contains("Privacy Tools"))
        assertTrue(items.first().snippet.isNotBlank())
        assertTrue(items.all { it.url.startsWith("http") && it.title.isNotBlank() })
        assertEquals("mojeek", items.first().engineId)
    }
}
