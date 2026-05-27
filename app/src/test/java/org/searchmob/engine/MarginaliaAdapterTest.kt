package org.searchmob.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.searchmob.engine.adapters.MarginaliaAdapter

class MarginaliaAdapterTest {
    private fun fixture(name: String): String =
        checkNotNull(this::class.java.getResourceAsStream("/fixtures/$name")) { "missing fixture $name" }
            .readBytes()
            .decodeToString()

    @Test
    fun parsesPublicApiResults() {
        val items = MarginaliaAdapter().parse(fixture("marginalia.json"))
        assertTrue(items.isNotEmpty())
        assertEquals("https://theprivacydad.com/privacy-tools-are-not-worth-the-hassle/", items.first().url)
        assertTrue(items.first().title.contains("Privacy Tools"))
        assertTrue(items.first().snippet.isNotBlank())
        assertEquals("marginalia", items.first().engineId)
    }
}
