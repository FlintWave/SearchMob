package org.searchmob.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// The single source of palette truth, shared by the Compose app and the served HTML page. Each
// AppTheme is a named, selectable theme: an id, a display name, its mode, the six role colors as hex
// strings, and an optional credit for a reused third-party palette.
//
// The two SearchMob defaults reuse the app's existing LightColors/DarkColors look (so the default
// appearance is unchanged); the eleven slate themes share the exact hex with the desktop app. The
// Compose layer derives a Material 3 ColorScheme from the roles (see colorSchemeFor); the served page
// derives CSS custom properties from the same roles (see the server's themeVars).
//
// Mirrors `searchmob_desktop.gui.theme` (the `THEMES` registry and `resolve_active_theme`).

// Theme mode partition. Reuses the names from the desktop registry so ids/modes line up.
enum class ThemePaletteMode {
    LIGHT,
    DARK,
}

/**
 * A named theme defined by its six interface roles. Colors are hex strings ("#rrggbb"): the served
 * page emits them verbatim as CSS, and Compose parses them into [Color]s.
 */
data class AppTheme(
    val id: String,
    val displayName: String,
    val mode: ThemePaletteMode,
    val background: String,
    val surface: String,
    val text: String,
    val muted: String,
    val accent: String,
    val border: String,
    // Third-party palette attribution + license, or null for SearchMob-original themes.
    val credit: String? = null,
)

const val DEFAULT_LIGHT_ID = "searchmob-light"
const val DEFAULT_DARK_ID = "searchmob-dark"

// Font size, in points. The A-/A+ controls step by 2pt; 12pt is the comfortable default base.
const val DEFAULT_FONT_POINT_SIZE = 12
const val MIN_FONT_POINT_SIZE = 8
const val MAX_FONT_POINT_SIZE = 24
const val FONT_POINT_STEP = 2

/**
 * The theme library, in display order: the two original SearchMob looks (the defaults) followed by
 * the curated slate of nine and two WCAG-AAA accessibility themes. The reused third-party palettes
 * carry an attribution credit; the SearchMob and accessibility palettes are original.
 *
 * The two defaults declare role hex that match the existing [LightColors]/[DarkColors] schemes, but
 * the Compose layer keeps using those full schemes for them (see [colorSchemeFor]); the role hex here
 * is what the served page renders, so the browser look tracks the app.
 */
val APP_THEMES: List<AppTheme> =
    listOf(
        AppTheme(
            "searchmob-light", "SearchMob Light", ThemePaletteMode.LIGHT,
            background = "#fdfbff", surface = "#fdfbff", text = "#1b1b1f",
            muted = "#6a6a75", accent = "#3d5afe", border = "#e1e2e9",
        ),
        AppTheme(
            "searchmob-dark", "SearchMob Dark", ThemePaletteMode.DARK,
            background = "#1b1b1f", surface = "#26262d", text = "#e4e2e6",
            muted = "#9a9aa4", accent = "#8c9eff", border = "#34343d",
        ),
        AppTheme(
            "github-light", "GitHub Light", ThemePaletteMode.LIGHT,
            background = "#ffffff", surface = "#f6f8fa", text = "#24292f",
            muted = "#57606a", accent = "#0969da", border = "#d0d7de",
            credit = "GitHub Primer palette (MIT)",
        ),
        AppTheme(
            "one-dark", "One Dark", ThemePaletteMode.DARK,
            background = "#282c34", surface = "#2c313a", text = "#abb2bf",
            muted = "#5c6370", accent = "#61afef", border = "#3e4451",
            credit = "One Dark Pro palette (MIT)",
        ),
        AppTheme(
            "dracula", "Dracula", ThemePaletteMode.DARK,
            background = "#282a36", surface = "#44475a", text = "#f8f8f2",
            muted = "#6272a4", accent = "#bd93f9", border = "#44475a",
            credit = "Dracula palette (MIT)",
        ),
        AppTheme(
            "tokyo-night", "Tokyo Night", ThemePaletteMode.DARK,
            background = "#1a1b2e", surface = "#24283b", text = "#c0caf5",
            muted = "#565f89", accent = "#7aa2f7", border = "#292e42",
            credit = "Tokyo Night palette (MIT)",
        ),
        AppTheme(
            "catppuccin-mocha", "Catppuccin Mocha", ThemePaletteMode.DARK,
            background = "#1e1e2e", surface = "#313244", text = "#cdd6f4",
            muted = "#7f849c", accent = "#89b4fa", border = "#45475a",
            credit = "Catppuccin palette (MIT)",
        ),
        AppTheme(
            "catppuccin-latte", "Catppuccin Latte", ThemePaletteMode.LIGHT,
            background = "#eff1f5", surface = "#e6e9ef", text = "#4c4f69",
            muted = "#9ca0b0", accent = "#1e66f5", border = "#ccd0da",
            credit = "Catppuccin palette (MIT)",
        ),
        AppTheme(
            "gruvbox", "Gruvbox", ThemePaletteMode.DARK,
            background = "#282828", surface = "#3c3836", text = "#ebdbb2",
            muted = "#928374", accent = "#83a598", border = "#504945",
            credit = "Gruvbox palette (MIT)",
        ),
        AppTheme(
            "nord", "Nord", ThemePaletteMode.DARK,
            background = "#2e3440", surface = "#3b4252", text = "#eceff4",
            muted = "#7b88a1", accent = "#88c0d0", border = "#434c5e",
            credit = "Nord palette (MIT)",
        ),
        AppTheme(
            "rose-pine-dawn", "Rose Pine Dawn", ThemePaletteMode.LIGHT,
            background = "#faf4ed", surface = "#fffaf3", text = "#575279",
            muted = "#9893a5", accent = "#d7827a", border = "#f2e9e1",
            credit = "Rose Pine palette (MIT)",
        ),
        AppTheme(
            "obsidian-slate", "Obsidian Slate", ThemePaletteMode.DARK,
            background = "#0d1117", surface = "#161b22", text = "#f0f6fc",
            muted = "#b1bac4", accent = "#58a6ff", border = "#30363d",
        ),
        AppTheme(
            "paper-white", "Paper White", ThemePaletteMode.LIGHT,
            background = "#ffffff", surface = "#f3f4f6", text = "#101010",
            muted = "#595959", accent = "#0058cc", border = "#bbbbbb",
        ),
    )

/** Themes keyed by id, for slot resolution and lookup. */
val APP_THEMES_BY_ID: Map<String, AppTheme> = APP_THEMES.associateBy { it.id }

/** The light-mode theme ids, in display order (the Settings light-slot selector list). */
val LIGHT_THEME_IDS: List<String> = APP_THEMES.filter { it.mode == ThemePaletteMode.LIGHT }.map { it.id }

/** The dark-mode theme ids, in display order (the Settings dark-slot selector list). */
val DARK_THEME_IDS: List<String> = APP_THEMES.filter { it.mode == ThemePaletteMode.DARK }.map { it.id }

/** The theme for a slot, falling back to the slot's default when the id is unknown or mismatched-mode. */
private fun slotTheme(
    themeId: String,
    mode: ThemePaletteMode,
): AppTheme {
    val theme = APP_THEMES_BY_ID[themeId]
    if (theme != null && theme.mode == mode) return theme
    return APP_THEMES_BY_ID.getValue(if (mode == ThemePaletteMode.DARK) DEFAULT_DARK_ID else DEFAULT_LIGHT_ID)
}

/**
 * Resolve the mode plus the two slot ids (plus the OS scheme for SYSTEM) to the active [AppTheme].
 * Mirrors the desktop `resolve_active_theme`: LIGHT -> light slot, DARK -> dark slot, SYSTEM follows
 * [systemDark]. An unknown or wrong-mode slot id falls back to that slot's SearchMob default.
 */
fun resolveActiveTheme(
    mode: ThemeMode,
    lightId: String,
    darkId: String,
    systemDark: Boolean,
): AppTheme =
    when (mode) {
        ThemeMode.LIGHT -> slotTheme(lightId, ThemePaletteMode.LIGHT)
        ThemeMode.DARK -> slotTheme(darkId, ThemePaletteMode.DARK)
        ThemeMode.SYSTEM ->
            if (systemDark) slotTheme(darkId, ThemePaletteMode.DARK) else slotTheme(lightId, ThemePaletteMode.LIGHT)
    }

/** Clamp a font-size preference to the supported point range (out of bounds is pulled to the bound). */
fun clampFontPointSize(pt: Int): Int = pt.coerceIn(MIN_FONT_POINT_SIZE, MAX_FONT_POINT_SIZE)

// --- Color math (pure; reused by colorSchemeFor and the WCAG contrast helper) -----------------

private fun hexToRgb(value: String): Triple<Int, Int, Int> {
    val h = value.trim().removePrefix("#")
    return Triple(h.substring(0, 2).toInt(16), h.substring(2, 4).toInt(16), h.substring(4, 6).toInt(16))
}

/** Parse a "#rrggbb" role color into a Compose [Color]. */
fun hexColor(value: String): Color {
    val (r, g, b) = hexToRgb(value)
    return Color(red = r, green = g, blue = b)
}

private fun linearize(channel: Int): Double {
    val c = channel / 255.0
    return if (c <= 0.04045) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
}

private fun luminance(value: String): Double {
    val (r, g, b) = hexToRgb(value)
    return 0.2126 * linearize(r) + 0.7152 * linearize(g) + 0.0722 * linearize(b)
}

/** WCAG contrast ratio between two "#rrggbb" colors (>= 1.0). Used by the a11y contrast tests. */
fun contrastRatio(
    a: String,
    b: String,
): Double {
    val la = luminance(a)
    val lb = luminance(b)
    val hi = maxOf(la, lb)
    val lo = minOf(la, lb)
    return (hi + 0.05) / (lo + 0.05)
}

/** Pick near-black or white for the most readable text on an accent fill. */
private fun onColor(background: String): Color {
    val nearBlack = "#0b0b0c"
    return if (contrastRatio(background, nearBlack) >= contrastRatio(background, "#ffffff")) {
        hexColor(nearBlack)
    } else {
        Color.White
    }
}

/**
 * Build a Material 3 [ColorScheme] from a theme's six roles: background/surface from the bg/surface
 * roles, on-background/on-surface from text, surfaceVariant from surface, onSurfaceVariant from muted,
 * primary/secondary/tertiary from accent (with onPrimary chosen for readability), outline/
 * outlineVariant from border. The two SearchMob defaults keep their existing full schemes so the
 * default look is byte-for-byte unchanged.
 */
fun colorSchemeFor(theme: AppTheme): ColorScheme {
    when (theme.id) {
        DEFAULT_LIGHT_ID -> return LightColors
        DEFAULT_DARK_ID -> return DarkColors
    }
    val bg = hexColor(theme.background)
    val surface = hexColor(theme.surface)
    val text = hexColor(theme.text)
    val muted = hexColor(theme.muted)
    val accent = hexColor(theme.accent)
    val border = hexColor(theme.border)
    val onAccent = onColor(theme.accent)
    val base = if (theme.mode == ThemePaletteMode.DARK) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        secondary = accent,
        onSecondary = onAccent,
        tertiary = accent,
        onTertiary = onAccent,
        background = bg,
        onBackground = text,
        surface = surface,
        onSurface = text,
        surfaceVariant = surface,
        onSurfaceVariant = muted,
        outline = border,
        outlineVariant = border,
    )
}
