package io.ronesec.android.ui.stats

import io.ronesec.android.data.AppStatsRow

/**
 * Immutable UI state for the Statistics screen (F69, F70, F71).
 */
data class StatsUiState(
    val isLoading: Boolean = false,
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
    val appStatsToday: List<AppStatsRow> = emptyList(),
    val errorMessage: String? = null
)
