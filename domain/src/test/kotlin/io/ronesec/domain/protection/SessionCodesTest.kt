package io.ronesec.domain.protection

import io.ronesec.domain.codes.SessionCodePort
import io.ronesec.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class SessionCodesTest {
    private val target = "protected"
    private var sequence = 0L
    private val port = object : SessionCodePort {
        var generations = 0
        override fun generate(length: Int): String {
            generations++
            return if (length == 10) "9876543210" else "0123456789".take(length)
        }
        override fun matches(expected: String, entered: String) = expected == entered
    }
    private var context = ReducerContext(
        Instant.parse("2026-09-11T12:00:00Z"), 1000L, ZoneId.of("UTC"),
        RuntimeState(RuntimePolicySnapshot(1, mapOf(target to TargetConfig(
            target, "Target", twoStageUnlock = true, unlockCodeLength = 4, requireEmergencyCode = true
        )), emptyMap(), emptyList(), emptyList(), GlobalPause.None)),
        nextSessionId = { SessionId(1, 1, ++sequence) }, codePort = port
    )
    private var state: ProtectionState = ProtectionState.Idle
    private fun step(event: ProtectionEvent): ReducerResult = ProtectionReducer.reduce(state, event, context).also {
        state = it.newState
        context = context.copy(runtimeState = it.updatedRuntimeState)
    }
    private fun active() = state as ProtectionState.Intervening
    private fun enter() {
        step(ProtectionEvent.ForegroundCandidate(target, 1, 1))
        val session = active().session
        step(ProtectionEvent.OverlayAttached(session.sessionId, session.cycle))
    }
    private fun generate() {
        val session = active().session
        step(ProtectionEvent.GenerateUnlockCode(session.sessionId, session.cycle))
    }
    private fun returnFromWattim() {
        step(ProtectionEvent.ForegroundCandidate(context.wattimPackageName, 2, 2))
        val session = active().session
        step(ProtectionEvent.CodePanelShown(
            session.sessionId,
            session.cycle,
            active().codes.unlockRequestRevision
        ))
        step(ProtectionEvent.ForegroundCandidate("launcher", 3, 3, isLauncher = true))
        step(ProtectionEvent.ForegroundCandidate(target, 4, 4))
    }

    @Test fun gateAndRoundTripRequireCorrectCodeBeforeBreathing() {
        enter()
        assertEquals(InterveningSubstate.CodeGate, active().substate)
        generate()
        val session = active().session
        val count = port.generations
        assertTrue(step(ProtectionEvent.GenerateUnlockCode(session.sessionId, session.cycle)).effects.isEmpty())
        assertEquals(count, port.generations)
        returnFromWattim()
        step(ProtectionEvent.SubmitUnlockCode(session.sessionId, session.cycle, "1234"))
        assertEquals(InterveningSubstate.CodeGate, active().substate)
        assertTrue(active().codes.error)
        context = context.copy(nowElapsedMs = 9000)
        val result = step(ProtectionEvent.SubmitUnlockCode(session.sessionId, session.cycle, "0123"))
        assertEquals(9000L, (active().substate as InterveningSubstate.Breathing).startElapsedMs)
        assertNull(active().codes.unlockCode)
        assertEquals("9876543210", active().codes.emergencyCode)
        assertTrue(result.effects.any { it is ProtectionEffect.ScheduleTemporalBoundary })
        assertTrue(step(ProtectionEvent.SubmitUnlockCode(session.sessionId, session.cycle, "0123")).effects.isEmpty())
    }

    @Test fun expiryAtExactBoundaryRejectsCodeAndClearsPanel() {
        enter(); generate(); returnFromWattim()
        val session = active().session
        context = context.copy(nowElapsedMs = active().codes.unlockExpiresElapsedMs!!)
        step(ProtectionEvent.SubmitUnlockCode(session.sessionId, session.cycle, "0123"))
        assertNull(active().codes.unlockCode)
        assertEquals(InterveningSubstate.CodeGate, active().substate)
        assertEquals("9876543210", active().codes.emergencyCode)
    }

    @Test fun emergencyIsIndependentAndWrongCodeCannotCreateAnyGrantOrTimer() {
        enter()
        val session = active().session
        val failed = step(ProtectionEvent.ActionEmergencyTimed(session.sessionId, session.cycle, 60000, "0123"))
        assertTrue(failed.effects.none { it is ProtectionEffect.CommitAccessGrant || it is ProtectionEffect.ScheduleTemporalBoundary })
        assertEquals(InterveningSubstate.CodeGate, active().substate)
        val success = step(ProtectionEvent.ActionEmergencyTimed(session.sessionId, session.cycle, 60000, "9876543210"))
        assertTrue(state is ProtectionState.Granted)
        assertEquals(1, success.effects.filterIsInstance<ProtectionEffect.CommitAccessGrant>().size)
        assertTrue(step(ProtectionEvent.ActionEmergencyTimed(session.sessionId, session.cycle, 60000, "9876543210")).effects.isEmpty())
    }

    @Test fun emergencyCodeProtectsTimedAccessOnly() {
        enter()
        val session = active().session
        val once = step(ProtectionEvent.ActionEmergencyOnce(session.sessionId, session.cycle))
        assertTrue(state is ProtectionState.Granted)
        assertTrue(once.effects.none { it is ProtectionEffect.CommitAccessGrant })
    }

    @Test fun lifecycleAndDepartureDiscardBothCodes() {
        listOf(ProtectionEvent.ScreenOff, ProtectionEvent.ServiceDisconnected, ProtectionEvent.ServiceConnected(2),
            ProtectionEvent.ForegroundCandidate("unrelated", 8, 8)).forEach { event ->
            state = ProtectionState.Idle
            enter(); generate()
            step(event)
            assertFalse(state is ProtectionState.Intervening)
            assertFalse(state.toString().contains("0123"))
            assertFalse(state.toString().contains("9876543210"))
        }
    }

    @Test fun staleSessionAndCycleCannotAffectReplacementSession() {
        enter(); generate()
        val old = active().session
        step(ProtectionEvent.ForegroundCandidate("other", 2, 2))
        enter()
        val current = active()
        step(ProtectionEvent.SubmitUnlockCode(old.sessionId, old.cycle, "0123"))
        step(ProtectionEvent.ActionEmergencyOnce(old.sessionId, old.cycle, "9876543210"))
        step(ProtectionEvent.ActionCancel(old.sessionId))
        step(ProtectionEvent.CodePanelShown(old.sessionId, old.cycle, active().codes.unlockRequestRevision))
        step(ProtectionEvent.GenerateUnlockCode(current.session.sessionId, current.session.cycle + 1))
        assertEquals(current, state)
    }

    @Test fun recreationAndDuplicateForegroundDoNotRestartChallengeOrTimeline() {
        enter(); generate(); returnFromWattim()
        val session = active().session
        val before = active()
        step(ProtectionEvent.OverlayAttached(session.sessionId, session.cycle))
        step(ProtectionEvent.ForegroundCandidate(target, 5, 5))
        assertEquals(before, state)
        step(ProtectionEvent.SubmitUnlockCode(session.sessionId, session.cycle, "0123"))
        val breathing = active()
        context = context.copy(nowElapsedMs = 2000)
        step(ProtectionEvent.OverlayAttached(session.sessionId, session.cycle))
        assertEquals(breathing, state)
    }

    @Test fun failedTripRestoresGateAndInvalidatesGeneratedCode() {
        enter(); generate()
        val session = active().session
        val failed = step(ProtectionEvent.CodeTripFailed(
            session.sessionId,
            session.cycle,
            active().codes.unlockRequestRevision
        ))
        assertNull(active().codes.unlockCode)
        assertEquals(CodeTravel.NONE, active().codes.travel)
        assertTrue(failed.effects.any { it is ProtectionEffect.ShowIntervention })
    }

    @Test fun targetReturnWithoutPanelAcknowledgementFailsClosed() {
        enter(); generate()
        val result = step(ProtectionEvent.ForegroundCandidate(target, 2, 2))
        assertEquals(InterveningSubstate.CodeGate, active().substate)
        assertNull(active().codes.unlockCode)
        assertEquals(CodeTravel.NONE, active().codes.travel)
        assertTrue(result.effects.any { it is ProtectionEffect.ShowIntervention })
    }

    @Test fun overlayIsReleasedOnlyAfterCodesPanelAcknowledgesVisibility() {
        enter()
        val session = active().session
        val generated = step(ProtectionEvent.GenerateUnlockCode(session.sessionId, session.cycle))
        assertTrue(generated.effects.none { it is ProtectionEffect.DismissOverlay })
        val shown = step(ProtectionEvent.CodePanelShown(
            session.sessionId,
            session.cycle,
            active().codes.unlockRequestRevision
        ))
        assertTrue(shown.effects.any { it is ProtectionEffect.DismissOverlay })
        assertEquals(CodeTravel.IN_WATTIM, active().codes.travel)
    }

    @Test fun stalePanelAcknowledgementCannotAcceptRegeneratedCode() {
        enter(); generate()
        val session = active().session
        val oldRevision = active().codes.unlockRequestRevision
        step(ProtectionEvent.ForegroundCandidate(target, 2, 2))
        generate()
        val newRevision = active().codes.unlockRequestRevision

        assertTrue(newRevision > oldRevision)
        val stale = step(ProtectionEvent.CodePanelShown(session.sessionId, session.cycle, oldRevision))

        assertTrue(stale.effects.isEmpty())
        assertEquals(CodeTravel.TO_WATTIM, active().codes.travel)
        assertNotNull(active().codes.unlockCode)
        assertEquals(newRevision, active().codes.unlockRequestRevision)

        val staleFailure = step(ProtectionEvent.CodeTripFailed(session.sessionId, session.cycle, oldRevision))
        assertTrue(staleFailure.effects.isEmpty())
        assertEquals(CodeTravel.TO_WATTIM, active().codes.travel)
        assertNotNull(active().codes.unlockCode)
    }

    @Test fun modesCanBeEnabledSeparately() {
        listOf(true to false, false to true, false to false).forEach { (gate, emergency) ->
            state = ProtectionState.Idle
            val snapshot = context.runtimeState.snapshot
            context = context.copy(runtimeState = RuntimeState(snapshot.copy(targets = mapOf(target to
                snapshot.targets.getValue(target).copy(twoStageUnlock = gate, requireEmergencyCode = emergency)))))
            enter()
            assertEquals(gate, active().substate is InterveningSubstate.CodeGate)
            assertEquals(emergency, active().codes.emergencyCode != null)
        }
    }

    @Test fun configurationEditsDoNotChangeActiveChallengeLength() {
        enter(); generate(); returnFromWattim()
        val snapshot = context.runtimeState.snapshot
        val updated = snapshot.copy(revision = snapshot.revision + 1, targets = mapOf(target to
            snapshot.targets.getValue(target).copy(unlockCodeLength = 8, twoStageUnlock = false, requireEmergencyCode = false)))
        step(ProtectionEvent.PolicyCommitted(updated.revision, updated))
        assertEquals(4, active().codeChallengeUi().codeLength)
        assertTrue(active().codeChallengeUi().gate)
        assertNotNull(active().codes.emergencyCode)
        val session = active().session
        step(ProtectionEvent.SubmitUnlockCode(session.sessionId, session.cycle, "0123"))
        assertTrue(active().substate is InterveningSubstate.Breathing)
    }
}
