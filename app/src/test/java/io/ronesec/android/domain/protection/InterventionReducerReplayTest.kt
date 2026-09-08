package io.ronesec.android.domain.protection

import io.ronesec.android.domain.engine.RuntimeState
import io.ronesec.android.domain.model.AttemptOutcome
import io.ronesec.android.domain.model.InterventionConfig
import io.ronesec.android.domain.model.TargetApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class InterventionReducerReplayTest {

    private val targetPackage = "com.zhiliaoapp.musically"
    private val launcherPackage = "com.google.android.apps.nexuslauncher"
    private val otherPackage = "com.instagram.android"

    private val snapshot = RuntimeState(
        targets = mapOf(
            targetPackage to TargetApp(
                packageName = targetPackage,
                displayName = "TikTok",
                enabled = true,
                intervention = InterventionConfig(durationMs = 10000L)
            ),
            otherPackage to TargetApp(
                packageName = otherPackage,
                displayName = "Instagram",
                enabled = true,
                intervention = InterventionConfig(durationMs = 10000L)
            )
        )
    )

    @Test
    fun testTikTokExitStaleEventRace() {
        val now = Instant.now()
        var state: ProtectionState = ProtectionState.Idle

        // 1. TikTok opened -> triggers Intervention
        val t1 = InterventionReducer.reduce(
            state = state,
            event = ProtectionEvent.ForegroundChanged(targetPackage, now),
            snapshot = snapshot,
            isPolicyReady = true,
            nextSessionId = SessionId(1L)
        )
        state = t1.state
        assertTrue(state is ProtectionState.Intervening)
        val session1 = (state as ProtectionState.Intervening).session
        assertEquals(1L, session1.id.value)
        assertTrue(t1.effects.any { it is ProtectionEffect.ShowInterventionOverlay })

        // 2. User presses Exit / Close button
        val t2 = InterventionReducer.reduce(
            state = state,
            event = ProtectionEvent.UserAction(
                sessionId = session1.id,
                targetPackage = targetPackage,
                action = UserProtectionAction.Close,
                timestamp = now.plusSeconds(1)
            ),
            snapshot = snapshot,
            isPolicyReady = true
        )
        state = t2.state
        assertTrue(state is ProtectionState.Exiting)
        assertTrue(t2.effects.any { it is ProtectionEffect.PerformGlobalHome })
        assertTrue(t2.effects.any { it is ProtectionEffect.PersistAttempt && it.outcome == AttemptOutcome.ABANDONED })

        // 3. Stale ForegroundChanged from TikTok arrives while navigating Home
        val t3 = InterventionReducer.reduce(
            state = state,
            event = ProtectionEvent.ForegroundChanged(targetPackage, now.plusMillis(1100)),
            snapshot = snapshot,
            isPolicyReady = true
        )
        state = t3.state
        // Must remain in Exiting; MUST NOT re-show overlay!
        assertTrue(state is ProtectionState.Exiting)
        assertTrue("Stale event during home transition must produce no effects", t3.effects.isEmpty())
        assertFalse(t3.effects.any { it is ProtectionEffect.ShowInterventionOverlay })

        // 4. Launcher enters foreground -> returns to Idle
        val t4 = InterventionReducer.reduce(
            state = state,
            event = ProtectionEvent.ForegroundChanged(launcherPackage, now.plusMillis(1300)),
            snapshot = snapshot,
            isPolicyReady = true
        )
        state = t4.state
        assertTrue(state is ProtectionState.Idle)
        assertTrue(t4.effects.any { it is ProtectionEffect.DismissOverlay })
        assertTrue(t4.effects.any { it is ProtectionEffect.ReleaseAudio })
    }

    @Test
    fun testStaleSession1EventDuringSession2Ignored() {
        val now = Instant.now()
        // Session 2 is active on Instagram
        val session2 = TargetSession(SessionId(2L), otherPackage, now)
        val state: ProtectionState = ProtectionState.Intervening(session2)

        // Stale event from old Session 1 on TikTok arrives
        val t = InterventionReducer.reduce(
            state = state,
            event = ProtectionEvent.UserAction(
                sessionId = SessionId(1L),
                targetPackage = targetPackage,
                action = UserProtectionAction.Continue,
                timestamp = now.plusSeconds(2)
            ),
            snapshot = snapshot,
            isPolicyReady = true
        )

        // Must remain in Session 2, and stale action must be completely ignored
        assertEquals(state, t.state)
        assertTrue(t.effects.isEmpty())
    }

    @Test
    fun testScreenOffDuringInterventionReleasesResources() {
        val now = Instant.now()
        val session = TargetSession(SessionId(1L), targetPackage, now)
        val state: ProtectionState = ProtectionState.Intervening(session)

        val t = InterventionReducer.reduce(
            state = state,
            event = ProtectionEvent.ScreenOff,
            snapshot = snapshot,
            isPolicyReady = true
        )

        assertTrue(t.state is ProtectionState.Suspended)
        assertTrue(t.effects.any { it is ProtectionEffect.DismissOverlay })
        assertTrue(t.effects.any { it is ProtectionEffect.ReleaseAudio })
        assertTrue(t.effects.any { it is ProtectionEffect.CancelBoundary })
    }

    @Test
    fun testServiceInterruptedReleasesResources() {
        val now = Instant.now()
        val session = TargetSession(SessionId(1L), targetPackage, now)
        val state: ProtectionState = ProtectionState.Intervening(session)

        val t = InterventionReducer.reduce(
            state = state,
            event = ProtectionEvent.ServiceInterrupted,
            snapshot = snapshot,
            isPolicyReady = true
        )

        assertTrue(t.state is ProtectionState.Idle)
        assertTrue(t.effects.any { it is ProtectionEffect.DismissOverlay })
        assertTrue(t.effects.any { it is ProtectionEffect.ReleaseAudio })
        assertTrue(t.effects.any { it is ProtectionEffect.CancelBoundary })
    }

    @Test
    fun testTimedGrantExpiryTriggersReintervention() {
        val now = Instant.now()
        val session = TargetSession(SessionId(1L), targetPackage, now)
        val state: ProtectionState = ProtectionState.Granted(session, AccessPermit.Timed(now.plusSeconds(900)))

        val t = InterventionReducer.reduce(
            state = state,
            event = ProtectionEvent.TemporalBoundaryReached(
                sessionId = session.id,
                targetPackage = targetPackage,
                boundaryType = BoundaryType.TIMED_PERMIT_EXPIRY,
                timestamp = now.plusSeconds(900)
            ),
            snapshot = snapshot,
            isPolicyReady = true
        )

        assertTrue(t.state is ProtectionState.Intervening)
        assertTrue(t.effects.any { it is ProtectionEffect.ShowInterventionOverlay })
        assertTrue(t.effects.any { it is ProtectionEffect.AcquireAudio })
    }
}
