package org.searchmob.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaIntentTest {
    private data class R(val url: String)

    @Test
    fun detectCategoryMapsEntityTypes() {
        assertEquals(MediaCategory.BOOKS, MediaIntent.detectCategory("1984 dystopian novel by George Orwell"))
        assertEquals(MediaCategory.MUSIC, MediaIntent.detectCategory("American rock band"))
        assertEquals(MediaCategory.MUSIC, MediaIntent.detectCategory("1999 studio album by The Roots"))
        assertEquals(MediaCategory.FILM_TV, MediaIntent.detectCategory("1982 science fiction film"))
        assertEquals(MediaCategory.FILM_TV, MediaIntent.detectCategory("American animated television series"))
        assertEquals(MediaCategory.GAMES, MediaIntent.detectCategory("2011 action-adventure video game"))
    }

    @Test
    fun detectCategoryReturnsNullForNonMedia() {
        assertNull(MediaIntent.detectCategory("American politician"))
        assertNull(MediaIntent.detectCategory("capital city of France"))
        assertNull(MediaIntent.detectCategory(""))
        // "game" alone is not a cue; only "video game" maps to GAMES.
        assertNull(MediaIntent.detectCategory("a traditional board game"))
    }

    @Test
    fun buildActionsRowLeadsWithWikipediaThenFreeOpenPlatforms() {
        val row = MediaIntent.buildActionsRow(MediaCategory.MUSIC, "The Cure", "https://en.wikipedia.org/wiki/The_Cure")
        assertEquals("Wikipedia", row.links[0].label)
        assertEquals("https://en.wikipedia.org/wiki/The_Cure", row.links[0].url)
        assertEquals("Bandcamp", row.links[1].label) // free/open first
        assertTrue(row.links.any { it.url.contains("The+Cure") }) // entity name URL-encoded
        assertEquals(1, row.links.count { it.label == "Wikipedia" })
    }

    @Test
    fun buildActionsRowWithoutWikipediaLeadsWithPlatforms() {
        val row = MediaIntent.buildActionsRow(MediaCategory.GAMES, "Minecraft", null)
        assertEquals("GOG", row.links[0].label)
    }

    @Test
    fun hostInCategoryMatchesSubdomains() {
        assertTrue(MediaIntent.hostInCategory("https://open.spotify.com/track/1", MediaCategory.MUSIC))
        assertTrue(MediaIntent.hostInCategory("https://www.imdb.com/title/tt1", MediaCategory.FILM_TV))
        assertFalse(MediaIntent.hostInCategory("https://example.com/x", MediaCategory.MUSIC))
        assertFalse(MediaIntent.hostInCategory("https://bandcamp.com/x", MediaCategory.GAMES))
    }

    @Test
    fun promoteMediaIsBoundedAndStable() {
        val results = (0 until 5).map { R("https://e$it.example/x") } + R("https://imdb.com/title/x")
        val promoted = MediaIntent.promoteMedia(results, MediaCategory.FILM_TV, urlOf = { it.url }, boost = 3)
        val imdbIndex = promoted.indexOfFirst { it.url.contains("imdb.com") }
        assertTrue("lifted but bounded: $imdbIndex", imdbIndex in (5 - 3)..4)
        // Non-matching results keep their relative order.
        assertEquals(
            results.take(5).map { it.url },
            promoted.filter { !it.url.contains("imdb") }.map { it.url },
        )
    }

    @Test
    fun promoteMediaNoMatchIsIdentity() {
        val results = (0 until 4).map { R("https://e$it.example/x") }
        assertEquals(results, MediaIntent.promoteMedia(results, MediaCategory.BOOKS, urlOf = { it.url }))
    }
}
