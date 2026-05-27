package org.searchmob.service

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ServiceStateMachineTest {
    @Before
    fun reset() {
        SearchMobServiceState.markStopped()
    }

    @Test
    fun startAdvancesThroughStartingToRunning() {
        assertEquals(ServiceState.Stopped, SearchMobServiceState.current)
        SearchMobServiceState.markStarting()
        assertEquals(ServiceState.Starting, SearchMobServiceState.current)
        SearchMobServiceState.markRunning()
        assertEquals(ServiceState.Running, SearchMobServiceState.current)
    }

    @Test
    fun stopReturnsToStopped() {
        SearchMobServiceState.markRunning()
        SearchMobServiceState.markStopped()
        assertEquals(ServiceState.Stopped, SearchMobServiceState.current)
    }

    @Test
    fun stateFlowExposesCurrentValue() {
        SearchMobServiceState.markRunning()
        assertEquals(ServiceState.Running, SearchMobServiceState.state.value)
    }
}
