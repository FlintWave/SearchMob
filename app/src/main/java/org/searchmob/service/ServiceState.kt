package org.searchmob.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Lifecycle of the always-on service. */
enum class ServiceState { Stopped, Starting, Running }

/**
 * Single source of truth for the service lifecycle, observed by both the UI and the service's
 * notification. Transitions: [Stopped] -> [Starting] -> [Running] on start, and -> [Stopped] on stop.
 */
object SearchMobServiceState {
    private val mutableState = MutableStateFlow(ServiceState.Stopped)

    /** Observable lifecycle state. */
    val state: StateFlow<ServiceState> = mutableState.asStateFlow()

    val current: ServiceState get() = mutableState.value

    fun markStarting() {
        mutableState.value = ServiceState.Starting
    }

    fun markRunning() {
        mutableState.value = ServiceState.Running
    }

    fun markStopped() {
        mutableState.value = ServiceState.Stopped
    }
}
