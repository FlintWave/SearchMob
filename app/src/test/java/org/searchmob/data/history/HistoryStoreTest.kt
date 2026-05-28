package org.searchmob.data.history

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryStoreTest {
    @Test
    fun deleteRemovesOnlyThatEntry() {
        val store = InMemoryHistoryStore()
        store.setEnabled(true)
        val a = HistoryEntry("alpha", 1_000)
        val b = HistoryEntry("beta", 2_000)
        store.add(a)
        store.add(b)
        store.delete(a)
        assertEquals(listOf(b), store.list(3_000))
    }

    @Test
    fun historyEntryListJsonRoundTrips() {
        val entries = listOf(HistoryEntry("kotlin", 1_000), HistoryEntry("compose", 2_000))
        val json = Json.encodeToString(entries)
        assertEquals(entries, Json.decodeFromString<List<HistoryEntry>>(json))
    }
}
