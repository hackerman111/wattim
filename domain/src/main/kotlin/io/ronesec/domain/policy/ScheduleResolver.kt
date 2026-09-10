package io.ronesec.domain.policy

import io.ronesec.domain.model.CompiledBlockSession
import io.ronesec.domain.model.CompiledSchedule
import io.ronesec.domain.model.ScheduleOverride
import io.ronesec.domain.model.ScheduleType
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

data class ScheduleOccurrence(
    val schedule: CompiledSchedule,
    val startInstant: Instant,
    val endInstant: Instant
) {
    fun contains(now: Instant): Boolean {
        // [startInstant, endInstant) half-open
        return !now.isBefore(startInstant) && now.isBefore(endInstant)
    }
}

data class TimeInterval(
    val start: Instant,
    val end: Instant
) {
    fun touchesOrOverlaps(other: TimeInterval): Boolean {
        return !this.end.isBefore(other.start) && !other.end.isBefore(this.start)
    }

    fun merge(other: TimeInterval): TimeInterval {
        val newStart = if (this.start.isBefore(other.start)) this.start else other.start
        val newEnd = if (this.end.isAfter(other.end)) this.end else other.end
        return TimeInterval(newStart, newEnd)
    }
}

object ScheduleResolver {

    /**
     * Resolves an instant for a local date and minute of day, applying DST rules:
     * - In a gap (nonexistent local time): moves forward by the gap.
     * - In an overlap (ambiguous local time): isStart = true picks the earlier instant; isStart = false picks the later instant.
     */
    fun resolveLocalInstant(
        date: LocalDate,
        minuteOfDay: Int,
        zoneId: ZoneId,
        isStart: Boolean
    ): Instant {
        val hour = minuteOfDay / 60
        val minute = minuteOfDay % 60
        val ldt = LocalDateTime.of(date, LocalTime.of(hour, minute))
        val rules = zoneId.rules
        val validOffsets = rules.getValidOffsets(ldt)

        return when {
            validOffsets.isEmpty() -> {
                // Gap (nonexistent local time) -> move forward by gap using transition
                val transition = rules.getTransition(ldt)
                transition?.instant ?: ldt.atZone(zoneId).toInstant()
            }
            validOffsets.size > 1 -> {
                // Overlap (ambiguous local time)
                // earlier instant = larger positive offset
                val sortedOffsets = validOffsets.sortedByDescending { it.totalSeconds }
                val chosenOffset = if (isStart) sortedOffsets.first() else sortedOffsets.last()
                ldt.atOffset(chosenOffset).toInstant()
            }
            else -> {
                ldt.atOffset(validOffsets[0]).toInstant()
            }
        }
    }

    /**
     * Computes all concrete occurrences of a schedule across a range of calendar dates.
     */
    fun computeOccurrences(
        schedule: CompiledSchedule,
        startDate: LocalDate,
        endDateInclusive: LocalDate,
        zoneId: ZoneId
    ): List<ScheduleOccurrence> {
        if (!schedule.enabled) return emptyList()

        val occurrences = mutableListOf<ScheduleOccurrence>()
        var currentDate = startDate
        while (!currentDate.isAfter(endDateInclusive)) {
            val dayOfWeek = currentDate.dayOfWeek
            if (schedule.isApplicableToDay(dayOfWeek)) {
                val startInstant = resolveLocalInstant(currentDate, schedule.startMinute, zoneId, isStart = true)
                val endDate = if (schedule.isOvernight) currentDate.plusDays(1) else currentDate
                val endInstant = resolveLocalInstant(endDate, schedule.endMinute, zoneId, isStart = false)

                // Half-open interval [start, end). Must be non-empty.
                if (startInstant.isBefore(endInstant)) {
                    occurrences.add(ScheduleOccurrence(schedule, startInstant, endInstant))
                }
            }
            currentDate = currentDate.plusDays(1)
        }
        return occurrences
    }

    data class HardBlockResolution(
        val isBlocked: Boolean,
        val until: Instant?
    )

    /**
     * Evaluates active HARD_BLOCK schedules at [now] for [packageName].
     * Computes the contiguous union of overlapping/touching hard blocks.
     * Returns HardBlockResolution(isBlocked = true, until = endInstant) (or until = null if indefinite).
     * If no hard block is active, returns null.
     */
    fun resolveActiveHardBlock(
        schedules: List<CompiledSchedule>,
        packageName: String,
        now: Instant,
        zoneId: ZoneId
    ): HardBlockResolution? {
        val localDate = now.atZone(zoneId).toLocalDate()
        // Check occurrences from 8 days in past to 8 days in future to capture weekly overnight overlaps
        val relevantOccurrences = schedules
            .filter { it.enabled && it.type == ScheduleType.HARD_BLOCK && it.targetPackages.contains(packageName) }
            .flatMap { computeOccurrences(it, localDate.minusDays(8), localDate.plusDays(8), zoneId) }
            .sortedBy { it.startInstant }

        // Find occurrences that cover 'now'
        val covering = relevantOccurrences.filter { it.contains(now) }
        if (covering.isEmpty()) return null

        // Merge contiguous intervals touching or overlapping
        var mergedInterval = TimeInterval(covering.first().startInstant, covering.first().endInstant)
        for (occ in covering.drop(1)) {
            mergedInterval = mergedInterval.merge(TimeInterval(occ.startInstant, occ.endInstant))
        }

        // Expand with any occurrences that touch/overlap the merged interval
        var expanded = true
        while (expanded) {
            expanded = false
            for (occ in relevantOccurrences) {
                val occInterval = TimeInterval(occ.startInstant, occ.endInstant)
                if (mergedInterval.touchesOrOverlaps(occInterval)) {
                    val newMerged = mergedInterval.merge(occInterval)
                    if (newMerged != mergedInterval) {
                        mergedInterval = newMerged
                        expanded = true
                    }
                }
            }
        }

        // If the merged interval extends indefinitely into future (>= 7 days in future from now)
        val sevenDaysAhead = now.plusSeconds(7 * 24 * 3600L)
        val until = if (!mergedInterval.end.isBefore(sevenDaysAhead)) null else mergedInterval.end

        return HardBlockResolution(isBlocked = true, until = until)
    }

    /**
     * Evaluates active INTERVENTION schedules at [now] for [packageName].
     * Resolves ties: "choose the active occurrence with the latest start instant; ties use stable schedule ID ascending."
     */
    fun resolveActiveInterventionSchedule(
        schedules: List<CompiledSchedule>,
        packageName: String,
        now: Instant,
        zoneId: ZoneId
    ): CompiledSchedule? {
        val localDate = now.atZone(zoneId).toLocalDate()
        val activeOccurrences = schedules
            .filter { it.enabled && it.type == ScheduleType.INTERVENTION && it.targetPackages.contains(packageName) }
            .flatMap { computeOccurrences(it, localDate.minusDays(8), localDate.plusDays(8), zoneId) }
            .filter { it.contains(now) }

        if (activeOccurrences.isEmpty()) return null

        // Latest start instant first; if equal start instant, lowest schedule.id first
        return activeOccurrences.maxWithOrNull(
            compareBy<ScheduleOccurrence> { it.startInstant }
                .thenByDescending { it.schedule.id } // inverted because maxWithOrNull picks highest, but we want lowest ID
        )?.schedule
    }

    /**
     * Finds the maximum end instant among all active manual block sessions matching [packageName].
     */
    fun resolveActiveManualBlockEnd(
        sessions: List<CompiledBlockSession>,
        packageName: String,
        now: Instant
    ): Instant? {
        val matchingActive = sessions.filter { it.active && it.targetPackages.contains(packageName) && it.isActiveAt(now) }
        return matchingActive.maxOfOrNull { it.endTime }
    }
}
