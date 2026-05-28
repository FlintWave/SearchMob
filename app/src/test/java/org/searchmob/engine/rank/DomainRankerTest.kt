package org.searchmob.engine.rank

import org.junit.Assert.assertEquals
import org.junit.Test

class DomainRankerTest {
    private data class R(val url: String, val text: String = "")

    private fun apply(
        items: List<R>,
        rules: RankingRules,
    ) = DomainRanker.apply(items, rules, hostOf = { DomainRanker.host(it.url) }, textOf = { it.text })

    private fun hosts(items: List<R>) = items.map { DomainRanker.host(it.url) }

    @Test
    fun emptyRulesLeaveOrderUnchanged() {
        val items = listOf(R("https://a.com"), R("https://b.com"))
        assertEquals(items, apply(items, RankingRules.EMPTY))
    }

    @Test
    fun blockRemovesADomain() {
        val items = listOf(R("https://a.com/1"), R("https://b.com/1"))
        val out = apply(items, RankingRules(domainRules = mapOf("a.com" to RankRule.BLOCK)))
        assertEquals(listOf("https://b.com/1"), out.map { it.url })
    }

    @Test
    fun blockMatchesSubdomains() {
        val items = listOf(R("https://sub.example.com/x"), R("https://other.com"))
        val out = apply(items, RankingRules(domainRules = mapOf("example.com" to RankRule.BLOCK)))
        assertEquals(listOf("https://other.com"), out.map { it.url })
    }

    @Test
    fun pinRaiseLowerBucketsInOrder() {
        val items =
            listOf(R("https://normal.com"), R("https://lower.com"), R("https://raise.com"), R("https://pin.com"))
        val rules =
            RankingRules(
                domainRules =
                    mapOf(
                        "pin.com" to RankRule.PIN,
                        "raise.com" to RankRule.RAISE,
                        "lower.com" to RankRule.LOWER,
                    ),
            )
        assertEquals(listOf("pin.com", "raise.com", "normal.com", "lower.com"), hosts(apply(items, rules)))
    }

    @Test
    fun activeLensIncludeRestrictsToItsDomains() {
        val items = listOf(R("https://keep.com"), R("https://drop.com"))
        val lens = Lens("only", includeDomains = listOf("keep.com"))
        val out = apply(items, RankingRules(lenses = listOf(lens), activeLens = "only"))
        assertEquals(listOf("https://keep.com"), out.map { it.url })
    }

    @Test
    fun activeLensExcludeKeywordRemovesMatches() {
        val items = listOf(R("https://a.com", "totally Sponsored post"), R("https://b.com", "clean"))
        val lens = Lens("nospam", excludeKeywords = listOf("sponsored"))
        val out = apply(items, RankingRules(lenses = listOf(lens), activeLens = "nospam"))
        assertEquals(listOf("https://b.com"), out.map { it.url })
    }

    @Test
    fun gogglesDiscardAndBoost() {
        val items = listOf(R("https://normal.com"), R("https://spam.example"), R("https://good.com"))
        val rules =
            RankingRules(
                goggles =
                    listOf(
                        GoggleRule("spam.example", RankRule.BLOCK),
                        GoggleRule("good.com", RankRule.RAISE),
                    ),
            )
        assertEquals(listOf("good.com", "normal.com"), hosts(apply(items, rules)))
    }

    @Test
    fun domainRuleWinsOverGoggle() {
        val items = listOf(R("https://x.com"))
        val rules =
            RankingRules(
                domainRules = mapOf("x.com" to RankRule.PIN),
                goggles = listOf(GoggleRule("x.com", RankRule.BLOCK)),
            )
        assertEquals(listOf("https://x.com"), apply(items, rules).map { it.url })
    }
}
