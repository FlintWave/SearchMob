package org.searchmob.ui.theme

/** User-selectable theme mode. An explicit [LIGHT]/[DARK] override beats the system setting. */
enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM,
    ;

    companion object {
        /** Parse a persisted name, tolerating unknown/legacy values by falling back to [SYSTEM]. */
        fun fromName(name: String?): ThemeMode = entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

/**
 * Which built-in scheme variant should be active given the mode and the system dark flag. This is the
 * single, pure, unit-testable place where "explicit override beats system" is decided.
 *
 * - [ThemeMode.LIGHT] -> false (light), regardless of [systemDark].
 * - [ThemeMode.DARK]  -> true (dark), regardless of [systemDark].
 * - [ThemeMode.SYSTEM] -> tracks [systemDark].
 */
fun resolveDarkTheme(
    mode: ThemeMode,
    systemDark: Boolean,
): Boolean =
    when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> systemDark
    }

/**
 * Which palette source the theme should use. Pure and unit-testable, mirroring the precedence rules:
 * dynamic color is only used on API 31+ when enabled; otherwise the built-in scheme is used. The
 * resolved light/dark variant always comes from [resolveDarkTheme] so the user's Light/Dark override
 * still governs which dynamic variant is applied.
 */
enum class PaletteSource { DYNAMIC, BUILT_IN }

fun resolvePaletteSource(
    dynamicColorEnabled: Boolean,
    sdkInt: Int,
    dynamicColorMinSdk: Int = 31,
): PaletteSource =
    if (dynamicColorEnabled && sdkInt >= dynamicColorMinSdk) PaletteSource.DYNAMIC else PaletteSource.BUILT_IN
