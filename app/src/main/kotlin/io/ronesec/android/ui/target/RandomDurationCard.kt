package io.ronesec.android.ui.target

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.ronesec.android.R
import io.ronesec.android.ui.designsystem.TerminalBadge
import io.ronesec.android.ui.designsystem.TerminalButton
import io.ronesec.android.ui.designsystem.TerminalCard
import io.ronesec.android.ui.designsystem.WattimTheme

@Composable
fun RandomDurationCard(
    enabled: Boolean,
    minDurationSeconds: Int,
    maxDurationSeconds: Int,
    onToggleEnabled: () -> Unit,
    onMaxDurationChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    isInteractive: Boolean = true
) {
    val colors = WattimTheme.colors
    val dimensions = WattimTheme.dimensions
    val typography = WattimTheme.typography

    TerminalCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(dimensions.space12)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = dimensions.space8)) {
                    Text(
                        text = stringResource(R.string.target_random_duration_title),
                        style = typography.labelSmall,
                        color = colors.textSecondary
                    )
                    Text(
                        text = stringResource(R.string.target_random_duration_desc),
                        style = typography.bodyMedium,
                        color = colors.textSecondary
                    )
                }
                TerminalBadge(
                    text = stringResource(if (enabled) R.string.status_on else R.string.status_off),
                    isActive = enabled,
                    onClick = { if (isInteractive) onToggleEnabled() }
                )
            }

            if (enabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.target_random_max_duration_label),
                        style = typography.labelSmall,
                        color = colors.textSecondary
                    )
                    Text(
                        text = "$maxDurationSeconds ${stringResource(R.string.unit_sec)}",
                        style = typography.titleMedium,
                        color = colors.accent
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(dimensions.space8)
                ) {
                    TerminalButton(
                        text = "-5s",
                        onClick = { onMaxDurationChange(maxOf(minDurationSeconds, maxDurationSeconds - 5)) },
                        enabled = isInteractive && maxDurationSeconds > minDurationSeconds,
                        modifier = Modifier.weight(1f)
                    )
                    TerminalButton(
                        text = "-1s",
                        onClick = { onMaxDurationChange(maxOf(minDurationSeconds, maxDurationSeconds - 1)) },
                        enabled = isInteractive && maxDurationSeconds > minDurationSeconds,
                        modifier = Modifier.weight(1f)
                    )
                    TerminalButton(
                        text = "+1s",
                        onClick = { onMaxDurationChange(minOf(120, maxDurationSeconds + 1)) },
                        enabled = isInteractive && maxDurationSeconds < 120,
                        modifier = Modifier.weight(1f)
                    )
                    TerminalButton(
                        text = "+5s",
                        onClick = { onMaxDurationChange(minOf(120, maxDurationSeconds + 5)) },
                        enabled = isInteractive && maxDurationSeconds < 120,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
