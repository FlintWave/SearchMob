package org.searchmob.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import org.searchmob.ui.about.AppVersion

/** Pure-logic tests for the version-name formatting that the About footer displays. */
class AppVersionTest {
    @Test
    fun format_passesThroughNonBlankName() {
        assertEquals("1.2.3", AppVersion.format("1.2.3"))
    }

    @Test
    fun format_nullName_fallsBackToUnknown() {
        assertEquals(AppVersion.UNKNOWN, AppVersion.format(null))
    }

    @Test
    fun format_blankName_fallsBackToUnknown() {
        assertEquals(AppVersion.UNKNOWN, AppVersion.format("   "))
    }
}
