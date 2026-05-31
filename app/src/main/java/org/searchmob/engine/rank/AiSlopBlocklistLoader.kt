package org.searchmob.engine.rank

import android.content.Context
import java.util.zip.GZIPInputStream

/**
 * Loads the bundled AI-slop / low-quality domain blocklist from the compressed
 * `blocklist/ai-slop-domains.txt.gz` asset (one bare domain per line). The asset is a merged snapshot
 * of CC0-licensed community lists, built reproducibly by `tools/build-slop-list.py` and shared with
 * the desktop client. Filtering is applied entirely on-device in the ranking pass; no query leaves the
 * device for filtering.
 *
 * Reading parses ~1k lines, so [load] should run off the main thread; the result is cached. [current]
 * returns the cached set or an empty set before the first load completes, which is what the ranker
 * uses so a search before loading simply applies no slop filter. Fail-soft: any read error yields an
 * empty set rather than failing a search.
 */
class AiSlopBlocklistLoader(
    private val context: Context,
    private val assetPath: String = "blocklist/ai-slop-domains.txt.gz",
) {
    @Volatile
    private var cached: Set<String>? = null

    /** The loaded blocklist, or an empty set if loading has not completed (or failed). */
    fun current(): Set<String> = cached ?: emptySet()

    suspend fun load(): Set<String> {
        cached?.let { return it }
        val domains =
            runCatching {
                val out = HashSet<String>()
                var decompressed = 0L
                context.assets.open(assetPath).use { raw ->
                    GZIPInputStream(raw).bufferedReader().use { reader ->
                        while (true) {
                            val line = reader.readLine() ?: break
                            // Bound the decompressed size so a corrupt/oversized asset cannot exhaust
                            // memory; the real list is well under this. Mirrors the desktop's cap.
                            decompressed += line.length + 1
                            if (decompressed > MAX_DECOMPRESSED_BYTES) break
                            val domain = line.trim().lowercase()
                            if (domain.isNotEmpty() && !domain.startsWith("#")) out.add(domain)
                        }
                    }
                }
                out as Set<String>
            }.getOrDefault(emptySet())
        return domains.also { cached = it }
    }

    private companion object {
        /** Upper bound on the decompressed blocklist; the real list is ~1k short lines (well under). */
        const val MAX_DECOMPRESSED_BYTES = 8L * 1024 * 1024
    }
}
