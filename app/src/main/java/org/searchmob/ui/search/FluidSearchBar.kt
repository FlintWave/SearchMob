package org.searchmob.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.drawWithContent
import kotlinx.coroutines.isActive

@Composable
fun FluidSearchBar(
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var showOverlay by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var timeMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(isLoading) {
        if (isLoading) {
            showOverlay = true
            progress = 0f
            val startNs = withFrameNanos { it }
            while (isActive) {
                withFrameNanos { nanos ->
                    val elapsed = (nanos - startNs) / 1_000_000L
                    timeMs = elapsed
                    progress = easeProgress((elapsed / 3000f).coerceAtMost(1f))
                }
            }
        } else if (showOverlay) {
            val snap = progress
            val startNs = withFrameNanos { it }
            while (true) {
                var done = false
                withFrameNanos { nanos ->
                    val t = ((nanos - startNs) / 1_000_000L / 200f).coerceIn(0f, 1f)
                    timeMs = (nanos - startNs) / 1_000_000L
                    progress = snap + (1f - snap) * t
                    if (t >= 1f) done = true
                }
                if (done) break
            }
            showOverlay = false
            progress = 0f
        }
    }

    val path = remember { Path() }

    Box(modifier = modifier) {
        content()
        if (showOverlay) {
            val primary = MaterialTheme.colorScheme.primary
            Canvas(Modifier.matchParentSize()) {
                val w = size.width
                val h = size.height
                val timeSec = timeMs / 1000f
                val stepPx = 2f
                for (layer in THREE_LAYERS) {
                    path.reset()
                    val fillX = (progress * w).coerceAtLeast(0f)
                    if (fillX <= 0f) continue

                    var x = 0f
                    val firstY = waveY(0f, w, h, layer, timeSec, progress)
                    path.moveTo(0f, firstY)
                    x += stepPx
                    while (x < fillX) {
                        path.lineTo(x, waveY(x, w, h, layer, timeSec, progress))
                        x += stepPx
                    }
                    path.lineTo(fillX, waveY(fillX, w, h, layer, timeSec, progress))
                    path.lineTo(fillX, h)
                    path.lineTo(0f, h)
                    path.close()

                    drawPath(
                        path,
                        color = primary.copy(alpha = layer.alpha),
                    )
                }
            }
        }
    }
}

@Composable
fun SlideUpResults(visible: Boolean, content: @Composable () -> Unit) {
    val borderAlpha = remember { Animatable(1f) }
    val accentColor = MaterialTheme.colorScheme.primary

    LaunchedEffect(visible) {
        if (visible) {
            borderAlpha.snapTo(1f)
            borderAlpha.animateTo(0f, animationSpec = tween(durationMillis = 300))
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(animationSpec = tween(300)) { it } + fadeIn(tween(300)),
    ) {
        Box(
            Modifier.drawWithContent {
                drawContent()
                val a = borderAlpha.value
                if (a > 0f) {
                    drawRect(
                        color = accentColor.copy(alpha = a),
                        topLeft = Offset.Zero,
                        size = Size(size.width, 2.dp.toPx()),
                    )
                }
            },
        ) {
            content()
        }
    }
}
