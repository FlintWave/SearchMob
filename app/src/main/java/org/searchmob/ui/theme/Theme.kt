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
        secondary = SearchMobSecondaryDark,
        tertiary = SearchMobTertiaryDark,
    )

private val LightColors =
    lightColorScheme(
        primary = SearchMobPrimary,
        secondary = SearchMobSecondary,
        tertiary = SearchMobTertiary,
    )

/**
 * SearchMob Material 3 theme.
 *
 * Follows the system light/dark setting. Uses Material You dynamic color on Android 12+
 * (API 31) when available; falls back to the bundled static [LightColors]/[DarkColors] below.
 * Explicit user theme override (light/dark/auto, custom accent) arrives in the UI/theming phase.
 */
@Composable
fun SearchMobTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
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
