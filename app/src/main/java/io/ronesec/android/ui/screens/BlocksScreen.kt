package io.ronesec.android.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ronesec.android.domain.model.BlockSchedule
import io.ronesec.android.domain.model.BlockSession
import io.ronesec.android.domain.model.TargetApp
import io.ronesec.android.ui.components.TerminalButton
import io.ronesec.android.ui.components.TerminalCard
import io.ronesec.android.ui.i18n.LocalAppStrings
import io.ronesec.android.ui.screens.blocks.ActiveBlockSessionCard
import io.ronesec.android.ui.screens.blocks.QuickFocusLauncherCard
import io.ronesec.android.ui.screens.blocks.ScheduleCard
import io.ronesec.android.ui.screens.blocks.ScheduleEditorDialog
import io.ronesec.android.ui.theme.LocalAppPalette
import io.ronesec.android.ui.theme.TerminalFontFamily
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant

@Composable
fun BlocksScreen(
    activeSessions: List<BlockSession>,
    schedules: List<BlockSchedule>,
    targets: List<TargetApp>,
    onStartHardBlock: (String, Int, Set<String>) -> Unit,
    onStopHardBlock: (Long) -> Unit,
    onSaveSchedule: (BlockSchedule) -> Unit,
    onDeleteSchedule: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    val strings = LocalAppStrings.current
    val activeSession = activeSessions.firstOrNull { it.active && it.endTime.isAfter(Instant.now()) }

    var showScheduleDialog by remember { mutableStateOf(false) }
    var editingSchedule by remember { mutableStateOf<BlockSchedule?>(null) }

    var remainingSeconds by remember(activeSession) {
        val rem = if (activeSession != null) Duration.between(Instant.now(), activeSession.endTime).seconds else 0L
        mutableLongStateOf(rem.coerceAtLeast(0L))
    }

    LaunchedEffect(activeSession) {
        while (activeSession != null && remainingSeconds > 0) {
            delay(1000L)
            remainingSeconds = Duration.between(Instant.now(), activeSession.endTime).seconds.coerceAtLeast(0L)
        }
    }

    if (showScheduleDialog) {
        ScheduleEditorDialog(
            initialSchedule = editingSchedule,
            targets = targets,
            onSave = { schedule ->
                onSaveSchedule(schedule)
                showScheduleDialog = false
                editingSchedule = null
            },
            onDismiss = {
                showScheduleDialog = false
                editingSchedule = null
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = strings.blocksTitle,
            fontFamily = TerminalFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            letterSpacing = 0.15.sp,
            color = palette.accent
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (activeSession != null && remainingSeconds > 0) {
            ActiveBlockSessionCard(
                session = activeSession,
                remainingSeconds = remainingSeconds,
                onStopSession = onStopHardBlock
            )
        } else {
            QuickFocusLauncherCard(
                targets = targets,
                onStartBlock = onStartHardBlock
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = strings.blockSchedulesTitle,
            fontFamily = TerminalFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            letterSpacing = 0.15.sp,
            color = palette.accent
        )

        Spacer(modifier = Modifier.height(12.dp))

        TerminalButton(
            text = strings.createScheduleButton,
            onClick = {
                editingSchedule = null
                showScheduleDialog = true
            },
            isPrimary = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (schedules.isEmpty()) {
            TerminalCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = strings.noSchedulesTitle,
                    fontFamily = TerminalFontFamily,
                    fontSize = 13.sp,
                    color = palette.textSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = strings.noSchedulesSubtitle,
                    fontFamily = TerminalFontFamily,
                    fontSize = 11.sp,
                    color = palette.textSecondary
                )
            }
        } else {
            schedules.forEach { schedule ->
                ScheduleCard(
                    schedule = schedule,
                    targets = targets,
                    onEdit = {
                        editingSchedule = schedule
                        showScheduleDialog = true
                    },
                    onToggleEnable = onSaveSchedule,
                    onDelete = onDeleteSchedule
                )
            }
        }
    }
}
