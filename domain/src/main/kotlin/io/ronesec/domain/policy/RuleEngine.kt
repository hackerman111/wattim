package io.ronesec.domain.policy

import io.ronesec.domain.model.AnimationMode
import io.ronesec.domain.model.GrantOrigin
import io.ronesec.domain.model.RuntimeState
import io.ronesec.domain.model.SessionId
import java.time.Instant
import java.time.ZoneId

enum class AllowReason {
    NOT_TARGET,
    TARGET_DISABLED,
    GLOBAL_PAUSE,
    ACTIVE_SESSION_PERMIT,
    ACTIVE_TIMED_PERMIT,
    QUICK_RETURN
}

data class EffectiveInterventionConfig(
    val packageName: String,
    val displayName: String,
    val phrase: String,
    val animation: AnimationMode,
    val durationMs: Long,
    val reinterventionMs: Long,
    val quickReturnGraceMs: Long,
    val baseDurationMs: Long,
    val backoffExponent: Int,
    val twoStageUnlock: Boolean = false,
    val unlockCodeLength: Int = 4,
    val requireEmergencyCode: Boolean = false
)

sealed interface Decision {
    data class Allow(val reason: AllowReason) : Decision
    data class Block(val until: Instant?) : Decision
    data class Intervention(val config: EffectiveInterventionConfig) : Decision
}

object RuleEngine {

    /**
     * Pure rule engine evaluation implementing §1.2 and §4.1 ordered checks:
     * 1. Active global pause -> Allow(GLOBAL_PAUSE)
     * 2. Missing target -> Allow(NOT_TARGET); Disabled target -> Allow(TARGET_DISABLED)
     * 3. Matching active manual session(s) -> Block(until = max(endTime))
     * 4. Active HARD_BLOCK occurrence(s) -> Block(until = union end)
     * 5. Active INTERVENTION schedule -> valid session permit, valid timed permit, grace; otherwise scheduled config + backoff
     * 6. No intervention schedule: valid session permit, valid timed permit
     * 7. Quick Return: elapsed < configured grace -> Allow(QUICK_RETURN)
     * 8. Remaining enabled target -> base-config Intervention + backoff
     */
    fun evaluate(
        packageName: String,
        nowWall: Instant,
        nowElapsedMs: Long,
        zoneId: ZoneId,
        runtimeState: RuntimeState,
        permitSessionId: SessionId? = null,
        priorEntryCountOverride: Int? = null,
        randomDurationProvider: (minMs: Long, maxMs: Long) -> Long = { min, max ->
            if (max > min) {
                val minSec = (min / 1000L).toInt()
                val maxSec = (max / 1000L).toInt()
                (minSec..maxSec).random().toLong() * 1000L
            } else min
        }
    ): Decision {
        val snapshot = runtimeState.snapshot

        // 1. Active global pause, finite or indefinite
        if (snapshot.globalPause.isActive(nowWall)) {
            return Decision.Allow(AllowReason.GLOBAL_PAUSE)
        }

        // 2. Missing target; then disabled target
        val target = snapshot.targets[packageName]
            ?: return Decision.Allow(AllowReason.NOT_TARGET)

        if (!target.enabled) {
            return Decision.Allow(AllowReason.TARGET_DISABLED)
        }

        // 3. Matching active manual session(s)
        val manualBlockEnd = ScheduleResolver.resolveActiveManualBlockEnd(
            sessions = snapshot.activeBlockSessions,
            packageName = packageName,
            now = nowWall
        )
        if (manualBlockEnd != null) {
            return Decision.Block(until = manualBlockEnd)
        }

        // 4. Active HARD_BLOCK occurrence(s)
        val hardBlock = ScheduleResolver.resolveActiveHardBlock(
            schedules = snapshot.activeSchedules,
            packageName = packageName,
            now = nowWall,
            zoneId = zoneId
        )
        if (hardBlock != null && hardBlock.isBlocked) {
            return Decision.Block(until = hardBlock.until)
        }

        // Compute prior ENTRY count for backoff:
        val priorEntryCount = priorEntryCountOverride ?: run {
            val history = runtimeState.getEffectiveHistory(packageName)
            Backoff.countEligiblePriorEntries(
                entryTimestamps = history,
                nowEpochMs = nowWall.toEpochMilli(),
                windowMs = target.growthConfig.windowMs
            )
        }

        // 5. Active INTERVENTION schedule
        val activeInterventionSchedule = ScheduleResolver.resolveActiveInterventionSchedule(
            schedules = snapshot.activeSchedules,
            packageName = packageName,
            now = nowWall,
            zoneId = zoneId
        )

        // Helper: Check valid permits
        val sessionPermit = runtimeState.sessionPermits[packageName]
        val hasValidSessionPermit = sessionPermit != null &&
            !sessionPermit.isExpired(nowElapsedMs) &&
            (permitSessionId == null || sessionPermit.sessionId == permitSessionId)

        val timedGrant = snapshot.activeGrants[packageName]
        val hasValidTimedPermit = timedGrant?.origin == GrantOrigin.EMERGENCY &&
            !timedGrant.isExpired(nowWall)

        // Helper: Check Quick Return
        val lastExit = runtimeState.lastExitElapsedMs[packageName]
        val hasQuickReturnGrace = lastExit != null &&
            target.quickReturnGraceMs > 0 &&
            (nowElapsedMs - lastExit) < target.quickReturnGraceMs

        if (activeInterventionSchedule != null) {
            if (hasValidSessionPermit) {
                return Decision.Allow(AllowReason.ACTIVE_SESSION_PERMIT)
            }
            if (hasValidTimedPermit) {
                return Decision.Allow(AllowReason.ACTIVE_TIMED_PERMIT)
            }
            if (hasQuickReturnGrace) {
                return Decision.Allow(AllowReason.QUICK_RETURN)
            }

            // Scheduled intervention override
            val override = activeInterventionSchedule.overrides[packageName]
            val configuredBaseMs = override?.durationMs ?: target.durationMs
            val baseDurationMs = if (target.randomDurationEnabled) {
                val minMs = configuredBaseMs
                val maxMs = maxOf(minMs, target.randomMaxDurationMs)
                randomDurationProvider(minMs, maxMs).coerceIn(minMs, maxMs)
            } else {
                configuredBaseMs
            }
            val reinterventionMs = override?.reinterventionMs ?: target.reinterventionMs

            val effectiveDurationMs = Backoff.resolveEffectiveDurationMs(
                baseDurationMs = baseDurationMs,
                config = target.growthConfig,
                priorEntriesCount = priorEntryCount
            )

            return Decision.Intervention(
                EffectiveInterventionConfig(
                    packageName = target.packageName,
                    displayName = target.displayName,
                    phrase = target.phrase,
                    animation = target.animation,
                    durationMs = effectiveDurationMs,
                    reinterventionMs = reinterventionMs,
                    quickReturnGraceMs = target.quickReturnGraceMs,
                    baseDurationMs = baseDurationMs,
                    backoffExponent = priorEntryCount,
                    twoStageUnlock = target.twoStageUnlock,
                    unlockCodeLength = target.unlockCodeLength,
                    requireEmergencyCode = target.requireEmergencyCode
                )
            )
        }

        // 6. No intervention schedule; valid grants (session first, then timed)
        if (hasValidSessionPermit) {
            return Decision.Allow(AllowReason.ACTIVE_SESSION_PERMIT)
        }
        if (hasValidTimedPermit) {
            return Decision.Allow(AllowReason.ACTIVE_TIMED_PERMIT)
        }

        // 7. Quick Return elapsed < configured grace
        if (hasQuickReturnGrace) {
            return Decision.Allow(AllowReason.QUICK_RETURN)
        }

        // 8. Remaining enabled target -> base config + backoff
        val baseDurationMs = if (target.randomDurationEnabled) {
            val minMs = target.durationMs
            val maxMs = maxOf(minMs, target.randomMaxDurationMs)
            randomDurationProvider(minMs, maxMs).coerceIn(minMs, maxMs)
        } else {
            target.durationMs
        }

        val effectiveDurationMs = Backoff.resolveEffectiveDurationMs(
            baseDurationMs = baseDurationMs,
            config = target.growthConfig,
            priorEntriesCount = priorEntryCount
        )

        return Decision.Intervention(
            EffectiveInterventionConfig(
                packageName = target.packageName,
                displayName = target.displayName,
                phrase = target.phrase,
                animation = target.animation,
                durationMs = effectiveDurationMs,
                reinterventionMs = target.reinterventionMs,
                quickReturnGraceMs = target.quickReturnGraceMs,
                baseDurationMs = baseDurationMs,
                backoffExponent = priorEntryCount,
                twoStageUnlock = target.twoStageUnlock,
                unlockCodeLength = target.unlockCodeLength,
                requireEmergencyCode = target.requireEmergencyCode
            )
        )
    }
}
