package org.searchmob.engine.correct

import org.apache.commons.codec.language.DoubleMetaphone

/**
 * "Similar sounding" encoder. Wraps Apache Commons Codec's Double Metaphone (the standard choice for
 * names) and returns its primary plus, when different, its alternate code. The same function indexes
 * the dictionary and encodes a query term, so words that sound alike land in the same bucket.
 */
object Phonetics {
    private val dm = DoubleMetaphone().apply { maxCodeLen = 6 }

    /** Distinct non-empty Double Metaphone codes for [term] (primary, and alternate if it differs). */
    fun codes(term: String): List<String> =
        synchronized(dm) {
            val primary = dm.doubleMetaphone(term).orEmpty()
            val alternate = dm.doubleMetaphone(term, true).orEmpty()
            buildList {
                if (primary.isNotEmpty()) add(primary)
                if (alternate.isNotEmpty() && alternate != primary) add(alternate)
            }
        }
}
