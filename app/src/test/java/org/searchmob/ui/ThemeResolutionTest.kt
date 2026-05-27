package org.searchmob.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import org.searchmob.ui.theme.PaletteSource
import org.searchmob.ui.theme.ThemeMode
import org.searchmob.ui.theme.resolveDarkTheme
import org.searchmob.ui.theme.resolvePaletteSource

/** Pure unit tests for the centralized theme-precedence logic (the resolveColorScheme inputs). */
class ThemeResolutionTest {
    @Test
    fun explicitDark_beatsLightSystem() {
        assertEquals(true, resolveDarkTheme(ThemeMode.DARK, systemDark = false))
    }

    @Test
    fun explicitLight_beatsDarkSystem() {
        assertEquals(false, resolveDarkTheme(ThemeMode.LIGHT, systemDark = true))
    }

    @Test
    fun followSystem_tracksSystemDark() {
        assertEquals(true, resolveDarkTheme(ThemeMode.SYSTEM, systemDark = true))
        assertEquals(false, resolveDarkTheme(ThemeMode.SYSTEM, systemDark = false))
    }

    @Test
    fun dynamicColor_appliedOnApi31Plus() {
        assertEquals(PaletteSource.DYNAMIC, resolvePaletteSource(dynamicColorEnabled = true, sdkInt = 31))
        assertEquals(PaletteSource.DYNAMIC, resolvePaletteSource(dynamicColorEnabled = true, sdkInt = 34))
    }

    @Test
    fun dynamicColor_fallsBackBelowApi31_withoutCrash() {
        assertEquals(PaletteSource.BUILT_IN, resolvePaletteSource(dynamicColorEnabled = true, sdkInt = 26))
    }

    @Test
    fun dynamicColor_disabled_usesBuiltIn() {
        assertEquals(PaletteSource.BUILT_IN, resolvePaletteSource(dynamicColorEnabled = false, sdkInt = 34))
    }

    @Test
    fun themeMode_fromName_isTolerant() {
        assertEquals(ThemeMode.DARK, ThemeMode.fromName("DARK"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromName("bogus"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromName(null))
    }
}
