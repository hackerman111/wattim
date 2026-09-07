package io.ronesec.android.domain.engine

import io.ronesec.android.domain.model.BlockSchedule
import io.ronesec.android.domain.model.Decision
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class RuleEngine {

    fun evaluate(
        packageName: String,
        now: Instant,
        state: RuntimeState,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Decision {
        val target = state.targets[packageName] ?: return Decision.Allow

        if (!target.enabled) {
            return Decision.Allow
        }

        // 1. Hard Block (Session or Schedule) has highest priority over AccessGrant (AC-11)
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

        val activeSchedule = state.blockSchedules.firstOrNull { schedule ->
            schedule.enabled &&
                    schedule.packages.contains(packageName) &&
                    schedule.days.contains(dayOfWeek) &&
                    isTimeWithinSchedule(currentTime, schedule.start, schedule.end)
        }
        if (activeSchedule != null) {
            val todayEnd = zonedDateTime.toLocalDate().atTime(activeSchedule.end).atZone(zoneId).toInstant()
            return Decision.Block(until = todayEnd)
        }

        // 2. Active unexpired AccessGrant?
        val grant = state.activeGrants[packageName]
        if (grant != null) {
            val expiresAt = grant.expiresAt
            if (expiresAt == null || now < expiresAt) {
                return Decision.Allow
            }
        }

        // 3. Quick Return Grace period?
        val lastExit = state.lastExitTimes[packageName]
        if (lastExit != null && target.intervention.quickReturnGraceMs > 0L) {
            val elapsedMs = now.toEpochMilli() - lastExit.toEpochMilli()
            if (elapsedMs in 0 until target.intervention.quickReturnGraceMs) {
                return Decision.Allow
            }
        }

        // 4. Intervention required
        return Decision.Intervention(config = target.intervention)
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
