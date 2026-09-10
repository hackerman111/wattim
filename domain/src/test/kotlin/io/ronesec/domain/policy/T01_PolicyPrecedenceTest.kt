package io.ronesec.domain.policy

import io.ronesec.domain.model.CompiledBlockSession
import io.ronesec.domain.model.CompiledSchedule
import io.ronesec.domain.model.GlobalPause
import io.ronesec.domain.model.GrantOrigin
import io.ronesec.domain.model.RuntimePolicySnapshot
import io.ronesec.domain.model.RuntimeState
import io.ronesec.domain.model.ScheduleOverride
import io.ronesec.domain.model.ScheduleType
import io.ronesec.domain.model.SessionId
import io.ronesec.domain.model.SessionPermit
import io.ronesec.domain.model.TargetConfig
import io.ronesec.domain.model.TimedGrant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class T01_PolicyPrecedenceTest {

    private val zoneId = ZoneId.of("UTC")
    private val nowWall = Instant.parse("2026-09-09T12:00:00Z")
    private val nowElapsedMs = 1_000_000L
    private val targetPackage = "com.example.target"
    private val target = TargetConfig(packageName = targetPackage, displayName = "Target")

    @Test
    fun step1_globalPauseBeatsEverything() {
        val snapshot = RuntimePolicySnapshot(
            revision = 1L,
            targets = mapOf(targetPackage to target),
            activeGrants = emptyMap(),
            activeBlockSessions = listOf(
                CompiledBlockSession(
                    id = "manual1",
                    name = "Focus",
                    startTime = nowWall.minusSeconds(60),
                    endTime = nowWall.plusSeconds(60),
                    active = true,
                    targetPackages = setOf(targetPackage)
                )
            ),
            activeSchedules = listOf(
                CompiledSchedule(
                    id = 1L,
                    name = "Hard Block",
                    weekdayMask = 0x7F, // all days
                    startMinute = 0,
                    endMinute = 1439,
                    enabled = true,
                    type = ScheduleType.HARD_BLOCK,
                    targetPackages = setOf(targetPackage)
                )
            ),
            globalPause = GlobalPause.Until(nowWall.plusSeconds(300))
        )
        val state = RuntimeState(snapshot)

        val decision = RuleEngine.evaluate(targetPackage, nowWall, nowElapsedMs, zoneId, state)
        assertTrue(decision is Decision.Allow)
        assertEquals(AllowReason.GLOBAL_PAUSE, (decision as Decision.Allow).reason)
    }

    @Test
    fun step2_missingTargetOrDisabledYieldsAllow() {
        val snapshot = RuntimePolicySnapshot(
            revision = 1L,
            targets = mapOf(
                targetPackage to target.copy(enabled = false)
            ),
            activeGrants = emptyMap(),
            activeBlockSessions = emptyList(),
            activeSchedules = emptyList(),
            globalPause = GlobalPause.None
        )
        val state = RuntimeState(snapshot)

        // Missing target
        val missingDecision = RuleEngine.evaluate("com.other.app", nowWall, nowElapsedMs, zoneId, state)
        assertEquals(Decision.Allow(AllowReason.NOT_TARGET), missingDecision)

        // Disabled target
        val disabledDecision = RuleEngine.evaluate(targetPackage, nowWall, nowElapsedMs, zoneId, state)
        assertEquals(Decision.Allow(AllowReason.TARGET_DISABLED), disabledDecision)
    }

    @Test
    fun step3_activeManualSessionBeatsSchedulesAndGrants() {
        val manualEnd = nowWall.plusSeconds(600)
        val snapshot = RuntimePolicySnapshot(
            revision = 1L,
            targets = mapOf(targetPackage to target),
            activeGrants = mapOf(
                targetPackage to TimedGrant(
                    packageName = targetPackage,
                    grantId = "g1",
                    origin = GrantOrigin.EMERGENCY,
                    createdAt = nowWall.minusSeconds(60),
                    expiresAt = nowWall.plusSeconds(3600)
                )
            ),
            activeBlockSessions = listOf(
                CompiledBlockSession(
                    id = "session1",
                    name = "Manual Lock",
                    startTime = nowWall.minusSeconds(60),
                    endTime = manualEnd,
                    active = true,
                    targetPackages = setOf(targetPackage)
                )
            ),
            activeSchedules = emptyList(),
            globalPause = GlobalPause.None
        )
        val state = RuntimeState(snapshot)

        val decision = RuleEngine.evaluate(targetPackage, nowWall, nowElapsedMs, zoneId, state)
        assertTrue(decision is Decision.Block)
        assertEquals(manualEnd, (decision as Decision.Block).until)
    }

    @Test
    fun step4_activeHardBlockBeatsInterventionSchedulesAndGrants() {
        val snapshot = RuntimePolicySnapshot(
            revision = 1L,
            targets = mapOf(targetPackage to target),
            activeGrants = mapOf(
                targetPackage to TimedGrant(
                    packageName = targetPackage,
                    grantId = "g1",
                    origin = GrantOrigin.REINTERVENTION,
                    createdAt = nowWall.minusSeconds(60),
                    expiresAt = nowWall.plusSeconds(3600)
                )
            ),
            activeBlockSessions = emptyList(),
            activeSchedules = listOf(
                CompiledSchedule(
                    id = 1L,
                    name = "Hard Block 11:00-13:00",
                    weekdayMask = 0x7F,
                    startMinute = 11 * 60, // 11:00
                    endMinute = 13 * 60,   // 13:00
                    enabled = true,
                    type = ScheduleType.HARD_BLOCK,
                    targetPackages = setOf(targetPackage)
                )
            ),
            globalPause = GlobalPause.None
        )
        val state = RuntimeState(snapshot)

        val decision = RuleEngine.evaluate(targetPackage, nowWall, nowElapsedMs, zoneId, state)
        assertTrue(decision is Decision.Block)
    }

    @Test
    fun step5_activeInterventionScheduleUsesOverrideAndHonorsGrants() {
        val snapshot = RuntimePolicySnapshot(
            revision = 1L,
            targets = mapOf(targetPackage to target.copy(durationMs = 8_000L, reinterventionMs = 300_000L)),
            activeGrants = emptyMap(),
            activeBlockSessions = emptyList(),
            activeSchedules = listOf(
                CompiledSchedule(
                    id = 1L,
                    name = "Work Focus Intervention",
                    weekdayMask = 0x7F,
                    startMinute = 9 * 60,
                    endMinute = 18 * 60,
                    enabled = true,
                    type = ScheduleType.INTERVENTION,
                    targetPackages = setOf(targetPackage),
                    overrides = mapOf(
                        targetPackage to ScheduleOverride(durationMs = 15_000L, reinterventionMs = 60_000L)
                    )
                )
            ),
            globalPause = GlobalPause.None
        )

        // Without grant -> Intervention with override duration
        val stateWithoutGrant = RuntimeState(snapshot)
        val decision = RuleEngine.evaluate(targetPackage, nowWall, nowElapsedMs, zoneId, stateWithoutGrant)
        assertTrue(decision is Decision.Intervention)
        val cfg = (decision as Decision.Intervention).config
        assertEquals(15_000L, cfg.durationMs)
        assertEquals(60_000L, cfg.reinterventionMs)

        // With session permit -> Allow(ACTIVE_SESSION_PERMIT)
        val stateWithPermit = stateWithoutGrant.copy(
            sessionPermits = mapOf(
                targetPackage to SessionPermit(targetPackage, SessionId(1, 1, 1))
            )
        )
        val allowDecision = RuleEngine.evaluate(targetPackage, nowWall, nowElapsedMs, zoneId, stateWithPermit)
        assertEquals(Decision.Allow(AllowReason.ACTIVE_SESSION_PERMIT), allowDecision)
    }

    @Test
    fun step6_noSchedule_validSessionOrTimedGrantAllowed() {
        val snapshot = RuntimePolicySnapshot(
            revision = 1L,
            targets = mapOf(targetPackage to target),
            activeGrants = mapOf(
                targetPackage to TimedGrant(
                    packageName = targetPackage,
                    grantId = "g1",
                    origin = GrantOrigin.EMERGENCY,
                    createdAt = nowWall.minusSeconds(60),
                    expiresAt = nowWall.plusSeconds(300)
                )
            ),
            activeBlockSessions = emptyList(),
            activeSchedules = emptyList(),
            globalPause = GlobalPause.None
        )
        val state = RuntimeState(snapshot)

        val decision = RuleEngine.evaluate(targetPackage, nowWall, nowElapsedMs, zoneId, state)
        assertEquals(Decision.Allow(AllowReason.ACTIVE_TIMED_PERMIT), decision)
    }

    @Test
    fun reinterventionGrantDoesNotPermitAReopenedForegroundSession() {
        val snapshot = RuntimePolicySnapshot(
            revision = 1L,
            targets = mapOf(targetPackage to target.copy(quickReturnGraceMs = 0L)),
            activeGrants = mapOf(
                targetPackage to TimedGrant(
                    packageName = targetPackage,
                    grantId = "legacy-reintervention",
                    origin = GrantOrigin.REINTERVENTION,
                    createdAt = nowWall.minusSeconds(60),
                    expiresAt = nowWall.plusSeconds(300)
                )
            ),
            activeBlockSessions = emptyList(),
            activeSchedules = emptyList(),
            globalPause = GlobalPause.None
        )

        val decision = RuleEngine.evaluate(
            targetPackage,
            nowWall,
            nowElapsedMs,
            zoneId,
            RuntimeState(snapshot)
        )

        assertTrue(decision is Decision.Intervention)
    }

    @Test
    fun step7_quickReturnWithinGraceAllowed() {
        val targetWithGrace = target.copy(quickReturnGraceMs = 30_000L) // 30s grace
        val snapshot = RuntimePolicySnapshot(
            revision = 1L,
            targets = mapOf(targetPackage to targetWithGrace),
            activeGrants = emptyMap(),
            activeBlockSessions = emptyList(),
            activeSchedules = emptyList(),
            globalPause = GlobalPause.None
        )
        // Exited 10 seconds ago
        val stateWithinGrace = RuntimeState(
            snapshot = snapshot,
            lastExitElapsedMs = mapOf(targetPackage to (nowElapsedMs - 10_000L))
        )
        val decisionWithin = RuleEngine.evaluate(targetPackage, nowWall, nowElapsedMs, zoneId, stateWithinGrace)
        assertEquals(Decision.Allow(AllowReason.QUICK_RETURN), decisionWithin)

        // Exited 40 seconds ago (> 30s grace) -> Intervention
        val stateAfterGrace = RuntimeState(
            snapshot = snapshot,
            lastExitElapsedMs = mapOf(targetPackage to (nowElapsedMs - 40_000L))
        )
        val decisionAfter = RuleEngine.evaluate(targetPackage, nowWall, nowElapsedMs, zoneId, stateAfterGrace)
        assertTrue(decisionAfter is Decision.Intervention)
    }

    @Test
    fun step8_baseTargetIntervention() {
        val snapshot = RuntimePolicySnapshot(
            revision = 1L,
            targets = mapOf(targetPackage to target),
            activeGrants = emptyMap(),
            activeBlockSessions = emptyList(),
            activeSchedules = emptyList(),
            globalPause = GlobalPause.None
        )
        val state = RuntimeState(snapshot)

        val decision = RuleEngine.evaluate(targetPackage, nowWall, nowElapsedMs, zoneId, state)
        assertTrue(decision is Decision.Intervention)
        val cfg = (decision as Decision.Intervention).config
        assertEquals(target.durationMs, cfg.durationMs)
        assertEquals(target.reinterventionMs, cfg.reinterventionMs)
    }
}
