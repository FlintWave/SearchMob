package org.searchmob.engine.correct

import android.content.Context
import java.util.zip.GZIPInputStream

/**
 * Loads the bundled correction [Dictionary] from the compressed `dict/words.txt.gz` asset and augments
 * it with the user's own search-history terms (private, on-device personalization). Loading parses ~60k
 * lines and builds the phonetic index, so [load] is heavy and must run off the main thread; the result
 * is cached. [current] returns the cached dictionary or null before the first load completes, which is
 * what the corrector checks so a search before loading simply yields no suggestion.
 */
class AssetDictionaryLoader(
    private val context: Context,
    private val assetPath: String = "dict/words.txt.gz",
    private val historyTerms: () -> List<String> = { emptyList() },
    private val historyWeight: Int = 15_000,
) {
    @Volatile
    private var cached: Dictionary? = null

    fun current(): Dictionary? = cached

    suspend fun load(): Dictionary {
        cached?.let { return it }
        val weights = HashMap<String, Int>()
        context.assets.open(assetPath).use { raw ->
            GZIPInputStream(raw).bufferedReader().forEachLine { line ->
                val tab = line.indexOf('\t')
                if (tab > 0) {
                    val word = line.substring(0, tab)
                    val weight = line.substring(tab + 1).toIntOrNull() ?: 0
                    if (word.isNotEmpty() && weight > 0) weights[word] = weight
                }
            }
        }
        // Fold in the user's past queries so corrections improve for terms they actually search. Never
        // let a history read failure (e.g. locked vault) abort the bundled dictionary.
        runCatching { historyTerms() }.getOrDefault(emptyList()).forEach { query ->
            query.lowercase().split(WHITESPACE).forEach { term ->
                if (term.length >= 2 && term.all { it in 'a'..'z' } && term !in weights) {
                    weights[term] = historyWeight
                }
            }
        }
        return Dictionary.build(weights).also { cached = it }
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}
