package org.searchmob.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.searchmob.engine.adapters.MwmblAdapter

class MwmblAdapterTest {
    private fun fixture(name: String): String =
        checkNotNull(this::class.java.getResourceAsStream("/fixtures/$name")) { "missing fixture $name" }
            .readBytes()
            .decodeToString()

    @Test
    fun parsesJsonResultsConcatenatingFragments() {
        val items = MwmblAdapter().parse(fixture("mwmbl.json"))

        assertTrue(items.isNotEmpty())
        val first = items.first()
        assertEquals("https://en.wikipedia.org/wiki/Privacy", first.url)
        assertEquals("Privacy", first.title)
        assertTrue("extract fragments should be joined", first.snippet.contains("ability of an individual"))
        assertEquals("mwmbl", first.engineId)
        assertEquals(0, first.position)
    }
}
