package org.searchmob.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified

// The base Material 3 type scale. The font-size preference scales every style on top of this; the
// system font scale is honored separately by Compose, so the two multiply.
val Typography = Typography()

/**
 * A copy of [base] with every text style's font size and line height multiplied by
 * `fontPointSize / DEFAULT_FONT_POINT_SIZE` (12pt = 1.0x). This is the served page's `html{font-size}`
 * scaling expressed for Compose, so result and interface text grow or shrink together. Unspecified
 * sizes (a style that inherits) are left untouched so the multiply never invents a value.
 */
fun scaledTypography(
    fontPointSize: Int,
    base: Typography = Typography,
): Typography {
    val factor = clampFontPointSize(fontPointSize) / DEFAULT_FONT_POINT_SIZE.toFloat()
    if (factor == 1f) return base

    fun scale(style: TextStyle): TextStyle =
        style.copy(
            fontSize = style.fontSize.scaleBy(factor),
            lineHeight = style.lineHeight.scaleBy(factor),
        )
    return Typography(
        displayLarge = scale(base.displayLarge),
        displayMedium = scale(base.displayMedium),
        displaySmall = scale(base.displaySmall),
        headlineLarge = scale(base.headlineLarge),
        headlineMedium = scale(base.headlineMedium),
        headlineSmall = scale(base.headlineSmall),
        titleLarge = scale(base.titleLarge),
        titleMedium = scale(base.titleMedium),
        titleSmall = scale(base.titleSmall),
        bodyLarge = scale(base.bodyLarge),
        bodyMedium = scale(base.bodyMedium),
        bodySmall = scale(base.bodySmall),
        labelLarge = scale(base.labelLarge),
        labelMedium = scale(base.labelMedium),
        labelSmall = scale(base.labelSmall),
    )
}

private fun TextUnit.scaleBy(factor: Float): TextUnit = if (isSpecified) this * factor else this
