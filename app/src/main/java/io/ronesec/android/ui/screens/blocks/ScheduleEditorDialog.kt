package io.ronesec.android.ui.screens.blocks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ronesec.android.domain.model.BlockSchedule
import io.ronesec.android.domain.model.ScheduleType
import io.ronesec.android.domain.model.TargetApp
import io.ronesec.android.ui.components.TerminalButton
import io.ronesec.android.ui.components.TerminalInputField
import io.ronesec.android.ui.components.TimeInputSection
import io.ronesec.android.ui.i18n.LocalAppStrings
import io.ronesec.android.ui.theme.LocalAppPalette
import io.ronesec.android.ui.theme.TerminalFontFamily
import java.time.DayOfWeek
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleEditorDialog(
    initialSchedule: BlockSchedule?,
    targets: List<TargetApp>,
    onSave: (BlockSchedule) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalAppPalette.current
    val accent = palette.accent
    val focusManager = LocalFocusManager.current
    val strings = LocalAppStrings.current

    var name by remember {
        mutableStateOf(
            initialSchedule?.name ?: if (initialSchedule?.scheduleType == ScheduleType.INTERVENTION) {
                strings.defaultScheduleNameIntervention
            } else {
                strings.defaultScheduleNameBlock
            }
        )
    }
    var scheduleType by remember {
        mutableStateOf(initialSchedule?.scheduleType ?: ScheduleType.HARD_BLOCK)
    }

    var startHour by remember { mutableIntStateOf(initialSchedule?.start?.hour ?: 9) }
    var startMinute by remember { mutableIntStateOf(initialSchedule?.start?.minute ?: 0) }
    var endHour by remember { mutableIntStateOf(initialSchedule?.end?.hour ?: 18) }
    var endMinute by remember { mutableIntStateOf(initialSchedule?.end?.minute ?: 0) }

    var selectedDays by remember {
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

    var selectedPackages by remember {
        mutableStateOf(
            initialSchedule?.packages ?: targets.filter { it.enabled }.map { it.packageName }.toSet()
        )
    }

    var appOverrides by remember {
        mutableStateOf(initialSchedule?.appOverrides ?: emptyMap())
    }

    val dialogTitle = if (initialSchedule != null) strings.editScheduleTitle else strings.newScheduleTitle

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

            ScheduleTypeSelector(
                scheduleType = scheduleType,
                onScheduleTypeChange = { scheduleType = it }
            )

            Spacer(modifier = Modifier.height(14.dp))

            SchedulePresetTimeChips(
                startHour = startHour,
                startMinute = startMinute,
                endHour = endHour,
                endMinute = endMinute,
                onSelectPreset = { sh, sm, eh, em ->
                    focusManager.clearFocus()
                    startHour = sh
                    startMinute = sm
                    endHour = eh
                    endMinute = em
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            TimeInputSection(
                title = strings.startLabel,
                hour = startHour,
                minute = startMinute,
                onTimeChange = { h, m -> startHour = h; startMinute = m }
            )

            Spacer(modifier = Modifier.height(10.dp))

            TimeInputSection(
                title = strings.endLabel,
                hour = endHour,
                minute = endMinute,
                onTimeChange = { h, m -> endHour = h; endMinute = m }
            )

            Spacer(modifier = Modifier.height(14.dp))

            ScheduleDaysSection(
                selectedDays = selectedDays,
                onDaysChange = { selectedDays = it }
            )

            Spacer(modifier = Modifier.height(14.dp))

            ScheduleTargetAppSelector(
                targets = targets,
                selectedPackages = selectedPackages,
                scheduleType = scheduleType,
                appOverrides = appOverrides,
                onSelectedPackagesChange = { selectedPackages = it },
                onAppOverridesChange = { appOverrides = it }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TerminalButton(
                    text = strings.cancelButton,
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
                TerminalButton(
                    text = strings.saveButton,
                    onClick = {
                        val schedule = BlockSchedule(
                            id = initialSchedule?.id ?: 0L,
                            name = name.ifBlank {
                                if (scheduleType == ScheduleType.INTERVENTION) strings.defaultScheduleNameIntervention else strings.defaultScheduleNameBlock
                            },
                            days = selectedDays,
                            start = LocalTime.of(startHour, startMinute),
                            end = LocalTime.of(endHour, endMinute),
                            packages = selectedPackages,
                            enabled = initialSchedule?.enabled ?: true,
                            scheduleType = scheduleType,
                            appOverrides = appOverrides
                        )
                        onSave(schedule)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
