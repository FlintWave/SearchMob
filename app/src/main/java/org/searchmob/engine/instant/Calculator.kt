package org.searchmob.engine.instant

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * A small, safe arithmetic evaluator for the calculator instant answer.
 *
 * Recursive descent over a fixed grammar - numbers, `+ - * / %`, `^` (right-associative), parentheses,
 * unary minus, the constants `pi`/`e`, and a few common functions (`sqrt`, `abs`, `ln`, `log`, `sin`,
 * `cos`, `tan`, radians). Nothing is interpreted or executed beyond this grammar, so arbitrary query
 * text can be thrown at [evaluate] safely; anything that does not parse cleanly (or divides by zero,
 * overflows, ...) yields null rather than an exception. Unicode `× ÷ −` and `**` are accepted as
 * spellings of `* / - ^`.
 */
object Calculator {
    private val FUNCTIONS: Map<String, (Double) -> Double> =
        mapOf(
            "sqrt" to ::sqrt,
            "abs" to ::abs,
            "ln" to ::ln,
            "log" to ::log10,
            "sin" to ::sin,
            "cos" to ::cos,
            "tan" to ::tan,
        )

    private val CONSTANTS = mapOf("pi" to Math.PI, "e" to Math.E)

    /**
     * Evaluate [expression]; null when it is not a well-formed arithmetic expression or the result is
     * not a finite number. A bare number ("2024") is parsed but rejected by [looksLikeMath]'s caller
     * (`InstantAnswers`), not here.
     */
    fun evaluate(expression: String): Double? {
        val parser = Parser(normalize(expression))
        val value = parser.parseExpression() ?: return null
        parser.skipWhitespace()
        if (!parser.atEnd()) return null
        return value.takeIf { it.isFinite() }
    }

    /**
     * True when [expression] contains at least one operator, function, or parenthesis, so a plain
     * number or word never triggers the calculator card.
     */
    fun looksLikeMath(expression: String): Boolean {
        val normalized = normalize(expression)
        if (normalized.isBlank()) return false
        val hasOperator = normalized.any { it in "+*/%^(" }
        // A '-' only counts as an operator when it is not just a leading sign ("-5").
        val interiorMinus = normalized.trim().drop(1).contains('-')
        val hasFunction = FUNCTIONS.keys.any { normalized.contains(it) }
        return hasOperator || interiorMinus || hasFunction
    }

    /** Fold the unicode/most common alternate operator spellings into the grammar's canonical ones. */
    private fun normalize(expression: String): String =
        expression
            .replace("**", "^")
            .replace('×', '*')
            .replace('÷', '/')
            .replace('−', '-')
            .replace(",", "") // thousands separators: "1,000,000 / 4"

    /** One-pass recursive-descent parser; every parse method returns null on any malformed input. */
    private class Parser(private val text: String) {
        private var pos = 0

        fun atEnd(): Boolean = pos >= text.length

        fun skipWhitespace() {
            while (pos < text.length && text[pos].isWhitespace()) pos++
        }

        private fun peek(): Char? = if (pos < text.length) text[pos] else null

        // expression := term (('+' | '-') term)*
        fun parseExpression(): Double? {
            var value = parseTerm() ?: return null
            while (true) {
                skipWhitespace()
                when (peek()) {
                    '+' -> {
                        pos++
                        value += parseTerm() ?: return null
                    }
                    '-' -> {
                        pos++
                        value -= parseTerm() ?: return null
                    }
                    else -> return value
                }
            }
        }

        // term := factor (('*' | '/' | '%') factor)*
        private fun parseTerm(): Double? {
            var value = parseFactor() ?: return null
            while (true) {
                skipWhitespace()
                when (peek()) {
                    '*' -> {
                        pos++
                        value *= parseFactor() ?: return null
                    }
                    '/' -> {
                        pos++
                        val divisor = parseFactor() ?: return null
                        if (divisor == 0.0) return null
                        value /= divisor
                    }
                    '%' -> {
                        pos++
                        val divisor = parseFactor() ?: return null
                        if (divisor == 0.0) return null
                        value %= divisor
                    }
                    else -> return value
                }
            }
        }

        // factor := unary ('^' factor)?   (right-associative power)
        private fun parseFactor(): Double? {
            val base = parseUnary() ?: return null
            skipWhitespace()
            if (peek() == '^') {
                pos++
                val exponent = parseFactor() ?: return null
                return base.pow(exponent)
            }
            return base
        }

        // unary := '-' unary | primary
        private fun parseUnary(): Double? {
            skipWhitespace()
            if (peek() == '-') {
                pos++
                val value = parseUnary() ?: return null
                return -value
            }
            return parsePrimary()
        }

        // primary := number | constant | function '(' expression ')' | '(' expression ')'
        private fun parsePrimary(): Double? {
            skipWhitespace()
            val ch = peek() ?: return null
            if (ch == '(') {
                pos++
                val value = parseExpression() ?: return null
                skipWhitespace()
                if (peek() != ')') return null
                pos++
                return value
            }
            if (ch.isDigit() || ch == '.') return parseNumber()
            if (ch.isLetter()) return parseWord()
            return null
        }

        private fun parseNumber(): Double? {
            val start = pos
            while (pos < text.length && (text[pos].isDigit() || text[pos] == '.')) pos++
            // Scientific notation: 1.5e3 / 2E-4 (only when digits follow the exponent marker).
            if (pos < text.length && (text[pos] == 'e' || text[pos] == 'E')) {
                var lookahead = pos + 1
                if (lookahead < text.length && (text[lookahead] == '+' || text[lookahead] == '-')) lookahead++
                if (lookahead < text.length && text[lookahead].isDigit()) {
                    pos = lookahead
                    while (pos < text.length && text[pos].isDigit()) pos++
                }
            }
            return text.substring(start, pos).toDoubleOrNull()
        }

        private fun parseWord(): Double? {
            val start = pos
            while (pos < text.length && text[pos].isLetter()) pos++
            val word = text.substring(start, pos).lowercase()
            CONSTANTS[word]?.let { return it }
            val function = FUNCTIONS[word] ?: return null
            skipWhitespace()
            if (peek() != '(') return null
            pos++
            val argument = parseExpression() ?: return null
            skipWhitespace()
            if (peek() != ')') return null
            pos++
            return function(argument).takeIf { it.isFinite() }
        }
    }
}
