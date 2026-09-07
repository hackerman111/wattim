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
import io.ronesec.android.domain.model.BlockSchedule
import io.ronesec.android.domain.model.BlockSession
import io.ronesec.android.domain.model.TargetApp
import io.ronesec.android.ui.components.TerminalBadge
import io.ronesec.android.ui.components.TerminalButton
import io.ronesec.android.ui.components.TerminalCard
import io.ronesec.android.ui.components.TerminalInputField
import io.ronesec.android.ui.theme.LocalAppPalette
import io.ronesec.android.ui.theme.TerminalFontFamily
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.util.Locale

private fun formatDaysRu(days: Set<DayOfWeek>): String {
    val dayMap = mapOf(
        DayOfWeek.MONDAY to "Пн",
        DayOfWeek.TUESDAY to "Вт",
        DayOfWeek.WEDNESDAY to "Ср",
        DayOfWeek.THURSDAY to "Чт",
        DayOfWeek.FRIDAY to "Пт",
        DayOfWeek.SATURDAY to "Сб",
        DayOfWeek.SUNDAY to "Вс"
    )
    return days.sorted().joinToString(", ") { dayMap[it] ?: it.name.take(2) }
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
    val activeSession = activeSessions.firstOrNull { it.active && it.endTime.isAfter(Instant.now()) }

    var showAddScheduleDialog by remember { mutableStateOf(false) }

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

    if (showAddScheduleDialog) {
        AddScheduleDialog(
            targets = targets,
            onSave = { newSchedule ->
                onSaveSchedule(newSchedule)
                showAddScheduleDialog = false
            },
            onDismiss = { showAddScheduleDialog = false }
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
            text = "БЛОКИРОВКА",
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
                    text = "СЕССИЯ ФОКУСА АКТИВНА",
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
                    text = "Блокировка приложений: ${activeSession.packages.size}",
                    fontFamily = TerminalFontFamily,
                    fontSize = 12.sp,
                    color = palette.textSecondary
                )

                Spacer(modifier = Modifier.height(12.dp))

                TerminalButton(
                    text = "ОСТАНОВИТЬ СЕССИЮ",
                    onClick = { onStopHardBlock(activeSession.id) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            // Quick launcher
            TerminalCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "БЫСТРАЯ СЕССИЯ ФОКУСА",
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
                            text = "ДЛЯ ПРИЛОЖЕНИЙ (${selectedQuickPackages.size}/${targets.size})",
                            fontFamily = TerminalFontFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.textSecondary
                        )

                        Text(
                            text = if (selectedQuickPackages.size == targets.size) "Снять все" else "Выбрать все",
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
                        "15м" to 15,
                        "30м" to 30,
                        "1ч" to 60,
                        "2ч" to 120
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
                                onStartHardBlock("Сессия фокуса", mins, pkgs)
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
                text = "РАСПИСАНИЕ БЛОКИРОВОК",
                fontFamily = TerminalFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                letterSpacing = 0.15.sp,
                color = palette.accent
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        TerminalButton(
            text = "+ СОЗДАТЬ РАСПИСАНИЕ",
            onClick = { showAddScheduleDialog = true },
            isPrimary = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (schedules.isEmpty()) {
            TerminalCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "НЕТ ЗАПЛАНИРОВАННЫХ БЛОКИРОВОК",
                    fontFamily = TerminalFontFamily,
                    fontSize = 13.sp,
                    color = palette.textSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Создайте расписание, чтобы автоматически блокировать приложения в заданные часы.",
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
                                color = palette.textPrimary
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TerminalBadge(
                                    text = if (schedule.enabled) "АКТИВНО" else "ВЫКЛ",
                                    isActive = schedule.enabled,
                                    modifier = Modifier.clickable {
                                        onSaveSchedule(schedule.copy(enabled = !schedule.enabled))
                                    }
                                )

                                TerminalBadge(
                                    text = "УДАЛИТЬ",
                                    isActive = false,
                                    modifier = Modifier.clickable {
                                        onDeleteSchedule(schedule.id)
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "⏰ ${formatDaysRu(schedule.days)} · ${schedule.start} → ${schedule.end}",
                            fontFamily = TerminalFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = palette.accent
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        val appNames = if (schedule.packages.size == targets.size && targets.isNotEmpty()) {
                            "Все приложения (${targets.size})"
                        } else {
                            val names = schedule.packages.mapNotNull { targetMap[it]?.displayName ?: it.substringAfterLast('.') }
                            if (names.isEmpty()) "Не выбраны" else names.joinToString(", ")
                        }

                        Text(
                            text = "Заблокировано: $appNames",
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
fun AddScheduleDialog(
    targets: List<TargetApp>,
    onSave: (BlockSchedule) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalAppPalette.current
    val accent = palette.accent
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var name by remember { mutableStateOf("") }
    var startHour by remember { mutableIntStateOf(9) }
    var startMinute by remember { mutableIntStateOf(0) }
    var endHour by remember { mutableIntStateOf(18) }
    var endMinute by remember { mutableIntStateOf(0) }

    var startHourInput by remember { mutableStateOf(String.format(Locale.US, "%02d", startHour)) }
    var startMinuteInput by remember { mutableStateOf(String.format(Locale.US, "%02d", startMinute)) }
    var endHourInput by remember { mutableStateOf(String.format(Locale.US, "%02d", endHour)) }
    var endMinuteInput by remember { mutableStateOf(String.format(Locale.US, "%02d", endMinute)) }

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

    var selectedDays by remember {
        mutableStateOf(
            setOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY
            )
        )
    }

    var selectedPackages by remember {
        mutableStateOf(targets.map { it.packageName }.toSet())
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
                text = "НОВОЕ РАСПИСАНИЕ БЛОКИРОВКИ",
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
                label = "НАЗВАНИЕ РАСПИСАНИЯ",
                maxLength = 24,
                maxLines = 1,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) })
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Time range display
            val startFormatted = String.format(Locale.US, "%02d:%02d", startHour, startMinute)
            val endFormatted = String.format(Locale.US, "%02d:%02d", endHour, endMinute)

            Text(
                text = "ПРОМЕЖУТОК: $startFormatted → $endFormatted",
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
                    "Работа (9-18)" to (9 to 18),
                    "Ночь (23-7)" to (23 to 7),
                    "Утро (7-12)" to (7 to 12),
                    "Вечер (18-23)" to (18 to 23)
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
                        text = "НАЧАЛО",
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
                            text = "ЧАСЫ",
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
                            text = "МИНУТЫ",
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
                    // Hours steppers
                    Row(
                        modifier = Modifier.weight(2f),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        TerminalButton(
                            text = "-1ч",
                            onClick = {
                                focusManager.clearFocus()
                                setStartTime(startHour - 1, startMinute)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        TerminalButton(
                            text = "+1ч",
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
                            text = "-5м",
                            onClick = {
                                focusManager.clearFocus()
                                setStartTime(startHour, startMinute - 5)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        TerminalButton(
                            text = "-1м",
                            onClick = {
                                focusManager.clearFocus()
                                setStartTime(startHour, startMinute - 1)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        TerminalButton(
                            text = "+1м",
                            onClick = {
                                focusManager.clearFocus()
                                setStartTime(startHour, startMinute + 1)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        TerminalButton(
                            text = "+5м",
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
                        text = "КОНЕЦ",
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
                            text = "ЧАСЫ",
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
                            text = "МИНУТЫ",
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
                    // Hours steppers
                    Row(
                        modifier = Modifier.weight(2f),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        TerminalButton(
                            text = "-1ч",
                            onClick = {
                                focusManager.clearFocus()
                                setEndTime(endHour - 1, endMinute)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        TerminalButton(
                            text = "+1ч",
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
                            text = "-5м",
                            onClick = {
                                focusManager.clearFocus()
                                setEndTime(endHour, endMinute - 5)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        TerminalButton(
                            text = "-1м",
                            onClick = {
                                focusManager.clearFocus()
                                setEndTime(endHour, endMinute - 1)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        TerminalButton(
                            text = "+1м",
                            onClick = {
                                focusManager.clearFocus()
                                setEndTime(endHour, endMinute + 1)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        TerminalButton(
                            text = "+5м",
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
                text = "ДНИ НЕДЕЛИ",
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
                    text = "БУДНИ",
                    isActive = selectedDays == weekdays,
                    modifier = Modifier.clickable { selectedDays = weekdays }
                )
                TerminalBadge(
                    text = "ВЫХОДНЫЕ",
                    isActive = selectedDays == weekends,
                    modifier = Modifier.clickable { selectedDays = weekends }
                )
                TerminalBadge(
                    text = "ВСЕ ДНИ",
                    isActive = selectedDays == allDays,
                    modifier = Modifier.clickable { selectedDays = allDays }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Day chips
            val dayList = listOf(
                DayOfWeek.MONDAY to "ПН",
                DayOfWeek.TUESDAY to "ВТ",
                DayOfWeek.WEDNESDAY to "СР",
                DayOfWeek.THURSDAY to "ЧТ",
                DayOfWeek.FRIDAY to "ПТ",
                DayOfWeek.SATURDAY to "СБ",
                DayOfWeek.SUNDAY to "ВС"
            )
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
                    text = "ПРИЛОЖЕНИЯ (${selectedPackages.size}/${targets.size})",
                    fontFamily = TerminalFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    color = palette.textSecondary
                )

                Text(
                    text = if (selectedPackages.size == targets.size) "Снять все" else "Выбрать все",
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
                    text = "Нет защищенных приложений. Добавьте их во вкладке «Приложения».",
                    fontFamily = TerminalFontFamily,
                    fontSize = 11.sp,
                    color = palette.textSecondary
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    targets.forEach { target ->
                        val isChecked = target.packageName in selectedPackages
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
                                text = if (isChecked) "ВКЛ" else "—",
                                isActive = isChecked
                            )
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
                    text = "ОТМЕНА",
                    onClick = onDismiss,
                    isPrimary = false,
                    modifier = Modifier.weight(1f)
                )
                TerminalButton(
                    text = "СОХРАНИТЬ",
                    onClick = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        val hStart = startHourInput.toIntOrNull()?.coerceIn(0, 23) ?: startHour
                        val mStart = startMinuteInput.toIntOrNull()?.coerceIn(0, 59) ?: startMinute
                        val hEnd = endHourInput.toIntOrNull()?.coerceIn(0, 23) ?: endHour
                        val mEnd = endMinuteInput.toIntOrNull()?.coerceIn(0, 59) ?: endMinute
                        val pkgs = if (selectedPackages.isEmpty()) targets.map { it.packageName }.toSet() else selectedPackages
                        val schedule = BlockSchedule(
                            name = name.trim().ifEmpty { "Блокировка" },
                            days = selectedDays,
                            start = LocalTime.of(hStart, mStart),
                            end = LocalTime.of(hEnd, mEnd),
                            packages = pkgs,
                            enabled = true
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
