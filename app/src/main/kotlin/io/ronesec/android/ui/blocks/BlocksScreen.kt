package io.ronesec.android.ui.blocks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ronesec.android.R
import io.ronesec.android.ui.designsystem.TerminalButton
import io.ronesec.android.ui.designsystem.TerminalButtonVariant
import io.ronesec.android.ui.designsystem.TerminalCard
import io.ronesec.android.ui.designsystem.WattimTheme

@Composable
fun BlocksScreen(
    uiState: BlocksUiState,
    onVisible: () -> Unit,
    onInvisible: () -> Unit,
    onSelectDuration: (Long) -> Unit,
    onTogglePackage: (String) -> Unit,
    onSelectAll: () -> Unit,
    onSelectNone: () -> Unit,
    onStartFocus: () -> Unit,
    onStopSession: (String) -> Unit,
    onOpenAddApp: () -> Unit,
    onOpenScheduleEditor: (scheduleId: Long?) -> Unit,
    onToggleSchedule: (scheduleId: Long, enabled: Boolean) -> Unit,
    onDeleteSchedule: (scheduleId: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = WattimTheme.colors
    val dimensions = WattimTheme.dimensions
    val typography = WattimTheme.typography

    DisposableEffect(Unit) {
        onVisible()
        onDispose { onInvisible() }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.blocks_screen_title),
            fontFamily = typography.bodyMedium.fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            letterSpacing = 0.15.sp,
            color = colors.accent
        )

        // Error banner
        if (uiState.errorMessage != null) {
            TerminalCard(modifier = Modifier.fillMaxWidth(), isError = true) {
                Text(
                    text = uiState.errorMessage,
                    style = typography.bodyMedium,
                    color = colors.error
                )
            }
        }

        // 1. Singular active session OR Quick Focus launcher (F61, F62)
        // Specification: While a manual block session is active, Blocks shows the one
        // ActiveBlockSessionCard and does NOT expose the Quick Focus launcher.
        if (uiState.hasActiveSession && uiState.activeSession != null) {
            ActiveBlockSessionCard(
                session = uiState.activeSession,
                onStop = onStopSession,
                isLoading = uiState.isLoading
            )
        } else {
            QuickFocusLauncherCard(
                protectedApps = uiState.protectedApps,
                selectedPackages = uiState.selectedPackagesForFocus,
                selectedDurationMs = uiState.selectedFocusDurationMs,
                onSelectDuration = onSelectDuration,
                onTogglePackage = onTogglePackage,
                onSelectAll = onSelectAll,
                onSelectNone = onSelectNone,
                onStartFocus = onStartFocus,
                onOpenAddApp = onOpenAddApp,
                isLoading = uiState.isLoading
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.block_schedules_title),
            fontFamily = typography.bodyMedium.fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            letterSpacing = 0.15.sp,
            color = colors.accent
        )

        TerminalButton(
            text = stringResource(R.string.action_create_schedule_full),
            onClick = { onOpenScheduleEditor(null) },
            variant = TerminalButtonVariant.PRIMARY,
            modifier = Modifier.fillMaxWidth()
        )

        // 3. Schedules List or Empty State (F63, F68)
        if (uiState.schedules.isEmpty()) {
            TerminalCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(dimensions.space8)
                ) {
                    Text(
                        text = stringResource(R.string.no_schedules),
                        style = typography.titleMedium,
                        color = colors.textPrimary
                    )
                    Text(
                        text = stringResource(R.string.no_schedules_desc),
                        style = typography.bodyMedium,
                        color = colors.textSecondary
                    )
                }
            }
        } else {
            uiState.schedules.forEach { schedule ->
                ScheduleCard(
                    schedule = schedule,
                    onEdit = onOpenScheduleEditor,
                    onToggle = onToggleSchedule,
                    onDelete = onDeleteSchedule
                )
            }
        }
    }
}
