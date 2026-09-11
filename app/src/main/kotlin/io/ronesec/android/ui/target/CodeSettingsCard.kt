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
fun CodeSettingsCard(
    twoStageUnlock: Boolean,
    unlockCodeLength: Int,
    requireEmergencyCode: Boolean,
    onTwoStageUnlockChange: (Boolean) -> Unit,
    onUnlockCodeLengthChange: (Int) -> Unit,
    onRequireEmergencyCodeChange: (Boolean) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val dimensions = WattimTheme.dimensions
    TerminalCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(dimensions.space12)) {
            CodeSettingToggle(
                title = stringResource(R.string.code_settings_two_stage),
                description = stringResource(R.string.code_settings_two_stage_description),
                checked = twoStageUnlock,
                enabled = enabled,
                onCheckedChange = onTwoStageUnlockChange
            )
            if (twoStageUnlock) {
                Text(
                    text = stringResource(R.string.code_settings_length, unlockCodeLength),
                    style = WattimTheme.typography.bodyMedium,
                    color = WattimTheme.colors.textPrimary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(dimensions.space8)) {
                    TerminalButton(
                        text = stringResource(R.string.code_settings_shorter),
                        onClick = { onUnlockCodeLengthChange(unlockCodeLength - 1) },
                        enabled = enabled && unlockCodeLength > 1,
                        modifier = Modifier.weight(1f)
                    )
                    TerminalButton(
                        text = stringResource(R.string.code_settings_longer),
                        onClick = { onUnlockCodeLengthChange(unlockCodeLength + 1) },
                        enabled = enabled && unlockCodeLength < 10,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            CodeSettingToggle(
                title = stringResource(R.string.code_settings_emergency),
                description = stringResource(R.string.code_settings_emergency_description),
                checked = requireEmergencyCode,
                enabled = enabled,
                onCheckedChange = onRequireEmergencyCodeChange
            )
        }
    }
}

@Composable
private fun CodeSettingToggle(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = WattimTheme.dimensions.space8),
        horizontalArrangement = Arrangement.spacedBy(WattimTheme.dimensions.space12),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = WattimTheme.typography.bodyMedium, color = WattimTheme.colors.textPrimary)
            Text(description, style = WattimTheme.typography.labelSmall, color = WattimTheme.colors.textSecondary)
        }
        TerminalBadge(
            text = stringResource(if (checked) R.string.status_on else R.string.status_off),
            isActive = checked
        )
    }
}
