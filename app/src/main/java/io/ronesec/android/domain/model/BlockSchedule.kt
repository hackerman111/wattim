package io.ronesec.android.domain.model

import java.time.DayOfWeek
import java.time.LocalTime

enum class ScheduleType {
    HARD_BLOCK,
    INTERVENTION
}

data class ScheduleAppOverride(
    val durationMs: Long? = null,
    val reinterventionMs: Long? = null
)

data class BlockSchedule(
    val id: Long = 0,
    val name: String,
    val days: Set<DayOfWeek>,
    val start: LocalTime,
    val end: LocalTime,
    val packages: Set<String>,
    val enabled: Boolean = true,
    val scheduleType: ScheduleType = ScheduleType.HARD_BLOCK,
    val appOverrides: Map<String, ScheduleAppOverride> = emptyMap()
)
