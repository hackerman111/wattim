package io.ronesec.android.data

import io.ronesec.android.data.dao.PerAppStatRow
import io.ronesec.domain.model.WallClock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

data class TodaySummary(
    val totalAttempts: Int,
    val continuedCount: Int,
    val closedCount: Int,
    val avoidedPercent: Int
)

data class AllTimeSavedLife(
    val totalClosedCount: Int,
    val multiplierMinutes: Int,
    val totalSavedMinutes: Long,
    val formattedSavedTime: String
)

data class AppStatsRow(
    val packageName: String,
    val displayName: String,
    val totalOpenings: Int,
    val totalClosed: Int
)

class StatisticsStore(
    private val database: WattimDatabase,
    private val wallClock: WallClock,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    /**
     * Computes today summary using local day bounds: [startOfLocalDay, startOfNextLocalDay)
     */
    suspend fun getTodaySummary(
        nowWall: Instant = wallClock.now(),
        zoneId: ZoneId = wallClock.zoneId()
    ): TodaySummary = withContext(ioDispatcher) {
        val (startMs, endMs) = getLocalDayBounds(nowWall, zoneId)
        val total = database.statisticsDao().getTodayTotalAttempts(startMs, endMs)
        val continued = database.statisticsDao().getTodayContinuedCount(startMs, endMs)
        val closed = database.statisticsDao().getTodayClosedCount(startMs, endMs)

        val avoidedPercent = if (total == 0) 0 else ((closed.toDouble() * 100.0) / total.toDouble()).roundToInt()

        TodaySummary(
            totalAttempts = total,
            continuedCount = continued,
            closedCount = closed,
            avoidedPercent = avoidedPercent
        )
    }

    /**
     * Computes all-time saved life multiplying all-time closed count by current savedSessionMinutes setting.
     */
    suspend fun getAllTimeSavedLife(): AllTimeSavedLife = withContext(ioDispatcher) {
        val totalClosed = database.statisticsDao().getAllTimeClosedCount()
        val settings = database.appSettingsDao().getSettings()
        val multiplier = settings?.savedSessionMinutes ?: 7
        val totalSavedMinutes = totalClosed.toLong() * multiplier.toLong()

        AllTimeSavedLife(
            totalClosedCount = totalClosed,
            multiplierMinutes = multiplier,
            totalSavedMinutes = totalSavedMinutes,
            formattedSavedTime = formatDuration(totalSavedMinutes)
        )
    }

    /**
     * Computes per-app open/closed counts today sorted by total openings DESC.
     */
    suspend fun getPerAppStatsToday(
        nowWall: Instant = wallClock.now(),
        zoneId: ZoneId = wallClock.zoneId()
    ): List<AppStatsRow> = withContext(ioDispatcher) {
        val (startMs, endMs) = getLocalDayBounds(nowWall, zoneId)
        val rows = database.statisticsDao().getPerAppStatsToday(startMs, endMs)
        rows.map {
            AppStatsRow(
                packageName = it.packageName,
                displayName = it.displayNameAtAttempt.ifBlank { it.packageName },
                totalOpenings = it.totalOpenings,
                totalClosed = it.totalClosed
            )
        }
    }

    companion object {
        fun getLocalDayBounds(now: Instant, zoneId: ZoneId): Pair<Long, Long> {
            val localDate = now.atZone(zoneId).toLocalDate()
            val startOfDay = localDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
            val startOfNextDay = localDate.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
            return Pair(startOfDay, startOfNextDay)
        }

        fun formatDuration(totalMinutes: Long, isRussian: Boolean = true): String {
            return io.ronesec.android.ui.stats.DurationFormatter.format(totalMinutes, isRussian)
        }
    }
}
