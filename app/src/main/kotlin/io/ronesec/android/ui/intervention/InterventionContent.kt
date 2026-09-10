package io.ronesec.android.ui.intervention

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.ronesec.android.R
import io.ronesec.android.ui.designsystem.BreathingCanvas
import io.ronesec.android.ui.designsystem.TerminalBadge
import io.ronesec.android.ui.designsystem.TerminalButton
import io.ronesec.android.ui.designsystem.TerminalButtonVariant
import io.ronesec.android.ui.designsystem.WattimTheme
import io.ronesec.domain.breathing.BreathingPhase
import io.ronesec.domain.breathing.BreathingProgress
import io.ronesec.domain.policy.EffectiveInterventionConfig

/**
 * Shared Intervention UI composable for both real WindowManager overlay and Activity preview dialog.
 * Satisfies F34, F35, F36, F42, §2.5, §2.9, and §8.1.
 *
 * Elements:
 * - Background: breathing animation Canvas with selected mode (FILL, PULSE, CIRCLE, WAVE).
 * - Target app title in uppercase.
 * - Optional leaf saved-time badge.
 * - INHALE / EXHALE / COMPLETE phase display.
 * - Monospace countdown SS.S (suppressed from continuous TalkBack spam).
 * - 22sp phrase (up to 3 lines).
 * - During breathing: Exit button + Emergency access button.
 * - On completion: Cancel button + Continue button (autofocused once).
 * - Emergency dialog overlay when requested.
 */
@Composable
fun InterventionContent(
    config: EffectiveInterventionConfig,
    progress: BreathingProgress,
    showSavedBadge: Boolean,
    savedMinutes: Long,
    isEmergencyDialogOpen: Boolean,
    onContinue: () -> Unit,
    onExit: () -> Unit,
    onCancel: () -> Unit,
    onEmergencyClick: () -> Unit,
    onDismissEmergency: () -> Unit,
    onEmergencyOnce: () -> Unit = {},
    onEmergencyTimed: (Long) -> Unit,
    onEmergencyForever: () -> Unit,
    customEmergencyMinutes: Int? = null,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = false,
    isAutofocusEnabled: Boolean = true
) {
    val colors = WattimTheme.colors
    val typography = WattimTheme.typography

    val continueFocusRequester = remember { FocusRequester() }
    var hasRequestedFocus by remember { mutableStateOf(false) }

    // Autofocus Continue button once upon completion
    LaunchedEffect(progress.isComplete) {
        if (progress.isComplete && !hasRequestedFocus && isAutofocusEnabled) {
            hasRequestedFocus = true
            try {
                continueFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    val targetTitle = config.displayName.ifBlank { config.packageName }.uppercase()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // Breathing animation background
        BreathingCanvas(
            style = config.animation,
            progress = progress.p.toFloat(),
            elapsedMs = progress.elapsedMs,
            reducedMotion = reducedMotion,
            modifier = Modifier.fillMaxSize()
        )

        // Foreground content with cutouts and system bars padding
        Column(
            modifier = Modifier
                .fillMaxSize()
                .displayCutoutPadding()
                .systemBarsPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header: Target app title and optional saved time badge
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text(
                    text = targetTitle,
                    style = typography.titleLarge,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center
                )

                if (showSavedBadge) {
                    val badgeDesc = stringResource(R.string.cd_saved_time_badge)
                    val minUnit = stringResource(R.string.unit_min)
                    TerminalBadge(
                        text = "🌿 $savedMinutes $minUnit",
                        isActive = true,
                        modifier = Modifier.semantics {
                            contentDescription = "$badgeDesc: $savedMinutes $minUnit"
                        }
                    )
                }
            }

            // Center: Phase, Countdown, and Phrase
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 500.dp)
            ) {
                // Phase text
                val phaseText = when (progress.phase) {
                    BreathingPhase.INHALE -> stringResource(R.string.breathing_inhale)
                    BreathingPhase.EXHALE -> stringResource(R.string.breathing_exhale)
                    BreathingPhase.COMPLETE -> stringResource(R.string.breathing_complete)
                }

                Text(
                    text = phaseText,
                    style = typography.titleMedium,
                    color = colors.accent,
                    textAlign = TextAlign.Center
                )

                if (config.animation.revealsRemainingTime) {
                    // Countdown SS.S (silent without liveRegion to avoid TalkBack speech spam)
                    Text(
                        text = progress.formattedCountdown,
                        style = typography.displayLarge,
                        color = colors.textPrimary,
                        textAlign = TextAlign.Center
                    )
                }

                // Phrase (22sp, max 3 lines)
                Text(
                    text = config.phrase,
                    style = typography.phrase,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            // Bottom Actions
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                if (!progress.isComplete) {
                    // During breathing: Exit + Emergency
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 400.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        TerminalButton(
                            text = stringResource(R.string.action_exit),
                            onClick = onExit,
                            modifier = Modifier.weight(1f),
                            variant = TerminalButtonVariant.SECONDARY
                        )

                        TerminalButton(
                            text = stringResource(R.string.action_emergency),
                            onClick = onEmergencyClick,
                            modifier = Modifier.weight(1f),
                            variant = TerminalButtonVariant.SECONDARY
                        )
                    }
                } else {
                    // On completion: Cancel + Continue (Continue focused once)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 400.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        TerminalButton(
                            text = stringResource(R.string.action_cancel),
                            onClick = onCancel,
                            modifier = Modifier.weight(1f),
                            variant = TerminalButtonVariant.SECONDARY
                        )

                        TerminalButton(
                            text = stringResource(R.string.action_continue),
                            onClick = onContinue,
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(continueFocusRequester),
                            variant = TerminalButtonVariant.PRIMARY
                        )
                    }
                }
            }
        }

        // Emergency Dialog overlay
        if (isEmergencyDialogOpen) {
            EmergencyDialog(
                targetName = targetTitle,
                onDismissRequest = onDismissEmergency,
                onEmergencyOnce = onEmergencyOnce,
                onEmergencyTimed = onEmergencyTimed,
                onEmergencyForever = onEmergencyForever,
                customEmergencyMinutes = customEmergencyMinutes
            )
        }
    }
}
