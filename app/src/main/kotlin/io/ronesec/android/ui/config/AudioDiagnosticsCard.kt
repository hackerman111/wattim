package io.ronesec.android.ui.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.ronesec.android.R
import io.ronesec.android.platform.audio.AudioDiagnosticState
import io.ronesec.android.platform.audio.AudioFocusStatus
import io.ronesec.android.platform.audio.MediaPauseStatus
import io.ronesec.android.ui.designsystem.TerminalCard
import io.ronesec.android.ui.designsystem.WattimTheme

@Composable
fun AudioDiagnosticsCard(
    state: AudioDiagnosticState,
    modifier: Modifier = Modifier
) {
    val colors = WattimTheme.colors
    val dimensions = WattimTheme.dimensions
    val typography = WattimTheme.typography
    TerminalCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(dimensions.space8)) {
            Text(
                text = stringResource(R.string.audio_diagnostics_title),
                fontFamily = typography.bodyMedium.fontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                letterSpacing = 0.1.sp,
                color = colors.textSecondary
            )
            Text(
                text = stringResource(
                    R.string.audio_diagnostics_target,
                    state.packageName ?: stringResource(R.string.audio_diagnostics_none)
                ),
                style = typography.bodyMedium,
                color = colors.textPrimary
            )
            Text(
                text = stringResource(
                    R.string.audio_diagnostics_focus,
                    focusStatusText(state.focusStatus)
                ),
                style = typography.labelSmall,
                color = colors.textSecondary
            )
            Text(
                text = stringResource(
                    R.string.audio_diagnostics_media,
                    mediaStatusText(state.mediaPauseStatus)
                ),
                style = typography.labelSmall,
                color = colors.textSecondary
            )
            Text(
                text = stringResource(
                    R.string.audio_diagnostics_commands,
                    state.targetedCommands,
                    state.fallbackCommands
                ),
                style = typography.labelSmall,
                color = colors.textSecondary
            )
        }
    }
}

@Composable
private fun focusStatusText(status: AudioFocusStatus): String = stringResource(
    when (status) {
        AudioFocusStatus.IDLE -> R.string.audio_status_idle
        AudioFocusStatus.GRANTED -> R.string.audio_status_granted
        AudioFocusStatus.DENIED -> R.string.audio_status_denied
        AudioFocusStatus.LOST -> R.string.audio_status_lost
        AudioFocusStatus.RECOVERING -> R.string.audio_status_recovering
    }
)

@Composable
private fun mediaStatusText(status: MediaPauseStatus): String = stringResource(
    when (status) {
        MediaPauseStatus.IDLE -> R.string.audio_status_idle
        MediaPauseStatus.ACCESS_DENIED -> R.string.audio_status_access_denied
        MediaPauseStatus.NO_ACTIVE_SESSION -> R.string.audio_status_no_session
        MediaPauseStatus.COMMAND_SENT -> R.string.audio_status_command_sent
        MediaPauseStatus.CONFIRMED_PAUSED -> R.string.audio_status_confirmed
        MediaPauseStatus.NOT_CONFIRMED -> R.string.audio_status_not_confirmed
        MediaPauseStatus.FAILED -> R.string.audio_status_failed
    }
)
