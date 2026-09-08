package io.ronesec.android.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import io.ronesec.android.domain.model.AnimationType
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun InterventionCanvas(
    animationType: AnimationType,
    accentColor: Color,
    progressProvider: () -> Float,
    elapsedMillisProvider: () -> Long,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val currentProgress = progressProvider()
        val elapsedMillis = elapsedMillisProvider()

        when (animationType) {
            AnimationType.FILL, AnimationType.VERTICAL_SWEEP -> {
                val fillHeight = size.height * currentProgress
                val topY = size.height - fillHeight

                drawRect(
                    color = accentColor.copy(alpha = 0.22f),
                    topLeft = Offset(0f, topY),
                    size = Size(size.width, fillHeight)
                )

                if (fillHeight > 0) {
                    drawLine(
                        color = accentColor,
                        start = Offset(0f, topY),
                        end = Offset(size.width, topY),
                        strokeWidth = 2.dp.toPx()
                    )
                }
            }

            AnimationType.PULSE -> {
                val centerX = size.width / 2f
                val centerY = size.height / 2f
                val maxRadius = size.width.coerceAtMost(size.height) * 0.40f
                val currentRadius = maxRadius * (0.32f + 0.68f * currentProgress)

                // Outer soft aura
                drawCircle(
                    color = accentColor.copy(alpha = 0.08f * currentProgress),
                    radius = currentRadius * 1.35f,
                    center = Offset(centerX, centerY)
                )
                // Mid aura
                drawCircle(
                    color = accentColor.copy(alpha = 0.16f * currentProgress),
                    radius = currentRadius * 1.15f,
                    center = Offset(centerX, centerY)
                )
                // Core breathing sphere
                drawCircle(
                    color = accentColor.copy(alpha = 0.20f + 0.20f * currentProgress),
                    radius = currentRadius,
                    center = Offset(centerX, centerY)
                )
                // Concentric perimeter ring
                drawCircle(
                    color = accentColor.copy(alpha = 0.5f + 0.5f * currentProgress),
                    radius = currentRadius,
                    center = Offset(centerX, centerY),
                    style = Stroke(width = 2.5.dp.toPx())
                )
            }

            AnimationType.CIRCLE, AnimationType.HORIZONTAL_SWEEP -> {
                val centerX = size.width / 2f
                val centerY = size.height / 2f
                val baseRadius = size.width.coerceAtMost(size.height) * 0.35f
                val orbitRadius = baseRadius * (0.45f + 0.55f * currentProgress)
                val particleCount = 20
                val rotationAngle = (elapsedMillis / 20f) % 360f

                // Soft orbital guide
                drawCircle(
                    color = accentColor.copy(alpha = 0.12f * (0.5f + 0.5f * currentProgress)),
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
                        color = accentColor.copy(alpha = particleAlpha),
                        radius = (2.5.dp.toPx() + 2.dp.toPx() * currentProgress),
                        center = Offset(px, py)
                    )
                }
            }
        }
    }
}
