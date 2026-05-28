package org.searchmob.data.history

import kotlinx.serialization.Serializable

/** A stored search-history entry. Serializable so history can be exported/imported as JSON. */
@Serializable
data class HistoryEntry(val query: String, val timestampMs: Long)

/**
 * Search history. OFF by default (privacy: store nothing). When enabled, entries are local-only,
 * encrypted at rest by the backing store, expire after a TTL, and are user-purgeable. There is no
 * background sweep: expiry is enforced inline on read/insert (no timer, no wake-lock).
 */
interface HistoryStore {
    val enabled: Boolean

    fun setEnabled(enabled: Boolean)

    fun add(entry: HistoryEntry)

    /** Delete a single stored entry (matched by query and timestamp). No-op if it is not present. */
    fun delete(entry: HistoryEntry)

    /** Non-expired entries as of [nowMs]; also sweeps expired entries opportunistically. */
    fun list(nowMs: Long): List<HistoryEntry>

    /**
     * Distinct, non-expired past queries that prefix-match [prefix] case-insensitively, most-recent
     * first, capped to [limit]. Powers local search suggestions. Returns an empty list (never throws)
     * when history is disabled, locked, empty, or [prefix] is blank, so suggestions are always safe to
     * request. [nowMs] is the clock used for TTL expiry, like [list].
     */
    fun suggest(
        prefix: String,
        limit: Int,
        nowMs: Long,
    ): List<String>

    /** Delete all entries but keep history enabled. */
    fun clear()

    /** Disable history and delete its data. */
    fun disable()

    /**
     * Close the open encrypted handle WITHOUT deleting data, used on vault lock/eviction so a locked
     * (zero-knowledge) session has no live DB handle while the stored, encrypted data survives until
     * the user explicitly clears or disables it. Default no-op for in-memory stores.
     */
    fun closeHandle() {}
}

/**
 * In-memory [HistoryStore] (no persistence). It is the JVM-testable reference for the off-by-default,
 * TTL, clear, and disable semantics; the SQLCipher-backed store mirrors this behavior on-device.
 */
class InMemoryHistoryStore(private val ttlMs: Long = 7L * 24 * 60 * 60 * 1000) : HistoryStore {
    private var on = false
    private val entries = mutableListOf<HistoryEntry>()

    override val enabled: Boolean get() = on

    override fun setEnabled(enabled: Boolean) {
        on = enabled
        if (!enabled) entries.clear()
    }

    override fun add(entry: HistoryEntry) {
        if (on) entries.add(entry)
    }

    override fun delete(entry: HistoryEntry) {
        entries.removeAll { it == entry }
    }

    override fun list(nowMs: Long): List<HistoryEntry> {
        if (!on) return emptyList()
        entries.removeAll { nowMs - it.timestampMs > ttlMs }
        return entries.toList()
    }

    override fun suggest(
        prefix: String,
        limit: Int,
        nowMs: Long,
    ): List<String> {
        if (!on || prefix.isBlank() || limit <= 0) return emptyList()
        val lowerPrefix = prefix.lowercase()
        // Newest first, distinct (case-insensitive, keeping the first/newest casing seen), capped.
        val seen = LinkedHashSet<String>()
        list(nowMs)
            .asReversed() // list() preserves insertion order (oldest->newest); reverse for newest-first.
            .asSequence()
            .filter { it.query.lowercase().startsWith(lowerPrefix) }
            .forEach { entry ->
                if (seen.none { it.equals(entry.query, ignoreCase = true) }) seen.add(entry.query)
            }
        return seen.take(limit)
    }

    override fun clear() {
        entries.clear()
    }

    override fun disable() {
        on = false
        entries.clear()
    }
}
