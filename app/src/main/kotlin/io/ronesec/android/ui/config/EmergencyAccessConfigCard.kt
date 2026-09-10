package io.ronesec.android.ui.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
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
fun EmergencyAccessConfigCard(
    customEmergencyMinutes: Int?,
    onSetCustomMinutes: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = WattimTheme.colors
    val dimensions = WattimTheme.dimensions
    val typography = WattimTheme.typography

    var minutesInput by remember(customEmergencyMinutes) {
        mutableIntStateOf(customEmergencyMinutes ?: 45)
    }

    val presets = listOf(10, 20, 45, 90, 120)

    TerminalCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(dimensions.space12)) {
            // Header
            Column {
                Text(
                    text = stringResource(R.string.emergency_config_title),
                    style = typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        letterSpacing = 0.1.sp
                    ),
                    color = colors.accent
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.emergency_config_desc),
                    style = typography.labelSmall,
                    color = colors.textSecondary
                )
            }

            // Current status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (customEmergencyMinutes != null) {
                        stringResource(R.string.emergency_config_active_status, customEmergencyMinutes)
                    } else {
                        stringResource(R.string.emergency_config_not_set)
                    },
                    style = typography.labelSmall,
                    color = if (customEmergencyMinutes != null) colors.accent else colors.textSecondary
                )
            }

            // Stepper and presets
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(dimensions.space8),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TerminalButton(
                    text = "-5",
                    onClick = { minutesInput = (minutesInput - 5).coerceAtLeast(5) },
                    variant = TerminalButtonVariant.SECONDARY,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = stringResource(R.string.emergency_custom_badge_format, minutesInput),
                    style = typography.titleMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    ),
                    color = colors.accent,
                    modifier = Modifier.padding(horizontal = dimensions.space8)
                )

                TerminalButton(
                    text = "+5",
                    onClick = { minutesInput = (minutesInput + 5).coerceAtMost(720) },
                    variant = TerminalButtonVariant.SECONDARY,
                    modifier = Modifier.weight(1f)
                )
            }

            // Preset Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(dimensions.space8, Alignment.CenterHorizontally)
            ) {
                presets.forEach { preset ->
                    TerminalBadge(
                        text = stringResource(R.string.emergency_custom_badge_format, preset),
                        isActive = minutesInput == preset,
                        onClick = { minutesInput = preset }
                    )
                }
            }

            Spacer(modifier = Modifier.height(dimensions.space4))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(dimensions.space8)
            ) {
                TerminalButton(
                    text = stringResource(R.string.emergency_config_add_btn),
                    onClick = { onSetCustomMinutes(minutesInput) },
                    variant = TerminalButtonVariant.PRIMARY,
                    modifier = Modifier.weight(1f)
                )

                if (customEmergencyMinutes != null) {
                    TerminalButton(
                        text = stringResource(R.string.emergency_config_remove_btn),
                        onClick = { onSetCustomMinutes(null) },
                        variant = TerminalButtonVariant.SECONDARY,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
