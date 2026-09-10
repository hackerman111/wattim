package io.ronesec.android.ui.blocks.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ronesec.android.data.PolicyStore
import io.ronesec.domain.model.CompiledSchedule
import io.ronesec.domain.model.RuntimePolicySnapshot
import io.ronesec.domain.model.ScheduleOverride
import io.ronesec.domain.model.ScheduleType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek

class ScheduleEditorViewModel(
    val scheduleId: Long? = null,
    private val policyStore: PolicyStore,
    private val savedStateHandle: SavedStateHandle? = null,
    private val coroutineScope: CoroutineScope? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val scope: CoroutineScope
        get() = coroutineScope ?: viewModelScope

    private val _uiState = MutableStateFlow(ScheduleEditorUiState())
    val uiState: StateFlow<ScheduleEditorUiState> = _uiState.asStateFlow()

    private var isDraftInitialized = false
    private var isUserSelectionInitialized = false

    init {
        scope.launch(ioDispatcher) {
            policyStore.snapshotFlow.collect { snapshot ->
                updateFromSnapshot(snapshot)
            }
        }
    }

    private fun updateFromSnapshot(snapshot: RuntimePolicySnapshot) {
        val available = snapshot.targets.values
            .sortedBy { it.displayName.lowercase() }
            .map { TargetAppChoice(it.packageName, it.displayName.ifBlank { it.packageName }) }

        if (scheduleId != null && !isDraftInitialized) {
            val existing = snapshot.activeSchedules.find { it.id == scheduleId }
            if (existing != null) {
                isDraftInitialized = true
                isUserSelectionInitialized = true
                val days = DayOfWeek.values().filter { existing.isApplicableToDay(it) }.toSet()
                _uiState.update { state ->
                    state.copy(
                        draft = ScheduleEditorDraft(
                            scheduleId = existing.id,
                            name = existing.name,
                            type = existing.type,
                            selectedDays = days,
                            startHour = existing.startMinute / 60,
                            startMinute = existing.startMinute % 60,
                            endHour = existing.endMinute / 60,
                            endMinute = existing.endMinute % 60,
                            selectedPackages = existing.targetPackages,
                            overrides = existing.overrides
                        ),
                        availableTargets = available
                    )
                }
                return
            }
        }

        if (scheduleId == null && !isUserSelectionInitialized && available.isNotEmpty()) {
            isUserSelectionInitialized = true
            val allPackages = available.map { it.packageName }.toSet()
            _uiState.update { state ->
                state.copy(
                    draft = state.draft.copy(selectedPackages = allPackages),
                    availableTargets = available
                )
            }
        } else {
            _uiState.update { it.copy(availableTargets = available) }
        }
    }

    fun onNameChange(name: String) {
        _uiState.update { it.copy(draft = it.draft.copy(name = name), errorMessage = null) }
    }

    fun onTypeChange(type: ScheduleType) {
        _uiState.update { it.copy(draft = it.draft.copy(type = type), errorMessage = null) }
    }

    fun onToggleDay(day: DayOfWeek) {
        _uiState.update { state ->
            val currentDays = state.draft.selectedDays
            val newDays = if (currentDays.contains(day)) currentDays - day else currentDays + day
            state.copy(draft = state.draft.copy(selectedDays = newDays), errorMessage = null)
        }
    }

    fun onStartTimeChange(hours: Int, minutes: Int) {
        _uiState.update { state ->
            state.copy(
                draft = state.draft.copy(
                    startHour = hours.coerceIn(0, 23),
                    startMinute = minutes.coerceIn(0, 59)
                ),
                errorMessage = null
            )
        }
    }

    fun onEndTimeChange(hours: Int, minutes: Int) {
        _uiState.update { state ->
            state.copy(
                draft = state.draft.copy(
                    endHour = hours.coerceIn(0, 23),
                    endMinute = minutes.coerceIn(0, 59)
                ),
                errorMessage = null
            )
        }
    }

    fun onApplyPreset(preset: SchedulePreset) {
        _uiState.update { state ->
            state.copy(
                draft = state.draft.copy(
                    startHour = preset.startHour,
                    startMinute = preset.startMinute,
                    endHour = preset.endHour,
                    endMinute = preset.endMinute
                ),
                errorMessage = null
            )
        }
    }

    fun onToggleTarget(packageName: String) {
        _uiState.update { state ->
            val current = state.draft.selectedPackages
            val newSet = if (current.contains(packageName)) current - packageName else current + packageName
            state.copy(draft = state.draft.copy(selectedPackages = newSet), errorMessage = null)
        }
    }

    fun onSelectAllTargets() {
        _uiState.update { state ->
            val all = state.availableTargets.map { it.packageName }.toSet()
            state.copy(draft = state.draft.copy(selectedPackages = all), errorMessage = null)
        }
    }

    fun onSelectNoneTargets() {
        _uiState.update { state ->
            state.copy(draft = state.draft.copy(selectedPackages = emptySet()), errorMessage = null)
        }
    }

    fun onSetDurationOverride(packageName: String, durationMs: Long?) {
        _uiState.update { state ->
            val currentOverrides = state.draft.overrides.toMutableMap()
            val currentOverride = currentOverrides[packageName] ?: ScheduleOverride()
            val updated = currentOverride.copy(durationMs = durationMs)
            if (updated.durationMs == null && updated.reinterventionMs == null) {
                currentOverrides.remove(packageName)
            } else {
                currentOverrides[packageName] = updated
            }
            state.copy(draft = state.draft.copy(overrides = currentOverrides))
        }
    }

    fun onSetRepeatOverride(packageName: String, repeatMs: Long?) {
        _uiState.update { state ->
            val currentOverrides = state.draft.overrides.toMutableMap()
            val currentOverride = currentOverrides[packageName] ?: ScheduleOverride()
            val updated = currentOverride.copy(reinterventionMs = repeatMs)
            if (updated.durationMs == null && updated.reinterventionMs == null) {
                currentOverrides.remove(packageName)
            } else {
                currentOverrides[packageName] = updated
            }
            state.copy(draft = state.draft.copy(overrides = currentOverrides))
        }
    }

    fun onSave() {
        val state = _uiState.value
        if (!state.canSave) return

        val draft = state.draft
        val startMin = draft.startHour * 60 + draft.startMinute
        val endMin = draft.endHour * 60 + draft.endMinute
        var mask = 0
        for (day in draft.selectedDays) {
            mask = mask or (1 shl (day.value - 1))
        }

        val schedule = CompiledSchedule(
            id = draft.scheduleId ?: 0L,
            name = draft.name.trim(),
            weekdayMask = mask,
            startMinute = startMin,
            endMinute = endMin,
            enabled = true,
            type = draft.type,
            targetPackages = draft.selectedPackages,
            overrides = if (draft.type == ScheduleType.INTERVENTION) draft.overrides else emptyMap()
        )

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        scope.launch(ioDispatcher) {
            val result = policyStore.saveSchedule(schedule)
            if (result.isSuccess) {
                _uiState.update { it.copy(isLoading = false, isSaved = true) }
            } else {
                val error = result.exceptionOrNull()?.message ?: "Failed to save schedule"
                _uiState.update { it.copy(isLoading = false, errorMessage = error) }
            }
        }
    }
}
