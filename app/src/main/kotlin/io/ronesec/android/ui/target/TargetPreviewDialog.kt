package io.ronesec.android.ui.target

import android.os.SystemClock
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.ronesec.android.ui.intervention.InterventionContent
import io.ronesec.domain.breathing.BreathingTimeline
import io.ronesec.domain.model.TargetConfig
import io.ronesec.domain.policy.EffectiveInterventionConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun TargetPreviewDialog(
    draft: TargetSettingsDraft,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val durationMs = draft.durationSeconds * 1000L
    val effectiveConfig = remember(draft) {
        EffectiveInterventionConfig(
            packageName = draft.packageName,
            displayName = draft.displayName,
            phrase = draft.phrase.ifBlank { TargetConfig.DEFAULT_PHRASE },
            animation = draft.animation,
            durationMs = durationMs,
            reinterventionMs = 0L,
            quickReturnGraceMs = 0L,
            baseDurationMs = durationMs,
            backoffExponent = 0
        )
    }

    val startElapsedMs = remember { SystemClock.elapsedRealtime() }
    var nowElapsedMs by remember { mutableStateOf(startElapsedMs) }
    var isEmergencyOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (isActive) {
            nowElapsedMs = SystemClock.elapsedRealtime()
            delay(16L) // Smooth ~60fps timeline update
        }
    }

    val progress = BreathingTimeline.calculate(
        startElapsedMs = startElapsedMs,
        nowElapsedMs = nowElapsedMs,
        durationMs = durationMs
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        InterventionContent(
            config = effectiveConfig,
            progress = progress,
            showSavedBadge = false,
            savedMinutes = 0L,
            isEmergencyDialogOpen = isEmergencyOpen,
            onContinue = onDismiss,
            onExit = onDismiss,
            onCancel = onDismiss,
            onEmergencyClick = { isEmergencyOpen = true },
            onDismissEmergency = { isEmergencyOpen = false },
            onEmergencyOnce = onDismiss,
            onEmergencyTimed = { onDismiss() },
            onEmergencyForever = onDismiss,
            modifier = modifier.fillMaxSize()
        )
    }
}
