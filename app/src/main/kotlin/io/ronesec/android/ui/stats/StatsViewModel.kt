package io.ronesec.android.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ronesec.android.data.PolicyStore
import io.ronesec.android.data.StatisticsStore
import io.ronesec.android.ui.locale.WattimLocale
import io.ronesec.domain.model.StatsPeriod
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
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * ViewModel owning statistics aggregates, today rollover, and duration formatting (F69, F70, F71).
 */
class StatsViewModel(
    private val statisticsStore: StatisticsStore,
    private val policyStore: PolicyStore?,
    private val wallClock: WallClock,
    private val coroutineScope: CoroutineScope? = null,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val scope: CoroutineScope
        get() = coroutineScope ?: viewModelScope

    private val _selectedPeriod = MutableStateFlow(StatsPeriod.TODAY)
    private val _uiState = MutableStateFlow(StatsUiState(isLoading = true))
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    private var isScreenVisible = false
    private var periodicRefreshJob: Job? = null
    private var lastRecordedDate: LocalDate? = null

    init {
        policyStore?.let { store ->
            scope.launch(dispatcher) {
                store.presentationSettings.collect {
                    loadStats()
                }
            }
        }
        loadStats()
    }

    fun onPeriodSelected(period: StatsPeriod) {
        if (_selectedPeriod.value != period) {
            _selectedPeriod.value = period
            loadStats()
        }
    }

    fun onVisible() {
        isScreenVisible = true
        loadStats()
        startDayBoundaryMonitor()
    }

    fun onInvisible() {
        isScreenVisible = false
        periodicRefreshJob?.cancel()
        periodicRefreshJob = null
    }

    fun refresh() {
        loadStats()
    }

    private fun startDayBoundaryMonitor() {
        periodicRefreshJob?.cancel()
        periodicRefreshJob = scope.launch(dispatcher) {
            while (isScreenVisible) {
                delay(30_000L)
                val currentDate = wallClock.now().atZone(wallClock.zoneId()).toLocalDate()
                if (lastRecordedDate != null && currentDate != lastRecordedDate) {
                    loadStats()
                }
            }
        }
    }

    private fun loadStats() {
        scope.launch(dispatcher) {
            try {
                val now = wallClock.now()
                val zoneId = wallClock.zoneId()
                lastRecordedDate = now.atZone(zoneId).toLocalDate()

                val presentation = policyStore?.presentationSettings?.value
                val isRussian = WattimLocale.isRussian(presentation?.language ?: "AUTO")

                val currentPeriod = _selectedPeriod.value
                val allTime = statisticsStore.getAllTimeSavedLife()
                val periodSummary = statisticsStore.getPeriodSummary(currentPeriod, now, zoneId)
                val perApp = statisticsStore.getPerAppStats(currentPeriod, now, zoneId)
                val dailyActivity = statisticsStore.getDailyActivity(currentPeriod, now, zoneId)

                val multiplier = allTime.multiplierMinutes
                val savedPeriodMinutes = periodSummary.savedMinutes

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        selectedPeriod = currentPeriod,
                        allTimeSavedDuration = DurationFormatter.format(allTime.totalSavedMinutes, isRussian),
                        allTimeSavedMinutes = allTime.totalSavedMinutes,
                        allTimeAvoidedCount = allTime.totalClosedCount,
                        savedTodayDuration = DurationFormatter.format(savedPeriodMinutes, isRussian),
                        savedTodayMinutes = savedPeriodMinutes,
                        multiplierMinutes = multiplier,
                        todayTotalAttempts = periodSummary.totalAttempts,
                        todayContinuedCount = periodSummary.continuedCount,
                        todayClosedCount = periodSummary.closedCount,
                        todayAvoidedPercent = periodSummary.avoidedPercent,
                        periodSavedDuration = DurationFormatter.format(savedPeriodMinutes, isRussian),
                        periodSavedMinutes = savedPeriodMinutes,
                        periodTotalAttempts = periodSummary.totalAttempts,
                        periodContinuedCount = periodSummary.continuedCount,
                        periodClosedCount = periodSummary.closedCount,
                        periodAvoidedPercent = periodSummary.avoidedPercent,
                        appStats = perApp,
                        appStatsToday = perApp,
                        dailyActivity = dailyActivity,
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.localizedMessage ?: "Failed to load statistics"
                    )
                }
            }
        }
    }
}
