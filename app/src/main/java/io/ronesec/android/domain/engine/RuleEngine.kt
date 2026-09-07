package io.ronesec.android.domain.engine

import io.ronesec.android.domain.model.BlockSchedule
import io.ronesec.android.domain.model.Decision
import io.ronesec.android.domain.model.ScheduleType
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class RuleEngine {

    fun evaluate(
        packageName: String,
        now: Instant,
        state: RuntimeState,
        zoneId: ZoneId = ZoneId.systemDefault(),
        recentAttemptsCount: Int = 0
    ): Decision {
        val pausedUntil = state.protectionPausedUntil
        if (pausedUntil != null) {
            if (pausedUntil == -1L || now.toEpochMilli() < pausedUntil) {
                return Decision.Allow
            }
        }

        val target = state.targets[packageName] ?: return Decision.Allow

        if (!target.enabled) {
            return Decision.Allow
        }

        // Priority 1: Check state.activeBlockSessions -> if active and matches package and time, return Decision.Block
        val activeSession = state.activeBlockSessions.firstOrNull { session ->
            session.active &&
                    session.packages.contains(packageName) &&
                    now >= session.startTime &&
                    now < session.endTime
        }
        if (activeSession != null) {
            return Decision.Block(until = activeSession.endTime)
        }

        val zonedDateTime = ZonedDateTime.ofInstant(now, zoneId)
        val dayOfWeek = zonedDateTime.dayOfWeek
        val currentTime = zonedDateTime.toLocalTime()

        // Priority 2: Check state.blockSchedules
        val activeSchedule = state.blockSchedules.firstOrNull { schedule ->
            schedule.enabled &&
                    schedule.packages.contains(packageName) &&
                    schedule.days.contains(dayOfWeek) &&
                    isTimeWithinSchedule(currentTime, schedule.start, schedule.end)
        }
        if (activeSchedule != null) {
            when (activeSchedule.scheduleType) {
                ScheduleType.HARD_BLOCK -> {
                    val todayEnd = if (activeSchedule.start <= activeSchedule.end) {
                        zonedDateTime.toLocalDate().atTime(activeSchedule.end).atZone(zoneId).toInstant()
                    } else {
                        val endDate = if (currentTime >= activeSchedule.start) {
                            zonedDateTime.toLocalDate().plusDays(1)
                        } else {
                            zonedDateTime.toLocalDate()
                        }
                        endDate.atTime(activeSchedule.end).atZone(zoneId).toInstant()
                    }
                    return Decision.Block(until = todayEnd)
                }
                ScheduleType.INTERVENTION -> {
                    if (isAccessGranted(packageName, now, state)) {
                        return Decision.Allow
                    }
                    if (isWithinQuickReturnGrace(packageName, now, state, target.intervention.quickReturnGraceMs)) {
                        return Decision.Allow
                    }

                    val override = activeSchedule.appOverrides[packageName]
                    val baseDurationMs = override?.durationMs ?: target.intervention.durationMs
                    val effectiveReinterventionMs = override?.reinterventionMs ?: target.intervention.reinterventionMs
                    val calculatedDurationMs = calculateDuration(
                        baseDurationMs = baseDurationMs,
                        exponentialGrowthEnabled = target.intervention.exponentialGrowthEnabled,
                        growthPercent = target.intervention.growthPercent,
                        recentAttemptsCount = recentAttemptsCount
                    )
                    return Decision.Intervention(
                        config = target.intervention.copy(
                            durationMs = calculatedDurationMs,
                            reinterventionMs = effectiveReinterventionMs
                        )
                    )
                }
            }
        }

        // Priority 3: Active unexpired AccessGrant
        if (isAccessGranted(packageName, now, state)) {
            return Decision.Allow
        }

        // Priority 4: Quick Return Grace period
        if (isWithinQuickReturnGrace(packageName, now, state, target.intervention.quickReturnGraceMs)) {
            return Decision.Allow
        }

        // Priority 5: Standard Intervention
        val baseDurationMs = target.intervention.durationMs
        val calculatedDurationMs = calculateDuration(
            baseDurationMs = baseDurationMs,
            exponentialGrowthEnabled = target.intervention.exponentialGrowthEnabled,
            growthPercent = target.intervention.growthPercent,
            recentAttemptsCount = recentAttemptsCount
        )
        return Decision.Intervention(
            config = target.intervention.copy(durationMs = calculatedDurationMs)
        )
    }

    private fun isAccessGranted(packageName: String, now: Instant, state: RuntimeState): Boolean {
        val grant = state.activeGrants[packageName] ?: return false
        val expiresAt = grant.expiresAt
        return expiresAt == null || now < expiresAt
    }

    private fun isWithinQuickReturnGrace(
        packageName: String,
        now: Instant,
        state: RuntimeState,
        quickReturnGraceMs: Long
    ): Boolean {
        if (quickReturnGraceMs <= 0L) return false
        val lastExit = state.lastExitTimes[packageName] ?: return false
        val elapsedMs = now.toEpochMilli() - lastExit.toEpochMilli()
        return elapsedMs in 0 until quickReturnGraceMs
    }

    private fun calculateDuration(
        baseDurationMs: Long,
        exponentialGrowthEnabled: Boolean,
        growthPercent: Int,
        recentAttemptsCount: Int
    ): Long {
        return if (exponentialGrowthEnabled && recentAttemptsCount > 0) {
            val multiplier = Math.pow(1.0 + growthPercent / 100.0, recentAttemptsCount.toDouble())
            (baseDurationMs * multiplier).toLong().coerceIn(1_000L, 300_000L)
        } else {
            baseDurationMs
        }
    }

    private fun isTimeWithinSchedule(current: LocalTime, start: LocalTime, end: LocalTime): Boolean {
        return if (start <= end) {
            current >= start && current < end
        } else {
            // Spanning midnight e.g. 22:00 -> 06:00
            current >= start || current < end
        }
    }
}
