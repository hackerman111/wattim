package io.ronesec.android.platform.accessibility

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.view.accessibility.AccessibilityEvent
import androidx.test.core.app.ApplicationProvider
import io.ronesec.domain.model.*
import io.ronesec.domain.protection.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Instant
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class HomeCodeRoundTripTest {
    @Test fun systemResolvedHomePreservesCodeUntilTargetReturn() {
        val pm = ApplicationProvider.getApplicationContext<Context>().packageManager
        val home = "org.example.custom.desktop"
        shadowOf(pm).addResolveInfoForIntent(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            ResolveInfo().apply {
                activityInfo = ActivityInfo().apply {
                    packageName = home
                    name = "$home.HomeActivity"
                }
            }
        )
        val tracker = ForegroundTracker()
        assertFalse(tracker.isLauncher(home))
        pm.homePackages().forEach(tracker::addKnownLauncher)
        assertTrue(tracker.isLauncher(home))

        val target = "protected"
        var context = ReducerContext(
            Instant.EPOCH, 1000L, ZoneId.of("UTC"),
            RuntimeState(RuntimePolicySnapshot(1, mapOf(target to TargetConfig(
                target, "Target", twoStageUnlock = true
            )), emptyMap(), emptyList(), emptyList(), GlobalPause.None)),
            nextSessionId = { SessionId(1, 1, 1) }
        )
        var state: ProtectionState = ProtectionState.Idle
        fun step(event: ProtectionEvent) {
            val result = ProtectionReducer.reduce(state, event, context)
            state = result.newState
            context = context.copy(runtimeState = result.updatedRuntimeState)
        }
        step(ProtectionEvent.ForegroundCandidate(target, 1, 1))
        val session = (state as ProtectionState.Intervening).session
        step(ProtectionEvent.OverlayAttached(session.sessionId, session.cycle))
        step(ProtectionEvent.GenerateUnlockCode(session.sessionId, session.cycle))
        val code = (state as ProtectionState.Intervening).codes.unlockCode!!
        step(ProtectionEvent.CodePanelShown(session.sessionId, session.cycle,
            (state as ProtectionState.Intervening).codes.unlockRequestRevision))
        step(tracker.normalizeEvent(RawAccessibilityPayload(
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, home, uptimeMs = 2
        ))!!)
        assertEquals(code, (state as ProtectionState.Intervening).codes.unlockCode)
        step(ProtectionEvent.ForegroundCandidate(target, 3, 3))
        assertTrue((state as ProtectionState.Intervening).codeChallengeUi().generated)
        step(ProtectionEvent.SubmitUnlockCode(session.sessionId, session.cycle, code))
        assertTrue((state as ProtectionState.Intervening).substate is InterveningSubstate.Breathing)
    }
}
