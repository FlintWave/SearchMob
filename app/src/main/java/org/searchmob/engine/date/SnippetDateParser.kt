package org.searchmob.engine.date

import java.time.LocalDate
import java.time.ZoneOffset

/** How precise/confident a parsed date is; the sorter discounts vaguer dates. */
enum class DatePrecision { EXACT, DAY, MONTH, RELATIVE }

data class ParsedDate(
    val epochMillis: Long,
    val precision: DatePrecision,
    val weak: Boolean = false,
)

/**
 * Best-effort publication-date extraction from a result's snippet/title. General web engines rarely
 * return a structured date, so the workhorse is parsing the leading date engines prefix onto
 * snippets ("3 days ago - ...", "May 28, 2026 - ...", ISO, a bare year). Fail-soft: anything
 * unrecognized returns null. Mirrors the desktop `snippet_date.py`.
 *
 * Guards: only a date at the start (or right before a separator) is trusted; absolute dates far in
 * the future are rejected as template/footer junk while near-future dates are kept (an upcoming
 * release is the point); a bare year on its own is `weak` (a coarse hint, never a real date).
 */
object SnippetDateParser {
    private const val DAY_MS = 86_400_000L
    private const val MAX_FUTURE_MS = 400 * DAY_MS
    private const val LEADING_WINDOW = 24

    private val UNIT_MS =
        mapOf(
            "second" to 1_000L,
            "minute" to 60_000L,
            "hour" to 3_600_000L,
            "day" to DAY_MS,
            "week" to 7 * DAY_MS,
            "month" to 2_629_800_000L,
            "year" to 31_557_600_000L,
        )
    private val MONTHS =
        listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
            .withIndex()
            .associate { (i, m) -> m to i + 1 }

    private val RELATIVE =
        Regex("(\\d{1,3})\\s+(second|minute|hour|day|week|month|year)s?\\s+ago", RegexOption.IGNORE_CASE)
    private val YESTERDAY = Regex("\\byesterday\\b", RegexOption.IGNORE_CASE)
    private const val MON = "(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\\.?"
    private val MDY = Regex("\\b$MON\\s+(\\d{1,2}),?\\s+(\\d{4})\\b", RegexOption.IGNORE_CASE)
    private val DMY = Regex("\\b(\\d{1,2})\\s+$MON\\s+(\\d{4})\\b", RegexOption.IGNORE_CASE)
    private val MY = Regex("\\b$MON\\s+(\\d{4})\\b", RegexOption.IGNORE_CASE)
    private val ISO = Regex("\\b(\\d{4})-(\\d{2})-(\\d{2})\\b")
    private val NUMERIC = Regex("\\b(\\d{1,2})/(\\d{1,2})/(\\d{4})\\b")
    private val YEAR = Regex("\\b(20\\d{2})\\b")
    private val SEPARATORS = setOf('-', '—', '–', '·', '.', '|')

    fun parse(
        text: String,
        nowMillis: Long,
    ): ParsedDate? {
        if (text.isEmpty()) return null
        val s = text.trim()

        RELATIVE.find(s)?.let { m ->
            if (m.range.first <= LEADING_WINDOW) {
                val amount = m.groupValues[1].toLong()
                val unit = UNIT_MS.getValue(m.groupValues[2].lowercase())
                return ParsedDate(nowMillis - amount * unit, DatePrecision.RELATIVE)
            }
        }
        YESTERDAY.find(s)?.let { m ->
            if (m.range.first <= LEADING_WINDOW) return ParsedDate(nowMillis - DAY_MS, DatePrecision.RELATIVE)
        }

        for ((regex, order) in listOf(MDY to "mdy", DMY to "dmy", ISO to "iso", NUMERIC to "num")) {
            val m = regex.find(s) ?: continue
            if (!isLeading(m, s)) continue
            val (y, mo, d) =
                when (order) {
                    "mdy" ->
                        Triple(
                            m.groupValues[3].toInt(),
                            MONTHS[m.groupValues[1].lowercase().take(3)] ?: 0,
                            m.groupValues[2].toInt(),
                        )
                    "dmy" ->
                        Triple(
                            m.groupValues[3].toInt(),
                            MONTHS[m.groupValues[2].lowercase().take(3)] ?: 0,
                            m.groupValues[1].toInt(),
                        )
                    "iso" -> Triple(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
                    else -> Triple(m.groupValues[3].toInt(), m.groupValues[1].toInt(), m.groupValues[2].toInt())
                }
            if (mo == 0) continue
            val ms = ymdToMs(y, mo, d) ?: continue
            if (ms <= nowMillis + MAX_FUTURE_MS) return ParsedDate(ms, DatePrecision.DAY)
        }

        MY.find(s)?.let { m ->
            if (isLeading(m, s)) {
                val mo = MONTHS[m.groupValues[1].lowercase().take(3)] ?: 0
                val ms = if (mo != 0) ymdToMs(m.groupValues[2].toInt(), mo, 15) else null
                if (ms != null && ms <= nowMillis + MAX_FUTURE_MS) return ParsedDate(ms, DatePrecision.MONTH)
            }
        }

        YEAR.find(s)?.let { m ->
            val ms = ymdToMs(m.groupValues[1].toInt(), 7, 1)
            if (ms != null && ms <= nowMillis + MAX_FUTURE_MS) {
                return ParsedDate(ms, DatePrecision.MONTH, weak = true)
            }
        }
        return null
    }

    private fun isLeading(
        m: MatchResult,
        text: String,
    ): Boolean {
        if (m.range.first <= LEADING_WINDOW) return true
        val after = text.substring(m.range.last + 1).trimStart()
        return after.firstOrNull() in SEPARATORS
    }

    private fun ymdToMs(
        year: Int,
        month: Int,
        day: Int,
    ): Long? =
        runCatching { LocalDate.of(year, month, day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
            .getOrNull()
}
