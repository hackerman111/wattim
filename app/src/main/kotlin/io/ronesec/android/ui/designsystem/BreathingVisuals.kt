package io.ronesec.android.ui.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
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
    const val FILL_ALPHA = 0.25f
    const val FILL_SEGMENT_MS = 1_600L
    const val FILL_MIN_HEIGHT = 0.15f
    const val FILL_MAX_HEIGHT = 0.85f

    const val PULSE_CYCLE_MS = 4_500L
    const val WAVE_CYCLE_1_MS = 4_000L
    const val WAVE_CYCLE_2_MS = 5_500L
    const val RIPPLE_CYCLE_MS = 4_000L
    const val RIPPLE_RING_COUNT = 4
    const val ORBIT_NODE_COUNT = 12
    const val ORBIT_CYCLE_MS = 4_000L

    // Legacy constants retained for backward compatibility
    const val PARTICLE_COUNT = 20
    const val OUTER_HALO_ALPHA = 0.15f
    const val INNER_HALO_ALPHA = 0.35f
    const val OUTER_HALO_MULTIPLIER = 1.35f
    const val INNER_HALO_MULTIPLIER = 1.15f
    const val PARTICLE_RADIUS_RATIO = 0.035f
    const val BASE_RADIUS_FRACTION = 0.40f
    const val CORE_BASE_RATIO = 0.32f
    const val CORE_GROWTH_RATIO = 0.68f
    const val WAVE_CYCLE_MS = 6_000L
    const val WAVE_AMPLITUDE_FRACTION = 0.08f
    const val FILL_2_SEGMENT_MS = 1_600L
    const val FILL_2_MIN_HEIGHT = 0.15f
    const val FILL_2_MAX_HEIGHT = 0.85f

    fun fillTargetHeight(segmentIndex: Long): Float {
        val norm = kotlin.random.Random(segmentIndex).nextFloat()
        return FILL_MIN_HEIGHT + norm * (FILL_MAX_HEIGHT - FILL_MIN_HEIGHT)
    }

    fun fillHeightFraction(elapsedMs: Long, progress: Float, reducedMotion: Boolean): Float {
        if (reducedMotion) return 0.5f
        if (progress >= 1.0f) return 1.0f

        val safeElapsed = elapsedMs.coerceAtLeast(0L)
        val currentSegment = safeElapsed / FILL_SEGMENT_MS
        val t = (safeElapsed % FILL_SEGMENT_MS).toFloat() / FILL_SEGMENT_MS.toFloat()
        val smoothT = t * t * (3f - 2f * t)

        val hStart = fillTargetHeight(currentSegment)
        val hEnd = fillTargetHeight(currentSegment + 1L)

        return hStart + (hEnd - hStart) * smoothT
    }

    fun fill2TargetHeight(segmentIndex: Long): Float = fillTargetHeight(segmentIndex)

    fun fill2HeightFraction(elapsedMs: Long, progress: Float, reducedMotion: Boolean): Float =
        fillHeightFraction(elapsedMs, progress, reducedMotion)

    fun pulseSine(elapsedMs: Long, progress: Float, reducedMotion: Boolean): Float {
        if (reducedMotion) return 0.5f
        if (progress >= 1.0f) return 1.0f
        val safeElapsed = elapsedMs.coerceAtLeast(0L)
        val cycleT = (safeElapsed % PULSE_CYCLE_MS).toFloat() / PULSE_CYCLE_MS.toFloat()
        return ((sin(cycleT * 2.0 * PI - PI / 2.0) + 1.0) / 2.0).toFloat()
    }

    fun rippleFraction(elapsedMs: Long, ringIndex: Int, reducedMotion: Boolean): Float {
        if (reducedMotion) return (ringIndex + 1).toFloat() / (RIPPLE_RING_COUNT + 1).toFloat()
        val offsetMs = ringIndex * (RIPPLE_CYCLE_MS / RIPPLE_RING_COUNT)
        val safeElapsed = (elapsedMs.coerceAtLeast(0L) + offsetMs) % RIPPLE_CYCLE_MS
        return safeElapsed.toFloat() / RIPPLE_CYCLE_MS.toFloat()
    }

    fun wavePhaseRadians(elapsedMs: Long): Float {
        val cyclePositionMs = elapsedMs.coerceAtLeast(0L) % WAVE_CYCLE_MS
        return (cyclePositionMs.toDouble() / WAVE_CYCLE_MS.toDouble() * 2.0 * PI).toFloat()
    }
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
        val effectiveProgress = if (reducedMotion) 0.5f else progress.coerceIn(0f, 1.2f)
        val effectiveElapsed = if (reducedMotion) 0L else elapsedMs

        when (style) {
            AnimationMode.FILL, AnimationMode.FILL_2 -> {
                val fraction = BreathingGeometry.fillHeightFraction(
                    elapsedMs = effectiveElapsed,
                    progress = effectiveProgress,
                    reducedMotion = reducedMotion
                )
                drawFill(fraction, colors)
            }
            AnimationMode.PULSE -> {
                val sine = BreathingGeometry.pulseSine(
                    elapsedMs = effectiveElapsed,
                    progress = effectiveProgress,
                    reducedMotion = reducedMotion
                )
                drawPulse(sine, colors)
            }
            AnimationMode.WAVE -> drawFluidWave(effectiveElapsed, effectiveProgress, reducedMotion, colors)
            AnimationMode.CIRCLE, AnimationMode.ORBIT -> drawCelestialOrbit(effectiveElapsed, effectiveProgress, reducedMotion, colors)
            AnimationMode.RIPPLE -> drawZenRipples(effectiveElapsed, effectiveProgress, reducedMotion, colors)
        }
    }
}

fun DrawScope.drawRandomFill(fraction: Float, colors: WattimColors) {
    drawFill(fraction, colors)
}

fun DrawScope.drawFill(fraction: Float, colors: WattimColors) {
    val fillHeight = size.height * fraction.coerceIn(0f, 1f)
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

fun DrawScope.drawPulse(sine: Float, colors: WattimColors) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val maxR = 0.40f * min(size.width, size.height)
    val r = maxR * (0.35f + 0.45f * sine)

    // Outer halo
    drawCircle(
        color = colors.accent.copy(alpha = 0.12f + 0.15f * sine),
        radius = r * 1.35f,
        center = center
    )
    // Inner halo
    drawCircle(
        color = colors.accent.copy(alpha = 0.25f + 0.15f * sine),
        radius = r * 1.15f,
        center = center
    )
    // Core orb fill
    drawCircle(
        color = colors.accent.copy(alpha = 0.35f + 0.35f * sine),
        radius = r,
        center = center
    )
    // Core orb border
    drawCircle(
        color = colors.accent,
        radius = r,
        center = center,
        style = Stroke(width = 2.5f)
    )
    // Center dot
    drawCircle(
        color = colors.accent,
        radius = (r * 0.2f).coerceAtLeast(3f),
        center = center
    )
}

fun DrawScope.drawFluidWave(
    elapsedMs: Long,
    progress: Float,
    reducedMotion: Boolean,
    colors: WattimColors
) {
    val centerY = size.height * 0.55f
    val baseAmplitude = size.height * 0.06f
    val amplitude = if (reducedMotion) 0f else if (progress >= 1.0f) baseAmplitude * 0.2f else baseAmplitude

    val phase1 = if (reducedMotion) 0f else (elapsedMs % BreathingGeometry.WAVE_CYCLE_1_MS).toFloat() / BreathingGeometry.WAVE_CYCLE_1_MS.toFloat() * 2f * PI.toFloat()
    val phase2 = if (reducedMotion) 0f else (elapsedMs % BreathingGeometry.WAVE_CYCLE_2_MS).toFloat() / BreathingGeometry.WAVE_CYCLE_2_MS.toFloat() * 2f * PI.toFloat()

    val steps = 64
    val wavePath1 = Path()
    val fillPath1 = Path()

    for (step in 0..steps) {
        val xFraction = step.toFloat() / steps.toFloat()
        val x = size.width * xFraction
        val y = centerY + amplitude * sin(xFraction * 3f * PI.toFloat() + phase1)
        if (step == 0) {
            wavePath1.moveTo(x, y)
            fillPath1.moveTo(x, y)
        } else {
            wavePath1.lineTo(x, y)
            fillPath1.lineTo(x, y)
        }
    }
    fillPath1.lineTo(size.width, size.height)
    fillPath1.lineTo(0f, size.height)
    fillPath1.close()

    drawPath(
        path = fillPath1,
        color = colors.accent.copy(alpha = 0.20f)
    )
    drawPath(
        path = wavePath1,
        color = colors.accent,
        style = Stroke(width = 2f)
    )

    val wavePath2 = Path()
    for (step in 0..steps) {
        val xFraction = step.toFloat() / steps.toFloat()
        val x = size.width * xFraction
        val y = centerY + (amplitude * 0.7f) * sin(xFraction * 4.5f * PI.toFloat() + phase2)
        if (step == 0) {
            wavePath2.moveTo(x, y)
        } else {
            wavePath2.lineTo(x, y)
        }
    }
    drawPath(
        path = wavePath2,
        color = colors.accent.copy(alpha = 0.45f),
        style = Stroke(width = 1.5f)
    )
}

fun DrawScope.drawCyclicWave(elapsedMs: Long, colors: WattimColors) {
    drawFluidWave(elapsedMs, 0.5f, false, colors)
}

fun DrawScope.drawCelestialOrbit(
    elapsedMs: Long,
    progress: Float,
    reducedMotion: Boolean,
    colors: WattimColors
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val maxR = 0.38f * min(size.width, size.height)
    val nodeCount = BreathingGeometry.ORBIT_NODE_COUNT

    val baseAngle = if (reducedMotion) 0f else (elapsedMs % BreathingGeometry.ORBIT_CYCLE_MS).toFloat() / BreathingGeometry.ORBIT_CYCLE_MS.toFloat() * 2f * PI.toFloat()

    drawCircle(
        color = colors.accent.copy(alpha = 0.85f),
        radius = 4f,
        center = center
    )
    drawCircle(
        color = colors.accent.copy(alpha = 0.20f),
        radius = 12f,
        center = center
    )

    drawCircle(
        color = colors.accent.copy(alpha = 0.15f),
        radius = maxR,
        center = center,
        style = Stroke(width = 1f)
    )

    for (i in 0 until nodeCount) {
        val nodeAngle = baseAngle + (2f * PI.toFloat() * i.toFloat() / nodeCount.toFloat())
        val orbitR = if (progress >= 1.0f || reducedMotion) {
            maxR
        } else {
            maxR * (0.80f + 0.20f * sin(nodeAngle * 2f + i))
        }

        val px = center.x + orbitR * cos(nodeAngle)
        val py = center.y + orbitR * sin(nodeAngle)
        val nodeAlpha = 0.35f + 0.65f * ((i + 1).toFloat() / nodeCount.toFloat())

        drawCircle(
            color = colors.accent.copy(alpha = nodeAlpha * 0.4f),
            radius = 6f,
            center = Offset(px, py)
        )
        drawCircle(
            color = colors.accent.copy(alpha = nodeAlpha),
            radius = 3f,
            center = Offset(px, py)
        )
    }
}

fun DrawScope.drawCircleOrbit(progress: Float, elapsedMs: Long, colors: WattimColors) {
    drawCelestialOrbit(elapsedMs, progress, false, colors)
}

fun DrawScope.drawZenRipples(
    elapsedMs: Long,
    progress: Float,
    reducedMotion: Boolean,
    colors: WattimColors
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val maxR = 0.45f * min(size.width, size.height)
    val ringCount = BreathingGeometry.RIPPLE_RING_COUNT

    drawCircle(
        color = colors.accent,
        radius = 4.5f,
        center = center
    )
    drawCircle(
        color = colors.accent.copy(alpha = 0.3f),
        radius = 10f,
        center = center
    )

    if (progress >= 1.0f) {
        drawCircle(
            color = colors.accent.copy(alpha = 0.6f),
            radius = maxR * 0.5f,
            center = center,
            style = Stroke(width = 2f)
        )
        drawCircle(
            color = colors.accent.copy(alpha = 0.4f),
            radius = maxR * 0.8f,
            center = center,
            style = Stroke(width = 1.5f)
        )
        return
    }

    for (i in 0 until ringCount) {
        val t = BreathingGeometry.rippleFraction(elapsedMs, i, reducedMotion)
        val r = maxR * t
        val alpha = ((1f - t) * 0.7f).coerceIn(0f, 1f)
        if (r > 2f) {
            drawCircle(
                color = colors.accent.copy(alpha = alpha),
                radius = r,
                center = center,
                style = Stroke(width = 2f)
            )
        }
    }
}
