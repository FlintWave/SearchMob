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

    @Test
    fun effectiveBlocklistDropsAllowlistedDomainsAndSubdomains() {
        // The community lists include the official sites of AI companies and major dev hubs. A search
        // for "huggingface"/"github" must surface those, so the allowlist (and any subdomain of one)
        // is subtracted. Regression for the "official site missing from page one" report.
        val raw = setOf("huggingface.co", "discuss.huggingface.co", "github.com", "slopfarm.example")
        val allow = setOf("huggingface.co", "github.com")
        val effective = AiSlopBlocklist.effectiveBlocklist(raw, allow)
        assertFalse(AiSlopBlocklist.matches("huggingface.co", effective))
        assertFalse(AiSlopBlocklist.matches("discuss.huggingface.co", effective))
        assertFalse(AiSlopBlocklist.matches("github.com", effective))
        // A genuine low-quality domain is still blocked.
        assertTrue(AiSlopBlocklist.matches("slopfarm.example", effective))
    }
}
