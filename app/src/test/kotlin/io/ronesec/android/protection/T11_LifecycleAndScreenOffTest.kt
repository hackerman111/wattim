package io.ronesec.android.protection

import io.ronesec.domain.model.GlobalPause
import io.ronesec.domain.model.RuntimePolicySnapshot
import io.ronesec.domain.model.RuntimeState
import io.ronesec.domain.model.SessionId
import io.ronesec.domain.model.SessionPermit
import io.ronesec.domain.model.TargetConfig
import io.ronesec.domain.protection.ActiveSession
import io.ronesec.domain.protection.ProtectionEffect
import io.ronesec.domain.protection.ProtectionEvent
import io.ronesec.domain.protection.ProtectionReducer
import io.ronesec.domain.protection.ProtectionState
import io.ronesec.domain.protection.ReducerContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * T11: Screen off/on, service interrupt/reconnect, process restart boundaries, and RAM state clearing.
 * F29, F30, F31, Section 2.4-2.6, Section 11.2 (T11).
 */
class T11_LifecycleAndScreenOffTest {

    private val targetPackage = "com.example.lifecycle"
    private val baseTarget = TargetConfig(
        packageName = targetPackage,
        displayName = "Lifecycle App",
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
    fun screenOff_suspends_clearsPermits_andReleasesResources() {
        val permit = SessionPermit(
            packageName = targetPackage,
            sessionId = SessionId(100L, 1L, 1L),
            expiresElapsedMs = 600_000L
        )
        val runtimeWithPermitAndExit = context.runtimeState.copy(
            sessionPermits = mapOf(targetPackage to permit),
            lastExitElapsedMs = mapOf(targetPackage to 490_000L)
        )

        val interveningState = ProtectionState.Intervening(
            session = ActiveSession(SessionId(100L, 1L, 1L), targetPackage, 1, "att-1"),
            substate = io.ronesec.domain.protection.InterveningSubstate.AwaitingAttachment
        )

        // 1. Screen Off while Intervening
        val screenOffResult = ProtectionReducer.reduce(
            interveningState,
            ProtectionEvent.ScreenOff,
            context.copy(runtimeState = runtimeWithPermitAndExit)
        )

        assertTrue("Must be Suspended", screenOffResult.newState is ProtectionState.Suspended)
        assertTrue("Suspended with locked=true", (screenOffResult.newState as ProtectionState.Suspended).locked)

        // Resources released
        assertTrue("DismissOverlay dispatched", screenOffResult.effects.any { it is ProtectionEffect.DismissOverlay })
        assertTrue("ReleaseAudioLease dispatched", screenOffResult.effects.any { it is ProtectionEffect.ReleaseAudioLease })

        // RAM session permits and Quick Return grace cleared
        assertTrue("Session permits must be cleared on ScreenOff", screenOffResult.updatedRuntimeState.sessionPermits.isEmpty())
        assertTrue("Quick Return exits must be cleared on ScreenOff", screenOffResult.updatedRuntimeState.lastExitElapsedMs.isEmpty())

        // 2. Screen On while locked: remains suspended, no work/animation
        val screenOnLockedResult = ProtectionReducer.reduce(
            screenOffResult.newState,
            ProtectionEvent.ScreenOnLocked,
            context.copy(runtimeState = screenOffResult.updatedRuntimeState)
        )
        assertTrue("Remains Suspended while locked", screenOnLockedResult.newState is ProtectionState.Suspended)
        assertTrue(screenOnLockedResult.effects.isEmpty())

        // 3. Screen Unlocked: enters Idle and requests bounded resync
        val unlockResult = ProtectionReducer.reduce(
            screenOnLockedResult.newState,
            ProtectionEvent.ScreenUnlocked(generation = 7L),
            context.copy(runtimeState = screenOnLockedResult.updatedRuntimeState)
        )
        assertTrue("State becomes Idle on unlock", unlockResult.newState is ProtectionState.Idle)
        val resync = unlockResult.effects.single() as ProtectionEffect.ResyncRequired
        assertEquals("Unlock resync must use the live service generation", 7L, resync.generation)
    }

    @Test
    fun serviceDisconnected_entersUnavailable_andClearsTransientRAM() {
        val permit = SessionPermit(
            packageName = targetPackage,
            sessionId = SessionId(100L, 1L, 1L),
            expiresElapsedMs = 600_000L
        )
        val runtimeWithPermit = context.runtimeState.copy(
            sessionPermits = mapOf(targetPackage to permit),
            lastExitElapsedMs = mapOf(targetPackage to 490_000L)
        )

        val interveningState = ProtectionState.Intervening(
            session = ActiveSession(SessionId(100L, 1L, 1L), targetPackage, 1, "att-1"),
            substate = io.ronesec.domain.protection.InterveningSubstate.AwaitingAttachment
        )

        val disconnectResult = ProtectionReducer.reduce(
            interveningState,
            ProtectionEvent.ServiceDisconnected,
            context.copy(runtimeState = runtimeWithPermit)
        )

        assertTrue("State must be Unavailable", disconnectResult.newState is ProtectionState.Unavailable)
        assertTrue("DismissOverlay dispatched", disconnectResult.effects.any { it is ProtectionEffect.DismissOverlay })
        assertTrue("ReleaseAudioLease dispatched", disconnectResult.effects.any { it is ProtectionEffect.ReleaseAudioLease })
        assertTrue("Permits cleared", disconnectResult.updatedRuntimeState.sessionPermits.isEmpty())
        assertTrue("Exits cleared", disconnectResult.updatedRuntimeState.lastExitElapsedMs.isEmpty())

        // Service Connected with new generation
        val connectResult = ProtectionReducer.reduce(
            disconnectResult.newState,
            ProtectionEvent.ServiceConnected(generation = 2L),
            context.copy(runtimeState = disconnectResult.updatedRuntimeState)
        )
        assertTrue("State enters Loading on reconnect", connectResult.newState is ProtectionState.Loading)
    }

    @Test
    fun processDeathSimulation_noResurrectedSessionOrRAMPermits() {
        // After process death, only DB snapshot is restored; RAM state starts fresh and empty
        val freshRestoredState = RuntimeState(baseSnapshot) // empty sessionPermits, empty lastExitElapsedMs

        // Target opened after restart
        val entryResult = ProtectionReducer.reduce(
            ProtectionState.Idle,
            ProtectionEvent.ForegroundCandidate(targetPackage, 1000L, 1L),
            context.copy(runtimeState = freshRestoredState)
        )

        // Must require full intervention! No resurrected permits or bypass
        assertTrue("Must intervene after restart", entryResult.newState is ProtectionState.Intervening)
        assertEquals(1, (entryResult.newState as ProtectionState.Intervening).session.cycle)
    }
}
