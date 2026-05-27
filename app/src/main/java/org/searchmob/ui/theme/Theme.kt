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

private val DarkColors =
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

private val LightColors =
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
 * Theme precedence is resolved in one place via [resolveDarkTheme] and [resolvePaletteSource]:
 * - an explicit [ThemeMode.LIGHT]/[ThemeMode.DARK] override beats the system setting;
 * - [ThemeMode.SYSTEM] tracks [isSystemInDarkTheme];
 * - Material You dynamic color is used only on API 31+ when [dynamicColor] is on, in the resolved
 *   light/dark variant; otherwise the bundled WCAG-AA [LightColors]/[DarkColors] are used.
 *
 * The font scale is intentionally not overridden, so the app respects the system font scale.
 */
@Composable
fun SearchMobTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val darkTheme = resolveDarkTheme(themeMode, isSystemInDarkTheme())
    val paletteSource = resolvePaletteSource(dynamicColor, Build.VERSION.SDK_INT)

    val colorScheme =
        when {
            paletteSource == PaletteSource.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            darkTheme -> DarkColors
            else -> LightColors
        }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
