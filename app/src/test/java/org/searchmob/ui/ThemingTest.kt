package org.searchmob.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.searchmob.ui.theme.APP_THEMES
import org.searchmob.ui.theme.APP_THEMES_BY_ID
import org.searchmob.ui.theme.DARK_THEME_IDS
import org.searchmob.ui.theme.DEFAULT_DARK_ID
import org.searchmob.ui.theme.DEFAULT_FONT_POINT_SIZE
import org.searchmob.ui.theme.DEFAULT_LIGHT_ID
import org.searchmob.ui.theme.LIGHT_THEME_IDS
import org.searchmob.ui.theme.MAX_FONT_POINT_SIZE
import org.searchmob.ui.theme.MIN_FONT_POINT_SIZE
import org.searchmob.ui.theme.ThemeMode
import org.searchmob.ui.theme.ThemePaletteMode
import org.searchmob.ui.theme.clampFontPointSize
import org.searchmob.ui.theme.colorSchemeFor
import org.searchmob.ui.theme.contrastRatio
import org.searchmob.ui.theme.hexColor
import org.searchmob.ui.theme.resolveActiveTheme
import org.searchmob.ui.theme.scaledTypography

/**
 * Pure tests for the named-theme registry, the two-slot resolver, the role -> ColorScheme mapping,
 * font scaling, and WCAG contrast. Mirrors the desktop `tests/gui/test_theming.py` in spirit.
 */
class ThemingTest {
    // The exact id set the desktop ships (hardcoded so this is a real parity check, not a tautology).
    private val desktopIds =
        setOf(
            "searchmob-light",
            "searchmob-dark",
            "github-light",
            "one-dark",
            "dracula",
            "tokyo-night",
            "catppuccin-mocha",
            "catppuccin-latte",
            "gruvbox",
            "nord",
            "rose-pine-dawn",
            "obsidian-slate",
            "paper-white",
        )

    @Test
    fun registry_matchesDesktopIdSet() {
        assertEquals(desktopIds, APP_THEMES_BY_ID.keys)
        assertEquals(13, APP_THEMES.size)
    }

    @Test
    fun lightAndDarkIdLists_partitionByMode() {
        assertEquals(desktopIds, (LIGHT_THEME_IDS + DARK_THEME_IDS).toSet())
        assertTrue((LIGHT_THEME_IDS.toSet() intersect DARK_THEME_IDS.toSet()).isEmpty())
        assertTrue(LIGHT_THEME_IDS.all { APP_THEMES_BY_ID.getValue(it).mode == ThemePaletteMode.LIGHT })
        assertTrue(DARK_THEME_IDS.all { APP_THEMES_BY_ID.getValue(it).mode == ThemePaletteMode.DARK })
        // The accessibility pair: one light, one dark.
        assertTrue("paper-white" in LIGHT_THEME_IDS)
        assertTrue("obsidian-slate" in DARK_THEME_IDS)
    }

    @Test
    fun defaults_keepTheOriginalSearchMobLook() {
        assertEquals("searchmob-light", DEFAULT_LIGHT_ID)
        assertEquals("searchmob-dark", DEFAULT_DARK_ID)
        assertEquals(ThemePaletteMode.LIGHT, APP_THEMES_BY_ID.getValue(DEFAULT_LIGHT_ID).mode)
        assertEquals(ThemePaletteMode.DARK, APP_THEMES_BY_ID.getValue(DEFAULT_DARK_ID).mode)
    }

    @Test
    fun resolveActiveTheme_truthTable() {
        // Light mode -> light slot, regardless of OS scheme.
        assertEquals("github-light", resolveActiveTheme(ThemeMode.LIGHT, "github-light", "one-dark", false).id)
        assertEquals("github-light", resolveActiveTheme(ThemeMode.LIGHT, "github-light", "one-dark", true).id)
        // Dark mode -> dark slot.
        assertEquals("one-dark", resolveActiveTheme(ThemeMode.DARK, "github-light", "one-dark", false).id)
        // System -> follows the OS scheme.
        assertEquals("one-dark", resolveActiveTheme(ThemeMode.SYSTEM, "github-light", "one-dark", true).id)
        assertEquals("github-light", resolveActiveTheme(ThemeMode.SYSTEM, "github-light", "one-dark", false).id)
    }

    @Test
    fun resolveActiveTheme_fallsBackOnBadSlotIds() {
        // Unknown id -> the slot default.
        assertEquals(DEFAULT_LIGHT_ID, resolveActiveTheme(ThemeMode.LIGHT, "nope", "one-dark", false).id)
        assertEquals(DEFAULT_DARK_ID, resolveActiveTheme(ThemeMode.DARK, "github-light", "nope", false).id)
        // A dark theme placed in the light slot is rejected (mode mismatch) -> light default.
        assertEquals(DEFAULT_LIGHT_ID, resolveActiveTheme(ThemeMode.LIGHT, "one-dark", "one-dark", false).id)
    }

    @Test
    fun colorSchemeFor_mapsRolesOntoTheScheme() {
        val theme = APP_THEMES_BY_ID.getValue("dracula")
        val scheme = colorSchemeFor(theme)
        assertEquals(hexColor(theme.background), scheme.background)
        assertEquals(hexColor(theme.text), scheme.onBackground)
        assertEquals(hexColor(theme.text), scheme.onSurface)
        assertEquals(hexColor(theme.surface), scheme.surface)
        assertEquals(hexColor(theme.muted), scheme.onSurfaceVariant)
        assertEquals(hexColor(theme.accent), scheme.primary)
        assertEquals(hexColor(theme.accent), scheme.secondary)
        assertEquals(hexColor(theme.border), scheme.outline)
        // onPrimary is the readable black/white on the accent (dracula's accent is light -> dark text).
        assertTrue(scheme.onPrimary == Color.White || scheme.onPrimary == hexColor("#0b0b0c"))
    }

    @Test
    fun colorSchemeFor_defaultsKeepTheExistingSchemes() {
        // The two defaults reuse the bundled LightColors/DarkColors, so the default look is unchanged.
        val light = colorSchemeFor(APP_THEMES_BY_ID.getValue(DEFAULT_LIGHT_ID))
        val dark = colorSchemeFor(APP_THEMES_BY_ID.getValue(DEFAULT_DARK_ID))
        assertEquals(Color(0xFF3D5AFE), light.primary)
        assertEquals(Color(0xFF8C9EFF), dark.primary)
    }

    @Test
    fun scaledTypography_growsWithFontPointSize() {
        val base = scaledTypography(DEFAULT_FONT_POINT_SIZE)
        val bigger = scaledTypography(24)
        assertTrue(bigger.bodyLarge.fontSize.value > base.bodyLarge.fontSize.value)
        assertTrue(bigger.titleLarge.fontSize.value > base.titleLarge.fontSize.value)
        // 24pt is exactly 2x the 12pt default.
        assertEquals(base.bodyLarge.fontSize.value * 2f, bigger.bodyLarge.fontSize.value, 0.01f)
        // The default factor is a no-op.
        assertEquals(base.bodyLarge.fontSize.value, scaledTypography(12).bodyLarge.fontSize.value, 0.001f)
    }

    @Test
    fun clampFontPointSize_boundsTheRange() {
        assertEquals(12, clampFontPointSize(12))
        assertEquals(MIN_FONT_POINT_SIZE, clampFontPointSize(2))
        assertEquals(MAX_FONT_POINT_SIZE, clampFontPointSize(99))
    }

    @Test
    fun contrastRatio_matchesKnownExtremes() {
        assertEquals(21.0, contrastRatio("#000000", "#ffffff"), 0.01)
        assertEquals(1.0, contrastRatio("#ffffff", "#ffffff"), 0.01)
    }

    @Test
    fun everyTheme_clearsAaBodyContrast() {
        // Every theme's primary text must clear WCAG AA (4.5:1) against its background.
        APP_THEMES.forEach { theme ->
            val ratio = contrastRatio(theme.text, theme.background)
            assertTrue("${theme.id} text-on-bg only $ratio:1", ratio >= 4.5)
        }
    }

    @Test
    fun accessibilityThemes_clearAaaBodyAndAaLinks() {
        // The two accessibility themes target AAA (7:1) for body text and AA (4.5:1) for links.
        listOf("paper-white", "obsidian-slate").forEach { id ->
            val theme = APP_THEMES_BY_ID.getValue(id)
            assertTrue("$id body", contrastRatio(theme.text, theme.background) >= 7.0)
            assertTrue("$id link", contrastRatio(theme.accent, theme.background) >= 4.5)
        }
    }

    @Test
    fun searchMobDefaults_differFromSlateThemes() {
        // Sanity: the defaults are not accidentally aliased to a slate theme id.
        assertNotEquals(DEFAULT_LIGHT_ID, "github-light")
        assertNotEquals(DEFAULT_DARK_ID, "one-dark")
    }
}
