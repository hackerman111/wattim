package io.ronesec.android.ui.blocks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ronesec.android.data.PolicyStore
import io.ronesec.domain.model.CompiledBlockSession
import io.ronesec.domain.model.RuntimePolicySnapshot
import io.ronesec.domain.model.WallClock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant

class BlocksViewModel(
    private val policyStore: PolicyStore,
    private val wallClock: WallClock,
    private val savedStateHandle: SavedStateHandle? = null,
    private val coroutineScope: CoroutineScope? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val scope: CoroutineScope
        get() = coroutineScope ?: viewModelScope

    private val _uiState = MutableStateFlow(BlocksUiState())
    val uiState: StateFlow<BlocksUiState> = _uiState.asStateFlow()

    private var tickerJob: Job? = null
    private var isUserSelectionInitialized = false

    init {
        scope.launch(ioDispatcher) {
            policyStore.snapshotFlow.collect { snapshot ->
                updateFromSnapshot(snapshot)
            }
        }
    }

    private fun updateFromSnapshot(snapshot: RuntimePolicySnapshot) {
        val now = wallClock.now()
        val protectedApps = snapshot.targets.values
            .sortedBy { it.displayName.lowercase() }
        val currentPackages = protectedApps.map { it.packageName }.toSet()

        // Selection: default all on first entry; deliberately retain across emissions (F61)
        val updatedSelection = if (!isUserSelectionInitialized) {
            isUserSelectionInitialized = true
            currentPackages
        } else {
            _uiState.value.selectedPackagesForFocus.intersect(currentPackages)
        }

        // Active manual block session (F62): singular card, max end time if overlapping
        val activeSessions = snapshot.activeBlockSessions.filter { it.isActiveAt(now) }
        val maxActiveSession = activeSessions.maxByOrNull { it.endTime }

        val activeSessionUi = maxActiveSession?.let { session ->
            val remainingSec = maxOf(0L, (session.endTime.toEpochMilli() - now.toEpochMilli()) / 1000L)
            ActiveBlockSessionUiModel(
                id = session.id,
                name = session.name,
                startTime = session.startTime,
                endTime = session.endTime,
                targetPackages = session.targetPackages,
                remainingSeconds = remainingSec
            )
        }

        // Schedules (F63): summary and models
        val scheduleUis = snapshot.activeSchedules.map { schedule ->
            ScheduleItemUiModel(
                id = schedule.id,
                name = schedule.name,
                weekdayMask = schedule.weekdayMask,
                daysSummary = BlocksFormatters.formatDaysSummary(schedule.weekdayMask),
                startMinute = schedule.startMinute,
                endMinute = schedule.endMinute,
                timeSummary = BlocksFormatters.formatTimeRange(schedule.startMinute, schedule.endMinute),
                isOvernight = schedule.isOvernight,
                enabled = schedule.enabled,
                type = schedule.type,
                targetPackages = schedule.targetPackages,
                targetsSummary = BlocksFormatters.formatTargetsSummary(schedule.targetPackages, snapshot.targets),
                overridesCount = schedule.overrides.size
            )
        }

        _uiState.update { current ->
            current.copy(
                activeSession = activeSessionUi,
                protectedApps = protectedApps,
                selectedPackagesForFocus = updatedSelection,
                schedules = scheduleUis,
                isLoading = false
            )
        }
    }

    fun onVisible() {
        if (tickerJob?.isActive == true) return
        tickerJob = scope.launch(ioDispatcher) {
            while (isActive) {
                delay(1000L)
                updateRemainingSeconds()
            }
        }
    }

    fun onInvisible() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun updateRemainingSeconds() {
        val currentSession = _uiState.value.activeSession ?: return
        val now = wallClock.now()
        val remainingSec = maxOf(0L, (currentSession.endTime.toEpochMilli() - now.toEpochMilli()) / 1000L)

        if (remainingSec <= 0L) {
            _uiState.update { it.copy(activeSession = null) }
        } else {
            _uiState.update { current ->
                current.copy(
                    activeSession = current.activeSession?.copy(remainingSeconds = remainingSec)
                )
            }
        }
    }

    fun onTogglePackageForFocus(packageName: String) {
        _uiState.update { current ->
            val selection = current.selectedPackagesForFocus.toMutableSet()
            if (selection.contains(packageName)) {
                selection.remove(packageName)
            } else {
                selection.add(packageName)
            }
            current.copy(selectedPackagesForFocus = selection)
        }
    }

    fun onSelectAllPackagesForFocus() {
        _uiState.update { current ->
            current.copy(selectedPackagesForFocus = current.protectedApps.map { it.packageName }.toSet())
        }
    }

    fun onSelectNonePackagesForFocus() {
        _uiState.update { current ->
            current.copy(selectedPackagesForFocus = emptySet())
        }
    }

    fun onSelectFocusDuration(durationMs: Long) {
        _uiState.update { it.copy(selectedFocusDurationMs = durationMs) }
    }

    fun onStartQuickFocus() {
        val state = _uiState.value
        if (!state.isQuickFocusEnabled) return

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        scope.launch(ioDispatcher) {
            val now = wallClock.now()
            val end = now.plusMillis(state.selectedFocusDurationMs)
            val result = policyStore.createBlockSession(
                name = "Quick Focus",
                startTime = now,
                endTime = end,
                targetPackages = state.selectedPackagesForFocus
            )
            if (result.isFailure) {
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Failed to start Quick Focus"
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun onStopActiveSession(sessionId: String) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        scope.launch(ioDispatcher) {
            val result = policyStore.stopBlockSession(sessionId)
            if (result.isFailure) {
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Failed to stop block session"
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false, activeSession = null) }
            }
        }
    }

    fun onToggleSchedule(scheduleId: Long, enabled: Boolean) {
        scope.launch(ioDispatcher) {
            val result = policyStore.toggleSchedule(scheduleId, enabled)
            if (result.isFailure) {
                _uiState.update { current ->
                    current.copy(
                        errorMessage = result.exceptionOrNull()?.message ?: "Failed to toggle schedule"
                    )
                }
            }
        }
    }

    fun onDeleteSchedule(scheduleId: Long) {
        scope.launch(ioDispatcher) {
            val result = policyStore.deleteSchedule(scheduleId)
            if (result.isFailure) {
                _uiState.update { current ->
                    current.copy(
                        errorMessage = result.exceptionOrNull()?.message ?: "Failed to delete schedule"
                    )
                }
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        onInvisible()
    }
}
