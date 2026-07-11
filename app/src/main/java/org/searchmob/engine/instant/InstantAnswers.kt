package org.searchmob.engine.instant

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.abs

/** Number formatting shared by the instant-answer producers: plain, trimmed, locale-independent. */
object Numbers {
    /**
     * Format [value] for display: integers without a decimal point, everything else trimmed to at
     * most 10 significant digits with trailing zeros dropped, never scientific notation for the
     * magnitudes a search box realistically produces.
     */
    fun format(value: Double): String {
        if (value == 0.0) return "0"
        if (!value.isFinite()) return value.toString()
        if (value == Math.floor(value) && abs(value) < 1e15) {
            return value.toLong().toString()
        }
        val rounded = BigDecimal(value, MathContext(10, RoundingMode.HALF_UP)).stripTrailingZeros()
        return rounded.toPlainString()
    }
}

/**
 * The instant-answer front door: given a raw query, try each on-device producer (calculator, unit
 * conversion, base conversion, percentage) in priority order. Pure string/number work - no network,
 * no storage, no logging - so it is safe to run on every keystroke-cheap search request. Returns null
 * for the overwhelming majority of queries, which then proceed to normal metasearch untouched.
 */
object InstantAnswers {
    // "15% of 80", "12.5 % of 40"
    private val PERCENT_OF =
        Regex("""^(?:what\s+is\s+)?(-?\d+(?:\.\d+)?)\s*%\s*of\s*(-?\d+(?:[.,]\d+)?)\??$""", RegexOption.IGNORE_CASE)

    // "0xff in decimal", "255 to hex", "0b1011 in decimal", "777 in binary"
    private val BASE_CONVERSION =
        Regex(
            """^(?:convert\s+)?(0x[0-9a-f]+|0b[01]+|0o[0-7]+|\d+)\s+(?:to|in|as)\s+""" +
                """(hex|hexadecimal|binary|bin|octal|oct|decimal|dec)$""",
            RegexOption.IGNORE_CASE,
        )

    // Digit runs joined only by '-' or '/': a date ("2024-01-15"), a year range ("2020-2021"), or a
    // phone-ish number ("555-1234"). Evaluating those as subtraction/division would be an absurd
    // answer card, so they are excluded from the calculator; a genuine "5-3" (short operands) is kept.
    private val DATE_OR_PHONE_LIKE = Regex("""^\d{1,4}([-/]\d{1,4}){2,}$|^\d{3,}\s*-\s*\d{3,}$""")

    /** The instant answer for [query], or null when no producer recognizes it. */
    fun answer(query: String): InstantAnswer? {
        val trimmed = query.trim()
        if (trimmed.isEmpty() || trimmed.length > 256) return null
        percentage(trimmed)?.let { return it }
        UnitConverter.convert(trimmed)?.let { return it }
        baseConversion(trimmed)?.let { return it }
        return calculation(trimmed)
    }

    private fun calculation(query: String): InstantAnswer? {
        // Guard before evaluating: a plain number, a date, or a word must never render a calculator
        // card. "what is 2+2" works; operator-free text does not reach the parser at all.
        val expression = query.removePrefix("what is ").removePrefix("What is ").removeSuffix("=").trim()
        if (DATE_OR_PHONE_LIKE.matches(expression)) return null
        if (!Calculator.looksLikeMath(expression)) return null
        val value = Calculator.evaluate(expression) ?: return null
        return InstantAnswer(
            expression = expression,
            result = Numbers.format(value),
            kind = InstantAnswer.Kind.CALCULATOR,
        )
    }

    private fun percentage(query: String): InstantAnswer? {
        val match = PERCENT_OF.find(query) ?: return null
        val percent = match.groupValues[1].toDoubleOrNull() ?: return null
        val of = match.groupValues[2].replace(",", "").toDoubleOrNull() ?: return null
        return InstantAnswer(
            expression = "${Numbers.format(percent)}% of ${Numbers.format(of)}",
            result = Numbers.format(percent / 100.0 * of),
            kind = InstantAnswer.Kind.PERCENTAGE,
        )
    }

    private fun baseConversion(query: String): InstantAnswer? {
        val match = BASE_CONVERSION.find(query) ?: return null
        val raw = match.groupValues[1].lowercase()
        val target = match.groupValues[2].lowercase()
        val value =
            runCatching {
                when {
                    raw.startsWith("0x") -> raw.substring(2).toLong(16)
                    raw.startsWith("0b") -> raw.substring(2).toLong(2)
                    raw.startsWith("0o") -> raw.substring(2).toLong(8)
                    else -> raw.toLong(10)
                }
            }.getOrNull() ?: return null
        val result =
            when (target) {
                "hex", "hexadecimal" -> "0x" + value.toString(16)
                "binary", "bin" -> "0b" + value.toString(2)
                "octal", "oct" -> "0o" + value.toString(8)
                else -> value.toString(10)
            }
        // Converting a plain decimal to decimal answers nothing; skip so it falls through to search.
        if (result == raw) return null
        return InstantAnswer(
            expression = raw,
            result = result,
            kind = InstantAnswer.Kind.BASE_CONVERSION,
        )
    }
}
