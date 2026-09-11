package io.ronesec.android.ui.codes

import io.ronesec.domain.model.SessionId
import io.ronesec.domain.policy.EffectiveInterventionConfig
import io.ronesec.domain.protection.ActiveSession
import io.ronesec.domain.protection.InterveningSubstate
import io.ronesec.domain.protection.ProtectionState
import io.ronesec.domain.protection.SessionCodes
import org.junit.Assert.assertEquals
import org.junit.Test

class CodesPanelStateTest {
    private val session = ActiveSession(
        sessionId = SessionId(1, 2, 3),
        packageName = "protected.app",
        cycle = 1,
        attemptId = "attempt",
        effectiveConfig = EffectiveInterventionConfig(
            packageName = "protected.app",
            displayName = "Protected",
            phrase = "Breathe",
            animation = io.ronesec.domain.model.AnimationMode.FILL,
            durationMs = 8_000L,
            reinterventionMs = 0L,
            quickReturnGraceMs = 0L,
            baseDurationMs = 8_000L,
            backoffExponent = 0,
            twoStageUnlock = true,
            requireEmergencyCode = true
        )
    )

    @Test
    fun `panel exposes only unlock code while Wattim is foreground`() {
        val state = ProtectionState.Intervening(
            session = session,
            substate = InterveningSubstate.CodeGate,
            codes = SessionCodes(
                unlockCode = "0123",
                unlockRequestRevision = 7L,
                emergencyCode = "9876543210"
            )
        )

        assertEquals(
            CodesPanelState.Active(session.sessionId, session.cycle, 7L, "Protected", "0123"),
            state.toCodesPanelState(true)
        )
        assertEquals(CodesPanelState.Empty, state.toCodesPanelState(false))
        assertEquals(CodesPanelState.Empty, state.copy(codes = state.codes.copy(unlockCode = null)).toCodesPanelState(true))
    }
}
