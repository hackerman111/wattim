package io.ronesec.android.ui.blocks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ronesec.android.R
import io.ronesec.android.ui.designsystem.TerminalBadge
import io.ronesec.android.ui.designsystem.TerminalButton
import io.ronesec.android.ui.designsystem.TerminalCard
import io.ronesec.android.ui.designsystem.WattimTheme
import io.ronesec.domain.model.TargetConfig

@Composable
fun QuickFocusLauncherCard(
    protectedApps: List<TargetConfig>,
    selectedPackages: Set<String>,
    selectedDurationMs: Long,
    onSelectDuration: (Long) -> Unit,
    onTogglePackage: (String) -> Unit,
    onSelectAll: () -> Unit,
    onSelectNone: () -> Unit,
    onStartFocus: () -> Unit,
    onOpenAddApp: () -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = WattimTheme.colors
    val typography = WattimTheme.typography

    TerminalCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.quick_focus_session),
            fontFamily = typography.bodyMedium.fontFamily,
            fontSize = 12.sp,
            color = colors.textSecondary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (protectedApps.isEmpty()) {
            Text(
                text = stringResource(R.string.no_apps_for_focus),
                fontFamily = typography.bodyMedium.fontFamily,
                fontSize = 12.sp,
                color = colors.textSecondary
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.for_apps_count, selectedPackages.size, protectedApps.size),
                    fontFamily = typography.bodyMedium.fontFamily,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textSecondary
                )

                Text(
                    text = if (selectedPackages.size == protectedApps.size) stringResource(R.string.deselect_all) else stringResource(R.string.select_all),
                    fontFamily = typography.bodyMedium.fontFamily,
                    fontSize = 10.sp,
                    color = colors.accent,
                    modifier = Modifier.clickable {
                        if (selectedPackages.size == protectedApps.size) onSelectNone() else onSelectAll()
                    }
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                protectedApps.forEach { app ->
                    val isChecked = app.packageName in selectedPackages
                    val displayName = app.displayName.ifBlank { app.packageName }
                    TerminalBadge(
                        text = displayName,
                        isActive = isChecked,
                        onClick = { onTogglePackage(app.packageName) }
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val minUnit = stringResource(R.string.unit_min).take(1).lowercase()
            val hrUnit = stringResource(R.string.unit_hours).take(1).lowercase()
            val durations = listOf(
                "15$minUnit" to 15,
                "30$minUnit" to 30,
                "1$hrUnit" to 60,
                "2$hrUnit" to 120
            )
            durations.forEach { (lbl, mins) ->
                TerminalButton(
                    text = lbl,
                    onClick = {
                        onSelectDuration(mins * 60 * 1000L)
                        onStartFocus()
                    },
                    isPrimary = (mins == 30),
                    enabled = !isLoading && (protectedApps.isEmpty() || selectedPackages.isNotEmpty()),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
