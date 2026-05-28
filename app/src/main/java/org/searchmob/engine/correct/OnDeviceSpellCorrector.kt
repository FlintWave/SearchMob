package org.searchmob.engine.correct

import kotlin.math.ln

/**
 * Fully offline spell/phonetic corrector. For each query term that is not already a dictionary word, it
 * gathers candidates two ways and keeps the best:
 *
 * - phonetic buckets (Double Metaphone) for "similar sounding" terms, including first-letter changes;
 * - an edit-distance scan over same-first-letter words of a nearby length, for ordinary typos.
 *
 * Candidates are scored by `jaroWinkler(term, candidate) * ln(weight)` so a close spelling that is also
 * a common word wins. A term is only corrected when the best candidate clears [similarityThreshold];
 * the whole query is rewritten only if at least one term changed. Never throws and never touches the
 * network. The dictionary is supplied lazily via [dictionary] so searches before it loads simply get
 * no suggestion.
 */
class OnDeviceSpellCorrector(
    private val dictionary: () -> Dictionary?,
    private val minTermLength: Int = 3,
    private val maxEdits: Int = 2,
    private val similarityThreshold: Double = 0.86,
) : SpellCorrector {
    override fun suggest(query: String): Correction? = runCatching { correct(query) }.getOrNull()

    private fun correct(query: String): Correction? {
        val dict = dictionary() ?: return null
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return null

        val tokens = trimmed.split(WHITESPACE)
        var changed = false
        var minConfidence = 1.0
        val rebuilt =
            tokens.map { token ->
                val best = bestCandidate(token.lowercase(), dict)
                if (best == null) {
                    token
                } else {
                    changed = true
                    minConfidence = minOf(minConfidence, best.second)
                    best.first
                }
            }
        if (!changed) return null
        val corrected = rebuilt.joinToString(" ")
        if (corrected.equals(trimmed, ignoreCase = true)) return null
        return Correction(corrected, minConfidence)
    }

    private fun bestCandidate(
        token: String,
        dict: Dictionary,
    ): Pair<String, Double>? {
        if (token.length < minTermLength) return null
        if (!token.all { it in 'a'..'z' }) return null // skip numbers, punctuation, mixed scripts
        if (dict.contains(token)) return null // already a known word

        val candidates = HashSet<String>()
        for (code in Phonetics.codes(token)) candidates += dict.phonetic(code)
        for (word in dict.nearLength(token.length, maxEdits)) {
            if (word[0] == token[0] && StringMetrics.osaDistance(token, word, maxEdits) <= maxEdits) {
                candidates += word
            }
        }
        if (candidates.isEmpty()) return null

        var bestWord: String? = null
        var bestScore = 0.0
        var bestSimilarity = 0.0
        for (candidate in candidates) {
            if (candidate == token) continue
            val similarity = StringMetrics.jaroWinkler(token, candidate)
            if (similarity < similarityThreshold) continue
            val score = similarity * ln(dict.weight(candidate).coerceAtLeast(1).toDouble() + 1.0)
            if (score > bestScore) {
                bestScore = score
                bestWord = candidate
                bestSimilarity = similarity
            }
        }
        return bestWord?.let { it to bestSimilarity }
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}
