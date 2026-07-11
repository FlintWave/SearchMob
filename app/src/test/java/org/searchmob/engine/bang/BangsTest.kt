package org.searchmob.engine.bang

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BangsTest {
    @Test
    fun resolvesLeadingBang() {
        val redirect = Bangs.resolve("!w privacy")
        assertNotNull(redirect)
        assertEquals("https://en.wikipedia.org/wiki/Special:Search?search=privacy", redirect!!.url)
        assertEquals("privacy", redirect.terms)
        assertEquals("w", redirect.bang.tag)
    }

    @Test
    fun resolvesTrailingBang() {
        val redirect = Bangs.resolve("kotlin coroutines !gh")
        assertNotNull(redirect)
        assertEquals("https://github.com/search?q=kotlin%20coroutines", redirect!!.url)
    }

    @Test
    fun encodesTermsSafely() {
        val redirect = Bangs.resolve("!ddg a&b=c d")
        assertNotNull(redirect)
        assertEquals("https://duckduckgo.com/?q=a%26b%3Dc%20d", redirect!!.url)
    }

    @Test
    fun usesPercentTwentyInPathTemplates() {
        val redirect = Bangs.resolve("!dict prickly pear")
        assertNotNull(redirect)
        assertEquals("https://www.merriam-webster.com/dictionary/prickly%20pear", redirect!!.url)
    }

    @Test
    fun bareBangGoesHome() {
        assertEquals("https://en.wikipedia.org", Bangs.resolve("!w")!!.url)
    }

    @Test
    fun caseInsensitiveAndAliases() {
        assertEquals("w", Bangs.resolve("!W kotlin")!!.bang.tag)
        assertEquals("w", Bangs.resolve("!wikipedia kotlin")!!.bang.tag)
        assertEquals("yt", Bangs.resolve("!YouTube lo-fi")!!.bang.tag)
    }

    @Test
    fun unknownTagsAndMidQueryBangsFallThrough() {
        assertNull(Bangs.resolve("!important css"))
        assertNull(Bangs.resolve("css !important rules")) // mid-query token is not a bang position
        assertNull(Bangs.resolve("hello world"))
        assertNull(Bangs.resolve("!"))
        assertNull(Bangs.resolve(""))
        assertNull(Bangs.resolve("wow!"))
    }

    @Test
    fun everyBangTemplateHasPlaceholderAndHttpsHome() {
        Bangs.ALL.forEach { bang ->
            assertEquals("searchUrl of !${bang.tag} must carry {q}", true, bang.searchUrl.contains("{q}"))
            assertEquals("homeUrl of !${bang.tag} must be https", true, bang.homeUrl.startsWith("https://"))
        }
    }
}
