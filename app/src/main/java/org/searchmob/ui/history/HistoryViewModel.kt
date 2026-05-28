package org.searchmob.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.searchmob.data.history.HistoryEntry
import org.searchmob.data.history.HistoryStore

/**
 * Backs the History screen. Reads the encrypted history store off the main thread and exposes the
 * entries (newest first) plus whether history is enabled. Deleting, clearing, exporting, and importing
 * all run on [ioDispatcher] because the SQLCipher store does Room I/O.
 */
class HistoryViewModel(
    private val historyStore: HistoryStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val nowMs: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    data class State(
        val enabled: Boolean = true,
        val entries: List<HistoryEntry> = emptyList(),
    )

    private val mutableState = MutableStateFlow(State())
    val state: StateFlow<State> = mutableState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val (enabled, entries) =
                withContext(ioDispatcher) { historyStore.enabled to historyStore.list(nowMs()) }
            mutableState.value = State(enabled, entries.sortedByDescending { it.timestampMs })
        }
    }

    fun delete(entry: HistoryEntry) {
        viewModelScope.launch {
            withContext(ioDispatcher) { historyStore.delete(entry) }
            refresh()
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            withContext(ioDispatcher) { historyStore.clear() }
            refresh()
        }
    }

    /** Produce the current history as a JSON document for the caller to write to a chosen file. */
    fun exportJson(onReady: (String) -> Unit) {
        viewModelScope.launch {
            val text = withContext(ioDispatcher) { json.encodeToString(historyStore.list(nowMs())) }
            onReady(text)
        }
    }

    /**
     * Merge entries from an exported JSON document into the store and report how many were added.
     * Requires history to be enabled (the store's add is a no-op while off), keeping store-nothing intact.
     */
    fun import(
        text: String,
        onDone: (Int) -> Unit,
    ) {
        viewModelScope.launch {
            val added =
                withContext(ioDispatcher) {
                    val entries =
                        runCatching { json.decodeFromString<List<HistoryEntry>>(text) }.getOrDefault(emptyList())
                    var count = 0
                    if (historyStore.enabled) {
                        entries.forEach {
                            historyStore.add(it)
                            count++
                        }
                    }
                    count
                }
            refresh()
            onDone(added)
        }
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
