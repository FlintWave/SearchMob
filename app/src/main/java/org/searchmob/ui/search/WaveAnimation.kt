package org.searchmob.ui.search

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

data class WaveConfig(
    val frequency: Float,
    val amplitude: Float,
    val speed: Float,
    val alpha: Float,
)

val THREE_LAYERS: List<WaveConfig> = listOf(
    WaveConfig(frequency = 2.0f, amplitude = 0.35f, speed = 1.0f, alpha = 0.45f),
    WaveConfig(frequency = 3.5f, amplitude = 0.25f, speed = 1.4f, alpha = 0.35f),
    WaveConfig(frequency = 5.0f, amplitude = 0.15f, speed = 0.7f, alpha = 0.25f),
)

fun easeProgress(rawProgress: Float): Float {
    val p = rawProgress.coerceIn(0f, 1f)
    val r = 1f - p
    return 1f - r * r * r
}

private const val FRONT_FALLOFF = 0.06f

private fun smoothstep(edge0: Float, edge1: Float, value: Float): Float {
    if (edge1 <= edge0) return if (value < edge0) 0f else 1f
    val t = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

fun waveY(
    x: Float,
    width: Float,
    height: Float,
    config: WaveConfig,
    timeSeconds: Float,
    fillFraction: Float,
): Float {
    if (width == 0f) return height * 0.5f
    val fillX = fillFraction * width
    if (x > fillX) return height * 0.5f

    val frontWidth = (abs(width) * FRONT_FALLOFF).coerceAtLeast(1f)
    val ampFactor = smoothstep(0f, frontWidth, fillX - x)

    val phase = (x / width) * config.frequency * 2.0 * PI +
        timeSeconds.toDouble() * config.speed * 2.0 * PI
    val normalizedY = (sin(phase) * config.amplitude * ampFactor).toFloat()
    return height * (0.5f + normalizedY * 0.5f)
}
