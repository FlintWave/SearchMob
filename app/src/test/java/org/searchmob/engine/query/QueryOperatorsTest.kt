package org.searchmob.engine.query

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class QueryOperatorsTest {
    private fun epochMillisOf(
        year: Int,
        month: Int = 1,
        day: Int = 1,
    ): Long = LocalDate.of(year, month, day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    // --- plain terms / phrases -------------------------------------------------------------------

    @Test
    fun plainTermsPassThroughUnchanged() {
        val p = QueryOperators.parse("wireless mouse")
        assertEquals("wireless mouse", p.cleanText)
        assertEquals("wireless mouse", p.engineQuery)
        assertFalse(p.hasFilters)
    }

    @Test
    fun leadingPlusIsNotSpecial() {
        // The server's ScopeToken pass runs before this parser, so `+word` and `c++` are ordinary text.
        val p = QueryOperators.parse("+word c++")
        assertEquals("+word c++", p.cleanText)
        assertEquals("+word c++", p.engineQuery)
    }

    @Test
    fun quotedPhraseFeedsPhrasesCleanTextAndEngineQuery() {
        val p = QueryOperators.parse("mouse \"gaming grade\" wireless")
        assertEquals(listOf("gaming grade"), p.phrases)
        assertEquals("mouse gaming grade wireless", p.cleanText)
        assertEquals("mouse \"gaming grade\" wireless", p.engineQuery)
    }

    @Test
    fun negatedPhraseGoesToExcludedPhrasesOnlyAndIsHiddenFromCleanText() {
        val p = QueryOperators.parse("mouse -\"black friday\"")
        assertEquals(listOf("black friday"), p.excludedPhrases)
        assertTrue(p.phrases.isEmpty())
        assertEquals("mouse", p.cleanText)
        assertEquals("mouse -\"black friday\"", p.engineQuery)
    }

    @Test
    fun negatedTermGoesToExcludedTermsAndIsHiddenFromCleanText() {
        val p = QueryOperators.parse("mouse -bluetooth")
        assertEquals(listOf("bluetooth"), p.excludedTerms)
        assertEquals("mouse", p.cleanText)
        assertEquals("mouse -bluetooth", p.engineQuery)
    }

    @Test
    fun bareDashIsAPlainTerm() {
        val p = QueryOperators.parse("mouse -")
        assertTrue(p.excludedTerms.isEmpty())
        assertEquals("mouse -", p.cleanText)
        assertEquals("mouse -", p.engineQuery)
    }

    @Test
    fun unrecognizedColonTokenIsAPlainTerm() {
        // "10:30" and unknown pseudo-operators must not be mistaken for a recognized op.
        val p = QueryOperators.parse("meet at 10:30 badop:xyz")
        assertEquals("meet at 10:30 badop:xyz", p.cleanText)
        assertEquals("meet at 10:30 badop:xyz", p.engineQuery)
        assertFalse(p.hasFilters)
    }

    // --- site: -------------------------------------------------------------------------------------

    @Test
    fun siteOperatorParsesAndNormalizes() {
        val p = QueryOperators.parse("foo site:*.Example.COM.")
        assertEquals(listOf("example.com"), p.includeSites)
        assertEquals("foo site:example.com", p.engineQuery)
        // site: is a pure operator; it never appears in cleanText.
        assertEquals("foo", p.cleanText)
        assertTrue(p.hasFilters)
    }

    @Test
    fun negatedSiteOperatorParses() {
        val p = QueryOperators.parse("foo -site:aliexpress.com")
        assertEquals(listOf("aliexpress.com"), p.excludeSites)
        assertEquals("foo -site:aliexpress.com", p.engineQuery)
    }

    @Test
    fun emptySiteOperatorIsDroppedEntirely() {
        val p = QueryOperators.parse("foo site: bar")
        assertTrue(p.includeSites.isEmpty())
        assertEquals("foo bar", p.cleanText)
        assertEquals("foo bar", p.engineQuery)
    }

    // --- intitle: / inurl: --------------------------------------------------------------------------

    @Test
    fun intitleAndInurlBecomeBareRecallHintsAndLocalFilters() {
        val p = QueryOperators.parse("intitle:review inurl:shop")
        assertEquals(listOf("review"), p.inTitle)
        assertEquals(listOf("shop"), p.inUrl)
        assertEquals("review shop", p.cleanText)
        assertEquals("review shop", p.engineQuery)
    }

    @Test
    fun negatedIntitleAndInurlAreLocalOnlyAndDroppedFromEngineQuery() {
        val p = QueryOperators.parse("mouse -intitle:sponsored -inurl:ad")
        assertEquals(listOf("sponsored"), p.notInTitle)
        assertEquals(listOf("ad"), p.notInUrl)
        assertEquals("mouse", p.cleanText)
        assertEquals("mouse", p.engineQuery)
    }

    @Test
    fun quotedIntitleValueIsOneEntryWithSpaces() {
        val p = QueryOperators.parse("intitle:\"foo bar\"")
        assertEquals(listOf("foo bar"), p.inTitle)
        assertEquals("foo bar", p.cleanText)
        assertEquals("foo bar", p.engineQuery)
    }

    // --- filetype: / ext: ----------------------------------------------------------------------------

    @Test
    fun filetypeOperatorParsesAndStripsLeadingDot() {
        val p = QueryOperators.parse("manual filetype:.PDF")
        assertEquals(listOf("pdf"), p.fileTypes)
        assertEquals("manual filetype:pdf", p.engineQuery)
        assertEquals("manual", p.cleanText)
    }

    @Test
    fun extIsAnAliasForFiletype() {
        val p = QueryOperators.parse("manual ext:DOC")
        assertEquals(listOf("doc"), p.fileTypes)
        assertEquals("manual filetype:doc", p.engineQuery)
    }

    @Test
    fun negatedFiletypeHasNoDefinedMeaningAndFallsBackToExcludedTerm() {
        val p = QueryOperators.parse("-filetype:exe")
        assertTrue(p.fileTypes.isEmpty())
        assertEquals(listOf("filetype:exe"), p.excludedTerms)
        assertEquals("-filetype:exe", p.engineQuery)
    }

    // --- before: / after: -----------------------------------------------------------------------------

    @Test
    fun dateFormsParseToUtcStartOfPeriod() {
        assertEquals(epochMillisOf(2023), QueryOperators.parse("after:2023").afterMillis)
        assertEquals(epochMillisOf(2023, 6), QueryOperators.parse("after:2023-06").afterMillis)
        assertEquals(epochMillisOf(2023, 6, 15), QueryOperators.parse("after:2023-06-15").afterMillis)
        assertEquals(epochMillisOf(2023, 6, 15), QueryOperators.parse("after:2023/06/15").afterMillis)
        assertEquals(epochMillisOf(2024), QueryOperators.parse("before:2024").beforeMillis)
    }

    @Test
    fun singleDigitMonthAndDayAreAccepted() {
        // Rejecting `after:2024-3-1` would keep the whole token as literal upstream query text.
        assertEquals(epochMillisOf(2024, 3, 1), QueryOperators.parse("after:2024-3-1").afterMillis)
        assertEquals(epochMillisOf(2024, 3), QueryOperators.parse("after:2024-3").afterMillis)
        assertEquals(epochMillisOf(2024, 3, 1), QueryOperators.parse("before:2024/3/1").beforeMillis)
    }

    @Test
    fun dateOperatorsAreDroppedFromCleanTextAndEngineQuery() {
        val p = QueryOperators.parse("news after:2023 before:2024")
        assertEquals("news", p.cleanText)
        assertEquals("news", p.engineQuery)
    }

    @Test
    fun repeatedDateOperatorLastOneWins() {
        val p = QueryOperators.parse("after:2020 after:2023")
        assertEquals(epochMillisOf(2023), p.afterMillis)
    }

    @Test
    fun invalidDateShapeIsKeptAsPlainTermNeverDroppedSilently() {
        val p = QueryOperators.parse("before:notadate")
        assertNull(p.beforeMillis)
        assertEquals("before:notadate", p.cleanText)
        assertEquals("before:notadate", p.engineQuery)
    }

    @Test
    fun impossibleCalendarDateIsKeptAsPlainTerm() {
        val p = QueryOperators.parse("before:2023-13")
        assertNull(p.beforeMillis)
        assertEquals("before:2023-13", p.cleanText)
        assertEquals("before:2023-13", p.engineQuery)
    }

    @Test
    fun negatedDateOperatorHasNoDefinedMeaningAndFallsBackToExcludedTerm() {
        val p = QueryOperators.parse("-after:2023")
        assertNull(p.afterMillis)
        assertEquals(listOf("after:2023"), p.excludedTerms)
        assertEquals("-after:2023", p.engineQuery)
    }

    // --- OR / | ----------------------------------------------------------------------------------------

    @Test
    fun orAndPipeAreKeptInEngineQueryButDroppedFromCleanText() {
        val p = QueryOperators.parse("cats OR dogs | birds")
        assertEquals("cats dogs birds", p.cleanText)
        assertEquals("cats OR dogs | birds", p.engineQuery)
    }

    @Test
    fun lowercaseOrIsAPlainTermNotTheOperator() {
        // Only the exact uppercase token "OR" is the operator.
        val p = QueryOperators.parse("cats or dogs")
        assertEquals("cats or dogs", p.cleanText)
        assertEquals("cats or dogs", p.engineQuery)
    }

    // --- tokenizer robustness ---------------------------------------------------------------------------

    @Test
    fun unterminatedQuoteRunsToEndOfStringWithoutCrashing() {
        val p = QueryOperators.parse("foo \"bar baz qux")
        assertEquals(listOf("bar baz qux"), p.phrases)
        assertEquals("foo bar baz qux", p.cleanText)
        assertEquals("foo \"bar baz qux\"", p.engineQuery)
    }

    @Test
    fun unterminatedQuoteOnANegatedOperatorValueDoesNotCrash() {
        val p = QueryOperators.parse("intitle:\"unterminated value")
        assertEquals(listOf("unterminated value"), p.inTitle)
    }

    // --- engineQuery order preservation & composite --------------------------------------------------

    @Test
    fun engineQueryPreservesOriginalTokenOrderAcrossMixedOperators() {
        val p =
            QueryOperators.parse(
                "wireless mouse \"gaming grade\" -bluetooth site:amazon.com -site:aliexpress.com " +
                    "intitle:review -intitle:sponsored filetype:pdf before:2024 badtoken:xyz OR keyboards",
            )
        assertEquals(
            "wireless mouse \"gaming grade\" -bluetooth site:amazon.com -site:aliexpress.com " +
                "review filetype:pdf badtoken:xyz OR keyboards",
            p.engineQuery,
        )
        assertEquals("wireless mouse gaming grade review badtoken:xyz keyboards", p.cleanText)
        assertEquals(listOf("bluetooth"), p.excludedTerms)
        assertEquals(listOf("amazon.com"), p.includeSites)
        assertEquals(listOf("aliexpress.com"), p.excludeSites)
        assertEquals(listOf("review"), p.inTitle)
        assertEquals(listOf("sponsored"), p.notInTitle)
        assertEquals(listOf("pdf"), p.fileTypes)
        assertEquals(epochMillisOf(2024), p.beforeMillis)
    }

    // --- matches(): site: / -site: ----------------------------------------------------------------------

    @Test
    fun matchesEnforcesSiteAsASuffixOnTheHost() {
        val p = QueryOperators.parse("foo site:example.com")
        assertTrue(p.matches("T", "https://docs.example.com/page", "", null))
        assertTrue(p.matches("T", "https://example.com/page", "", null))
        assertFalse(p.matches("T", "https://notexample.com/page", "", null))
    }

    @Test
    fun matchesSiteSupportsBareTldEntries() {
        val p = QueryOperators.parse("research site:.edu")
        assertTrue(p.matches("T", "https://mit.edu/x", "", null))
        assertFalse(p.matches("T", "https://mit.com/x", "", null))
    }

    @Test
    fun matchesExcludeSiteRejectsMatchingHosts() {
        val p = QueryOperators.parse("foo -site:pinterest.com")
        assertFalse(p.matches("T", "https://pinterest.com/x", "", null))
        assertFalse(p.matches("T", "https://sub.pinterest.com/x", "", null))
        assertTrue(p.matches("T", "https://other.com/x", "", null))
    }

    @Test
    fun matchesIncludeSiteRejectsAnUnparsableHost() {
        val p = QueryOperators.parse("foo site:example.com")
        assertFalse(p.matches("T", "not a url", "", null))
    }

    // --- matches(): intitle: / inurl: ----------------------------------------------------------------

    @Test
    fun matchesEnforcesIntitleAndNotInTitle() {
        val p = QueryOperators.parse("intitle:review -intitle:sponsored")
        assertTrue(p.matches("Full Review of X", "https://x.com", "", null))
        assertFalse(p.matches("Just a post", "https://x.com", "", null))
        assertFalse(p.matches("Sponsored Review", "https://x.com", "", null))
    }

    @Test
    fun matchesEnforcesInurlAndNotInUrl() {
        val p = QueryOperators.parse("inurl:shop -inurl:ad")
        assertTrue(p.matches("T", "https://example.com/shop/item", "", null))
        assertFalse(p.matches("T", "https://example.com/other", "", null))
        assertFalse(p.matches("T", "https://example.com/shop/ad/item", "", null))
    }

    // --- matches(): filetype: -------------------------------------------------------------------------

    @Test
    fun matchesExtractsExtensionIgnoringQueryString() {
        val p = QueryOperators.parse("filetype:pdf")
        assertTrue(p.matches("T", "https://x.y/file.pdf?dl=1", "", null))
        assertFalse(p.matches("T", "https://x.y/file.docx?dl=1", "", null))
    }

    @Test
    fun matchesFiletypeRejectsAUrlWithNoExtension() {
        val p = QueryOperators.parse("filetype:pdf")
        assertFalse(p.matches("T", "https://example.com/", "", null))
        // A dot in the host (its TLD) must not be misread as a file extension.
        assertFalse(p.matches("T", "https://example.com", "", null))
    }

    // --- matches(): before: / after: -------------------------------------------------------------------

    @Test
    fun matchesEnforcesDateWindowInclusiveLowerExclusiveUpper() {
        val p = QueryOperators.parse("after:2023 before:2024")
        val lower = epochMillisOf(2023)
        val upper = epochMillisOf(2024)
        assertTrue(p.matches("T", "https://x.com", "", lower)) // inclusive lower bound
        assertTrue(p.matches("T", "https://x.com", "", upper - 1))
        assertFalse(p.matches("T", "https://x.com", "", upper)) // exclusive upper bound
        assertFalse(p.matches("T", "https://x.com", "", lower - 1))
    }

    @Test
    fun matchesExcludesAnUndatedResultWhenADateBoundIsSet() {
        val p = QueryOperators.parse("after:2023")
        assertFalse(p.matches("T", "https://x.com", "", null))
    }

    // --- matches(): -term / -"phrase" ------------------------------------------------------------------

    @Test
    fun matchesEnforcesExcludedTermAsAWholeWord() {
        val p = QueryOperators.parse("-cat")
        assertTrue(p.matches("Category page", "https://x.com", "listing categories", null))
        assertFalse(p.matches("I love cat food", "https://x.com", "", null))
        // The host is also checked (excluding a term that only appears as the domain name).
        assertFalse(p.matches("T", "https://cat.example.com", "", null))
    }

    @Test
    fun matchesEnforcesExcludedPhraseAsASubstring() {
        val p = QueryOperators.parse("-\"black friday\"")
        assertFalse(p.matches("Black Friday Deals", "https://x.com", "", null))
        assertTrue(p.matches("Regular Deals", "https://x.com", "", null))
    }

    // --- matches(): positive terms/phrases are NOT locally enforced --------------------------------------

    @Test
    fun matchesDoesNotEnforcePositiveTermsOrPhrases() {
        val p = QueryOperators.parse("mouse \"gaming grade\"")
        assertFalse(p.hasFilters)
        assertTrue(p.matches("Totally unrelated title", "https://x.com", "no overlap here either", null))
    }

    // --- hasFilters --------------------------------------------------------------------------------------

    @Test
    fun hasFiltersIsFalseForPlainTermsPhrasesAndOr() {
        assertFalse(QueryOperators.parse("just plain words").hasFilters)
        assertFalse(QueryOperators.parse("\"a phrase\" OR term").hasFilters)
    }

    @Test
    fun hasFiltersIsTrueForEveryLocallyEnforcedOperator() {
        assertTrue(QueryOperators.parse("-excluded").hasFilters)
        assertTrue(QueryOperators.parse("-\"excluded phrase\"").hasFilters)
        assertTrue(QueryOperators.parse("site:example.com").hasFilters)
        assertTrue(QueryOperators.parse("-site:example.com").hasFilters)
        assertTrue(QueryOperators.parse("intitle:x").hasFilters)
        assertTrue(QueryOperators.parse("-intitle:x").hasFilters)
        assertTrue(QueryOperators.parse("inurl:x").hasFilters)
        assertTrue(QueryOperators.parse("-inurl:x").hasFilters)
        assertTrue(QueryOperators.parse("filetype:pdf").hasFilters)
        assertTrue(QueryOperators.parse("after:2023").hasFilters)
        assertTrue(QueryOperators.parse("before:2023").hasFilters)
    }

    // --- degenerate quotes -------------------------------------------------------------------------------

    @Test
    fun blankPhraseIsDroppedInsteadOfExcludingEverything() {
        // A stray `-"` used to add an empty excluded phrase, and `contains("")` is always true, so a
        // single stray character filtered out every result. A stray `"` likewise polluted cleanText.
        val negated = QueryOperators.parse("mouse -\"")
        assertTrue(negated.excludedPhrases.isEmpty())
        assertEquals("mouse", negated.cleanText)
        assertTrue(negated.matches("Any title", "https://example.com/a", "any snippet", null))
        val positive = QueryOperators.parse("mouse \"")
        assertTrue(positive.phrases.isEmpty())
        assertEquals("mouse", positive.cleanText)
    }
}
