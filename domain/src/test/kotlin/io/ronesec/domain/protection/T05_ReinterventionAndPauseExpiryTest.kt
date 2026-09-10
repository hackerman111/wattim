package io.ronesec.domain.protection

import io.ronesec.domain.model.AttemptKind
import io.ronesec.domain.model.CompiledBlockSession
import io.ronesec.domain.model.GlobalPause
import io.ronesec.domain.model.RuntimePolicySnapshot
import io.ronesec.domain.model.RuntimeState
import io.ronesec.domain.model.SessionId
import io.ronesec.domain.model.SessionPermit
import io.ronesec.domain.model.TargetConfig
import io.ronesec.domain.policy.AllowReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * T05: Reintervention, repeat deadlines, global pause expiry, and hard-block precedence during waiting.
 * F28, Section 4.3, Section 11.2 (T05).
 */
class T05_ReinterventionAndPauseExpiryTest {

    private val targetPackage = "com.example.reintervene"
    private val baseTarget = TargetConfig(
        packageName = targetPackage,
        displayName = "Reintervene App",
        durationMs = 8_000L,
        reinterventionMs = 60_000L // 60s repeat
    )

    private val baseSnapshot = RuntimePolicySnapshot(
        revision = 1L,
        targets = mapOf(targetPackage to baseTarget),
        activeGrants = emptyMap(),
        activeBlockSessions = emptyList(),
        activeSchedules = emptyList(),
        globalPause = GlobalPause.None
    )

    private val initialSessionId = SessionId(100L, 1L, 1L)
    private val initialSession = ActiveSession(
        sessionId = initialSessionId,
        packageName = targetPackage,
        cycle = 1,
        attemptId = "att-1"
    )

    @Test
    fun reinterventionRepeat_advancesToNewCycleOnSameSession() {
        val deadlineElapsedMs = 560_000L
        val permit = SessionPermit(
            packageName = targetPackage,
            sessionId = initialSessionId,
            expiresElapsedMs = deadlineElapsedMs
        )
        val runtimeState = RuntimeState(
            snapshot = baseSnapshot,
            sessionPermits = mapOf(targetPackage to permit)
        )

        val context = ReducerContext(
            nowWall = Instant.parse("2026-09-09T12:01:00Z"),
            nowElapsedMs = deadlineElapsedMs, // exactly at repeat deadline
            zoneId = ZoneId.of("UTC"),
            runtimeState = runtimeState,
            nextSessionId = { SessionId(100L, 1L, 999L) }, // should NOT be called for reintervention
            nextAttemptId = { "att-2" }
        )

        val currentState = ProtectionState.Granted(
            session = initialSession,
            packageName = targetPackage,
            reason = AllowReason.ACTIVE_SESSION_PERMIT
        )

        val result = ProtectionReducer.reduce(
            currentState = currentState,
            event = ProtectionEvent.TemporalBoundaryReached(boundaryToken = deadlineElapsedMs),
            context = context
        )

        assertTrue("State must become Intervening", result.newState is ProtectionState.Intervening)
        val intervening = result.newState as ProtectionState.Intervening
        assertEquals("Must retain same sessionId across cycles", initialSessionId, intervening.session.sessionId)
        assertEquals("Cycle must increment to 2", 2, intervening.session.cycle)
        assertEquals("AwaitingAttachment substate", InterveningSubstate.AwaitingAttachment, intervening.substate)

        // Verify RecordAttempt effect with AttemptKind.REINTERVENTION
        val recordEffect = result.effects.filterIsInstance<ProtectionEffect.RecordAttempt>().firstOrNull()
        assertNotNull("Must emit RecordAttempt effect", recordEffect)
        assertEquals(AttemptKind.REINTERVENTION, recordEffect!!.record.kind)
        assertEquals(2, recordEffect.record.cycle)
        assertEquals(initialSessionId, recordEffect.record.sessionId)

        // Verify ShowIntervention & AcquireAudioLease
        assertTrue(result.effects.any { it is ProtectionEffect.ShowIntervention && it.cycle == 2 })
        val audioEffect = result.effects.filterIsInstance<ProtectionEffect.AcquireAudioLease>().single()
        assertEquals(initialSessionId, audioEffect.sessionId)
        assertEquals(targetPackage, audioEffect.packageName)
    }

    @Test
    fun globalPauseActiveAtReinterventionDeadline_staysGrantedWithoutIntervention() {
        val deadlineElapsedMs = 560_000L
        val pauseUntil = Instant.parse("2026-09-09T12:15:00Z")
        val permit = SessionPermit(
            packageName = targetPackage,
            sessionId = initialSessionId,
            expiresElapsedMs = deadlineElapsedMs
        )
        val snapshotWithPause = baseSnapshot.copy(
            globalPause = GlobalPause.Until(pauseUntil)
        )
        val runtimeState = RuntimeState(
            snapshot = snapshotWithPause,
            sessionPermits = mapOf(targetPackage to permit)
        )

        val context = ReducerContext(
            nowWall = Instant.parse("2026-09-09T12:01:00Z"),
            nowElapsedMs = deadlineElapsedMs,
            zoneId = ZoneId.of("UTC"),
            runtimeState = runtimeState,
            nextSessionId = { SessionId(100L, 1L, 999L) }
        )

        val currentState = ProtectionState.Granted(
            session = initialSession,
            packageName = targetPackage,
            reason = AllowReason.ACTIVE_SESSION_PERMIT
        )

        val result = ProtectionReducer.reduce(
            currentState = currentState,
            event = ProtectionEvent.TemporalBoundaryReached(boundaryToken = deadlineElapsedMs),
            context = context
        )

        assertTrue("Must stay Granted under active global pause", result.newState is ProtectionState.Granted)
        val granted = result.newState as ProtectionState.Granted
        assertEquals(AllowReason.GLOBAL_PAUSE, granted.reason)
        assertEquals(initialSessionId, granted.session?.sessionId)
        assertTrue("No intervention effects under pause", result.effects.none { it is ProtectionEffect.ShowIntervention })
    }

    @Test
    fun hardBlockStartsWhileWaiting_triggersBlock() {
        val deadlineElapsedMs = 560_000L
        val permit = SessionPermit(
            packageName = targetPackage,
            sessionId = initialSessionId,
            expiresElapsedMs = deadlineElapsedMs
        )
        val blockEnd = Instant.parse("2026-09-09T13:00:00Z")
        val manualBlock = CompiledBlockSession(
            id = "block-1",
            name = "Focus",
            startTime = Instant.parse("2026-09-09T12:00:00Z"),
            endTime = blockEnd,
            active = true,
            targetPackages = setOf(targetPackage)
        )
        val snapshotWithBlock = baseSnapshot.copy(
            activeBlockSessions = listOf(manualBlock)
        )
        val runtimeState = RuntimeState(
            snapshot = snapshotWithBlock,
            sessionPermits = mapOf(targetPackage to permit)
        )

        val context = ReducerContext(
            nowWall = Instant.parse("2026-09-09T12:01:00Z"),
            nowElapsedMs = deadlineElapsedMs,
            zoneId = ZoneId.of("UTC"),
            runtimeState = runtimeState,
            nextSessionId = { SessionId(100L, 1L, 999L) }
        )

        val currentState = ProtectionState.Granted(
            session = initialSession,
            packageName = targetPackage,
            reason = AllowReason.ACTIVE_SESSION_PERMIT
        )

        val result = ProtectionReducer.reduce(
            currentState = currentState,
            event = ProtectionEvent.TemporalBoundaryReached(boundaryToken = deadlineElapsedMs),
            context = context
        )

        assertTrue("Must transition to Blocked", result.newState is ProtectionState.Blocked)
        val blocked = result.newState as ProtectionState.Blocked
        assertEquals(blockEnd, blocked.until)
        assertTrue("ShowBlock emitted", result.effects.any { it is ProtectionEffect.ShowBlock })
    }
}
