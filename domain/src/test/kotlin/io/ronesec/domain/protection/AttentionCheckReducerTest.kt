package io.ronesec.domain.protection

import io.ronesec.domain.codes.SessionCodePort
import io.ronesec.domain.model.GlobalPause
import io.ronesec.domain.model.RuntimePolicySnapshot
import io.ronesec.domain.model.RuntimeState
import io.ronesec.domain.model.SessionId
import io.ronesec.domain.model.TargetConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class AttentionCheckReducerTest {

    private val targetPackage = "com.example.attention"
    private val attentionTarget = TargetConfig(
        packageName = targetPackage,
        displayName = "Attention Target",
        durationMs = 10_000L,
        attentionChecksEnabled = true,
        attentionCheckCount = 1,
        attentionCheckCodeLength = 4,
        attentionCheckTimeoutMs = 5_000L
    )
    private val snapshot = RuntimePolicySnapshot(
        revision = 1L,
        targets = mapOf(targetPackage to attentionTarget),
        activeGrants = emptyMap(),
        activeBlockSessions = emptyList(),
        activeSchedules = emptyList(),
        globalPause = GlobalPause.None
    )

    private val testCodePort = object : SessionCodePort {
        override fun generate(length: Int): String = "1234".take(length).padEnd(length, '0')
        override fun matches(expected: String, entered: String): Boolean = expected == entered
    }

    private var sessionCounter = 1L
    private val baseContext = ReducerContext(
        nowWall = Instant.parse("2026-09-11T12:00:00Z"),
        nowElapsedMs = 100_000L,
        zoneId = ZoneId.of("UTC"),
        runtimeState = RuntimeState(snapshot),
        nextSessionId = { SessionId(100L, 1L, sessionCounter++) },
        nextAttemptId = { "att-$sessionCounter" },
        codePort = testCodePort
    )

    @Test
    fun attentionCheck_fullLifecycle_trigger_submitSuccess_resumesBreathing() {
        // 1. Enter target
        val entry = ProtectionReducer.reduce(
            ProtectionState.Idle,
            ProtectionEvent.ForegroundCandidate(targetPackage, 1000L, 1L),
            baseContext
        )
        val intervening = entry.newState as ProtectionState.Intervening
        val session = intervening.session

        // 2. Overlay attached -> Breathing starts with check scheduled
        val attachContext = baseContext.copy(runtimeState = entry.updatedRuntimeState)
        val attached = ProtectionReducer.reduce(
            intervening,
            ProtectionEvent.OverlayAttached(session.sessionId, session.cycle),
            attachContext
        )
        val breathing = (attached.newState as ProtectionState.Intervening).substate as InterveningSubstate.Breathing
        val checkBoundary = breathing.deadlineElapsedMs
        assertTrue("Check boundary must be before end of breathing", checkBoundary < attachContext.nowElapsedMs + 10_000L)

        // 3. Reach checkpoint boundary
        val checkContext = attachContext.copy(
            nowElapsedMs = checkBoundary,
            runtimeState = attached.updatedRuntimeState
        )
        val checkReached = ProtectionReducer.reduce(
            attached.newState,
            ProtectionEvent.TemporalBoundaryReached(checkBoundary),
            checkContext
        )
        val checkSubstate = (checkReached.newState as ProtectionState.Intervening).substate
        assertTrue("Must transition to AttentionCheck substate", checkSubstate is InterveningSubstate.AttentionCheck)
        val attention = checkSubstate as InterveningSubstate.AttentionCheck
        assertEquals("1234", attention.code)
        assertEquals(5_000L, attention.timeoutMs)
        assertEquals(checkBoundary + 5_000L, attention.deadlineElapsedMs)
        val pausedProgress = attention.pausedElapsedProgressMs
        assertEquals(checkBoundary - attachContext.nowElapsedMs, pausedProgress)
        assertFalse(attention.hasError)

        // 4. Submit wrong code -> error flag set
        val wrongCodeResult = ProtectionReducer.reduce(
            checkReached.newState,
            ProtectionEvent.SubmitAttentionCheckCode(session.sessionId, session.cycle, "9999"),
            checkContext.copy(nowElapsedMs = checkBoundary + 1_000L, runtimeState = checkReached.updatedRuntimeState)
        )
        val wrongSubstate = (wrongCodeResult.newState as ProtectionState.Intervening).substate as InterveningSubstate.AttentionCheck
        assertTrue(wrongSubstate.hasError)

        // 5. Submit correct code before deadline -> Breathing resumes from exact paused progress
        val correctContext = checkContext.copy(
            nowElapsedMs = checkBoundary + 2_000L,
            runtimeState = wrongCodeResult.updatedRuntimeState
        )
        val resumed = ProtectionReducer.reduce(
            wrongCodeResult.newState,
            ProtectionEvent.SubmitAttentionCheckCode(session.sessionId, session.cycle, "1234"),
            correctContext
        )
        val resumedBreathing = (resumed.newState as ProtectionState.Intervening).substate
        assertTrue("Substate must return to Breathing", resumedBreathing is InterveningSubstate.Breathing)
        val rb = resumedBreathing as InterveningSubstate.Breathing
        // Breathing elapsed progress at correctContext.nowElapsedMs must equal pausedProgress!
        assertEquals(pausedProgress, correctContext.nowElapsedMs - rb.startElapsedMs)

        // 6. Complete remaining breathing time
        val remainingMs = 10_000L - pausedProgress
        val completionContext = correctContext.copy(
            nowElapsedMs = correctContext.nowElapsedMs + remainingMs,
            runtimeState = resumed.updatedRuntimeState
        )
        val completed = ProtectionReducer.reduce(
            resumed.newState,
            ProtectionEvent.TemporalBoundaryReached(rb.deadlineElapsedMs),
            completionContext
        )
        val completedSubstate = (completed.newState as ProtectionState.Intervening).substate
        assertTrue("Must be Complete", completedSubstate is InterveningSubstate.Complete)
    }

    @Test
    fun attentionCheck_timeout_resetsBreathingToZero() {
        // 1. Enter & attach
        val entry = ProtectionReducer.reduce(
            ProtectionState.Idle,
            ProtectionEvent.ForegroundCandidate(targetPackage, 1000L, 1L),
            baseContext
        )
        val session = (entry.newState as ProtectionState.Intervening).session
        val attached = ProtectionReducer.reduce(
            entry.newState,
            ProtectionEvent.OverlayAttached(session.sessionId, session.cycle),
            baseContext.copy(runtimeState = entry.updatedRuntimeState)
        )
        val breathing = (attached.newState as ProtectionState.Intervening).substate as InterveningSubstate.Breathing
        val checkBoundary = breathing.deadlineElapsedMs

        // 2. Reach checkpoint
        val checkContext = baseContext.copy(
            nowElapsedMs = checkBoundary,
            runtimeState = attached.updatedRuntimeState
        )
        val checkReached = ProtectionReducer.reduce(
            attached.newState,
            ProtectionEvent.TemporalBoundaryReached(checkBoundary),
            checkContext
        )
        val attention = (checkReached.newState as ProtectionState.Intervening).substate as InterveningSubstate.AttentionCheck

        // 3. Time passes past deadline (deadlineElapsedMs + 100) without code
        val timeoutContext = checkContext.copy(
            nowElapsedMs = attention.deadlineElapsedMs + 100L,
            runtimeState = checkReached.updatedRuntimeState
        )
        val resetResult = ProtectionReducer.reduce(
            checkReached.newState,
            ProtectionEvent.TemporalBoundaryReached(attention.deadlineElapsedMs),
            timeoutContext
        )

        val resetSubstate = (resetResult.newState as ProtectionState.Intervening).substate
        assertTrue("Must reset to Breathing", resetSubstate is InterveningSubstate.Breathing)
        val resetBreathing = resetSubstate as InterveningSubstate.Breathing
        // Progress should be reset: startElapsedMs should be reset to timeoutContext.nowElapsedMs!
        assertEquals(timeoutContext.nowElapsedMs, resetBreathing.startElapsedMs)
    }

    @Test
    fun attentionCheck_exitButton_exitsCleanly() {
        val entry = ProtectionReducer.reduce(
            ProtectionState.Idle,
            ProtectionEvent.ForegroundCandidate(targetPackage, 1000L, 1L),
            baseContext
        )
        val session = (entry.newState as ProtectionState.Intervening).session
        val attached = ProtectionReducer.reduce(
            entry.newState,
            ProtectionEvent.OverlayAttached(session.sessionId, session.cycle),
            baseContext.copy(runtimeState = entry.updatedRuntimeState)
        )
        val breathing = (attached.newState as ProtectionState.Intervening).substate as InterveningSubstate.Breathing

        val checkContext = baseContext.copy(
            nowElapsedMs = breathing.deadlineElapsedMs,
            runtimeState = attached.updatedRuntimeState
        )
        val checkReached = ProtectionReducer.reduce(
            attached.newState,
            ProtectionEvent.TemporalBoundaryReached(breathing.deadlineElapsedMs),
            checkContext
        )

        // Exit while in AttentionCheck
        val exitResult = ProtectionReducer.reduce(
            checkReached.newState,
            ProtectionEvent.ActionExit(session.sessionId),
            checkContext.copy(runtimeState = checkReached.updatedRuntimeState)
        )
        assertTrue(exitResult.newState is ProtectionState.Exiting)
    }
}
