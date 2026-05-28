package org.searchmob.engine.correct

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class OnDeviceSpellCorrectorTest {
    private val dictionary =
        Dictionary.build(
            mapOf(
                "the" to 1_000_000,
                "program" to 8_000,
                "kotlin" to 5_000,
                "scarlett" to 20_000,
                "johansson" to 600,
                "leonardo" to 20_000,
                "dicaprio" to 700,
            ),
        )
    private val corrector = OnDeviceSpellCorrector(dictionary = { dictionary })

    @Test
    fun correctsASingleEditTypo() {
        val correction = corrector.suggest("kotln")
        assertNotNull(correction)
        assertEquals("kotlin", correction!!.corrected)
    }

    @Test
    fun correctsAMultiWordNameByEditAndPhonetics() {
        val correction = corrector.suggest("scarlet johanson")
        assertNotNull(correction)
        assertEquals("scarlett johansson", correction!!.corrected)
    }

    @Test
    fun leavesKnownWordsAlone() {
        assertNull(corrector.suggest("kotlin"))
        assertNull(corrector.suggest("the program"))
    }

    @Test
    fun blankInputReturnsNull() {
        assertNull(corrector.suggest(""))
        assertNull(corrector.suggest("   "))
    }

    @Test
    fun shortAndNumericTokensAreNotCorrected() {
        // "the" is known, "12" is numeric: nothing changes, so no suggestion.
        assertNull(corrector.suggest("the 12"))
    }

    @Test
    fun returnsNullUntilDictionaryIsLoaded() {
        val notReady = OnDeviceSpellCorrector(dictionary = { null })
        assertNull(notReady.suggest("kotln"))
    }
}
