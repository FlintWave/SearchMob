package org.searchmob.engine.rank

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the click-personalization math and serialization, and proves cross-platform parity with the
 * desktop app by loading a model the desktop produced and reproducing its boosts exactly.
 */
class PersonalizationTest {
    private val dayMs = 86_400_000L

    private fun day(n: Long) = n * dayMs

    private fun train(
        model: PersonalizationModel,
        hosts: List<String?>,
        clicked: Int,
        times: Int,
        now: Long,
        terms: List<String> = listOf("python", "list"),
    ) {
        repeat(times) { Personalizer.updateFromClick(model, hosts, clicked, terms, now) }
    }

    @Test
    fun queryTermsAreAsciiLowercaseDistinctAndCapped() {
        assertEquals(listOf("python", "list"), Personalizer.queryTerms("Python  list, list!!"))
        assertEquals(listOf("of"), Personalizer.queryTerms("a I/O of"))
        assertEquals(8, Personalizer.queryTerms((0..19).joinToString(" ") { "term$it" }).size)
    }

    @Test
    fun normalizeHostStripsWwwAndLowercases() {
        assertEquals("example.com", Personalizer.normalizeHost("WWW.Example.COM"))
        assertEquals("sub.example.com", Personalizer.normalizeHost("sub.example.com"))
    }

    @Test
    fun skipAboveOnlyCountsHostsAboveTheClick() {
        val m = PersonalizationModel()
        val now = day(20000)
        Personalizer.updateFromClick(m, listOf("a.com", "b.com", "so.com", "c.com"), 2, emptyList(), now)
        assertEquals(m.config.alphaPrior + 1, m.domains["so.com"]!!.alpha, 1e-9)
        assertEquals(m.config.betaPrior + 1, m.domains["a.com"]!!.beta, 1e-9)
        assertEquals(m.config.betaPrior + 1, m.domains["b.com"]!!.beta, 1e-9)
        assertFalse(m.domains.containsKey("c.com"))
    }

    @Test
    fun outOfRangeOrUnparsableClickIsNoOp() {
        val m = PersonalizationModel()
        Personalizer.updateFromClick(m, listOf("a.com"), 5, emptyList(), day(20000))
        Personalizer.updateFromClick(m, listOf(null), 0, emptyList(), day(20000))
        assertTrue(m.isEmpty())
    }

    @Test
    fun boostIsNeutralUntilColdStartThresholdMet() {
        val m = PersonalizationModel()
        val now = day(20000)
        train(m, listOf("a.com", "so.com"), 1, m.config.minSignalQueries - 1, now)
        assertEquals(1.0, Personalizer.boost(m, "so.com", emptyList(), now), 1e-9)
        train(m, listOf("a.com", "so.com"), 1, 1, now)
        assertTrue(Personalizer.boost(m, "so.com", emptyList(), now) > 1.0)
    }

    @Test
    fun boostIsClampedToConfiguredBounds() {
        val m = PersonalizationModel()
        val now = day(20000)
        train(m, listOf("a.com", "so.com"), 1, 40, now)
        assertEquals(m.config.boostMax, Personalizer.boost(m, "so.com", emptyList(), now), 1e-9)
        assertEquals(m.config.boostMin, Personalizer.boost(m, "a.com", emptyList(), now), 1e-9)
        assertEquals(1.0, Personalizer.boost(m, "never.example", emptyList(), now), 1e-9)
    }

    @Test
    fun timeDecayFadesExcessTowardPrior() {
        val cfg = PersonalizationConfig(boostMax = 100.0, minSignalQueries = 1, minDomainImpressions = 1)
        val m = PersonalizationModel(config = cfg)
        val start = day(20000)
        train(m, listOf("a.com", "so.com"), 1, 5, start)
        val fresh = Personalizer.boost(m, "so.com", emptyList(), start)
        assertTrue(fresh > 1.0)
        val later = day(20000 + cfg.halfLifeDays.toLong())
        val decayed = Personalizer.boost(m, "so.com", emptyList(), later)
        assertTrue(decayed in 1.0..fresh)
        assertNotEquals(fresh, decayed, 1e-6)
    }

    @Test
    fun reorderPromotesLearnedDomainWithinBounds() {
        val m = PersonalizationModel()
        val now = day(20000)
        val hosts = listOf("a.com", "b.com", "so.com", "c.com")
        train(m, hosts, 2, 30, now)
        val out = Personalizer.reorder(hosts, { it }, "python list", m, now, rng = { 0.99 })
        assertTrue(out.indexOf("so.com") < out.indexOf("b.com"))
        assertEquals(hosts.toSet(), out.toSet())
    }

    @Test
    fun reorderBypassesUnderEpsilonAndColdStart() {
        val m = PersonalizationModel()
        val now = day(20000)
        val hosts = listOf("a.com", "b.com", "so.com")
        assertEquals(hosts, Personalizer.reorder(hosts, { it }, "q", m, now, rng = { 0.99 }))
        train(m, hosts, 2, 30, now)
        assertEquals(hosts, Personalizer.reorder(hosts, { it }, "q", m, now, rng = { 0.0 }))
    }

    @Test
    fun evictionCapsTableSizeKeepingMostObserved() {
        val cfg = PersonalizationConfig(maxDomains = 2, maxQtPairs = 2)
        val m = PersonalizationModel(config = cfg)
        val now = day(20000)
        for (i in 0 until 6) {
            Personalizer.updateFromClick(m, listOf("h$i.com", "keep.com"), 1, emptyList(), now)
        }
        assertTrue(m.domains.size <= 2)
        assertTrue(m.domains.containsKey("keep.com"))
    }

    @Test
    fun jsonRoundTripIsIdentity() {
        val m = PersonalizationModel()
        train(m, listOf("a.com", "so.com"), 1, 7, day(20000))
        val text = Personalizer.toJson(m)
        val again = Personalizer.fromJson(text)
        assertEquals(text, Personalizer.toJson(again))
        assertEquals(m.totalClickedQueries, again.totalClickedQueries)
    }

    @Test
    fun fromJsonIsFailSoft() {
        assertTrue(Personalizer.fromJson("not json").isEmpty())
        assertTrue(Personalizer.fromJson("[]").isEmpty())
        assertTrue(Personalizer.fromJson("{}").isEmpty())
    }

    @Test
    fun resetClearsCountsButKeepsConfig() {
        val cfg = PersonalizationConfig(epsilon = 0.25)
        val m = PersonalizationModel(config = cfg)
        train(m, listOf("a.com", "so.com"), 1, 7, day(20000))
        val cleared = Personalizer.reset(m)
        assertTrue(cleared.isEmpty())
        assertEquals(0.25, cleared.config.epsilon, 1e-9)
    }

    @Test
    fun crossPlatformDesktopFixtureLoadsWithIdenticalBoosts() {
        // A model produced by the desktop app (engines/rank/personalize.py) must load here and
        // reproduce the exact boosts the desktop computed for the same inputs.
        val text =
            javaClass.classLoader!!
                .getResourceAsStream("fixtures/personalization_desktop.json")!!
                .bufferedReader()
                .use { it.readText() }
        val m = Personalizer.fromJson(text)
        val now = day(20000) // same day the fixture was stamped, so decay is a no-op
        assertEquals(6, m.totalClickedQueries)
        assertEquals(2.0, Personalizer.boost(m, "so.com", emptyList(), now), 1e-6)
        assertEquals(0.769231, Personalizer.boost(m, "a.com", emptyList(), now), 1e-6)
        // qt pairs are below the impression gate, so query terms do not change the boost yet.
        assertEquals(2.0, Personalizer.boost(m, "so.com", listOf("python", "list"), now), 1e-6)
        assertEquals(1.0, Personalizer.boost(m, "zzz.example", listOf("python"), now), 1e-6)
    }
}
