package io.ronesec.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.style.TextOverflow
import io.ronesec.android.domain.model.BlockSchedule
import io.ronesec.android.domain.model.BlockSession
import io.ronesec.android.domain.model.ScheduleAppOverride
import io.ronesec.android.domain.model.ScheduleType
import io.ronesec.android.domain.model.TargetApp
import io.ronesec.android.ui.components.TerminalBadge
import io.ronesec.android.ui.components.TerminalButton
import io.ronesec.android.ui.components.TerminalCard
import io.ronesec.android.ui.components.TerminalInputField
import io.ronesec.android.ui.i18n.AppStrings
import io.ronesec.android.ui.i18n.LocalAppStrings
import io.ronesec.android.ui.theme.LocalAppPalette
import io.ronesec.android.ui.theme.TerminalFontFamily
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.util.Locale

private fun formatDays(days: Set<DayOfWeek>, strings: AppStrings): String {
    return days.sorted().joinToString(", ") { strings.dayShortName(it) }
}

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
            // Active session card
            TerminalCard(modifier = Modifier.fillMaxWidth()) {
                val hours = remainingSeconds / 3600
                val mins = (remainingSeconds % 3600) / 60
                val secs = remainingSeconds % 60
                val timeStr = String.format(Locale.US, "%02d:%02d:%02d", hours, mins, secs)

                Text(
                    text = strings.focusSessionActive,
                    fontFamily = TerminalFontFamily,
                    fontSize = 12.sp,
                    letterSpacing = 0.1.sp,
                    color = palette.accent
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = timeStr,
                    fontFamily = TerminalFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp,
                    color = palette.textPrimary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = strings.blockedAppsCount(activeSession.packages.size),
                    fontFamily = TerminalFontFamily,
                    fontSize = 12.sp,
                    color = palette.textSecondary
                )

                Spacer(modifier = Modifier.height(12.dp))

                TerminalButton(
                    text = strings.stopSessionButton,
                    onClick = { onStopHardBlock(activeSession.id) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            // Quick launcher
            TerminalCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = strings.quickFocusSession,
                    fontFamily = TerminalFontFamily,
                    fontSize = 12.sp,
                    color = palette.textSecondary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                var selectedQuickPackages by remember(targets) {
                    mutableStateOf(targets.map { it.packageName }.toSet())
                }

                if (targets.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = strings.forAppsCount(selectedQuickPackages.size, targets.size),
                            fontFamily = TerminalFontFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.textSecondary
                        )

                        Text(
                            text = if (selectedQuickPackages.size == targets.size) strings.deselectAll else strings.selectAll,
                            fontFamily = TerminalFontFamily,
                            fontSize = 10.sp,
                            color = palette.accent,
                            modifier = Modifier.clickable {
                                selectedQuickPackages = if (selectedQuickPackages.size == targets.size) {
                                    emptySet()
                                } else {
                                    targets.map { it.packageName }.toSet()
                                }
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
                        targets.forEach { target ->
                            val isChecked = target.packageName in selectedQuickPackages
                            TerminalBadge(
                                text = target.displayName,
                                isActive = isChecked,
                                modifier = Modifier.clickable {
                                    selectedQuickPackages = if (isChecked) {
                                        selectedQuickPackages - target.packageName
                                    } else {
                                        selectedQuickPackages + target.packageName
                                    }
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val durations = listOf(
                        "${15}${strings.minutesUnit.take(1)}" to 15,
                        "${30}${strings.minutesUnit.take(1)}" to 30,
                        "${1}${strings.hoursLabel.take(1).lowercase()}" to 60,
                        "${2}${strings.hoursLabel.take(1).lowercase()}" to 120
                    )
                    durations.forEach { (lbl, mins) ->
                        TerminalButton(
                            text = lbl,
                            onClick = {
                                val pkgs = if (selectedQuickPackages.isEmpty()) {
                                    targets.map { it.packageName }.toSet()
                                } else {
                                    selectedQuickPackages
                                }
                                onStartHardBlock(strings.defaultFocusSessionName, mins, pkgs)
                            },
                            isPrimary = (mins == 30),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Scheduled Blocks Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = strings.blockSchedulesTitle,
                fontFamily = TerminalFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                letterSpacing = 0.15.sp,
                color = palette.accent
            )
        }

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
            val targetMap = targets.associateBy { it.packageName }
            schedules.forEach { schedule ->
                TerminalCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .clickable {
                            editingSchedule = schedule
                            showScheduleDialog = true
                        }
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = schedule.name.uppercase(),
                                fontFamily = TerminalFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = palette.textPrimary,
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .padding(end = 6.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TerminalBadge(
                                    text = strings.editButton,
                                    isActive = false,
                                    modifier = Modifier.clickable {
                                        editingSchedule = schedule
                                        showScheduleDialog = true
                                    }
                                )

                                TerminalBadge(
                                    text = if (schedule.enabled) strings.activeBadge else strings.offLabel,
                                    isActive = schedule.enabled,
                                    modifier = Modifier.clickable {
                                        onSaveSchedule(schedule.copy(enabled = !schedule.enabled))
                                    }
                                )

                                TerminalBadge(
                                    text = strings.deleteButton,
                                    isActive = false,
                                    modifier = Modifier.clickable {
                                        onDeleteSchedule(schedule.id)
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        val typeLabel = if (schedule.scheduleType == ScheduleType.HARD_BLOCK) {
                            strings.fullBlockBadge
                        } else {
                            strings.scheduledInterventionsBadge
                        }
                        Text(
                            text = typeLabel,
                            fontFamily = TerminalFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp,
                            color = palette.textSecondary
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "⏰ ${formatDays(schedule.days, strings)} · ${schedule.start} → ${schedule.end}",
                            fontFamily = TerminalFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = palette.accent
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        val appNames = if (schedule.packages.size == targets.size && targets.isNotEmpty()) {
                            strings.allAppsBadge(targets.size)
                        } else {
                            val names = schedule.packages.mapNotNull { targetMap[it]?.displayName ?: it.substringAfterLast('.') }
                            if (names.isEmpty()) strings.noneSelectedLabel else names.joinToString(", ")
                        }

                        val appPrefix = if (schedule.scheduleType == ScheduleType.HARD_BLOCK) strings.blockedPrefix else strings.interventionsPrefix
                        Text(
                            text = "$appPrefix: $appNames",
                            fontFamily = TerminalFontFamily,
                            fontSize = 11.sp,
                            color = palette.textSecondary,
                            maxLines = 2
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleEditorDialog(
    initialSchedule: BlockSchedule? = null,
    targets: List<TargetApp>,
    onSave: (BlockSchedule) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalAppPalette.current
    val accent = palette.accent
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var name by remember(initialSchedule) { mutableStateOf(initialSchedule?.name ?: "") }
    var startHour by remember(initialSchedule) { mutableIntStateOf(initialSchedule?.start?.hour ?: 9) }
    var startMinute by remember(initialSchedule) { mutableIntStateOf(initialSchedule?.start?.minute ?: 0) }
    var endHour by remember(initialSchedule) { mutableIntStateOf(initialSchedule?.end?.hour ?: 18) }
    var endMinute by remember(initialSchedule) { mutableIntStateOf(initialSchedule?.end?.minute ?: 0) }

    var startHourInput by remember(initialSchedule) { mutableStateOf(String.format(Locale.US, "%02d", startHour)) }
    var startMinuteInput by remember(initialSchedule) { mutableStateOf(String.format(Locale.US, "%02d", startMinute)) }
    var endHourInput by remember(initialSchedule) { mutableStateOf(String.format(Locale.US, "%02d", endHour)) }
    var endMinuteInput by remember(initialSchedule) { mutableStateOf(String.format(Locale.US, "%02d", endMinute)) }

    fun setStartTime(h: Int, m: Int) {
        startHour = h.mod(24)
        startMinute = m.mod(60)
        startHourInput = String.format(Locale.US, "%02d", startHour)
        startMinuteInput = String.format(Locale.US, "%02d", startMinute)
    }

    fun setEndTime(h: Int, m: Int) {
        endHour = h.mod(24)
        endMinute = m.mod(60)
        endHourInput = String.format(Locale.US, "%02d", endHour)
        endMinuteInput = String.format(Locale.US, "%02d", endMinute)
    }

    var selectedDays by remember(initialSchedule) {
        mutableStateOf(
            initialSchedule?.days ?: setOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY
            )
        )
    }

    var selectedPackages by remember(initialSchedule) {
        mutableStateOf(initialSchedule?.packages ?: targets.map { it.packageName }.toSet())
    }

    var scheduleType by remember(initialSchedule) {
        mutableStateOf(initialSchedule?.scheduleType ?: ScheduleType.HARD_BLOCK)
    }

    var appOverrides by remember(initialSchedule) {
        mutableStateOf(initialSchedule?.appOverrides?.toMutableMap() ?: mutableMapOf<String, ScheduleAppOverride>())
    }

    val strings = LocalAppStrings.current
    val dialogTitle = if (initialSchedule != null) {
        strings.editScheduleTitle
    } else {
        strings.newScheduleTitle
    }

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(palette.surface)
                .border(1.dp, palette.border, RoundedCornerShape(8.dp))
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = dialogTitle,
                fontFamily = TerminalFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = accent
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Name
            TerminalInputField(
                value = name,
                onValueChange = { name = it },
                label = strings.scheduleNameLabel,
                maxLength = 24,
                maxLines = 1,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) })
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Schedule Type Selector
            Text(
                text = strings.scheduleTypeLabel,
                fontFamily = TerminalFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                color = palette.textSecondary
            )

            Spacer(modifier = Modifier.height(6.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val isHardBlock = scheduleType == ScheduleType.HARD_BLOCK
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isHardBlock) palette.surfaceElevated else palette.surface)
                        .border(1.dp, if (isHardBlock) accent else palette.border, RoundedCornerShape(6.dp))
                        .clickable { scheduleType = ScheduleType.HARD_BLOCK }
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = strings.hardBlockTypeTitle,
                            fontFamily = TerminalFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (isHardBlock) accent else palette.textPrimary
                        )
                        TerminalBadge(
                            text = if (isHardBlock) strings.selectedBadge else "—",
                            isActive = isHardBlock
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = strings.hardBlockTypeDesc,
                        fontFamily = TerminalFontFamily,
                        fontSize = 10.sp,
                        color = palette.textSecondary
                    )
                }

                val isIntervention = scheduleType == ScheduleType.INTERVENTION
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isIntervention) palette.surfaceElevated else palette.surface)
                        .border(1.dp, if (isIntervention) accent else palette.border, RoundedCornerShape(6.dp))
                        .clickable { scheduleType = ScheduleType.INTERVENTION }
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = strings.customInterventionsTitle,
                            fontFamily = TerminalFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (isIntervention) accent else palette.textPrimary
                        )
                        TerminalBadge(
                            text = if (isIntervention) strings.selectedBadge else "—",
                            isActive = isIntervention
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = strings.customInterventionsDesc,
                        fontFamily = TerminalFontFamily,
                        fontSize = 10.sp,
                        color = palette.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Time range display
            val startFormatted = String.format(Locale.US, "%02d:%02d", startHour, startMinute)
            val endFormatted = String.format(Locale.US, "%02d:%02d", endHour, endMinute)

            Text(
                text = "${strings.timeWindowPrefix}: $startFormatted → $endFormatted",
                fontFamily = TerminalFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                color = palette.textSecondary
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Quick range presets
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    strings.presetWork to (9 to 18),
                    strings.presetNight to (23 to 7),
                    strings.presetMorning to (7 to 12),
                    strings.presetEvening to (18 to 23)
                ).forEach { (lbl, range) ->
                    val isActive = startHour == range.first && startMinute == 0 && endHour == range.second && endMinute == 0
                    TerminalBadge(
                        text = lbl,
                        isActive = isActive,
                        modifier = Modifier.clickable {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                            setStartTime(range.first, 0)
                            setEndTime(range.second, 0)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Start time section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.surfaceElevated, RoundedCornerShape(6.dp))
                    .border(1.dp, palette.border, RoundedCornerShape(6.dp))
                    .padding(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.startLabel,
                        fontFamily = TerminalFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = palette.textSecondary
                    )
                    Text(
                        text = startFormatted,
                        fontFamily = TerminalFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = accent
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Inputs row: Hours & Minutes
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Hours input
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.hoursLabel,
                            fontFamily = TerminalFontFamily,
                            fontSize = 10.sp,
                            color = palette.textSecondary,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, palette.border, RoundedCornerShape(4.dp))
                                .background(palette.surface, RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            BasicTextField(
                                value = startHourInput,
                                onValueChange = { input ->
                                    val filtered = input.filter { it.isDigit() }.take(2)
                                    startHourInput = filtered
                                    val parsed = filtered.toIntOrNull()
                                    if (parsed != null) {
                                        startHour = parsed.coerceIn(0, 23)
                                    }
                                },
                                textStyle = TextStyle(
                                    fontFamily = TerminalFontFamily,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.textPrimary,
                                    textAlign = TextAlign.Center
                                ),
                                cursorBrush = SolidColor(accent),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Next
                                ),
                                keyboardActions = KeyboardActions(
                                    onNext = { focusManager.moveFocus(FocusDirection.Next) }
                                ),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onKeyEvent {
                                        if (it.key == Key.Enter) {
                                            focusManager.moveFocus(FocusDirection.Next)
                                             true
                                        } else false
                                    }
                            )
                        }
                    }

                    Text(
                        text = ":",
                        fontFamily = TerminalFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = accent,
                        modifier = Modifier.padding(top = 12.dp)
                    )

                    // Minutes input
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.minutesLabel,
                            fontFamily = TerminalFontFamily,
                            fontSize = 10.sp,
                            color = palette.textSecondary,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, palette.border, RoundedCornerShape(4.dp))
                                .background(palette.surface, RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            BasicTextField(
                                value = startMinuteInput,
                                onValueChange = { input ->
                                    val filtered = input.filter { it.isDigit() }.take(2)
                                    startMinuteInput = filtered
                                    val parsed = filtered.toIntOrNull()
                                    if (parsed != null) {
                                        startMinute = parsed.coerceIn(0, 59)
                                    }
                                },
                                textStyle = TextStyle(
                                    fontFamily = TerminalFontFamily,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.textPrimary,
                                    textAlign = TextAlign.Center
                                ),
                                cursorBrush = SolidColor(accent),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Next
                                ),
                                keyboardActions = KeyboardActions(
                                    onNext = { focusManager.moveFocus(FocusDirection.Next) }
                                ),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onKeyEvent {
                                        if (it.key == Key.Enter) {
                                            focusManager.moveFocus(FocusDirection.Next)
                                            true
                                        } else false
                                    }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Steppers row: hours on left, minutes on right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val hUnit = strings.hoursLabel.take(1).lowercase()
                    val mUnit = strings.minutesUnit.take(1)
                    // Hours steppers
                    Row(
                        modifier = Modifier.weight(2f),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        TerminalButton(
                            text = "-1$hUnit",
                            onClick = {
                                focusManager.clearFocus()
                                setStartTime(startHour - 1, startMinute)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        TerminalButton(
                            text = "+1$hUnit",
                            onClick = {
                                focusManager.clearFocus()
                                setStartTime(startHour + 1, startMinute)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Minutes steppers: -5m, -1m, +1m, +5m
                    Row(
                        modifier = Modifier.weight(4f),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        TerminalButton(
                            text = "-5$mUnit",
                            onClick = {
                                focusManager.clearFocus()
                                setStartTime(startHour, startMinute - 5)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        TerminalButton(
                            text = "-1$mUnit",
                            onClick = {
                                focusManager.clearFocus()
                                setStartTime(startHour, startMinute - 1)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        TerminalButton(
                            text = "+1$mUnit",
                            onClick = {
                                focusManager.clearFocus()
                                setStartTime(startHour, startMinute + 1)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        TerminalButton(
                            text = "+5$mUnit",
                            onClick = {
                                focusManager.clearFocus()
                                setStartTime(startHour, startMinute + 5)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // End time section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.surfaceElevated, RoundedCornerShape(6.dp))
                    .border(1.dp, palette.border, RoundedCornerShape(6.dp))
                    .padding(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.endLabel,
                        fontFamily = TerminalFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = palette.textSecondary
                    )
                    Text(
                        text = endFormatted,
                        fontFamily = TerminalFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = accent
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Inputs row: Hours & Minutes
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Hours input
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.hoursLabel,
                            fontFamily = TerminalFontFamily,
                            fontSize = 10.sp,
                            color = palette.textSecondary,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, palette.border, RoundedCornerShape(4.dp))
                                .background(palette.surface, RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            BasicTextField(
                                value = endHourInput,
                                onValueChange = { input ->
                                    val filtered = input.filter { it.isDigit() }.take(2)
                                    endHourInput = filtered
                                    val parsed = filtered.toIntOrNull()
                                    if (parsed != null) {
                                        endHour = parsed.coerceIn(0, 23)
                                    }
                                },
                                textStyle = TextStyle(
                                    fontFamily = TerminalFontFamily,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.textPrimary,
                                    textAlign = TextAlign.Center
                                ),
                                cursorBrush = SolidColor(accent),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Next
                                ),
                                keyboardActions = KeyboardActions(
                                    onNext = { focusManager.moveFocus(FocusDirection.Next) }
                                ),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onKeyEvent {
                                        if (it.key == Key.Enter) {
                                            focusManager.moveFocus(FocusDirection.Next)
                                            true
                                        } else false
                                    }
                            )
                        }
                    }

                    Text(
                        text = ":",
                        fontFamily = TerminalFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = accent,
                        modifier = Modifier.padding(top = 12.dp)
                    )

                    // Minutes input
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.minutesLabel,
                            fontFamily = TerminalFontFamily,
                            fontSize = 10.sp,
                            color = palette.textSecondary,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, palette.border, RoundedCornerShape(4.dp))
                                .background(palette.surface, RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            BasicTextField(
                                value = endMinuteInput,
                                onValueChange = { input ->
                                    val filtered = input.filter { it.isDigit() }.take(2)
                                    endMinuteInput = filtered
                                    val parsed = filtered.toIntOrNull()
                                    if (parsed != null) {
                                        endMinute = parsed.coerceIn(0, 59)
                                    }
                                },
                                textStyle = TextStyle(
                                    fontFamily = TerminalFontFamily,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.textPrimary,
                                    textAlign = TextAlign.Center
                                ),
                                cursorBrush = SolidColor(accent),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        if (endMinuteInput.isBlank()) endMinuteInput = "00"
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                    }
                                ),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onKeyEvent {
                                        if (it.key == Key.Enter) {
                                            if (endMinuteInput.isBlank()) endMinuteInput = "00"
                                            focusManager.clearFocus()
                                            keyboardController?.hide()
                                            true
                                        } else false
                                    }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Steppers row: hours on left, minutes on right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val hUnit = strings.hoursLabel.take(1).lowercase()
                    val mUnit = strings.minutesUnit.take(1)
                    // Hours steppers
                    Row(
                        modifier = Modifier.weight(2f),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        TerminalButton(
                            text = "-1$hUnit",
                            onClick = {
                                focusManager.clearFocus()
                                setEndTime(endHour - 1, endMinute)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        TerminalButton(
                            text = "+1$hUnit",
                            onClick = {
                                focusManager.clearFocus()
                                setEndTime(endHour + 1, endMinute)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Minutes steppers: -5m, -1m, +1m, +5m
                    Row(
                        modifier = Modifier.weight(4f),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        TerminalButton(
                            text = "-5$mUnit",
                            onClick = {
                                focusManager.clearFocus()
                                setEndTime(endHour, endMinute - 5)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        TerminalButton(
                            text = "-1$mUnit",
                            onClick = {
                                focusManager.clearFocus()
                                setEndTime(endHour, endMinute - 1)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        TerminalButton(
                            text = "+1$mUnit",
                            onClick = {
                                focusManager.clearFocus()
                                setEndTime(endHour, endMinute + 1)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        TerminalButton(
                            text = "+5$mUnit",
                            onClick = {
                                focusManager.clearFocus()
                                setEndTime(endHour, endMinute + 5)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Days of week
            Text(
                text = strings.daysOfWeekTitle,
                fontFamily = TerminalFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                color = palette.textSecondary
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Quick days presets
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
                val weekends = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
                val allDays = DayOfWeek.entries.toSet()

                TerminalBadge(
                    text = strings.weekdaysBadge,
                    isActive = selectedDays == weekdays,
                    modifier = Modifier.clickable { selectedDays = weekdays }
                )
                TerminalBadge(
                    text = strings.weekendsBadge,
                    isActive = selectedDays == weekends,
                    modifier = Modifier.clickable { selectedDays = weekends }
                )
                TerminalBadge(
                    text = strings.allDaysBadge,
                    isActive = selectedDays == allDays,
                    modifier = Modifier.clickable { selectedDays = allDays }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Day chips
            val dayList = listOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
                DayOfWeek.SATURDAY,
                DayOfWeek.SUNDAY
            ).map { it to strings.dayChipName(it) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                dayList.forEach { (day, label) ->
                    val isDaySelected = day in selectedDays
                    TerminalBadge(
                        text = label,
                        isActive = isDaySelected,
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                selectedDays = if (isDaySelected) {
                                    if (selectedDays.size > 1) selectedDays - day else selectedDays
                                } else {
                                    selectedDays + day
                                }
                            }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Target Apps Selection
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = strings.forAppsCount(selectedPackages.size, targets.size),
                    fontFamily = TerminalFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    color = palette.textSecondary
                )

                Text(
                    text = if (selectedPackages.size == targets.size) strings.deselectAll else strings.selectAll,
                    fontFamily = TerminalFontFamily,
                    fontSize = 10.sp,
                    color = accent,
                    modifier = Modifier.clickable {
                        selectedPackages = if (selectedPackages.size == targets.size) {
                            emptySet()
                        } else {
                            targets.map { it.packageName }.toSet()
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (targets.isEmpty()) {
                Text(
                    text = strings.noProtectedAppsBlocksHint,
                    fontFamily = TerminalFontFamily,
                    fontSize = 11.sp,
                    color = palette.textSecondary
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    targets.forEach { target ->
                        val isChecked = target.packageName in selectedPackages
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isChecked) palette.surfaceElevated else palette.surface)
                                    .border(1.dp, if (isChecked) accent else palette.border, RoundedCornerShape(4.dp))
                                    .clickable {
                                        selectedPackages = if (isChecked) {
                                            selectedPackages - target.packageName
                                        } else {
                                            selectedPackages + target.packageName
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = target.displayName,
                                    fontFamily = TerminalFontFamily,
                                    fontSize = 12.sp,
                                    color = if (isChecked) accent else palette.textPrimary
                                )
                                TerminalBadge(
                                    text = if (isChecked) strings.onLabel else "—",
                                    isActive = isChecked
                                )
                            }

                            if (isChecked && scheduleType == ScheduleType.INTERVENTION) {
                                AppInterventionOverrideCard(
                                    target = target,
                                    override = appOverrides[target.packageName],
                                    onOverrideChange = { newOverride ->
                                        appOverrides = appOverrides.toMutableMap().apply {
                                            put(target.packageName, newOverride)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TerminalButton(
                    text = strings.cancelButton,
                    onClick = onDismiss,
                    isPrimary = false,
                    modifier = Modifier.weight(1f)
                )
                TerminalButton(
                    text = strings.saveButton,
                    onClick = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        val hStart = startHourInput.toIntOrNull()?.coerceIn(0, 23) ?: startHour
                        val mStart = startMinuteInput.toIntOrNull()?.coerceIn(0, 59) ?: startMinute
                        val hEnd = endHourInput.toIntOrNull()?.coerceIn(0, 23) ?: endHour
                        val mEnd = endMinuteInput.toIntOrNull()?.coerceIn(0, 59) ?: endMinute
                        val pkgs = if (selectedPackages.isEmpty()) targets.map { it.packageName }.toSet() else selectedPackages
                        val schedule = BlockSchedule(
                            id = initialSchedule?.id ?: 0L,
                            name = name.trim().ifEmpty { if (scheduleType == ScheduleType.HARD_BLOCK) strings.defaultScheduleNameBlock else strings.defaultScheduleNameIntervention },
                            days = selectedDays,
                            start = LocalTime.of(hStart, mStart),
                            end = LocalTime.of(hEnd, mEnd),
                            packages = pkgs,
                            enabled = initialSchedule?.enabled ?: true,
                            scheduleType = scheduleType,
                            appOverrides = if (scheduleType == ScheduleType.INTERVENTION) appOverrides else emptyMap()
                        )
                        onSave(schedule)
                    },
                    isPrimary = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun AddScheduleDialog(
    targets: List<TargetApp>,
    onSave: (BlockSchedule) -> Unit,
    onDismiss: () -> Unit
) {
    ScheduleEditorDialog(
        initialSchedule = null,
        targets = targets,
        onSave = onSave,
        onDismiss = onDismiss
    )
}

@Composable
private fun AppInterventionOverrideCard(
    target: TargetApp,
    override: ScheduleAppOverride?,
    onOverrideChange: (ScheduleAppOverride) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    val accent = palette.accent
    val focusManager = LocalFocusManager.current

    val effectiveDurationMs = override?.durationMs ?: target.intervention.durationMs
    val effectiveDurationSec = (effectiveDurationMs / 1000L).coerceAtLeast(1L)
    val effectiveReinterventionMs = if (override != null) override.reinterventionMs else target.intervention.reinterventionMs

    var isExpanded by remember { mutableStateOf(true) }
    var durationText by remember(effectiveDurationSec) { mutableStateOf(effectiveDurationSec.toString()) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(palette.surfaceElevated)
            .border(1.dp, palette.border, RoundedCornerShape(6.dp))
            .padding(10.dp)
    ) {
        val strings = LocalAppStrings.current
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = strings.customRulesTitle(target.displayName),
                fontFamily = TerminalFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = accent
            )
            Text(
                text = if (isExpanded) "▲" else "▼",
                fontFamily = TerminalFontFamily,
                fontSize = 10.sp,
                color = palette.textSecondary
            )
        }

        if (isExpanded) {
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = strings.pauseDurationTitle,
                fontFamily = TerminalFontFamily,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.textSecondary
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val sUnit = strings.secondsShort
                TerminalButton(
                    text = "-5$sUnit",
                    onClick = {
                        val newSec = (effectiveDurationSec - 5L).coerceAtLeast(1L)
                        durationText = newSec.toString()
                        onOverrideChange(
                            ScheduleAppOverride(
                                durationMs = newSec * 1000L,
                                reinterventionMs = effectiveReinterventionMs
                            )
                        )
                    },
                    modifier = Modifier.weight(1f)
                )

                TerminalButton(
                    text = "-1$sUnit",
                    onClick = {
                        val newSec = (effectiveDurationSec - 1L).coerceAtLeast(1L)
                        durationText = newSec.toString()
                        onOverrideChange(
                            ScheduleAppOverride(
                                durationMs = newSec * 1000L,
                                reinterventionMs = effectiveReinterventionMs
                            )
                        )
                    },
                    modifier = Modifier.weight(1f)
                )

                Box(
                    modifier = Modifier
                        .weight(1.4f)
                        .border(1.dp, palette.border, RoundedCornerShape(4.dp))
                        .background(palette.surface, RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        BasicTextField(
                            value = durationText,
                            onValueChange = { input ->
                                val filtered = input.filter { it.isDigit() }.take(3)
                                durationText = filtered
                                val parsed = filtered.toLongOrNull()
                                if (parsed != null && parsed > 0) {
                                    val sec = parsed.coerceIn(1L, 300L)
                                    onOverrideChange(
                                        ScheduleAppOverride(
                                            durationMs = sec * 1000L,
                                            reinterventionMs = effectiveReinterventionMs
                                        )
                                    )
                                }
                            },
                            textStyle = TextStyle(
                                fontFamily = TerminalFontFamily,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.textPrimary,
                                textAlign = TextAlign.Center
                            ),
                            cursorBrush = SolidColor(accent),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (durationText.isBlank() || durationText == "0") {
                                        durationText = effectiveDurationSec.toString()
                                    }
                                    focusManager.clearFocus()
                                }
                            ),
                            singleLine = true,
                            modifier = Modifier.width(32.dp)
                        )
                        Text(
                            text = sUnit,
                            fontFamily = TerminalFontFamily,
                            fontSize = 11.sp,
                            color = palette.textSecondary
                        )
                    }
                }

                TerminalButton(
                    text = "+1$sUnit",
                    onClick = {
                        val newSec = (effectiveDurationSec + 1L).coerceAtMost(300L)
                        durationText = newSec.toString()
                        onOverrideChange(
                            ScheduleAppOverride(
                                durationMs = newSec * 1000L,
                                reinterventionMs = effectiveReinterventionMs
                            )
                        )
                    },
                    modifier = Modifier.weight(1f)
                )

                TerminalButton(
                    text = "+5$sUnit",
                    onClick = {
                        val newSec = (effectiveDurationSec + 5L).coerceAtMost(300L)
                        durationText = newSec.toString()
                        onOverrideChange(
                            ScheduleAppOverride(
                                durationMs = newSec * 1000L,
                                reinterventionMs = effectiveReinterventionMs
                            )
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = strings.repeatInterventionTitle,
                fontFamily = TerminalFontFamily,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.textSecondary
            )

            Spacer(modifier = Modifier.height(4.dp))

            val mUnit = strings.minutesUnit.take(1)
            val intervalChips = listOf(
                strings.optionOff to null,
                "1$mUnit" to 60_000L,
                "3$mUnit" to 180_000L,
                "5$mUnit" to 300_000L,
                "10$mUnit" to 600_000L
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                intervalChips.forEach { (label, ms) ->
                    val isSelected = effectiveReinterventionMs == ms
                    TerminalBadge(
                        text = label,
                        isActive = isSelected,
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                onOverrideChange(
                                    ScheduleAppOverride(
                                        durationMs = effectiveDurationSec * 1000L,
                                        reinterventionMs = ms
                                    )
                                )
                            }
                    )
                }
            }
        }
    }
}
