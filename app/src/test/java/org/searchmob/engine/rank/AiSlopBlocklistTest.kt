package org.searchmob.engine.rank

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiSlopBlocklistTest {
    private val domains = setOf("slopfarm.example", "junk.test")

    @Test
    fun matchesExactHost() {
        assertTrue(AiSlopBlocklist.matches("slopfarm.example", domains))
    }

    @Test
    fun matchesSubdomainViaParent() {
        assertTrue(AiSlopBlocklist.matches("www.slopfarm.example", domains))
        assertTrue(AiSlopBlocklist.matches("a.b.junk.test", domains))
    }

    @Test
    fun nonListedHostDoesNotMatch() {
        assertFalse(AiSlopBlocklist.matches("en.wikipedia.org", domains))
    }

    @Test
    fun sharedTldAloneDoesNotMatch() {
        // "other.example" shares the .example TLD with a listed domain but must not match.
        assertFalse(AiSlopBlocklist.matches("other.example", domains))
    }

    @Test
    fun emptyBlocklistNeverMatches() {
        assertFalse(AiSlopBlocklist.matches("anything.test", emptySet()))
    }
}
