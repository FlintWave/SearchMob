package org.searchmob.engine.adapters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Verifies the correction-parsing wiring against fixture HTML. The live selectors are confirmed on a
 * device; if a selector drifts, the on-device corrector still covers the case.
 */
class SpellingCorrectionParseTest {
    @Test
    fun duckDuckGoReadsTheSpellingSuggestionLink() {
        val html =
            """
            <html><body>
              <a class="js-spelling-suggestion-link">scarlett johansson</a>
              <div class="result web-result"></div>
            </body></html>
            """.trimIndent()
        assertEquals("scarlett johansson", DuckDuckGoAdapter().parseCorrection(html))
    }

    @Test
    fun duckDuckGoReturnsNullWithoutASuggestion() {
        assertNull(DuckDuckGoAdapter().parseCorrection("<html><body></body></html>"))
    }

    @Test
    fun mojeekReadsTheDidYouMeanLink() {
        val html =
            """
            <html><body>
              <p class="did-you-mean">Did you mean: <a href="/search?q=x">scarlett johansson</a></p>
            </body></html>
            """.trimIndent()
        assertEquals("scarlett johansson", MojeekAdapter().parseCorrection(html))
    }

    @Test
    fun mojeekReturnsNullWithoutASuggestion() {
        assertNull(MojeekAdapter().parseCorrection("<html><body></body></html>"))
    }
}
