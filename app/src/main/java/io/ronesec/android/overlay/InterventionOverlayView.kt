package io.ronesec.android.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ronesec.android.domain.animation.FillAnimation
import io.ronesec.android.domain.model.AnimationPhase
import io.ronesec.android.domain.model.InterventionConfig
import io.ronesec.android.ui.components.TerminalButton
import io.ronesec.android.ui.theme.LocalTerminalAccent
import io.ronesec.android.ui.theme.TerminalBackground
import io.ronesec.android.ui.theme.TerminalBorder
import io.ronesec.android.ui.theme.TerminalFontFamily
import io.ronesec.android.ui.theme.TerminalTextPrimary
import io.ronesec.android.ui.theme.TerminalTextSecondary
import java.util.Locale

@Composable
fun InterventionOverlayContent(
    targetAppName: String,
    config: InterventionConfig,
    onClose: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalTerminalAccent.current
    val animation = remember { FillAnimation() }

    var currentProgress by remember { mutableFloatStateOf(0f) }
    var currentPhase by remember { mutableStateOf(AnimationPhase.INHALE) }
    var remainingTimeMs by remember { mutableLongStateOf(config.durationMs) }

    // 60 FPS animation loop
    LaunchedEffect(config) {
        val startNano = System.nanoTime()
        val startMillis = System.currentTimeMillis()
        animation.start(durationMs = config.durationMs, startTimeMs = startMillis)

        while (true) {
            withFrameNanos { frameNano ->
                val elapsedMillis = (frameNano - startNano) / 1_000_000L
                val currentMillis = startMillis + elapsedMillis

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
            .background(TerminalBackground)
    ) {
        // Dynamic breathing fill rising from bottom
        Canvas(modifier = Modifier.fillMaxSize()) {
            val fillHeight = size.height * currentProgress
            val topY = size.height - fillHeight

            // Fill body
            drawRect(
                color = accent.copy(alpha = 0.22f),
                topLeft = Offset(0f, topY),
                size = Size(size.width, fillHeight)
            )

            // Fill boundary line
            if (fillHeight > 0) {
                drawLine(
                    color = accent,
                    start = Offset(0f, topY),
                    end = Offset(size.width, topY),
                    strokeWidth = 2.dp.toPx()
                )
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
                    color = TerminalTextPrimary
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when (currentPhase) {
                            AnimationPhase.INHALE -> "INHALE"
                            AnimationPhase.EXHALE -> "EXHALE"
                            AnimationPhase.COMPLETE -> "COMPLETE"
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
                        color = TerminalTextPrimary
                    )
                }
            }

            // Phrase: Static centered custom text (up to 3 lines, 1-80 chars)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = config.phrase,
                    fontFamily = TerminalFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 22.sp,
                    lineHeight = 32.sp,
                    letterSpacing = 0.04.sp,
                    textAlign = TextAlign.Center,
                    color = TerminalTextPrimary,
                    maxLines = 3
                )
            }

            // Action buttons: Strictly visible ONLY when animation has COMPLETED (AC-06)
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
                            text = "CLOSE",
                            onClick = onClose,
                            isPrimary = false,
                            modifier = Modifier.weight(1f)
                        )

                        TerminalButton(
                            text = "CONTINUE",
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
