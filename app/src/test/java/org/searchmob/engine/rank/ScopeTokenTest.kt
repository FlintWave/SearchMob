package org.searchmob.engine.rank

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScopeTokenTest {
    private val rules =
        RankingRules(
            lenses =
                listOf(
                    Lens(name = "Research mode", includeDomains = listOf("arxiv.org")),
                    Lens(name = "Less clutter (no Pinterest/Quora)", excludeDomains = listOf("pinterest.com")),
                    Lens(name = "Developer docs", includeDomains = listOf("docs.python.org")),
                ),
        )

    @Test
    fun firstWordMatchStripsTokenAndReturnsName() {
        val (cleaned, name) = ScopeToken.parse("mechanical keyboards +research", rules)
        assertEquals("mechanical keyboards", cleaned)
        assertEquals("Research mode", name)
    }

    @Test
    fun matchIsCaseInsensitive() {
        val (cleaned, name) = ScopeToken.parse("rust +DEVELOPER", rules)
        assertEquals("rust", cleaned)
        assertEquals("Developer docs", name)
    }

    @Test
    fun firstWordMatchOnAParenthesisedName() {
        val (cleaned, name) = ScopeToken.parse("cake recipe +less", rules)
        assertEquals("cake recipe", cleaned)
        assertEquals("Less clutter (no Pinterest/Quora)", name)
    }

    @Test
    fun normalizedFullNameFallback() {
        val (cleaned, name) = ScopeToken.parse("flask +developerdocs", rules)
        assertEquals("flask", cleaned)
        assertEquals("Developer docs", name)
    }

    @Test
    fun unmatchedTokenStaysInQuery() {
        val (cleaned, name) = ScopeToken.parse("rust +tokio async", rules)
        assertEquals("rust +tokio async", cleaned)
        assertNull(name)
    }

    @Test
    fun firstMatchingTokenWinsAndOnlyItIsStripped() {
        val (cleaned, name) = ScopeToken.parse("+research neural nets +developer", rules)
        assertEquals("neural nets +developer", cleaned)
        assertEquals("Research mode", name)
    }

    @Test
    fun tokenMidQueryIsRemovedInPlace() {
        val (cleaned, name) = ScopeToken.parse("quantum +research computing", rules)
        assertEquals("quantum computing", cleaned)
        assertEquals("Research mode", name)
    }

    @Test
    fun queryWithoutPlusIsReturnedUnchanged() {
        val (cleaned, name) = ScopeToken.parse("plain query", rules)
        assertEquals("plain query", cleaned)
        assertNull(name)
    }

    @Test
    fun barePlusIsNotAToken() {
        val (cleaned, name) = ScopeToken.parse("a + b", rules)
        assertEquals("a + b", cleaned)
        assertNull(name)
    }

    @Test
    fun noLensesMeansNoMatch() {
        val (cleaned, name) = ScopeToken.parse("foo +research", RankingRules.EMPTY)
        assertEquals("foo +research", cleaned)
        assertNull(name)
    }

    @Test
    fun tokenOnlyQueryCollapsesToEmpty() {
        val (cleaned, name) = ScopeToken.parse("+research", rules)
        assertEquals("", cleaned)
        assertEquals("Research mode", name)
    }
}
