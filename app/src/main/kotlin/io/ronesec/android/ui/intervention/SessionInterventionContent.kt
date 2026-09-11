package io.ronesec.android.ui.intervention

import android.animation.ValueAnimator
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import io.ronesec.android.platform.overlay.OverlayMode
import io.ronesec.android.platform.overlay.OverlayPresenter
import io.ronesec.android.platform.overlay.OverlayUiState
import io.ronesec.domain.breathing.BreathingTimeline
import io.ronesec.domain.model.MonotonicClock
import io.ronesec.domain.protection.ProtectionEvent
import kotlinx.coroutines.isActive

@Composable
internal fun SessionInterventionContent(
    mode: OverlayMode.Intervention,
    uiState: OverlayUiState,
    presenter: OverlayPresenter,
    monotonicClock: MonotonicClock
) {
    val challenge = mode.challenge
    val isCodeGate = challenge?.gate ?: mode.config.twoStageUnlock
    val attentionCheck = challenge?.attentionCheck
    val isAttentionCheck = attentionCheck?.active == true
    val breathingStart = challenge?.breathingStartElapsedMs
    var progress by remember(mode.sessionId, mode.cycle, breathingStart) {
        val start = breathingStart ?: 0L
        mutableStateOf(BreathingTimeline.calculate(start, start, mode.config.durationMs))
    }
    var nowElapsedMs by remember(mode.sessionId, mode.cycle) {
        mutableStateOf(monotonicClock.elapsedRealtimeMs())
    }

    LaunchedEffect(mode.sessionId, mode.cycle, breathingStart, isCodeGate, isAttentionCheck, mode.config.durationMs) {
        if (!isCodeGate && !mode.isComplete) {
            while (isActive) {
                withFrameNanos {
                    val now = monotonicClock.elapsedRealtimeMs()
                    nowElapsedMs = now
                    if (!isAttentionCheck && breathingStart != null) {
                        progress = BreathingTimeline.calculate(
                            breathingStart,
                            now,
                            mode.config.durationMs
                        )
                    }
                }
            }
        }
    }

    val send: (ProtectionEvent) -> Unit = presenter::dispatchCodeEvent
    Box {
        if (isCodeGate) {
            CodeGateContent(
                targetName = mode.config.displayName.ifBlank { mode.config.packageName },
                generated = challenge?.generated == true,
                length = challenge?.codeLength ?: mode.config.unlockCodeLength,
                error = challenge?.error == true,
                emergencyCode = challenge?.emergencyCode,
                onGenerate = { send(ProtectionEvent.GenerateUnlockCode(mode.sessionId, mode.cycle)) },
                onSubmit = { send(ProtectionEvent.SubmitUnlockCode(mode.sessionId, mode.cycle, it)) },
                onExit = { send(ProtectionEvent.ActionExit(mode.sessionId)) },
                onEmergency = presenter::openEmergencyDialog
            )
        } else if (attentionCheck?.active == true) {
            val pausedProgress = BreathingTimeline.calculate(
                0L,
                attentionCheck.pausedElapsedProgressMs,
                mode.config.durationMs
            )
            AttentionCheckContent(
                config = mode.config,
                pausedProgress = pausedProgress,
                attentionCheck = attentionCheck,
                onSubmit = { send(ProtectionEvent.SubmitAttentionCheckCode(mode.sessionId, mode.cycle, it)) },
                onExit = { send(ProtectionEvent.ActionExit(mode.sessionId)) },
                onEmergency = presenter::openEmergencyDialog,
                nowElapsedMs = nowElapsedMs
            )
        } else {
            val displayedProgress = if (mode.isComplete) {
                BreathingTimeline.calculate(0L, mode.config.durationMs, mode.config.durationMs)
            } else {
                progress
            }
            InterventionContent(
                config = mode.config,
                progress = displayedProgress,
                showSavedBadge = uiState.showOverlayStats,
                savedMinutes = uiState.allTimeSavedMinutes,
                isEmergencyDialogOpen = false,
                onContinue = { send(ProtectionEvent.ActionContinue(mode.sessionId, mode.cycle)) },
                onExit = { send(ProtectionEvent.ActionExit(mode.sessionId)) },
                onCancel = { send(ProtectionEvent.ActionCancel(mode.sessionId)) },
                onEmergencyClick = presenter::openEmergencyDialog,
                onDismissEmergency = presenter::dismissEmergencyDialog,
                onEmergencyTimed = {},
                onEmergencyForever = {},
                reducedMotion = !ValueAnimator.areAnimatorsEnabled(),
                isAutofocusEnabled = !uiState.isEmergencyDialogOpen,
                emergencyCode = challenge?.emergencyCode
            )
        }

        if (uiState.isEmergencyDialogOpen) {
            EmergencyDialog(
                targetName = mode.config.displayName.ifBlank { mode.config.packageName },
                onDismissRequest = presenter::dismissEmergencyDialog,
                onEmergencyTimed = {},
                customEmergencyMinutes = uiState.customEmergencyMinutes,
                requireCode = mode.config.requireEmergencyCode,
                emergencyCode = challenge?.emergencyCode,
                codeError = challenge?.emergencyError == true,
                onEmergencyOnce = { send(ProtectionEvent.ActionEmergencyOnce(mode.sessionId, mode.cycle, challenge?.emergencyCode)) },
                onOnceWithCode = { code -> send(ProtectionEvent.ActionEmergencyOnce(mode.sessionId, mode.cycle, code)) },
                onTimedWithCode = { duration, code ->
                    send(ProtectionEvent.ActionEmergencyTimed(mode.sessionId, mode.cycle, duration, code))
                },
                onEmergencyForever = { send(ProtectionEvent.ActionEmergencyForever(mode.sessionId, mode.cycle, challenge?.emergencyCode)) },
                onForeverWithCode = { code -> send(ProtectionEvent.ActionEmergencyForever(mode.sessionId, mode.cycle, code)) }
            )
        }
    }
}
