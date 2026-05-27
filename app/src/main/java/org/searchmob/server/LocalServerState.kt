package org.searchmob.server

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Exposes the actually-bound loopback port (or null when not running) for the UI and other components. */
object LocalServerState {
    private val mutablePort = MutableStateFlow<Int?>(null)
    val port: StateFlow<Int?> = mutablePort.asStateFlow()

    fun setPort(port: Int?) {
        mutablePort.value = port
    }
}
