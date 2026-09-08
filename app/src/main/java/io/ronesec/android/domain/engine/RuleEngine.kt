package io.ronesec.android.domain.engine

import io.ronesec.android.domain.model.AllowReason
import io.ronesec.android.domain.model.BlockSchedule
import io.ronesec.android.domain.model.Decision
import io.ronesec.android.domain.model.ScheduleType
import io.ronesec.android.domain.protection.SessionId
import java.time.DayOfWeek
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
        recentAttemptsCount: Int = 0,
        sessionId: SessionId?
    ): Decision = evaluate(
        packageName = packageName,
        now = now,
        state = state,
        zoneId = zoneId,
        recentAttemptsCount = recentAttemptsCount,
        currentSessionId = sessionId?.value
    )

    fun evaluate(
        packageName: String,
        now: Instant,
        state: RuntimeState,
        zoneId: ZoneId = ZoneId.systemDefault(),
        recentAttemptsCount: Int = 0,
        currentSessionId: Long? = null
    ): Decision {
        val pausedUntil = state.protectionPausedUntil
        if (pausedUntil != null && (pausedUntil == -1L || now.toEpochMilli() < pausedUntil)) {
            return Decision.Allow(AllowReason.GLOBAL_PAUSE)
        }

        val target = state.targets[packageName] ?: return Decision.Allow(AllowReason.NOT_TARGET)
        if (!target.enabled) {
            return Decision.Allow(AllowReason.TARGET_DISABLED)
        }

        // Priority 1: Active manual/quick block sessions
        val activeSessions = state.activeBlockSessions.filter { session ->
            session.active && session.packages.contains(packageName) && now >= session.startTime && now < session.endTime
        }
        if (activeSessions.isNotEmpty()) {
            val maxUntil = activeSessions.maxOf { it.endTime }
            return Decision.Block(until = maxUntil)
        }

        val zonedDateTime = ZonedDateTime.ofInstant(now, zoneId)
        val activeSchedulesWithEnd = getActiveSchedulesWithEnd(state.blockSchedules, packageName, zonedDateTime, zoneId)

        // Priority 2: Hard Block schedules strictly override intervention schedules and access grants
        val hardBlockSchedules = activeSchedulesWithEnd.filter { it.first.scheduleType == ScheduleType.HARD_BLOCK }
        if (hardBlockSchedules.isNotEmpty()) {
            val maxUntil = hardBlockSchedules.maxOf { it.second }
            return Decision.Block(until = maxUntil)
        }

        // Priority 3: Intervention schedules
        val interventionSchedules = activeSchedulesWithEnd.filter { it.first.scheduleType == ScheduleType.INTERVENTION }
        if (interventionSchedules.isNotEmpty()) {
            val grantReason = getAccessGrantedReason(packageName, now, state, currentSessionId)
            if (grantReason != null) {
                return Decision.Allow(grantReason)
            }
            if (isWithinQuickReturnGrace(packageName, now, state, target.intervention.quickReturnGraceMs)) {
                return Decision.Allow(AllowReason.QUICK_RETURN)
            }

            val activeSchedule = interventionSchedules.first().first
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

        // Priority 4: Active unexpired AccessGrant (session permit or timed permit)
        val grantReason = getAccessGrantedReason(packageName, now, state, currentSessionId)
        if (grantReason != null) {
            return Decision.Allow(grantReason)
        }

        // Priority 5: Quick Return Grace period
        if (isWithinQuickReturnGrace(packageName, now, state, target.intervention.quickReturnGraceMs)) {
            return Decision.Allow(AllowReason.QUICK_RETURN)
        }

        // Priority 6: Standard Intervention
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

    private fun getActiveSchedulesWithEnd(
        schedules: List<BlockSchedule>,
        packageName: String,
        zdt: ZonedDateTime,
        zoneId: ZoneId
    ): List<Pair<BlockSchedule, Instant>> {
        val currentDay = zdt.dayOfWeek
        val previousDay = currentDay.minus(1)
        val currentTime = zdt.toLocalTime()
        val localDate = zdt.toLocalDate()

        val results = mutableListOf<Pair<BlockSchedule, Instant>>()
        for (schedule in schedules) {
            if (!schedule.enabled || !schedule.packages.contains(packageName)) continue

            val isOvernight = schedule.start > schedule.end
            if (!isOvernight) {
                if (schedule.days.contains(currentDay) && currentTime >= schedule.start && currentTime < schedule.end) {
                    val endInstant = localDate.atTime(schedule.end).atZone(zoneId).toInstant()
                    results.add(schedule to endInstant)
                }
            } else {
                // Overnight: Evening part on start day
                if (schedule.days.contains(currentDay) && currentTime >= schedule.start) {
                    val endInstant = localDate.plusDays(1).atTime(schedule.end).atZone(zoneId).toInstant()
                    results.add(schedule to endInstant)
                }
                // Overnight: Morning part on following day
                else if (schedule.days.contains(previousDay) && currentTime < schedule.end) {
                    val endInstant = localDate.atTime(schedule.end).atZone(zoneId).toInstant()
                    results.add(schedule to endInstant)
                }
            }
        }
        return results
    }

    private fun getAccessGrantedReason(
        packageName: String,
        now: Instant,
        state: RuntimeState,
        currentSessionId: Long?
    ): AllowReason? {
        if (currentSessionId != null && state.activeSessionPermits[packageName] == currentSessionId) {
            return AllowReason.ACTIVE_SESSION_PERMIT
        }
        val grant = state.activeGrants[packageName] ?: return null
        val expiresAt = grant.expiresAt
        return if (expiresAt != null && now < expiresAt) AllowReason.ACTIVE_TIMED_PERMIT else null
    }

    private fun isAccessGranted(
        packageName: String,
        now: Instant,
        state: RuntimeState,
        currentSessionId: Long?
    ): Boolean = getAccessGrantedReason(packageName, now, state, currentSessionId) != null

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
}
