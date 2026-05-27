package org.searchmob.data.history

/** A stored search-history entry. */
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

    /** Non-expired entries as of [nowMs]; also sweeps expired entries opportunistically. */
    fun list(nowMs: Long): List<HistoryEntry>

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

    override fun list(nowMs: Long): List<HistoryEntry> {
        if (!on) return emptyList()
        entries.removeAll { nowMs - it.timestampMs > ttlMs }
        return entries.toList()
    }

    override fun clear() {
        entries.clear()
    }

    override fun disable() {
        on = false
        entries.clear()
    }
}
