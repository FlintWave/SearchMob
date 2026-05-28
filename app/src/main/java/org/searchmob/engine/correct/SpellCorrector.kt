package org.searchmob.engine.correct

/** A proposed correction for a query and a confidence in [0,1]. */
data class Correction(
    val corrected: String,
    val confidence: Double,
)

/**
 * Proposes a corrected query for a likely-misspelled input, or null when the input looks fine or no
 * confident correction exists. Implementations must be fail-soft (never throw) and must not perform
 * any network I/O.
 */
fun interface SpellCorrector {
    fun suggest(query: String): Correction?
}

/** Default corrector that never suggests anything; used in tests and before the dictionary loads. */
object NoopSpellCorrector : SpellCorrector {
    override fun suggest(query: String): Correction? = null
}
