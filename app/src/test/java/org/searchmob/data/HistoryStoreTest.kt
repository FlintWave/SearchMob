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
}
