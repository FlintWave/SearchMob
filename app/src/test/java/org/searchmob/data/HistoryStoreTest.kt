package org.searchmob.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.searchmob.data.history.HistoryEntry
import org.searchmob.data.history.InMemoryHistoryStore

class HistoryStoreTest {
    @Test
    fun offByDefaultStoresNothing() {
        val store = InMemoryHistoryStore()
        assertFalse(store.enabled)
        store.add(HistoryEntry("private query", 1_000))
        assertTrue(store.list(2_000).isEmpty())
    }

    @Test
    fun storesWhenEnabled() {
        val store = InMemoryHistoryStore()
        store.setEnabled(true)
        store.add(HistoryEntry("kotlin", 1_000))
        assertEquals(listOf("kotlin"), store.list(1_500).map { it.query })
    }

    @Test
    fun expiredEntriesAreNotReturnedAndAreSwept() {
        val store = InMemoryHistoryStore(ttlMs = 1_000)
        store.setEnabled(true)
        store.add(HistoryEntry("old", 0))
        store.add(HistoryEntry("fresh", 5_000))
        // now=5_500: "old" is 5_500ms old (> ttl) and excluded; "fresh" is 500ms old.
        assertEquals(listOf("fresh"), store.list(5_500).map { it.query })
    }

    @Test
    fun clearEmptiesButKeepsEnabled() {
        val store = InMemoryHistoryStore()
        store.setEnabled(true)
        store.add(HistoryEntry("x", 1_000))
        store.clear()
        assertTrue(store.enabled)
        assertTrue(store.list(1_000).isEmpty())
    }

    @Test
    fun disableTurnsOffAndPurges() {
        val store = InMemoryHistoryStore()
        store.setEnabled(true)
        store.add(HistoryEntry("x", 1_000))
        store.disable()
        assertFalse(store.enabled)
        assertTrue(store.list(1_000).isEmpty())
    }

    @Test
    fun suggestPrefixMatchesCaseInsensitiveDistinctMostRecentFirst() {
        val store = InMemoryHistoryStore()
        store.setEnabled(true)
        store.add(HistoryEntry("kotlin coroutines", 1_000))
        store.add(HistoryEntry("kotlin flow", 2_000))
        store.add(HistoryEntry("KOTLIN flow", 3_000)) // case-insensitive duplicate
        store.add(HistoryEntry("rust", 4_000)) // no prefix match
        store.add(HistoryEntry("Kotlin sequences", 5_000))
        // Case-insensitive prefix, distinct, most-recent first.
        assertEquals(
            listOf("Kotlin sequences", "KOTLIN flow", "kotlin coroutines"),
            store.suggest("kot", limit = 10, nowMs = 5_500),
        )
    }

    @Test
    fun suggestRespectsLimit() {
        val store = InMemoryHistoryStore()
        store.setEnabled(true)
        store.add(HistoryEntry("apple", 1_000))
        store.add(HistoryEntry("apricot", 2_000))
        store.add(HistoryEntry("avocado", 3_000))
        assertEquals(listOf("avocado", "apricot"), store.suggest("a", limit = 2, nowMs = 3_500))
    }

    @Test
    fun suggestExcludesExpiredEntries() {
        val store = InMemoryHistoryStore(ttlMs = 1_000)
        store.setEnabled(true)
        store.add(HistoryEntry("apple old", 0))
        store.add(HistoryEntry("apple fresh", 5_000))
        // now=5_500: "apple old" is expired and excluded.
        assertEquals(listOf("apple fresh"), store.suggest("apple", limit = 10, nowMs = 5_500))
    }

    @Test
    fun suggestEmptyWhenDisabledEmptyOrBlankPrefix() {
        val disabled = InMemoryHistoryStore()
        assertTrue(disabled.suggest("a", limit = 5, nowMs = 1_000).isEmpty())

        val enabledEmpty = InMemoryHistoryStore().apply { setEnabled(true) }
        assertTrue(enabledEmpty.suggest("a", limit = 5, nowMs = 1_000).isEmpty())

        val seeded = InMemoryHistoryStore().apply { setEnabled(true) }
        seeded.add(HistoryEntry("apple", 1_000))
        assertTrue(seeded.suggest("   ", limit = 5, nowMs = 1_500).isEmpty())
    }
}
