package io.ronesec.android.data

import io.ronesec.android.data.dao.PerAppStatRow
import io.ronesec.domain.model.StatsPeriod
import io.ronesec.domain.model.WallClock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
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

data class PeriodSummary(
    val totalAttempts: Int,
    val continuedCount: Int,
    val closedCount: Int,
    val avoidedPercent: Int,
    val savedMinutes: Long,
    val formattedSavedTime: String
)

data class DailyActivityPoint(
    val date: LocalDate,
    val label: String,
    val totalOpenings: Int,
    val totalClosed: Int
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

    fun getPeriodBounds(
        period: StatsPeriod,
        nowWall: Instant = wallClock.now(),
        zoneId: ZoneId = wallClock.zoneId()
    ): Pair<Long, Long> {
        val localDate = nowWall.atZone(zoneId).toLocalDate()
        val startOfNextDay = localDate.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val startMs = when (period) {
            StatsPeriod.TODAY -> localDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
            StatsPeriod.WEEK -> localDate.minusDays(6).atStartOfDay(zoneId).toInstant().toEpochMilli()
            StatsPeriod.MONTH -> localDate.minusDays(29).atStartOfDay(zoneId).toInstant().toEpochMilli()
            StatsPeriod.ALL_TIME -> 0L
        }
        val endMs = if (period == StatsPeriod.ALL_TIME) Long.MAX_VALUE else startOfNextDay
        return Pair(startMs, endMs)
    }

    suspend fun getPeriodSummary(
        period: StatsPeriod,
        nowWall: Instant = wallClock.now(),
        zoneId: ZoneId = wallClock.zoneId()
    ): PeriodSummary = withContext(ioDispatcher) {
        val (startMs, endMs) = getPeriodBounds(period, nowWall, zoneId)
        val total = database.statisticsDao().getTodayTotalAttempts(startMs, endMs)
        val continued = database.statisticsDao().getTodayContinuedCount(startMs, endMs)
        val closed = database.statisticsDao().getTodayClosedCount(startMs, endMs)

        val avoidedPercent = if (total == 0) 0 else ((closed.toDouble() * 100.0) / total.toDouble()).roundToInt()
        val settings = database.appSettingsDao().getSettings()
        val multiplier = settings?.savedSessionMinutes ?: 7
        val savedMinutes = closed.toLong() * multiplier.toLong()

        PeriodSummary(
            totalAttempts = total,
            continuedCount = continued,
            closedCount = closed,
            avoidedPercent = avoidedPercent,
            savedMinutes = savedMinutes,
            formattedSavedTime = formatDuration(savedMinutes)
        )
    }

    suspend fun getPerAppStats(
        period: StatsPeriod,
        nowWall: Instant = wallClock.now(),
        zoneId: ZoneId = wallClock.zoneId()
    ): List<AppStatsRow> = withContext(ioDispatcher) {
        val (startMs, endMs) = getPeriodBounds(period, nowWall, zoneId)
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

    suspend fun getDailyActivity(
        period: StatsPeriod,
        nowWall: Instant = wallClock.now(),
        zoneId: ZoneId = wallClock.zoneId()
    ): List<DailyActivityPoint> = withContext(ioDispatcher) {
        if (period == StatsPeriod.TODAY || period == StatsPeriod.ALL_TIME) {
            return@withContext emptyList()
        }
        val (startMs, endMs) = getPeriodBounds(period, nowWall, zoneId)
        val events = database.statisticsDao().getAttemptEvents(startMs, endMs)

        val localToday = nowWall.atZone(zoneId).toLocalDate()
        val numDays = if (period == StatsPeriod.WEEK) 7 else 30
        val startDate = localToday.minusDays((numDays - 1).toLong())

        val countsByDate = mutableMapOf<LocalDate, Pair<Int, Int>>()
        for (event in events) {
            val eventDate = Instant.ofEpochMilli(event.timestamp).atZone(zoneId).toLocalDate()
            val current = countsByDate[eventDate] ?: Pair(0, 0)
            val isClosed = event.outcome == "ABANDONED" || event.outcome == "BLOCKED"
            countsByDate[eventDate] = Pair(
                current.first + 1,
                current.second + (if (isClosed) 1 else 0)
            )
        }

        val result = mutableListOf<DailyActivityPoint>()
        for (i in 0 until numDays) {
            val date = startDate.plusDays(i.toLong())
            val counts = countsByDate[date] ?: Pair(0, 0)
            val label = if (period == StatsPeriod.WEEK) {
                when (date.dayOfWeek) {
                    DayOfWeek.MONDAY -> "Пн"
                    DayOfWeek.TUESDAY -> "Вт"
                    DayOfWeek.WEDNESDAY -> "Ср"
                    DayOfWeek.THURSDAY -> "Чт"
                    DayOfWeek.FRIDAY -> "Пт"
                    DayOfWeek.SATURDAY -> "Сб"
                    DayOfWeek.SUNDAY -> "Вс"
                    else -> ""
                }
            } else {
                "${date.dayOfMonth}.${date.monthValue}"
            }
            result.add(
                DailyActivityPoint(
                    date = date,
                    label = label,
                    totalOpenings = counts.first,
                    totalClosed = counts.second
                )
            )
        }
        result
    }

    /**
     * Computes today summary using local day bounds: [startOfLocalDay, startOfNextLocalDay)
     */
    suspend fun getTodaySummary(
        nowWall: Instant = wallClock.now(),
        zoneId: ZoneId = wallClock.zoneId()
    ): TodaySummary {
        val summary = getPeriodSummary(StatsPeriod.TODAY, nowWall, zoneId)
        return TodaySummary(
            totalAttempts = summary.totalAttempts,
            continuedCount = summary.continuedCount,
            closedCount = summary.closedCount,
            avoidedPercent = summary.avoidedPercent
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
    ): List<AppStatsRow> = getPerAppStats(StatsPeriod.TODAY, nowWall, zoneId)

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
