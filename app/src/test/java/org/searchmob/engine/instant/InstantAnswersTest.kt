package org.searchmob.engine.instant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class InstantAnswersTest {
    // --- Calculator ---

    @Test
    fun evaluatesBasicArithmetic() {
        val answer = InstantAnswers.answer("2+2")
        assertNotNull(answer)
        assertEquals("4", answer!!.result)
        assertEquals(InstantAnswer.Kind.CALCULATOR, answer.kind)
    }

    @Test
    fun respectsPrecedenceAndParentheses() {
        assertEquals("14", InstantAnswers.answer("2 + 3 * 4")!!.result)
        assertEquals("20", InstantAnswers.answer("(2 + 3) * 4")!!.result)
        assertEquals("512", InstantAnswers.answer("2 ^ 3 ^ 2")!!.result) // right-associative
        assertEquals("512", InstantAnswers.answer("2 ** 3 ** 2")!!.result)
    }

    @Test
    fun evaluatesFunctionsAndConstants() {
        assertEquals("3", InstantAnswers.answer("sqrt(9)")!!.result)
        assertEquals("2", InstantAnswers.answer("log(100)")!!.result)
        val pi = InstantAnswers.answer("pi * 2")!!.result
        assertEquals("6.283185307", pi)
    }

    @Test
    fun evaluatesUnicodeOperatorsAndWhatIsPrefix() {
        assertEquals("42", InstantAnswers.answer("6 × 7")!!.result)
        assertEquals("5", InstantAnswers.answer("10 ÷ 2")!!.result)
        assertEquals("4", InstantAnswers.answer("what is 2+2")!!.result)
        assertEquals("4", InstantAnswers.answer("2+2=")!!.result)
    }

    @Test
    fun rejectsNonMathQueries() {
        assertNull(InstantAnswers.answer("plain words"))
        assertNull(InstantAnswers.answer("2024")) // bare number: no operator
        assertNull(InstantAnswers.answer("windows 11"))
        assertNull(InstantAnswers.answer("covid-19 symptoms"))
        assertNull(InstantAnswers.answer(""))
    }

    @Test
    fun rejectsDatesPhonesAndYearRanges() {
        assertNull(InstantAnswers.answer("2020-2021"))
        assertNull(InstantAnswers.answer("555-1234"))
        assertNull(InstantAnswers.answer("2024-01-15"))
        assertNull(InstantAnswers.answer("01/02/2024"))
        // A short subtraction is still math.
        assertEquals("2", InstantAnswers.answer("5-3")!!.result)
    }

    @Test
    fun rejectsDivisionByZeroAndMalformedInput() {
        assertNull(InstantAnswers.answer("1/0"))
        assertNull(InstantAnswers.answer("2 +"))
        assertNull(InstantAnswers.answer("(2+3"))
        assertNull(InstantAnswers.answer("2 + foo"))
    }

    @Test
    fun formatsResultsCleanly() {
        assertEquals("0.5", InstantAnswers.answer("1/2")!!.result)
        assertEquals("3333333.333", InstantAnswers.answer("10000000/3")!!.result)
        assertEquals("-6", InstantAnswers.answer("-2*3")!!.result)
        assertEquals("1000000", InstantAnswers.answer("1,000,000 / 1")!!.result)
    }

    // --- Percentage ---

    @Test
    fun computesPercentOf() {
        val answer = InstantAnswers.answer("15% of 80")
        assertNotNull(answer)
        assertEquals("12", answer!!.result)
        assertEquals(InstantAnswer.Kind.PERCENTAGE, answer.kind)
        assertEquals("30", InstantAnswers.answer("what is 25% of 120?")!!.result)
    }

    // --- Base conversion ---

    @Test
    fun convertsBetweenBases() {
        assertEquals("255", InstantAnswers.answer("0xff in decimal")!!.result)
        assertEquals("0xff", InstantAnswers.answer("255 to hex")!!.result)
        assertEquals("0b1111", InstantAnswers.answer("15 in binary")!!.result)
        assertEquals("11", InstantAnswers.answer("0b1011 in decimal")!!.result)
        assertEquals("0o777", InstantAnswers.answer("511 in octal")!!.result)
        assertEquals(InstantAnswer.Kind.BASE_CONVERSION, InstantAnswers.answer("255 to hex")!!.kind)
    }

    @Test
    fun decimalToDecimalFallsThrough() {
        assertNull(InstantAnswers.answer("255 to decimal"))
    }

    // --- Unit conversion (via the front door) ---

    @Test
    fun convertsUnitsThroughFrontDoor() {
        val answer = InstantAnswers.answer("10 km to miles")
        assertNotNull(answer)
        assertEquals(InstantAnswer.Kind.UNIT_CONVERSION, answer!!.kind)
        assertEquals("6.213711922 miles", answer.result)
    }
}
