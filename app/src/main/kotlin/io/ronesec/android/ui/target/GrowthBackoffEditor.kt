package io.ronesec.android.ui.target

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.ronesec.android.R
import io.ronesec.android.ui.designsystem.TerminalBadge
import io.ronesec.android.ui.designsystem.TerminalButton
import io.ronesec.android.ui.designsystem.TerminalCard
import io.ronesec.android.ui.designsystem.TerminalHelpCircle
import io.ronesec.android.ui.designsystem.WattimTheme

@Composable
fun GrowthBackoffEditor(
    enabled: Boolean,
    percent: Int,
    windowMs: Long,
    onEnabledChange: (Boolean) -> Unit,
    onPercentChange: (Int) -> Unit,
    onWindowChange: (Long) -> Unit,
    showContainer: Boolean = true,
    onHelpClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = WattimTheme.colors
    val dimensions = WattimTheme.dimensions
    val typography = WattimTheme.typography

    val percentPresets = listOf(10, 20, 30, 50)
    val windowOptions = listOf(
        Pair(15 * 60 * 1000L, "15M"),
        Pair(30 * 60 * 1000L, "30M"),
        Pair(60 * 60 * 1000L, "1H"),
        Pair(2 * 60 * 60 * 1000L, "2H"),
        Pair(24 * 60 * 60 * 1000L, "24H")
    )

    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(dimensions.space12)
        ) {
            // Header: Title + Help + ON/OFF Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.growth_label).uppercase(),
                        style = typography.labelSmall,
                        color = colors.textSecondary
                    )
                    if (onHelpClick != null) {
                        TerminalHelpCircle(
                            onClick = onHelpClick,
                            contentDescriptionText = stringResource(R.string.growth_label)
                        )
                    }
                }

                TerminalBadge(
                    text = if (enabled) stringResource(R.string.status_on) else stringResource(R.string.status_off),
                    isActive = enabled,
                    modifier = Modifier.clickable(role = Role.Switch) { onEnabledChange(!enabled) }
                )
            }

            if (enabled) {
                // Growth rate controls: value %, steppers, presets
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(dimensions.space8)
                ) {
                    Text(
                        text = stringResource(R.string.growth_percent_label).uppercase(),
                        style = typography.labelSmall,
                        color = colors.textSecondary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "+$percent%",
                            style = typography.displayLarge,
                            color = colors.accent
                        )
                    }

                    // Steppers: -5%, -1%, +1%, +5%
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TerminalButton("-5%", { onPercentChange((percent - 5).coerceIn(1, 200)) }, modifier = Modifier.weight(1f))
                        TerminalButton("-1%", { onPercentChange((percent - 1).coerceIn(1, 200)) }, modifier = Modifier.weight(1f))
                        TerminalButton("+1%", { onPercentChange((percent + 1).coerceIn(1, 200)) }, modifier = Modifier.weight(1f))
                        TerminalButton("+5%", { onPercentChange((percent + 5).coerceIn(1, 200)) }, modifier = Modifier.weight(1f))
                    }

                    // Presets: 10, 20, 30, 50
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        percentPresets.forEach { p ->
                            val isSelected = (percent == p)
                            TerminalBadge(
                                text = "$p%",
                                isActive = isSelected,
                                modifier = Modifier.clickable { onPercentChange(p) }
                            )
                        }
                    }
                }

                // Rolling window selector: 15m, 30m, 1h, 2h, 24h
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(dimensions.space8)
                ) {
                    Text(
                        text = stringResource(R.string.growth_window_label).uppercase(),
                        style = typography.labelSmall,
                        color = colors.textSecondary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        windowOptions.forEach { (ms, label) ->
                            val isSelected = (windowMs == ms)
                            TerminalBadge(
                                text = label,
                                isActive = isSelected,
                                modifier = Modifier.clickable { onWindowChange(ms) }
                            )
                        }
                    }
                }
            }
        }
    }
    if (showContainer) {
        TerminalCard(modifier = modifier.fillMaxWidth()) { content() }
    } else {
        content()
    }
}
