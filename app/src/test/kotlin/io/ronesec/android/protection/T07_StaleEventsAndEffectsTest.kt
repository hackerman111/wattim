package io.ronesec.android.protection

import io.ronesec.domain.model.AttemptOutcome
import io.ronesec.domain.model.GlobalPause
import io.ronesec.domain.model.RuntimePolicySnapshot
import io.ronesec.domain.model.RuntimeState
import io.ronesec.domain.model.SessionId
import io.ronesec.domain.model.TargetConfig
import io.ronesec.domain.policy.AllowReason
import io.ronesec.domain.protection.InterveningSubstate
import io.ronesec.domain.protection.ProtectionEvent
import io.ronesec.domain.protection.ProtectionReducer
import io.ronesec.domain.protection.ProtectionState
import io.ronesec.domain.protection.ReducerContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * T07: Stale events, session isolation, and effect rejection across sessions.
 * F26, Section 2.4, Section 3.1, Section 11.2 (T07).
 */
class T07_StaleEventsAndEffectsTest {

    private val targetPackage = "com.example.target"
    private val baseTarget = TargetConfig(
        packageName = targetPackage,
        displayName = "Target App",
        durationMs = 8_000L,
        reinterventionMs = 300_000L
    )

    private val baseSnapshot = RuntimePolicySnapshot(
        revision = 1L,
        targets = mapOf(targetPackage to baseTarget),
        activeGrants = emptyMap(),
        activeBlockSessions = emptyList(),
        activeSchedules = emptyList(),
        globalPause = GlobalPause.None
    )

    private var sessionCounter = 1L
    private val context = ReducerContext(
        nowWall = Instant.parse("2026-09-09T12:00:00Z"),
        nowElapsedMs = 500_000L,
        zoneId = ZoneId.of("UTC"),
        runtimeState = RuntimeState(baseSnapshot),
        nextSessionId = { SessionId(100L, 1L, sessionCounter++) },
        nextAttemptId = { "att-${sessionCounter}" }
    )

    @Test
    fun staleSessionActionContinue_isRejectedInNewSession() {
        // 1. Session 1 opens
        val entry1 = ProtectionReducer.reduce(
            ProtectionState.Idle,
            ProtectionEvent.ForegroundCandidate(targetPackage, 1000L, 1L),
            context
        )
        assertTrue(entry1.newState is ProtectionState.Intervening)
        val session1 = (entry1.newState as ProtectionState.Intervening).session

        // Session 1 is attached and breathes
        val attach1 = ProtectionReducer.reduce(
            entry1.newState,
            ProtectionEvent.OverlayAttached(session1.sessionId, session1.cycle),
            context.copy(runtimeState = entry1.updatedRuntimeState)
        )
        assertTrue(attach1.newState is ProtectionState.Intervening)

        // Session 1 exits
        val exit1 = ProtectionReducer.reduce(
            attach1.newState,
            ProtectionEvent.ActionExit(session1.sessionId),
            context.copy(runtimeState = attach1.updatedRuntimeState)
        )
        assertTrue(exit1.newState is ProtectionState.Exiting)

        // Departure confirmed to launcher
        val depart1 = ProtectionReducer.reduce(
            exit1.newState,
            ProtectionEvent.DepartureConfirmed(targetPackage),
            context.copy(runtimeState = exit1.updatedRuntimeState)
        )
        assertTrue(depart1.newState is ProtectionState.Idle)

        // 2. Session 2 opens (new session)
        val entry2 = ProtectionReducer.reduce(
            depart1.newState,
            ProtectionEvent.ForegroundCandidate(targetPackage, 2000L, 2L),
            context.copy(runtimeState = depart1.updatedRuntimeState)
        )
        assertTrue(entry2.newState is ProtectionState.Intervening)
        val session2 = (entry2.newState as ProtectionState.Intervening).session
        assertTrue("Session 2 must have different sessionId", session1.sessionId != session2.sessionId)

        // 3. Stale ActionContinue from Session 1 arrives during Session 2
        val staleResult = ProtectionReducer.reduce(
            entry2.newState,
            ProtectionEvent.ActionContinue(session1.sessionId, session1.cycle),
            context.copy(runtimeState = entry2.updatedRuntimeState)
        )

        // Must remain in Session 2 Intervening state! NOT Granted!
        assertTrue("Stale continue must be rejected", staleResult.newState is ProtectionState.Intervening)
        val currentIntervening = staleResult.newState as ProtectionState.Intervening
        assertEquals("Session 2 identity must be preserved", session2.sessionId, currentIntervening.session.sessionId)
        assertTrue("Effects must be empty (no grant or dismiss for stale session)", staleResult.effects.isEmpty())
    }

    @Test
    fun staleBreathingDeadline_fromOldSession_isRejected() {
        val entry1 = ProtectionReducer.reduce(
            ProtectionState.Idle,
            ProtectionEvent.ForegroundCandidate(targetPackage, 1000L, 1L),
            context
        )
        val session1 = (entry1.newState as ProtectionState.Intervening).session

        // Open Session 2 directly
        val entry2 = ProtectionReducer.reduce(
            ProtectionState.Idle,
            ProtectionEvent.ForegroundCandidate(targetPackage, 2000L, 2L),
            context
        )
        val session2 = (entry2.newState as ProtectionState.Intervening).session

        // Stale deadline from session 1 arrives
        val staleDeadlineResult = ProtectionReducer.reduce(
            entry2.newState,
            ProtectionEvent.BreathingDeadlineReached(session1.sessionId, session1.cycle),
            context.copy(nowElapsedMs = context.nowElapsedMs + 10_000L)
        )

        // Must NOT complete session 2
        val substate = (staleDeadlineResult.newState as ProtectionState.Intervening).substate
        assertFalse("Old session deadline must not complete session 2", substate is InterveningSubstate.Complete)
        assertTrue("Effects must be empty", staleDeadlineResult.effects.isEmpty())
    }

    @Test
    fun staleOverlayAttached_fromOldSession_isRejected() {
        val entry1 = ProtectionReducer.reduce(
            ProtectionState.Idle,
            ProtectionEvent.ForegroundCandidate(targetPackage, 1000L, 1L),
            context
        )
        val session1 = (entry1.newState as ProtectionState.Intervening).session

        val entry2 = ProtectionReducer.reduce(
            ProtectionState.Idle,
            ProtectionEvent.ForegroundCandidate(targetPackage, 2000L, 2L),
            context
        )
        val session2 = (entry2.newState as ProtectionState.Intervening).session

        // Stale attach from session 1 arrives
        val staleAttachResult = ProtectionReducer.reduce(
            entry2.newState,
            ProtectionEvent.OverlayAttached(session1.sessionId, session1.cycle),
            context
        )

        // Session 2 remains AwaitingAttachment
        val substate = (staleAttachResult.newState as ProtectionState.Intervening).substate
        assertEquals(InterveningSubstate.AwaitingAttachment, substate)
        assertTrue("No effects dispatched", staleAttachResult.effects.isEmpty())
    }

    @Test
    fun staleOverlayAttachFailure_fromOldSession_isRejected() {
        val entry1 = ProtectionReducer.reduce(
            ProtectionState.Idle,
            ProtectionEvent.ForegroundCandidate(targetPackage, 1000L, 1L),
            context
        )
        val session1 = (entry1.newState as ProtectionState.Intervening).session

        val entry2 = ProtectionReducer.reduce(
            ProtectionState.Idle,
            ProtectionEvent.ForegroundCandidate(targetPackage, 2000L, 2L),
            context
        )
        val session2 = (entry2.newState as ProtectionState.Intervening).session

        val staleFailureResult = ProtectionReducer.reduce(
            entry2.newState,
            ProtectionEvent.OverlayAttachFailed(
                sessionId = session1.sessionId,
                cycle = session1.cycle,
                errorType = "IllegalStateException"
            ),
            context.copy(runtimeState = entry2.updatedRuntimeState)
        )

        assertEquals(entry2.newState, staleFailureResult.newState)
        assertEquals(session2.sessionId, (staleFailureResult.newState as ProtectionState.Intervening).session.sessionId)
        assertTrue("Stale failure must not release the active session", staleFailureResult.effects.isEmpty())
    }
}
