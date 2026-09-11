package io.ronesec.android.ui.target

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.ronesec.android.R
import io.ronesec.android.ui.designsystem.TerminalBadge
import io.ronesec.android.ui.designsystem.TerminalButton
import io.ronesec.android.ui.designsystem.TerminalCard
import io.ronesec.android.ui.designsystem.WattimTheme

@Composable
fun AttentionCheckCard(
    enabled: Boolean,
    count: Int,
    randomCountEnabled: Boolean,
    minCount: Int,
    maxCount: Int,
    codeLength: Int,
    timeoutSeconds: Int,
    onToggleEnabled: () -> Unit,
    onCountChange: (Int) -> Unit,
    onToggleRandomCount: () -> Unit,
    onMinCountChange: (Int) -> Unit,
    onMaxCountChange: (Int) -> Unit,
    onCodeLengthChange: (Int) -> Unit,
    onTimeoutChange: (Int) -> Unit,
    isInteractive: Boolean,
    modifier: Modifier = Modifier
) {
    val dimensions = WattimTheme.dimensions

    TerminalCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(dimensions.space12)) {
            // Header Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .toggleable(
                        value = enabled,
                        enabled = isInteractive,
                        role = Role.Switch,
                        onValueChange = { onToggleEnabled() }
                    )
                    .padding(vertical = dimensions.space8),
                horizontalArrangement = Arrangement.spacedBy(dimensions.space12),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.target_attention_check_title),
                        style = WattimTheme.typography.bodyMedium,
                        color = WattimTheme.colors.textPrimary
                    )
                    Text(
                        text = stringResource(R.string.target_attention_check_desc),
                        style = WattimTheme.typography.labelSmall,
                        color = WattimTheme.colors.textSecondary
                    )
                }
                TerminalBadge(
                    text = stringResource(if (enabled) R.string.status_on else R.string.status_off),
                    isActive = enabled
                )
            }

            if (enabled) {
                // Random check count toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .toggleable(
                            value = randomCountEnabled,
                            enabled = isInteractive,
                            role = Role.Switch,
                            onValueChange = { onToggleRandomCount() }
                        )
                        .padding(vertical = dimensions.space8),
                    horizontalArrangement = Arrangement.spacedBy(dimensions.space12),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.target_attention_check_random_title),
                            style = WattimTheme.typography.bodyMedium,
                            color = WattimTheme.colors.textPrimary
                        )
                        Text(
                            text = stringResource(R.string.target_attention_check_random_desc),
                            style = WattimTheme.typography.labelSmall,
                            color = WattimTheme.colors.textSecondary
                        )
                    }
                    TerminalBadge(
                        text = stringResource(if (randomCountEnabled) R.string.status_on else R.string.status_off),
                        isActive = randomCountEnabled
                    )
                }

                if (!randomCountEnabled) {
                    // 1. Check count stepper (1..5)
                    Column(verticalArrangement = Arrangement.spacedBy(dimensions.space4)) {
                        Text(
                            text = stringResource(R.string.target_attention_check_count_label, count),
                            style = WattimTheme.typography.bodyMedium,
                            color = WattimTheme.colors.textPrimary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(dimensions.space8)
                        ) {
                            TerminalButton(
                                text = "-1",
                                onClick = { onCountChange(count - 1) },
                                enabled = isInteractive && count > 1,
                                modifier = Modifier.weight(1f)
                            )
                            TerminalButton(
                                text = "+1",
                                onClick = { onCountChange(count + 1) },
                                enabled = isInteractive && count < 5,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                } else {
                    // 1a. Min check count stepper (1..maxCount)
                    Column(verticalArrangement = Arrangement.spacedBy(dimensions.space4)) {
                        Text(
                            text = stringResource(R.string.target_attention_check_min_count_label, minCount),
                            style = WattimTheme.typography.bodyMedium,
                            color = WattimTheme.colors.textPrimary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(dimensions.space8)
                        ) {
                            TerminalButton(
                                text = "-1",
                                onClick = { onMinCountChange(minCount - 1) },
                                enabled = isInteractive && minCount > 1,
                                modifier = Modifier.weight(1f)
                            )
                            TerminalButton(
                                text = "+1",
                                onClick = { onMinCountChange(minCount + 1) },
                                enabled = isInteractive && minCount < maxCount,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // 1b. Max check count stepper (minCount..5)
                    Column(verticalArrangement = Arrangement.spacedBy(dimensions.space4)) {
                        Text(
                            text = stringResource(R.string.target_attention_check_max_count_label, maxCount),
                            style = WattimTheme.typography.bodyMedium,
                            color = WattimTheme.colors.textPrimary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(dimensions.space8)
                        ) {
                            TerminalButton(
                                text = "-1",
                                onClick = { onMaxCountChange(maxCount - 1) },
                                enabled = isInteractive && maxCount > minCount,
                                modifier = Modifier.weight(1f)
                            )
                            TerminalButton(
                                text = "+1",
                                onClick = { onMaxCountChange(maxCount + 1) },
                                enabled = isInteractive && maxCount < 5,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // 2. Code length stepper (3..8)
                Column(verticalArrangement = Arrangement.spacedBy(dimensions.space4)) {
                    Text(
                        text = stringResource(R.string.target_attention_check_length_label, codeLength),
                        style = WattimTheme.typography.bodyMedium,
                        color = WattimTheme.colors.textPrimary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(dimensions.space8)
                    ) {
                        TerminalButton(
                            text = "-1",
                            onClick = { onCodeLengthChange(codeLength - 1) },
                            enabled = isInteractive && codeLength > 3,
                            modifier = Modifier.weight(1f)
                        )
                        TerminalButton(
                            text = "+1",
                            onClick = { onCodeLengthChange(codeLength + 1) },
                            enabled = isInteractive && codeLength < 8,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // 3. Timeout seconds stepper (3..30s)
                Column(verticalArrangement = Arrangement.spacedBy(dimensions.space4)) {
                    Text(
                        text = stringResource(R.string.target_attention_check_timeout_label, timeoutSeconds),
                        style = WattimTheme.typography.bodyMedium,
                        color = WattimTheme.colors.textPrimary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(dimensions.space8)
                    ) {
                        TerminalButton(
                            text = "-1s",
                            onClick = { onTimeoutChange(timeoutSeconds - 1) },
                            enabled = isInteractive && timeoutSeconds > 3,
                            modifier = Modifier.weight(1f)
                        )
                        TerminalButton(
                            text = "+1s",
                            onClick = { onTimeoutChange(timeoutSeconds + 1) },
                            enabled = isInteractive && timeoutSeconds < 30,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
