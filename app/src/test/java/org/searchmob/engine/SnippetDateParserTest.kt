package org.searchmob.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.searchmob.engine.date.DatePrecision
import org.searchmob.engine.date.SnippetDateParser
import java.time.LocalDate
import java.time.ZoneOffset

class SnippetDateParserTest {
    private val now = ms(2026, 5, 29)
    private val dayMs = 86_400_000L

    private fun ms(
        y: Int,
        m: Int,
        d: Int,
    ): Long = LocalDate.of(y, m, d).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test
    fun relativeDaysAgo() {
        val p = SnippetDateParser.parse("3 days ago - a review", now)!!
        assertEquals(DatePrecision.RELATIVE, p.precision)
        assertEquals(now - 3 * dayMs, p.epochMillis)
    }

    @Test
    fun monthDayYear() {
        val p = SnippetDateParser.parse("May 28, 2026 - The Matrix 5 release date", now)!!
        assertEquals(ms(2026, 5, 28), p.epochMillis)
        assertEquals(DatePrecision.DAY, p.precision)
    }

    @Test
    fun isoAndDayMonthYear() {
        assertEquals(ms(2026, 5, 28), SnippetDateParser.parse("2026-05-28 notes", now)!!.epochMillis)
        assertEquals(ms(2026, 5, 28), SnippetDateParser.parse("28 May 2026 — profile", now)!!.epochMillis)
    }

    @Test
    fun nearFutureKept() {
        assertEquals(ms(2026, 5, 31), SnippetDateParser.parse("May 31, 2026 - opening weekend", now)!!.epochMillis)
    }

    @Test
    fun farFutureRejected() {
        assertNull(SnippetDateParser.parse("Copyright 2099 Example Inc", now))
    }

    @Test
    fun bareYearIsWeak() {
        assertTrue(SnippetDateParser.parse("A history of computing in 2019", now)!!.weak)
    }

    @Test
    fun noDate() {
        assertNull(SnippetDateParser.parse("Mount Everest is Earth's highest mountain", now))
        assertNull(SnippetDateParser.parse("", now))
    }

    @Test
    fun numericSlashDatesDefaultToMonthDayYear() {
        assertEquals(ms(2024, 5, 3), SnippetDateParser.parse("05/03/2024 - report", now)!!.epochMillis)
    }

    @Test
    fun numericSlashDayFirstRecognizedWhenDayExceedsTwelve() {
        // "31/12/2024" cannot be month 31; it must parse as 31 December, not be silently lost.
        assertEquals(ms(2024, 12, 31), SnippetDateParser.parse("31/12/2024 - jahresrückblick", now)!!.epochMillis)
    }
}
