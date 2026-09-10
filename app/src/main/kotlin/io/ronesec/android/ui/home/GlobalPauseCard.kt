package io.ronesec.android.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ronesec.android.R
import io.ronesec.android.ui.designsystem.TerminalBadge
import io.ronesec.android.ui.designsystem.TerminalButton
import io.ronesec.android.ui.designsystem.TerminalButtonVariant
import io.ronesec.android.ui.designsystem.TerminalCard
import io.ronesec.android.ui.designsystem.WattimTheme

@Composable
fun GlobalPauseCard(
    pauseState: HomePauseState,
    onSetPause: (durationMillis: Long?) -> Unit,
    onResume: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = WattimTheme.colors
    val typography = WattimTheme.typography

    when (pauseState) {
        is HomePauseState.Active -> {
            TerminalCard(
                modifier = modifier.fillMaxWidth(),
                isError = true
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.status_paused),
                        fontFamily = typography.bodyMedium.fontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 0.15.sp,
                        color = colors.error
                    )
                    Text(
                        text = pauseState.formattedRemaining,
                        fontFamily = typography.bodyMedium.fontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = colors.textPrimary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    TerminalButton(
                        text = stringResource(R.string.action_resume),
                        onClick = onResume,
                        variant = TerminalButtonVariant.PRIMARY,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        is HomePauseState.Inactive -> {
            TerminalCard(modifier = modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.global_pause_title).uppercase(),
                        fontFamily = typography.bodyMedium.fontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        letterSpacing = 0.15.sp,
                        color = colors.textSecondary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TerminalBadge(
                            text = stringResource(R.string.global_pause_15m),
                            onClick = { onSetPause(15 * 60 * 1000L) }
                        )
                        TerminalBadge(
                            text = stringResource(R.string.global_pause_30m),
                            onClick = { onSetPause(30 * 60 * 1000L) }
                        )
                        TerminalBadge(
                            text = stringResource(R.string.global_pause_1h),
                            onClick = { onSetPause(60 * 60 * 1000L) }
                        )
                        TerminalBadge(
                            text = stringResource(R.string.global_pause_forever),
                            onClick = { onSetPause(null) }
                        )
                    }
                }
            }
        }
    }
}
