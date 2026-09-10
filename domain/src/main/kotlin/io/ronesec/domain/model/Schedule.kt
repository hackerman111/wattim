package io.ronesec.domain.model

import java.time.DayOfWeek
import java.time.Instant

enum class ScheduleType {
    HARD_BLOCK,
    INTERVENTION
}

data class ScheduleOverride(
    val durationMs: Long? = null,
    val reinterventionMs: Long? = null
)

data class CompiledSchedule(
    val id: Long,
    val name: String,
    val weekdayMask: Int,
    val startMinute: Int,
    val endMinute: Int,
    val enabled: Boolean,
    val type: ScheduleType,
    val targetPackages: Set<String>,
    val overrides: Map<String, ScheduleOverride> = emptyMap()
) {
    init {
        require(startMinute in 0..1439) { "Start minute must be in 0..1439, was $startMinute" }
        require(endMinute in 0..1439) { "End minute must be in 0..1439, was $endMinute" }
        require(startMinute != endMinute) { "Equal start and end minute is invalid" }
    }

    fun isApplicableToDay(dayOfWeek: DayOfWeek): Boolean {
        val bit = 1 shl (dayOfWeek.value - 1) // Monday = 1 -> bit 0, Sunday = 7 -> bit 6
        return (weekdayMask and bit) != 0
    }

    val isOvernight: Boolean
        get() = endMinute < startMinute
}

data class CompiledBlockSession(
    val id: String,
    val name: String,
    val startTime: Instant,
    val endTime: Instant,
    val active: Boolean,
    val targetPackages: Set<String>
) {
    init {
        require(startTime.isBefore(endTime)) { "Start time must be before end time" }
    }

    fun isActiveAt(now: Instant): Boolean {
        // [startTime, endTime) half-open interval
        return active && !now.isBefore(startTime) && now.isBefore(endTime)
    }
}
