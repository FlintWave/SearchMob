package org.searchmob.engine.vertical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.searchmob.engine.sort.SortMode

/** The search-vertical query scoping and per-vertical default sort. Mirrors the desktop verticals. */
class VerticalsTest {
    @Test
    fun web_and_blank_query_are_unchanged() {
        assertEquals("cats", Verticals.transformQuery("cats", Vertical.WEB))
        assertEquals("   ", Verticals.transformQuery("   ", Vertical.NEWS))
    }

    @Test
    fun news_appends_a_site_or_group_keeping_terms_first() {
        val scoped = Verticals.transformQuery("election results", Vertical.NEWS)
        assertTrue(scoped.startsWith("election results ("))
        assertTrue(scoped.contains("site:reuters.com"))
        assertTrue(scoped.contains(" OR "))
        assertTrue(scoped.endsWith(")"))
    }

    @Test
    fun academic_includes_the_edu_tld_filter() {
        val scoped = Verticals.transformQuery("transformer model", Vertical.ACADEMIC)
        assertTrue(scoped.contains("site:arxiv.org"))
        assertTrue(scoped.contains("site:.edu"))
    }

    @Test
    fun forums_scopes_to_discussion_sites() {
        val scoped = Verticals.transformQuery("rust borrow checker", Vertical.FORUMS)
        assertTrue(scoped.contains("site:reddit.com"))
        assertTrue(scoped.contains("site:stackoverflow.com"))
    }

    @Test
    fun from_value_defaults_to_web_and_is_case_insensitive() {
        assertEquals(Vertical.WEB, Vertical.fromValue(null))
        assertEquals(Vertical.WEB, Vertical.fromValue("nonsense"))
        assertEquals(Vertical.NEWS, Vertical.fromValue("NEWS"))
        assertEquals(Vertical.ACADEMIC, Vertical.fromValue(" academic "))
    }

    @Test
    fun default_sort_is_fresh_for_web_and_news_relevance_otherwise() {
        assertEquals(SortMode.FRESH_RELEVANT, Verticals.defaultSort(Vertical.WEB))
        assertEquals(SortMode.FRESH_RELEVANT, Verticals.defaultSort(Vertical.NEWS))
        assertEquals(SortMode.RELEVANCE, Verticals.defaultSort(Vertical.FORUMS))
        assertEquals(SortMode.RELEVANCE, Verticals.defaultSort(Vertical.ACADEMIC))
    }
}
