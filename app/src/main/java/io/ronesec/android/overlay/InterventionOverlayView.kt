package io.ronesec.android.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ronesec.android.domain.animation.FillAnimation
import io.ronesec.android.domain.model.AnimationPhase
import io.ronesec.android.domain.model.InterventionConfig
import io.ronesec.android.ui.components.TerminalButton
import io.ronesec.android.ui.i18n.LocalAppStrings
import io.ronesec.android.ui.theme.LocalAppPalette
import io.ronesec.android.ui.theme.TerminalFontFamily
import java.util.Locale

@Composable
fun InterventionOverlayContent(
    targetAppName: String,
    config: InterventionConfig,
    savedTimeText: String? = null,
    onEmergencyAccess: ((durationMs: Long?, disableTarget: Boolean) -> Unit)? = null,
    onClose: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    val accent = palette.accent
    val strings = LocalAppStrings.current
    val animation = remember { FillAnimation() }

    var currentProgress by remember { mutableFloatStateOf(0f) }
    var currentPhase by remember { mutableStateOf(AnimationPhase.INHALE) }
    var remainingTimeMs by remember { mutableLongStateOf(config.durationMs) }
    var elapsedMillisState by remember { mutableLongStateOf(0L) }
    var showEmergencyConfirmDialog by remember { mutableStateOf(false) }

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
        // Dynamic Mindful Breathing Animations on Canvas (draw phase state reads)
        InterventionCanvas(
            animationType = config.animation,
            accentColor = accent,
            progressProvider = { currentProgress },
            elapsedMillisProvider = { elapsedMillisState }
        )

        // Overlay UI content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
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
                            AnimationPhase.INHALE -> strings.phaseInhale.uppercase()
                            AnimationPhase.EXHALE -> strings.phaseExhale.uppercase()
                            AnimationPhase.COMPLETE -> strings.readyBadge
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

            // Action buttons: immediate exit during breathing, or close/continue when complete
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (currentPhase == AnimationPhase.INHALE || currentPhase == AnimationPhase.EXHALE) {
                    TerminalButton(
                        text = strings.exitButton,
                        onClick = onClose,
                        isPrimary = false,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "⚡ ${strings.emergencyButton}",
                        fontFamily = TerminalFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.1.sp,
                        color = palette.textSecondary,
                        modifier = Modifier
                            .clickable { showEmergencyConfirmDialog = true }
                            .padding(vertical = 4.dp, horizontal = 8.dp)
                    )
                } else if (currentPhase == AnimationPhase.COMPLETE) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        TerminalButton(
                            text = strings.cancelButton,
                            onClick = onClose,
                            isPrimary = false,
                            modifier = Modifier.weight(1f)
                        )
                        TerminalButton(
                            text = strings.continueButton,
                            onClick = onContinue,
                            isPrimary = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        EmergencyConfirmDialog(
            visible = showEmergencyConfirmDialog,
            targetAppName = targetAppName,
            onDismiss = { showEmergencyConfirmDialog = false },
            onEmergencyAccess = onEmergencyAccess
        )
    }
}
