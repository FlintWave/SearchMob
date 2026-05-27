package org.searchmob.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.searchmob.ui.onboarding.OnboardingProgress
import org.searchmob.ui.onboarding.OnboardingStep

class OnboardingProgressTest {
    @Test
    fun defaults_toFirstWelcomeStep() {
        val p = OnboardingProgress()
        assertEquals(OnboardingStep.WELCOME, p.step)
        assertTrue(p.isFirst)
        assertFalse(p.isLast)
    }

    @Test
    fun next_walksThroughAllStepsInOrder() {
        var p = OnboardingProgress()
        assertEquals(OnboardingStep.WELCOME, p.step)
        p = p.next()
        assertEquals(OnboardingStep.PERMISSIONS, p.step)
        p = p.next()
        assertEquals(OnboardingStep.DEFAULT_SEARCH, p.step)
        p = p.next()
        assertEquals(OnboardingStep.PRIVACY, p.step)
        assertTrue(p.isLast)
    }

    @Test
    fun next_isClampedAtLastStep() {
        val last = OnboardingProgress(OnboardingStep.entries.lastIndex)
        assertEquals(last, last.next())
    }

    @Test
    fun back_isClampedAtFirstStep() {
        val first = OnboardingProgress()
        assertEquals(first, first.back())
    }

    @Test
    fun back_retreatsOneStep() {
        val p = OnboardingProgress(2).back()
        assertEquals(1, p.index)
    }

    @Test
    fun pageCount_matchesStepCount() {
        assertEquals(OnboardingStep.entries.size, OnboardingProgress().pageCount)
    }

    @Test
    fun outOfRangeIndex_isRejected() {
        assertThrows(IllegalArgumentException::class.java) { OnboardingProgress(99) }
    }
}
