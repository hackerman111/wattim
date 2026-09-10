package io.ronesec.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.ronesec.android.R
import io.ronesec.android.platform.system.PermissionSnapshot
import io.ronesec.android.platform.system.PermissionState
import io.ronesec.android.ui.designsystem.TerminalBadge
import io.ronesec.android.ui.designsystem.TerminalButton
import io.ronesec.android.ui.designsystem.TerminalButtonVariant
import io.ronesec.android.ui.designsystem.TerminalCard
import io.ronesec.android.ui.designsystem.WattimTheme

@Composable
internal fun AppsTabShell(
    permissionSnapshot: PermissionSnapshot,
    onOpenDetail: (packageName: String) -> Unit
) {
    val colors = WattimTheme.colors
    val dimensions = WattimTheme.dimensions
    val typography = WattimTheme.typography

    TerminalCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(dimensions.space12)) {
            Text(
                text = stringResource(R.string.nav_apps).uppercase(),
                style = typography.titleLarge,
                color = colors.accent
            )
            Text(
                text = if (permissionSnapshot.isProtectionActive) {
                    stringResource(R.string.fgs_status_active)
                } else {
                    stringResource(R.string.fgs_status_degraded)
                },
                style = typography.bodyMedium,
                color = colors.textPrimary
            )
            TerminalButton(
                text = stringResource(R.string.action_add_app),
                onClick = { onOpenDetail("com.example.sample") },
                variant = TerminalButtonVariant.PRIMARY,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
internal fun BlocksTabShell(permissionSnapshot: PermissionSnapshot) {
    val colors = WattimTheme.colors
    val dimensions = WattimTheme.dimensions
    val typography = WattimTheme.typography

    TerminalCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(dimensions.space12)) {
            Text(
                text = stringResource(R.string.nav_block).uppercase(),
                style = typography.titleLarge,
                color = colors.accent
            )
            Text(
                text = stringResource(R.string.status_system_ready),
                style = typography.bodyMedium,
                color = colors.textSecondary
            )
        }
    }
}

@Composable
internal fun StatsTabShell(permissionSnapshot: PermissionSnapshot) {
    val colors = WattimTheme.colors
    val dimensions = WattimTheme.dimensions
    val typography = WattimTheme.typography

    TerminalCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(dimensions.space12)) {
            Text(
                text = stringResource(R.string.nav_stats).uppercase(),
                style = typography.titleLarge,
                color = colors.accent
            )
            Text(
                text = stringResource(R.string.status_system_ready),
                style = typography.bodyMedium,
                color = colors.textSecondary
            )
        }
    }
}

@Composable
internal fun ConfigTabShell(permissionSnapshot: PermissionSnapshot) {
    val colors = WattimTheme.colors
    val dimensions = WattimTheme.dimensions
    val typography = WattimTheme.typography

    TerminalCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(dimensions.space12)) {
            Text(
                text = stringResource(R.string.nav_config).uppercase(),
                style = typography.titleLarge,
                color = colors.accent
            )
            PermissionRow(
                name = stringResource(R.string.badge_accessibility),
                isGranted = permissionSnapshot.accessibility == PermissionState.Granted
            )
            PermissionRow(
                name = stringResource(R.string.badge_overlay),
                isGranted = permissionSnapshot.overlay == PermissionState.Granted
            )
            PermissionRow(
                name = stringResource(R.string.badge_battery),
                isGranted = permissionSnapshot.batteryExemption == PermissionState.Granted
            )
        }
    }
}

@Composable
private fun PermissionRow(
    name: String,
    isGranted: Boolean
) {
    val colors = WattimTheme.colors
    val typography = WattimTheme.typography

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            style = typography.bodyMedium,
            color = colors.textPrimary
        )
        TerminalBadge(
            text = if (isGranted) stringResource(R.string.status_on) else stringResource(R.string.status_off),
            isActive = isGranted
        )
    }
}
