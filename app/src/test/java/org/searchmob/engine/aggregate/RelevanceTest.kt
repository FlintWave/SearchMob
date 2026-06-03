package org.searchmob.engine.aggregate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mirrors the desktop `tests/engines/test_relevance.py` suite for the Kotlin port. */
class RelevanceTest {
    // --- contentTerms -------------------------------------------------------------------------

    @Test
    fun contentTermsStripsStopwordsKeepsSubject() {
        assertEquals(listOf("mechanical", "keyboard", "2026"), Relevance.contentTerms("best mechanical keyboard 2026"))
    }

    @Test
    fun contentTermsDistinctAndLowercased() {
        assertEquals(listOf("tie", "knot"), Relevance.contentTerms("Tie a TIE knot tie"))
    }

    @Test
    fun contentTermsFallsBackWhenAllStopwords() {
        // "how to" is all stopwords; rather than score everything zero, keep the tokens.
        assertEquals(listOf("how", "to"), Relevance.contentTerms("how to"))
    }

    // --- lexicalScore -------------------------------------------------------------------------

    @Test
    fun fullMatchScoresHigh() {
        val terms = Relevance.contentTerms("mechanical keyboard")
        assertTrue(Relevance.lexicalScore("Best Mechanical Keyboard Guide", "review", terms) >= 0.9)
    }

    @Test
    fun stemmingMatchesPlural() {
        // "keyboard" should match a title that says "keyboards" (light stemming).
        assertTrue(Relevance.lexicalScore("The Best Keyboards", "", listOf("keyboard")) >= 0.9)
    }

    @Test
    fun missingSubjectIsPenalized() {
        val terms = Relevance.contentTerms("ai news today") // subject/head term is "ai"
        val hasSubject = Relevance.lexicalScore("AI News Today", "latest ai coverage", terms)
        val noSubject = Relevance.lexicalScore("Viral News Today", "trending news today", terms)
        assertTrue(noSubject < hasSubject)
        // The head penalty halves a result that never mentions the subject anywhere.
        assertTrue(noSubject <= 0.5 * hasSubject + 0.01)
    }

    @Test
    fun noTermsScoresZero() {
        assertEquals(0.0, Relevance.lexicalScore("anything", "anything", emptyList()), 1e-9)
    }

    @Test
    fun nonAsciiTermsNotMangledByEnglishStemmer() {
        // A Cyrillic term must still match itself (the English stemmer is gated to ASCII).
        assertTrue(Relevance.lexicalScore("Новости сегодня", "", listOf("новости")) >= 0.9)
    }

    // --- languageAffinity (script-relative, multilingual) -------------------------------------

    @Test
    fun sameScriptQueryAndResultIsKept() {
        assertEquals(1.0, Relevance.languageAffinity("ai news", "AI News Today", "latest"), 1e-9)
        assertEquals(1.0, Relevance.languageAffinity("новости ии", "Новости ИИ", "статья"), 1e-9)
    }

    @Test
    fun crossScriptResultIsDemotedEitherDirection() {
        assertEquals(0.4, Relevance.languageAffinity("ai news", "Новости искусственного интеллекта", "сегодня"), 1e-9)
        assertEquals(0.4, Relevance.languageAffinity("新闻 人工智能", "AI News Today", "english article"), 1e-9)
    }

    @Test
    fun letterlessQueryIsNeverPenalized() {
        assertEquals(1.0, Relevance.languageAffinity("2026 / 1080", "Любой результат", "текст"), 1e-9)
    }

    // --- blendedScore (demotion-only) ---------------------------------------------------------

    @Test
    fun blendIsDemotionOnlyCappedAtOne() {
        // A strong and a perfect match both keep full RRF weight (keyword stuffing is not promoted).
        assertEquals(1.0, Relevance.blendedScore(1.0, 1.0), 1e-9)
        assertEquals(1.0, Relevance.blendedScore(1.0, 0.6), 1e-9)
    }

    @Test
    fun blendSinksWeakMatchTowardBase() {
        assertEquals(Relevance.BASE, Relevance.blendedScore(1.0, 0.0), 1e-9)
        assertTrue(Relevance.blendedScore(1.0, 0.0) < Relevance.blendedScore(1.0, 0.3))
        assertTrue(Relevance.blendedScore(1.0, 0.3) < 1.0)
    }

    @Test
    fun affinityMultipliesOnTop() {
        // A perfect lexical match in the wrong script is still demoted by the affinity factor.
        assertEquals(0.4, Relevance.blendedScore(1.0, 1.0, affinity = 0.4), 1e-9)
    }
}
