package org.searchmob.ui.theme

import androidx.compose.ui.graphics.Color

// Static fallback palette used on devices without dynamic color (< Android 12) or when the user has
// turned dynamic color off. Brand accents.
val SearchMobPrimary = Color(0xFF3D5AFE)
val SearchMobSecondary = Color(0xFF00BFA5)
val SearchMobTertiary = Color(0xFF7C4DFF)

val SearchMobPrimaryDark = Color(0xFF8C9EFF)
val SearchMobSecondaryDark = Color(0xFF64FFDA)
val SearchMobTertiaryDark = Color(0xFFB388FF)

// Neutral surfaces/backgrounds with on-colors chosen for WCAG AA body-text contrast.
// Light: near-black text (0xFF1B1B1F) on near-white surface (0xFFFDFBFF) ~= 16:1.
val SearchMobLightBackground = Color(0xFFFDFBFF)
val SearchMobLightSurface = Color(0xFFFDFBFF)
val SearchMobLightOnBackground = Color(0xFF1B1B1F)
val SearchMobLightOnSurface = Color(0xFF1B1B1F)
val SearchMobLightOnPrimary = Color(0xFFFFFFFF)

// Dark: near-white text (0xFFE4E2E6) on near-black surface (0xFF1B1B1F) ~= 14:1.
val SearchMobDarkBackground = Color(0xFF1B1B1F)
val SearchMobDarkSurface = Color(0xFF1B1B1F)
val SearchMobDarkOnBackground = Color(0xFFE4E2E6)
val SearchMobDarkOnSurface = Color(0xFFE4E2E6)
val SearchMobDarkOnPrimary = Color(0xFF00105C)
