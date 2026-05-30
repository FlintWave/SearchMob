package org.searchmob.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.searchmob.engine.sort.QdfHeuristic
import org.searchmob.engine.sort.ResultSorter
import org.searchmob.engine.sort.SortMode

class ResultSorterTest {
    private val now = 1_900_000_000_000L
    private val day = 86_400_000L

    private data class R(val name: String, val published: Long?)

    private fun sort(
        items: List<R>,
        mode: SortMode,
        query: String = "q",
    ) = ResultSorter.sort(items, mode, query, now) { it.published }.map { it.name }

    @Test
    fun fromValueDefaultsToFresh() {
        assertEquals(SortMode.FRESH_RELEVANT, SortMode.fromValue(null))
        assertEquals(SortMode.DATE, SortMode.fromValue("date"))
        assertEquals(SortMode.RELEVANCE, SortMode.fromValue("relevance"))
        assertEquals(SortMode.FRESH_RELEVANT, SortMode.fromValue("garbage"))
    }

    @Test
    fun relevanceIsIdentity() {
        val items = listOf(R("a", null), R("b", now), R("c", null))
        assertEquals(listOf("a", "b", "c"), sort(items, SortMode.RELEVANCE))
    }

    @Test
    fun dateModeNewestFirstThenUndated() {
        val items = listOf(R("old", now - 100 * day), R("undated", null), R("new", now - 2 * day))
        assertEquals(listOf("new", "old", "undated"), sort(items, SortMode.DATE))
    }

    @Test
    fun freshBlendAllUndatedIsIdentity() {
        val items = listOf(R("a", null), R("b", null), R("c", null))
        assertEquals(listOf("a", "b", "c"), sort(items, SortMode.FRESH_RELEVANT, "how to tie a tie"))
    }

    @Test
    fun freshBlendPromotesRecentResult() {
        val items = listOf(R("undated_top", null), R("old", now - 300 * day), R("fresh", now - day))
        assertEquals("fresh", sort(items, SortMode.FRESH_RELEVANT, "the matrix 5 release date").first())
    }

    @Test
    fun qdfBoostsTimeSensitiveQueries() {
        val base = QdfHeuristic.weightFor("best laptops", now)
        assertTrue(QdfHeuristic.weightFor("avatar 3 release date", now) > base)
        assertTrue(QdfHeuristic.weightFor("lakers score", now) > base)
    }
}
