package org.searchmob.ui

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.searchmob.ui.prefs.InMemoryPreferencesStore
import org.searchmob.ui.prefs.PreferencesRepository

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingGatingTest {
    private fun repo(store: InMemoryPreferencesStore = InMemoryPreferencesStore()) = PreferencesRepository(store)

    @Test
    fun onboarding_defaultsToNotCompleted() =
        runTest {
            assertFalse(repo().onboardingCompleted.first())
        }

    @Test
    fun completing_persistsTheFlag() =
        runTest {
            val r = repo()
            r.setOnboardingCompleted(true)
            assertTrue(r.onboardingCompleted.first())
        }

    @Test
    fun completion_survivesRepositoryRebuildOverSameStore() =
        runTest {
            // Simulate a relaunch: same backing store, fresh repository instance.
            val store = InMemoryPreferencesStore()
            repo(store).setOnboardingCompleted(true)
            assertTrue(repo(store).onboardingCompleted.first())
        }
}
