package io.ronesec.android.ui.stats

import io.ronesec.android.data.AppStatsRow
import io.ronesec.android.data.DailyActivityPoint
import io.ronesec.domain.model.StatsPeriod

/**
 * Immutable UI state for the Statistics screen (F69, F70, F71).
 */
data class StatsUiState(
    val isLoading: Boolean = false,
    val selectedPeriod: StatsPeriod = StatsPeriod.TODAY,
    val allTimeSavedDuration: String = "0 мин",
    val allTimeSavedMinutes: Long = 0L,
    val allTimeAvoidedCount: Int = 0,
    val savedTodayDuration: String = "0 мин",
    val savedTodayMinutes: Long = 0L,
    val multiplierMinutes: Int = 7,
    val todayTotalAttempts: Int = 0,
    val todayContinuedCount: Int = 0,
    val todayClosedCount: Int = 0,
    val todayAvoidedPercent: Int = 0,
    val periodSavedDuration: String = "0 мин",
    val periodSavedMinutes: Long = 0L,
    val periodTotalAttempts: Int = 0,
    val periodContinuedCount: Int = 0,
    val periodClosedCount: Int = 0,
    val periodAvoidedPercent: Int = 0,
    val appStats: List<AppStatsRow> = emptyList(),
    val appStatsToday: List<AppStatsRow> = emptyList(),
    val dailyActivity: List<DailyActivityPoint> = emptyList(),
    val errorMessage: String? = null
)
