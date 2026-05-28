package org.searchmob.engine.rank

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GogglesTest {
    @Test
    fun parsesActionsAndIgnoresMetadataAndComments() {
        val text =
            """
            name: My Goggle
            description: test
            ! a comment
            ${'$'}boost,site=dev.to
            ${'$'}discard,site=spam.example
            ${'$'}downrank,site=slow.example
            """.trimIndent()
        assertEquals(
            listOf(
                GoggleRule("dev.to", RankRule.RAISE),
                GoggleRule("spam.example", RankRule.BLOCK),
                GoggleRule("slow.example", RankRule.LOWER),
            ),
            Goggles.parse(text),
        )
    }

    @Test
    fun ignoresMalformedLines() {
        assertEquals(emptyList<GoggleRule>(), Goggles.parse("garbage line\n\$unknown,site=x.com\nsite=onlysite.com"))
    }

    @Test
    fun matchesExactParentAndWildcard() {
        assertTrue(Goggles.matches("example.com", "example.com"))
        assertTrue(Goggles.matches("example.com", "sub.example.com"))
        assertTrue(Goggles.matches("*.example.com", "a.example.com"))
        assertFalse(Goggles.matches("example.com", "notexample.com"))
        assertFalse(Goggles.matches("example.com", "example.org"))
    }
}
