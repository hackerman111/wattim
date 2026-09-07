package io.ronesec.android.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ronesec.android.domain.animation.FillAnimation
import io.ronesec.android.domain.model.AnimationPhase
import io.ronesec.android.domain.model.AnimationType
import io.ronesec.android.domain.model.InterventionConfig
import io.ronesec.android.ui.components.TerminalButton
import io.ronesec.android.ui.theme.LocalAppPalette
import io.ronesec.android.ui.theme.TerminalFontFamily
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun InterventionOverlayContent(
    targetAppName: String,
    config: InterventionConfig,
    savedTimeText: String? = null,
    onClose: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    val accent = palette.accent
    val animation = remember { FillAnimation() }

    var currentProgress by remember { mutableFloatStateOf(0f) }
    var currentPhase by remember { mutableStateOf(AnimationPhase.INHALE) }
    var remainingTimeMs by remember { mutableLongStateOf(config.durationMs) }
    var elapsedMillisState by remember { mutableLongStateOf(0L) }

    // 60 FPS animation loop
    LaunchedEffect(config) {
        val startNano = System.nanoTime()
        val startMillis = System.currentTimeMillis()
        animation.start(durationMs = config.durationMs, startTimeMs = startMillis)

        while (true) {
            withFrameNanos { frameNano ->
                val elapsedMillis = (frameNano - startNano) / 1_000_000L
                val currentMillis = startMillis + elapsedMillis
                elapsedMillisState = elapsedMillis

                currentProgress = animation.easedProgress(currentMillis)
                currentPhase = animation.phase(currentMillis)
                remainingTimeMs = animation.remainingTimeMs(currentMillis)
            }
            if (currentPhase == AnimationPhase.COMPLETE) {
                break
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        // Dynamic Mindful Breathing Animations on Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            when (config.animation) {
                AnimationType.FILL, AnimationType.VERTICAL_SWEEP -> {
                    // Fluid fill rising from bottom
                    val fillHeight = size.height * currentProgress
                    val topY = size.height - fillHeight

                    drawRect(
                        color = accent.copy(alpha = 0.22f),
                        topLeft = Offset(0f, topY),
                        size = Size(size.width, fillHeight)
                    )

                    if (fillHeight > 0) {
                        drawLine(
                            color = accent,
                            start = Offset(0f, topY),
                            end = Offset(size.width, topY),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                }

                AnimationType.PULSE -> {
                    // Breathing Sphere / Pulsar
                    val centerX = size.width / 2f
                    val centerY = size.height / 2f
                    val maxRadius = size.width.coerceAtMost(size.height) * 0.40f
                    val currentRadius = maxRadius * (0.32f + 0.68f * currentProgress)

                    // Outer soft aura
                    drawCircle(
                        color = accent.copy(alpha = 0.08f * currentProgress),
                        radius = currentRadius * 1.35f,
                        center = Offset(centerX, centerY)
                    )
                    // Mid aura
                    drawCircle(
                        color = accent.copy(alpha = 0.16f * currentProgress),
                        radius = currentRadius * 1.15f,
                        center = Offset(centerX, centerY)
                    )
                    // Core breathing sphere
                    drawCircle(
                        color = accent.copy(alpha = 0.20f + 0.20f * currentProgress),
                        radius = currentRadius,
                        center = Offset(centerX, centerY)
                    )
                    // Concentric perimeter ring
                    drawCircle(
                        color = accent.copy(alpha = 0.5f + 0.5f * currentProgress),
                        radius = currentRadius,
                        center = Offset(centerX, centerY),
                        style = Stroke(width = 2.5.dp.toPx())
                    )
                }

                AnimationType.CIRCLE, AnimationType.HORIZONTAL_SWEEP -> {
                    // Zen Orbit / Particle Vortex
                    val centerX = size.width / 2f
                    val centerY = size.height / 2f
                    val baseRadius = size.width.coerceAtMost(size.height) * 0.35f
                    val orbitRadius = baseRadius * (0.45f + 0.55f * currentProgress)
                    val particleCount = 20
                    val rotationAngle = (elapsedMillisState / 20f) % 360f

                    // Soft orbital guide
                    drawCircle(
                        color = accent.copy(alpha = 0.12f * (0.5f + 0.5f * currentProgress)),
                        radius = orbitRadius,
                        center = Offset(centerX, centerY),
                        style = Stroke(width = 1.5.dp.toPx())
                    )

                    // Orbiting luminous particles
                    for (i in 0 until particleCount) {
                        val angleDeg = (i * (360.0 / particleCount) + rotationAngle).toFloat()
                        val angleRad = Math.toRadians(angleDeg.toDouble())
                        val px = (centerX + orbitRadius * cos(angleRad)).toFloat()
                        val py = (centerY + orbitRadius * sin(angleRad)).toFloat()
                        val fraction = (i.toFloat() / particleCount)
                        val particleAlpha = (0.25f + 0.75f * fraction) * (0.4f + 0.6f * currentProgress)

                        drawCircle(
                            color = accent.copy(alpha = particleAlpha),
                            radius = (2.5.dp.toPx() + 2.dp.toPx() * currentProgress),
                            center = Offset(px, py)
                        )
                    }
                }
            }
        }

        // Overlay UI content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header: Target app title & phase / countdown
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = targetAppName.uppercase(),
                    fontFamily = TerminalFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    letterSpacing = 0.18.sp,
                    color = palette.textPrimary
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when (currentPhase) {
                            AnimationPhase.INHALE -> "ВДОХ"
                            AnimationPhase.EXHALE -> "ВЫДОХ"
                            AnimationPhase.COMPLETE -> "ГОТОВО"
                        },
                        fontFamily = TerminalFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        letterSpacing = 0.15.sp,
                        color = accent
                    )

                    val seconds = remainingTimeMs / 1000f
                    Text(
                        text = String.format(Locale.US, "%04.1f", seconds),
                        fontFamily = TerminalFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        letterSpacing = 0.05.sp,
                        color = palette.textPrimary
                    )
                }
            }

            // Phrase & Life Saved Notification
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = config.phrase,
                    fontFamily = TerminalFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 22.sp,
                    lineHeight = 32.sp,
                    letterSpacing = 0.04.sp,
                    textAlign = TextAlign.Center,
                    color = palette.textPrimary,
                    maxLines = 3
                )

                if (!savedTimeText.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .background(palette.surfaceElevated, RoundedCornerShape(16.dp))
                            .border(1.dp, palette.border, RoundedCornerShape(16.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "🌱 $savedTimeText",
                            fontFamily = TerminalFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            color = accent,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Action buttons: Strictly visible ONLY when animation has COMPLETED
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                contentAlignment = Alignment.Center
            ) {
                if (currentPhase == AnimationPhase.COMPLETE) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        TerminalButton(
                            text = "ЗАКРЫТЬ",
                            onClick = onClose,
                            isPrimary = false,
                            modifier = Modifier.weight(1f)
                        )

                        TerminalButton(
                            text = "ПРОДОЛЖИТЬ",
                            onClick = onContinue,
                            isPrimary = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
