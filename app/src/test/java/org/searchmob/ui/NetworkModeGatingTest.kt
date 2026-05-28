package org.searchmob.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.searchmob.data.history.InMemoryHistoryStore
import org.searchmob.ui.prefs.InMemoryPreferencesStore
import org.searchmob.ui.prefs.PreferencesRepository
import org.searchmob.ui.settings.SettingsViewModel

/**
 * Verifies the network-mode warning gate: turning the toggle ON only opens the warning and does not
 * persist; confirming persists ON; cancelling leaves it OFF; turning OFF persists immediately.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NetworkModeGatingTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun fixture(): Pair<SettingsViewModel, PreferencesRepository> {
        val repo = PreferencesRepository(InMemoryPreferencesStore())
        val vm =
            SettingsViewModel(
                preferences = repo,
                historyStore = InMemoryHistoryStore(),
                engineCatalog = emptyList(),
                engineEnabledSink = MutableStateFlow(emptyMap()),
                apiKeysSink = MutableStateFlow(emptyMap()),
            )
        return vm to repo
    }

    @Test
    fun turningOn_opensWarningButDoesNotPersist() =
        runTest(dispatcher) {
            val (vm, repo) = fixture()
            vm.onNetworkAccessToggle(requestedOn = true)
            advanceUntilIdle()
            assertTrue(vm.showNetworkWarning.value)
            assertFalse(repo.networkAccessEnabled.first())
        }

    @Test
    fun confirm_enablesAndClosesWarning() =
        runTest(dispatcher) {
            val (vm, repo) = fixture()
            vm.onNetworkAccessToggle(requestedOn = true)
            vm.confirmNetworkAccess()
            advanceUntilIdle()
            assertFalse(vm.showNetworkWarning.value)
            assertTrue(repo.networkAccessEnabled.first())
        }

    @Test
    fun cancel_leavesOffAndClosesWarning() =
        runTest(dispatcher) {
            val (vm, repo) = fixture()
            vm.onNetworkAccessToggle(requestedOn = true)
            vm.cancelNetworkAccess()
            advanceUntilIdle()
            assertFalse(vm.showNetworkWarning.value)
            assertFalse(repo.networkAccessEnabled.first())
        }

    @Test
    fun turningOff_persistsImmediatelyWithoutWarning() =
        runTest(dispatcher) {
            val (vm, repo) = fixture()
            // First enable so there is something to turn off.
            vm.onNetworkAccessToggle(requestedOn = true)
            vm.confirmNetworkAccess()
            advanceUntilIdle()
            assertTrue(repo.networkAccessEnabled.first())

            vm.onNetworkAccessToggle(requestedOn = false)
            advanceUntilIdle()
            assertFalse(vm.showNetworkWarning.value)
            assertFalse(repo.networkAccessEnabled.first())
        }
}
