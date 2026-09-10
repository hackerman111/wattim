package io.ronesec.android.ui.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.ronesec.domain.model.AnimationMode
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

object BreathingGeometry {
    const val PARTICLE_COUNT = 20
    const val OUTER_HALO_ALPHA = 0.15f
    const val INNER_HALO_ALPHA = 0.35f
    const val OUTER_HALO_MULTIPLIER = 1.35f
    const val INNER_HALO_MULTIPLIER = 1.15f
    const val PARTICLE_RADIUS_RATIO = 0.035f
    const val FILL_ALPHA = 0.25f
    const val BASE_RADIUS_FRACTION = 0.40f
    const val CORE_BASE_RATIO = 0.32f
    const val CORE_GROWTH_RATIO = 0.68f
}

@Composable
fun BreathingCanvas(
    style: AnimationMode,
    progress: Float,
    elapsedMs: Long,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = false,
    contentDesc: String = "Breathing animation"
) {
    val colors = WattimTheme.colors

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .semantics { contentDescription = contentDesc }
    ) {
        val effectiveProgress = if (reducedMotion) 0.5f else progress.coerceIn(0f, 1f)
        val effectiveElapsed = if (reducedMotion) 0L else elapsedMs

        when (style) {
            AnimationMode.FILL -> drawFill(effectiveProgress, colors)
            AnimationMode.PULSE -> drawPulse(effectiveProgress, colors)
            AnimationMode.CIRCLE -> drawCircleOrbit(effectiveProgress, effectiveElapsed, colors)
        }
    }
}

fun DrawScope.drawFill(progress: Float, colors: WattimColors) {
    val fillHeight = size.height * progress
    val topY = size.height - fillHeight

    drawRect(
        color = colors.accent.copy(alpha = BreathingGeometry.FILL_ALPHA),
        topLeft = Offset(0f, topY),
        size = Size(size.width, fillHeight)
    )

    if (fillHeight > 0f) {
        drawLine(
            color = colors.accent,
            start = Offset(0f, topY),
            end = Offset(size.width, topY),
            strokeWidth = 2f
        )
    }
}

fun DrawScope.drawPulse(progress: Float, colors: WattimColors) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val bigR = BreathingGeometry.BASE_RADIUS_FRACTION * min(size.width, size.height)
    val r = bigR * (BreathingGeometry.CORE_BASE_RATIO + BreathingGeometry.CORE_GROWTH_RATIO * progress)

    val outerAlpha = (BreathingGeometry.OUTER_HALO_ALPHA * progress).coerceIn(0f, 1f)
    drawCircle(
        color = colors.accent.copy(alpha = outerAlpha),
        radius = r * BreathingGeometry.OUTER_HALO_MULTIPLIER,
        center = center
    )

    val innerAlpha = (BreathingGeometry.INNER_HALO_ALPHA * progress).coerceIn(0f, 1f)
    drawCircle(
        color = colors.accent.copy(alpha = innerAlpha),
        radius = r * BreathingGeometry.INNER_HALO_MULTIPLIER,
        center = center
    )

    drawCircle(
        color = colors.accent.copy(alpha = 0.85f),
        radius = r,
        center = center
    )

    drawCircle(
        color = colors.accent,
        radius = r,
        center = center,
        style = Stroke(width = 2f)
    )
}

fun DrawScope.drawCircleOrbit(progress: Float, elapsedMs: Long, colors: WattimColors) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val bigR = BreathingGeometry.BASE_RADIUS_FRACTION * min(size.width, size.height)
    val orbitR = bigR * (BreathingGeometry.CORE_BASE_RATIO + BreathingGeometry.CORE_GROWTH_RATIO * progress)
    val particleRadius = BreathingGeometry.PARTICLE_RADIUS_RATIO * bigR

    for (i in 0 until BreathingGeometry.PARTICLE_COUNT) {
        val angleDeg = (360f * i / BreathingGeometry.PARTICLE_COUNT) + (elapsedMs / 20f)
        val angleRad = angleDeg * (PI / 180.0)

        val px = center.x + orbitR * cos(angleRad).toFloat()
        val py = center.y + orbitR * sin(angleRad).toFloat()

        val particleAlpha = 0.15f + 0.85f * (i.toFloat() / (BreathingGeometry.PARTICLE_COUNT - 1).toFloat())

        drawCircle(
            color = colors.accent.copy(alpha = particleAlpha.coerceIn(0f, 1f)),
            radius = particleRadius,
            center = Offset(px, py)
        )
    }
}
