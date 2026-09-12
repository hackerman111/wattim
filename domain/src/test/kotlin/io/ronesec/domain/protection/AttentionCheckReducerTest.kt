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
        val expiredResult = ProtectionReducer.reduce(
            checkReached.newState,
            ProtectionEvent.TemporalBoundaryReached(attention.deadlineElapsedMs),
            timeoutContext
        )

        val expiredSubstate = (expiredResult.newState as ProtectionState.Intervening).substate
        assertTrue("Must transition to expired AttentionCheck", expiredSubstate is InterveningSubstate.AttentionCheck)
        val expiredCheck = expiredSubstate as InterveningSubstate.AttentionCheck
        assertTrue("isExpired must be true", expiredCheck.isExpired)
        assertEquals(timeoutContext.nowElapsedMs + 1000L, expiredCheck.deadlineElapsedMs)

        // Submitting code while expired is ignored
        val submitWhileExpired = ProtectionReducer.reduce(
            expiredResult.newState,
            ProtectionEvent.SubmitAttentionCheckCode(session.sessionId, session.cycle, expiredCheck.code),
            timeoutContext.copy(runtimeState = expiredResult.updatedRuntimeState)
        )
        val stillExpiredSubstate = (submitWhileExpired.newState as ProtectionState.Intervening).substate as InterveningSubstate.AttentionCheck
        assertTrue(stillExpiredSubstate.isExpired)

        // 4. Reach the 1000ms expiration boundary -> Resets to Breathing with progress = 0
        val resetContext = timeoutContext.copy(
            nowElapsedMs = expiredCheck.deadlineElapsedMs,
            runtimeState = submitWhileExpired.updatedRuntimeState
        )
        val resetResult = ProtectionReducer.reduce(
            submitWhileExpired.newState,
            ProtectionEvent.TemporalBoundaryReached(expiredCheck.deadlineElapsedMs),
            resetContext
        )

        val resetSubstate = (resetResult.newState as ProtectionState.Intervening).substate
        assertTrue("Must reset to Breathing", resetSubstate is InterveningSubstate.Breathing)
        val resetBreathing = resetSubstate as InterveningSubstate.Breathing
        // Progress should be reset: startElapsedMs should be reset to resetContext.nowElapsedMs!
        assertEquals(resetContext.nowElapsedMs, resetBreathing.startElapsedMs)
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

    @Test
    fun emergencyOpen_pausesBreathing_preventsAttentionCheck_andResumingRestoresBreathingTimeline() {
        // 1. Enter & attach
        val entry = ProtectionReducer.reduce(
            ProtectionState.Idle,
            ProtectionEvent.ForegroundCandidate(targetPackage, 1000L, 1L),
            baseContext
        )
        val session = (entry.newState as ProtectionState.Intervening).session
        val attachContext = baseContext.copy(runtimeState = entry.updatedRuntimeState)
        val attached = ProtectionReducer.reduce(
            entry.newState,
            ProtectionEvent.OverlayAttached(session.sessionId, session.cycle),
            attachContext
        )
        val breathing = (attached.newState as ProtectionState.Intervening).substate as InterveningSubstate.Breathing
        val checkBoundary = breathing.deadlineElapsedMs

        // 2. 1000ms into breathing, user opens Emergency dialog
        val emergencyOpenContext = attachContext.copy(
            nowElapsedMs = attachContext.nowElapsedMs + 1000L,
            runtimeState = attached.updatedRuntimeState
        )
        val emergencyOpenResult = ProtectionReducer.reduce(
            attached.newState,
            ProtectionEvent.ActionOpenEmergency(session.sessionId, session.cycle),
            emergencyOpenContext
        )
        val emergencyState = emergencyOpenResult.newState as ProtectionState.Intervening
        assertTrue("Substate must be Emergency", emergencyState.substate is InterveningSubstate.Emergency)
        val em = emergencyState.substate as InterveningSubstate.Emergency
        assertEquals(1000L, em.pausedElapsedProgressMs)
        assertTrue("Must include CancelTemporalBoundary effect",
            emergencyOpenResult.effects.contains(ProtectionEffect.CancelTemporalBoundary))
        val challengeUi = emergencyState.codeChallengeUi()
        assertTrue("CodeChallengeUi must indicate emergency", challengeUi.isEmergency)
        assertEquals(1000L, challengeUi.pausedProgressMs)

        // 3. Time passes past the original check boundary while in Emergency
        val pastBoundaryContext = emergencyOpenContext.copy(
            nowElapsedMs = checkBoundary + 2000L,
            runtimeState = emergencyOpenResult.updatedRuntimeState
        )
        val staleBoundaryResult = ProtectionReducer.reduce(
            emergencyOpenResult.newState,
            ProtectionEvent.TemporalBoundaryReached(checkBoundary),
            pastBoundaryContext
        )
        // Must STILL be in Emergency substate! Check was NOT triggered!
        assertTrue("Attention check must NOT trigger during emergency",
            (staleBoundaryResult.newState as ProtectionState.Intervening).substate is InterveningSubstate.Emergency)

        // 4. User dismisses emergency dialog ("Return to breathing")
        val dismissContext = pastBoundaryContext.copy(
            nowElapsedMs = pastBoundaryContext.nowElapsedMs + 3000L,
            runtimeState = staleBoundaryResult.updatedRuntimeState
        )
        val resumedResult = ProtectionReducer.reduce(
            staleBoundaryResult.newState,
            ProtectionEvent.ActionDismissEmergency(session.sessionId, session.cycle),
            dismissContext
        )
        val resumedState = resumedResult.newState as ProtectionState.Intervening
        assertTrue("Substate must return to Breathing", resumedState.substate is InterveningSubstate.Breathing)
        val resumedBreathing = resumedState.substate as InterveningSubstate.Breathing
        // Breathing start must be shifted so elapsed progress at dismissContext.nowElapsedMs equals 1000L
        assertEquals(1000L, dismissContext.nowElapsedMs - resumedBreathing.startElapsedMs)
        // Schedule effect must be emitted for the remaining check
        val scheduleEffect = resumedResult.effects.filterIsInstance<ProtectionEffect.ScheduleTemporalBoundary>().firstOrNull()
        assertNotNull("Must reschedule boundary for remaining check", scheduleEffect)
        assertEquals(resumedBreathing.deadlineElapsedMs, scheduleEffect!!.boundaryToken)

        // 5. Reach the newly scheduled check boundary
        val checkContext = dismissContext.copy(
            nowElapsedMs = resumedBreathing.deadlineElapsedMs,
            runtimeState = resumedResult.updatedRuntimeState
        )
        val checkReached = ProtectionReducer.reduce(
            resumedResult.newState,
            ProtectionEvent.TemporalBoundaryReached(resumedBreathing.deadlineElapsedMs),
            checkContext
        )
        assertTrue("Must transition to AttentionCheck at shifted boundary",
            (checkReached.newState as ProtectionState.Intervening).substate is InterveningSubstate.AttentionCheck)
    }

    @Test
    fun emergencyOpen_duringAttentionCheck_pausesTimeout_andResumingReschedulesRemainingTimeout() {
        // 1. Enter & reach attention check
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
        val attention = (checkReached.newState as ProtectionState.Intervening).substate as InterveningSubstate.AttentionCheck
        assertEquals(5000L, attention.timeoutMs)

        // 2. 2000ms into attention check (3000ms remaining), user taps Emergency
        val emergencyContext = checkContext.copy(
            nowElapsedMs = checkContext.nowElapsedMs + 2000L,
            runtimeState = checkReached.updatedRuntimeState
        )
        val emergencyResult = ProtectionReducer.reduce(
            checkReached.newState,
            ProtectionEvent.ActionOpenEmergency(session.sessionId, session.cycle),
            emergencyContext
        )
        val emState = emergencyResult.newState as ProtectionState.Intervening
        assertTrue(emState.substate is InterveningSubstate.Emergency)
        val em = emState.substate as InterveningSubstate.Emergency
        assertEquals(3000L, em.remainingTimeoutMs)
        assertTrue(emergencyResult.effects.contains(ProtectionEffect.CancelTemporalBoundary))

        // 3. User stays in emergency for 10 seconds (way past 5s timeout)
        val longWaitContext = emergencyContext.copy(
            nowElapsedMs = emergencyContext.nowElapsedMs + 10_000L,
            runtimeState = emergencyResult.updatedRuntimeState
        )
        // 4. User dismisses emergency
        val resumeResult = ProtectionReducer.reduce(
            emergencyResult.newState,
            ProtectionEvent.ActionDismissEmergency(session.sessionId, session.cycle),
            longWaitContext
        )
        val resumedState = resumeResult.newState as ProtectionState.Intervening
        assertTrue("Must resume AttentionCheck", resumedState.substate is InterveningSubstate.AttentionCheck)
        val resumedCheck = resumedState.substate as InterveningSubstate.AttentionCheck
        assertEquals(3000L, resumedCheck.timeoutMs)
        assertEquals(longWaitContext.nowElapsedMs + 3000L, resumedCheck.deadlineElapsedMs)
    }

    @Test
    fun emergencyOpen_with10DigitCodeRequired_allowsAccessFromEmergencySubstate() {
        val targetWithCode = attentionTarget.copy(requireEmergencyCode = true)
        val snapWithCode = snapshot.copy(targets = mapOf(targetPackage to targetWithCode))
        val contextWithCode = baseContext.copy(runtimeState = RuntimeState(snapWithCode))

        val entry = ProtectionReducer.reduce(
            ProtectionState.Idle,
            ProtectionEvent.ForegroundCandidate(targetPackage, 1000L, 1L),
            contextWithCode
        )
        val session = (entry.newState as ProtectionState.Intervening).session
        val attached = ProtectionReducer.reduce(
            entry.newState,
            ProtectionEvent.OverlayAttached(session.sessionId, session.cycle),
            contextWithCode.copy(runtimeState = entry.updatedRuntimeState)
        )
        val attachedState = attached.newState as ProtectionState.Intervening
        val emergencyCode = attachedState.codes.emergencyCode
        assertNotNull("Emergency code must be generated", emergencyCode)

        // Open emergency
        val openResult = ProtectionReducer.reduce(
            attached.newState,
            ProtectionEvent.ActionOpenEmergency(session.sessionId, session.cycle),
            contextWithCode.copy(nowElapsedMs = contextWithCode.nowElapsedMs + 500L, runtimeState = attached.updatedRuntimeState)
        )
        assertTrue((openResult.newState as ProtectionState.Intervening).substate is InterveningSubstate.Emergency)

        // Submit wrong code
        val wrongResult = ProtectionReducer.reduce(
            openResult.newState,
            ProtectionEvent.ActionEmergencyOnce(session.sessionId, session.cycle, "0000000000"),
            contextWithCode.copy(nowElapsedMs = contextWithCode.nowElapsedMs + 1000L, runtimeState = openResult.updatedRuntimeState)
        )
        assertTrue((wrongResult.newState as ProtectionState.Intervening).codes.emergencyError)

        // Submit correct 10-digit code
        val successResult = ProtectionReducer.reduce(
            wrongResult.newState,
            ProtectionEvent.ActionEmergencyOnce(session.sessionId, session.cycle, emergencyCode),
            contextWithCode.copy(nowElapsedMs = contextWithCode.nowElapsedMs + 2000L, runtimeState = wrongResult.updatedRuntimeState)
        )
        assertTrue("Must grant access on correct code", successResult.newState is ProtectionState.Granted)
    }
}
