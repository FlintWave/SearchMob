package org.searchmob.engine.correct

/**
 * Read-only correction vocabulary: each word carries a weight (corpus frequency, or a baseline for
 * names) and is indexed two ways so the corrector can find candidates cheaply: by phonetic code (for
 * "similar sounding" matches) and by length (for the edit-distance scan). Built once via [build] and
 * then queried; immutable thereafter.
 */
class Dictionary internal constructor(
    private val weights: Map<String, Int>,
    private val byPhonetic: Map<String, List<String>>,
    private val byLength: Map<Int, List<String>>,
) {
    val size: Int get() = weights.size

    fun contains(term: String): Boolean = weights.containsKey(term)

    fun weight(term: String): Int = weights[term] ?: 0

    /** Words sharing the phonetic [code]. */
    fun phonetic(code: String): List<String> = byPhonetic[code].orEmpty()

    /** Words whose length is within [delta] of [length]. */
    fun nearLength(
        length: Int,
        delta: Int,
    ): List<String> = ((length - delta)..(length + delta)).flatMap { byLength[it].orEmpty() }

    companion object {
        /**
         * Build a dictionary from [weights] (word -> weight), indexing each word by the phonetic codes
         * returned by [phoneticCodes] and by its length. The corrector must encode query terms with the
         * same [phoneticCodes] function so they hash into the same buckets.
         */
        fun build(
            weights: Map<String, Int>,
            phoneticCodes: (String) -> List<String> = Phonetics::codes,
        ): Dictionary {
            val byPhonetic = HashMap<String, MutableList<String>>()
            val byLength = HashMap<Int, MutableList<String>>()
            for (word in weights.keys) {
                for (code in phoneticCodes(word)) {
                    byPhonetic.getOrPut(code) { ArrayList() }.add(word)
                }
                byLength.getOrPut(word.length) { ArrayList() }.add(word)
            }
            return Dictionary(weights, byPhonetic, byLength)
        }
    }
}
