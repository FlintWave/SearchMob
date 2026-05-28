package org.searchmob.engine.correct

import kotlin.math.abs
import kotlin.math.max

/**
 * Small, dependency-free string-similarity metrics used by the corrector. Both are textbook
 * algorithms kept in-house to avoid adding a fuzzy-matching dependency:
 *
 * - [osaDistance]: Optimal String Alignment distance (Damerau-Levenshtein restricted to adjacent
 *   transpositions), which covers the overwhelming majority of real typos (insert, delete, substitute,
 *   swap two neighbours). Banded with an early cutoff so it is cheap to reject distant words.
 * - [jaroWinkler]: similarity in [0,1] that rewards a shared prefix, which suits names well.
 */
object StringMetrics {
    /**
     * OSA edit distance between [a] and [b], returning early with `max + 1` once the distance is known
     * to exceed [max]. Comparisons are on the strings as given (callers lower-case first).
     */
    fun osaDistance(
        a: String,
        b: String,
        max: Int = Int.MAX_VALUE,
    ): Int {
        if (a == b) return 0
        if (abs(a.length - b.length) > max) return max + 1
        val n = a.length
        val m = b.length
        if (n == 0) return m
        if (m == 0) return n

        var prevPrev = IntArray(m + 1)
        var prev = IntArray(m + 1) { it }
        var curr = IntArray(m + 1)

        for (i in 1..n) {
            curr[0] = i
            var rowMin = curr[0]
            for (j in 1..m) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var v = minOf(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    v = minOf(v, prevPrev[j - 2] + 1)
                }
                curr[j] = v
                if (v < rowMin) rowMin = v
            }
            if (rowMin > max) return max + 1
            val tmp = prevPrev
            prevPrev = prev
            prev = curr
            curr = tmp
        }
        return prev[m]
    }

    /** Jaro-Winkler similarity in [0,1]; 1.0 is identical. */
    fun jaroWinkler(
        a: String,
        b: String,
    ): Double {
        val jaro = jaro(a, b)
        if (jaro == 0.0) return 0.0
        var prefix = 0
        val maxPrefix = minOf(4, minOf(a.length, b.length))
        while (prefix < maxPrefix && a[prefix] == b[prefix]) prefix++
        return jaro + prefix * 0.1 * (1.0 - jaro)
    }

    private fun jaro(
        a: String,
        b: String,
    ): Double {
        if (a.isEmpty() && b.isEmpty()) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val matchWindow = max(a.length, b.length) / 2 - 1
        val aMatched = BooleanArray(a.length)
        val bMatched = BooleanArray(b.length)
        var matches = 0
        for (i in a.indices) {
            val start = maxOf(0, i - matchWindow)
            val end = minOf(i + matchWindow + 1, b.length)
            for (j in start until end) {
                if (!bMatched[j] && a[i] == b[j]) {
                    aMatched[i] = true
                    bMatched[j] = true
                    matches++
                    break
                }
            }
        }
        if (matches == 0) return 0.0
        var transpositions = 0
        var k = 0
        for (i in a.indices) {
            if (!aMatched[i]) continue
            while (!bMatched[k]) k++
            if (a[i] != b[k]) transpositions++
            k++
        }
        val m = matches.toDouble()
        return (m / a.length + m / b.length + (m - transpositions / 2.0) / m) / 3.0
    }
}
