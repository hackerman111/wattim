package io.ronesec.android.ui.config

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ronesec.android.R
import io.ronesec.android.platform.system.PermissionState
import io.ronesec.android.ui.designsystem.TerminalBadge
import io.ronesec.android.ui.designsystem.TerminalButton
import io.ronesec.android.ui.designsystem.TerminalButtonVariant
import io.ronesec.android.ui.designsystem.TerminalCard
import io.ronesec.android.ui.designsystem.WattimTheme

/**
 * F76 permission status UI: core grants plus optional package-targeted media control.
 */
@Composable
fun PermissionStatusCard(
    accessibilityState: PermissionState,
    mediaControlState: PermissionState,
    overlayState: PermissionState,
    batteryState: PermissionState,
    onEnableAccessibility: () -> Unit,
    onEnableMediaControl: () -> Unit,
    onEnableOverlay: () -> Unit,
    onEnableBattery: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = WattimTheme.colors
    val typography = WattimTheme.typography

    TerminalCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.config_permissions_title),
            fontFamily = typography.bodyMedium.fontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            letterSpacing = 0.1.sp,
            color = colors.textSecondary,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PermissionItem(
                name = stringResource(R.string.badge_accessibility),
                description = stringResource(R.string.permission_accessibility_desc),
                isGranted = accessibilityState == PermissionState.Granted,
                onEnable = onEnableAccessibility
            )

            PermissionItem(
                name = stringResource(R.string.badge_media_control),
                description = stringResource(R.string.permission_media_control_desc),
                isGranted = mediaControlState == PermissionState.Granted,
                onEnable = onEnableMediaControl
            )

            PermissionItem(
                name = stringResource(R.string.badge_overlay),
                description = stringResource(R.string.permission_overlay_desc),
                isGranted = overlayState == PermissionState.Granted,
                onEnable = onEnableOverlay
            )

            PermissionItem(
                name = stringResource(R.string.badge_battery),
                description = stringResource(R.string.permission_battery_desc),
                isGranted = batteryState == PermissionState.Granted,
                onEnable = onEnableBattery
            )
        }
    }
}

@Composable
private fun PermissionItem(
    name: String,
    description: String,
    isGranted: Boolean,
    onEnable: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = WattimTheme.colors
    val typography = WattimTheme.typography

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(
                text = name.uppercase(),
                fontFamily = typography.bodyMedium.fontFamily,
                fontSize = 13.sp,
                color = colors.textPrimary
            )
            Text(
                text = description,
                fontFamily = typography.bodyMedium.fontFamily,
                fontSize = 11.sp,
                color = colors.textSecondary
            )
        }

        if (isGranted) {
            TerminalBadge(
                text = stringResource(R.string.status_on),
                isActive = true
            )
        } else {
            TerminalButton(
                text = stringResource(R.string.action_enable),
                onClick = onEnable,
                variant = TerminalButtonVariant.PRIMARY
            )
        }
    }
}
