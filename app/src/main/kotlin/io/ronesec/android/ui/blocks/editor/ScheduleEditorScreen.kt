package io.ronesec.android.ui.blocks.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.ronesec.android.R
import io.ronesec.android.ui.designsystem.TerminalBadge
import io.ronesec.android.ui.designsystem.TerminalButton
import io.ronesec.android.ui.designsystem.TerminalButtonVariant
import io.ronesec.android.ui.designsystem.TerminalCard
import io.ronesec.android.ui.designsystem.TerminalInputField
import io.ronesec.android.ui.designsystem.TimeInputSection
import io.ronesec.android.ui.designsystem.WattimTheme
import io.ronesec.domain.model.ScheduleType
import java.time.DayOfWeek

@Composable
fun ScheduleEditorScreen(
    state: ScheduleEditorUiState,
    onBack: () -> Unit,
    onNameChange: (String) -> Unit,
    onTypeChange: (ScheduleType) -> Unit,
    onToggleDay: (DayOfWeek) -> Unit,
    onStartTimeChange: (hours: Int, minutes: Int) -> Unit,
    onEndTimeChange: (hours: Int, minutes: Int) -> Unit,
    onApplyPreset: (SchedulePreset) -> Unit,
    onToggleTarget: (packageName: String) -> Unit,
    onSelectAllTargets: () -> Unit,
    onSelectNoneTargets: () -> Unit,
    onSetDurationOverride: (packageName: String, durationMs: Long?) -> Unit,
    onSetRepeatOverride: (packageName: String, repeatMs: Long?) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = WattimTheme.colors
    val dimensions = WattimTheme.dimensions
    val typography = WattimTheme.typography

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) {
            onBack()
        }
    }

    val daysOrder = listOf(
        Pair(DayOfWeek.MONDAY, "MON"),
        Pair(DayOfWeek.TUESDAY, "TUE"),
        Pair(DayOfWeek.WEDNESDAY, "WED"),
        Pair(DayOfWeek.THURSDAY, "THU"),
        Pair(DayOfWeek.FRIDAY, "FRI"),
        Pair(DayOfWeek.SATURDAY, "SAT"),
        Pair(DayOfWeek.SUNDAY, "SUN")
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.92f)
                .background(colors.surface, RoundedCornerShape(8.dp))
                .border(1.dp, colors.border, RoundedCornerShape(8.dp))
                .padding(dimensions.space16)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(dimensions.space16)
        ) {
        Text(
            text = if (state.draft.scheduleId != null) {
                stringResource(R.string.schedule_editor_edit_title)
            } else {
                stringResource(R.string.schedule_editor_create_title)
            },
            style = typography.titleMedium,
            color = colors.accent
        )

        // Error message banner (commit failure or validation issue)
        if (state.errorMessage != null) {
            TerminalCard(modifier = Modifier.fillMaxWidth(), isError = true) {
                Text(
                    text = state.errorMessage,
                    style = typography.bodyMedium,
                    color = colors.error
                )
            }
        }

        // 2. Schedule Name (F66)
        TerminalInputField(
            value = state.draft.name,
            onValueChange = onNameChange,
            label = stringResource(R.string.schedule_name_label),
            placeholder = stringResource(R.string.schedule_name_hint),
            maxLength = 40,
            isError = !state.isNameValid && state.draft.name.isNotEmpty()
        )

        // 3. Schedule Type: HARD_BLOCK vs INTERVENTION (F64, F65)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(dimensions.space8)
        ) {
            Text(
                text = stringResource(R.string.schedule_type_label).uppercase(),
                style = typography.labelSmall,
                color = colors.textSecondary
            )

            ScheduleTypeChoiceCard(
                title = stringResource(R.string.schedule_type_hard_block),
                description = stringResource(R.string.schedule_type_hard_block_desc),
                isSelected = state.draft.type == ScheduleType.HARD_BLOCK,
                onClick = { onTypeChange(ScheduleType.HARD_BLOCK) }
            )

            ScheduleTypeChoiceCard(
                title = stringResource(R.string.schedule_type_intervention),
                description = stringResource(R.string.schedule_type_intervention_desc),
                isSelected = state.draft.type == ScheduleType.INTERVENTION,
                onClick = { onTypeChange(ScheduleType.INTERVENTION) }
            )
        }

        // 4. Weekdays multiselect (F66)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(dimensions.space8)
        ) {
            Text(
                text = stringResource(R.string.schedule_days_label).uppercase(),
                style = typography.labelSmall,
                color = colors.textSecondary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(dimensions.space4, Alignment.CenterHorizontally)
            ) {
                daysOrder.forEach { (day, label) ->
                    val isSelected = state.draft.selectedDays.contains(day)
                    EditorFilterChip(
                        text = label,
                        isSelected = isSelected,
                        onClick = { onToggleDay(day) }
                    )
                }
            }
        }

        // 5. Time Window & Presets (F66)
        TerminalCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(dimensions.space12)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.schedule_time_label).uppercase(),
                        style = typography.labelSmall,
                        color = colors.textSecondary
                    )

                    if (state.isOvernight) {
                        TerminalBadge(
                            text = stringResource(R.string.overnight_badge),
                            isActive = true
                        )
                    }
                }

                // Presets: Work, Night, Morning, Evening
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(dimensions.space4, Alignment.CenterHorizontally)
                ) {
                    SchedulePreset.values().forEach { preset ->
                        EditorFilterChip(
                            text = stringResource(preset.labelResId).substringBefore(" "),
                            isSelected = (state.draft.startHour == preset.startHour &&
                                    state.draft.startMinute == preset.startMinute &&
                                    state.draft.endHour == preset.endHour &&
                                    state.draft.endMinute == preset.endMinute),
                            onClick = { onApplyPreset(preset) }
                        )
                    }
                }

                // Start Time
                TimeInputSection(
                    hours = state.draft.startHour,
                    minutes = state.draft.startMinute,
                    onTimeChange = onStartTimeChange,
                    label = stringResource(R.string.schedule_time_start)
                )

                // End Time
                TimeInputSection(
                    hours = state.draft.endHour,
                    minutes = state.draft.endMinute,
                    onTimeChange = onEndTimeChange,
                    label = stringResource(R.string.schedule_time_end)
                )
            }
        }

        // 6. Target Applications Selector (F66)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(dimensions.space8)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${stringResource(R.string.schedule_targets_label)} (${state.draft.selectedPackages.size})".uppercase(),
                    style = typography.labelSmall,
                    color = colors.textSecondary
                )

                Row(horizontalArrangement = Arrangement.spacedBy(dimensions.space4)) {
                    EditorFilterChip(
                        text = stringResource(R.string.select_all),
                        isSelected = state.draft.selectedPackages.size == state.availableTargets.size && state.availableTargets.isNotEmpty(),
                        onClick = onSelectAllTargets
                    )
                    EditorFilterChip(
                        text = stringResource(R.string.deselect_all),
                        isSelected = state.draft.selectedPackages.isEmpty(),
                        onClick = onSelectNoneTargets
                    )
                }
            }

            // Target app chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(dimensions.space4)
            ) {
                state.availableTargets.forEach { target ->
                    val isSelected = state.draft.selectedPackages.contains(target.packageName)
                    EditorFilterChip(
                        text = target.displayName,
                        isSelected = isSelected,
                        onClick = { onToggleTarget(target.packageName) }
                    )
                }
            }
        }

        // 7. Per-App Overrides (F67): Visible ONLY for INTERVENTION mode
        if (state.draft.type == ScheduleType.INTERVENTION && state.draft.selectedPackages.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(dimensions.space8)
            ) {
                Text(
                    text = stringResource(R.string.schedule_overrides_label).uppercase(),
                    style = typography.labelSmall,
                    color = colors.textSecondary
                )

                state.availableTargets
                    .filter { state.draft.selectedPackages.contains(it.packageName) }
                    .forEach { target ->
                        AppInterventionOverrideCard(
                            packageName = target.packageName,
                            displayName = target.displayName,
                            override = state.draft.overrides[target.packageName],
                            onSetDurationOverride = onSetDurationOverride,
                            onSetRepeatOverride = onSetRepeatOverride
                        )
                    }
            }
        }

        // Validation warning explanation if invalid
        val validationError = state.validationErrorResId
        if (validationError != null) {
            Text(
                text = stringResource(validationError),
                style = typography.labelSmall,
                color = colors.error,
                modifier = Modifier.padding(horizontal = dimensions.space4)
            )
        }

        // 8. Bottom Save and Cancel actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(dimensions.space8)
        ) {
            TerminalButton(
                text = stringResource(R.string.action_cancel),
                onClick = onBack,
                variant = TerminalButtonVariant.SECONDARY,
                modifier = Modifier.weight(1f)
            )

            TerminalButton(
                text = stringResource(R.string.action_save),
                onClick = onSave,
                enabled = state.canSave,
                variant = TerminalButtonVariant.PRIMARY,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(dimensions.space16))
        }
    }
}

@Composable
private fun ScheduleTypeChoiceCard(
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = WattimTheme.colors
    val dimensions = WattimTheme.dimensions
    val typography = WattimTheme.typography
    val shape = RoundedCornerShape(dimensions.cardRadius)

    val borderColor = if (isSelected) colors.accent else colors.border
    val bgColor = if (isSelected) colors.accent.copy(alpha = 0.08f) else colors.surface

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.RadioButton, onClick = onClick)
            .border(BorderStroke(dimensions.borderWidth, borderColor), shape)
            .background(bgColor, shape)
            .padding(dimensions.space12)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(dimensions.space4)) {
            Text(
                text = title.uppercase(),
                style = typography.titleMedium,
                color = if (isSelected) colors.accent else colors.textPrimary
            )
            Text(
                text = description,
                style = typography.bodyMedium,
                color = colors.textSecondary
            )
        }
    }
}

@Composable
private fun EditorFilterChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = WattimTheme.colors
    val dimensions = WattimTheme.dimensions
    val typography = WattimTheme.typography
    val shape = RoundedCornerShape(dimensions.buttonRadius)

    val borderColor = if (isSelected) colors.accent else colors.border
    val bgColor = if (isSelected) colors.accent.copy(alpha = 0.15f) else colors.surface

    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 40.dp, minHeight = dimensions.minTouchTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .border(BorderStroke(dimensions.borderWidth, borderColor), shape)
            .background(bgColor, shape)
            .padding(horizontal = dimensions.space8, vertical = dimensions.space8),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = typography.labelSmall,
            color = if (isSelected) colors.accent else colors.textPrimary
        )
    }
}
