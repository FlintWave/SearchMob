package org.searchmob.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

internal val DarkColors =
    darkColorScheme(
        primary = SearchMobPrimaryDark,
        onPrimary = SearchMobDarkOnPrimary,
        secondary = SearchMobSecondaryDark,
        tertiary = SearchMobTertiaryDark,
        background = SearchMobDarkBackground,
        onBackground = SearchMobDarkOnBackground,
        surface = SearchMobDarkSurface,
        onSurface = SearchMobDarkOnSurface,
    )

internal val LightColors =
    lightColorScheme(
        primary = SearchMobPrimary,
        onPrimary = SearchMobLightOnPrimary,
        secondary = SearchMobSecondary,
        tertiary = SearchMobTertiary,
        background = SearchMobLightBackground,
        onBackground = SearchMobLightOnBackground,
        surface = SearchMobLightSurface,
        onSurface = SearchMobLightOnSurface,
    )

/**
 * SearchMob Material 3 theme.
 *
 * Theme precedence is resolved in one place:
 * - Material You dynamic color is used only on API 31+ when [dynamicColor] is on (it overrides the
 *   named theme), in the light/dark variant chosen by [resolveDarkTheme];
 * - otherwise the active named theme is resolved by the two-slot rule ([resolveActiveTheme]): the
 *   [lightThemeId] slot, the [darkThemeId] slot, or (for [ThemeMode.SYSTEM]) the one matching the OS
 *   scheme, and its [colorSchemeFor] scheme is applied. The two SearchMob defaults keep the existing
 *   [LightColors]/[DarkColors] look, so the default appearance is unchanged.
 *
 * [fontPointSize] scales the whole type scale (12pt = 1.0x) on top of the system font scale, which
 * Compose still honors separately.
 */
@Composable
fun SearchMobTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    lightThemeId: String = DEFAULT_LIGHT_ID,
    darkThemeId: String = DEFAULT_DARK_ID,
    fontPointSize: Int = DEFAULT_FONT_POINT_SIZE,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = resolveDarkTheme(themeMode, systemDark)
    val paletteSource = resolvePaletteSource(dynamicColor, Build.VERSION.SDK_INT)

    val colorScheme =
        when {
            paletteSource == PaletteSource.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            else -> colorSchemeFor(resolveActiveTheme(themeMode, lightThemeId, darkThemeId, systemDark))
        }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = scaledTypography(fontPointSize),
        content = content,
    )
}
