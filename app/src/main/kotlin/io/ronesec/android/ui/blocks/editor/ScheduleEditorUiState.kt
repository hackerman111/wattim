package io.ronesec.android.ui.blocks.editor

import io.ronesec.android.R
import io.ronesec.domain.model.ScheduleOverride
import io.ronesec.domain.model.ScheduleType
import java.time.DayOfWeek

enum class SchedulePreset(
    val labelResId: Int,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int
) {
    WORK(R.string.preset_work, 9, 0, 18, 0),
    NIGHT(R.string.preset_night, 23, 0, 7, 0),
    MORNING(R.string.preset_morning, 7, 0, 12, 0),
    EVENING(R.string.preset_evening, 18, 0, 23, 0)
}

data class TargetAppChoice(
    val packageName: String,
    val displayName: String
)

data class ScheduleEditorDraft(
    val scheduleId: Long? = null,
    val name: String = "",
    val type: ScheduleType = ScheduleType.HARD_BLOCK,
    val selectedDays: Set<DayOfWeek> = setOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY
    ),
    val startHour: Int = 9,
    val startMinute: Int = 0,
    val endHour: Int = 18,
    val endMinute: Int = 0,
    val selectedPackages: Set<String> = emptySet(),
    val overrides: Map<String, ScheduleOverride> = emptyMap()
)

data class ScheduleEditorUiState(
    val draft: ScheduleEditorDraft = ScheduleEditorDraft(),
    val availableTargets: List<TargetAppChoice> = emptyList(),
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val errorMessage: String? = null
) {
    val isNameValid: Boolean
        get() = draft.name.trim().isNotEmpty()

    val areDaysValid: Boolean
        get() = draft.selectedDays.isNotEmpty()

    val isTimeValid: Boolean
        get() = (draft.startHour * 60 + draft.startMinute) != (draft.endHour * 60 + draft.endMinute)

    val areTargetsValid: Boolean
        get() = draft.selectedPackages.isNotEmpty()

    val isOvernight: Boolean
        get() = (draft.endHour * 60 + draft.endMinute) < (draft.startHour * 60 + draft.startMinute)

    val canSave: Boolean
        get() = isNameValid && areDaysValid && isTimeValid && areTargetsValid && !isLoading

    val validationErrorResId: Int?
        get() = when {
            !isNameValid -> R.string.error_empty_name
            !areDaysValid -> R.string.error_empty_days
            !isTimeValid -> R.string.error_equal_times
            !areTargetsValid -> R.string.error_empty_targets
            else -> null
        }
}
