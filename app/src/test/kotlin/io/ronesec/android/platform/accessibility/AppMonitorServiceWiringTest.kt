package io.ronesec.android.platform.accessibility

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import io.ronesec.android.WattimApplication
import io.ronesec.domain.model.TargetConfig
import io.ronesec.domain.protection.ProtectionEvent
import io.ronesec.domain.protection.ProtectionState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowSettings
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AppMonitorServiceWiringTest {

    @Test
    fun serviceLifecycle_createsAndTearsDownOverlayHostAndAudioGuard() {
        val serviceController = Robolectric.buildService(AppMonitorService::class.java)
        val service = serviceController.get()

        assertNull(service.overlayHost)
        assertNull(service.audioGuard)

        serviceController.create()
        service.onServiceConnected()

        assertNotNull("OverlayHost should be created on service connected", service.overlayHost)
        assertNotNull("AudioGuard should be created on service connected", service.audioGuard)

        serviceController.destroy()

        assertNull("OverlayHost should be null after destroy", service.overlayHost)
        assertNull("AudioGuard should be null after destroy", service.audioGuard)
    }

    @Test
    fun targetEventReachesWindowManagerOnMainDispatcher() {
        ShadowSettings.setCanDrawOverlays(true)
        val serviceController = Robolectric.buildService(AppMonitorService::class.java).create()
        val service = serviceController.get()
        val app = service.application as WattimApplication
        val targetPackage = "com.example.main-thread-target"
        runBlocking {
            app.policyStore.awaitReady()
            app.policyStore.saveTarget(
                TargetConfig(
                    packageName = targetPackage,
                    displayName = "Main thread target",
                    durationMs = 8_000L
                )
            ).getOrThrow()
        }

        service.onServiceConnected()
        shadowOf(Looper.getMainLooper()).idle()
        service.coordinator!!.onForegroundCandidate(
            ProtectionEvent.ForegroundCandidate(
                packageName = targetPackage,
                sourceUptimeMs = 1_000L,
                eventSequence = 1L
            )
        )

        assertTrue(
            "state=${service.coordinator!!.protectionState.value}; " +
                    "journal=${service.coordinator!!.journal.getEntries()}",
            service.overlayHost!!.isWindowAttached
        )
        val startedIntent = shadowOf(service).nextStartedActivity
        assertNotNull("InterventionActivity should be started on target event", startedIntent)
        org.junit.Assert.assertEquals(
            io.ronesec.android.ui.intervention.InterventionActivity::class.java.name,
            startedIntent.component?.className
        )
        assertTrue(app.permissionMonitor.statusFlow.value.isProtectionOperational)

        serviceController.destroy()
    }

    @Test
    fun requestResync_fallsBackToLastPackage_whenRootInActiveWindowIsNull() {
        val serviceController = Robolectric.buildService(AppMonitorService::class.java).create()
        val service = serviceController.get()
        val app = service.application as WattimApplication
        val targetPackage = "org.telegram.messenger"

        runBlocking {
            app.policyStore.awaitReady()
            app.policyStore.saveTarget(
                TargetConfig(
                    packageName = targetPackage,
                    displayName = "Telegram",
                    durationMs = 8_000L
                )
            ).getOrThrow()
        }

        service.onServiceConnected()
        shadowOf(Looper.getMainLooper()).idle()

        // Send window state changed event to establish lastConfirmedPackage
        val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED).apply {
            packageName = targetPackage
        }
        service.onAccessibilityEvent(event)
        shadowOf(Looper.getMainLooper()).idle()

        // Turn screen off -> state becomes Suspended
        service.coordinator!!.onScreenOff()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(service.coordinator!!.protectionState.value is ProtectionState.Suspended)

        println("DEBUG: before onScreenUnlocked, state = ${service.coordinator!!.protectionState.value}")
        service.coordinator!!.onScreenUnlocked()
        shadowOf(Looper.getMainLooper()).idle()
        println("DEBUG: after 1st idle, state = ${service.coordinator!!.protectionState.value}")
        shadowOf(Looper.getMainLooper()).idle()
        println("DEBUG: after 2nd idle, state = ${service.coordinator!!.protectionState.value}")

        // Because rootInActiveWindow is null, requestResync should fall back to lastPackage (Telegram)
        // and trigger an intervention!
        assertTrue(
            "Expected Intervening state after unlock resync fallback, was ${service.coordinator!!.protectionState.value}",
            service.coordinator!!.protectionState.value is ProtectionState.Intervening
        )

        serviceController.destroy()
    }

    @Test
    fun screenReceiver_whenKeyguardUnlocked_screenOnImmediatelyUnlocks() {
        val serviceController = Robolectric.buildService(AppMonitorService::class.java).create()
        val service = serviceController.get()
        service.onServiceConnected()
        shadowOf(Looper.getMainLooper()).idle()

        val km = service.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        shadowOf(km).setKeyguardLocked(false)

        // Turn screen off
        service.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF))
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(service.coordinator!!.protectionState.value is ProtectionState.Suspended)

        // Turn screen on while keyguard is unlocked -> should immediately unlock to Idle
        service.sendBroadcast(Intent(Intent.ACTION_SCREEN_ON))
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(
            "Expected Idle when keyguard is unlocked on screen on, was ${service.coordinator!!.protectionState.value}",
            service.coordinator!!.protectionState.value is ProtectionState.Idle
        )

        serviceController.destroy()
    }

    @Test
    fun screenReceiver_whenKeyguardLocked_screenOnWaitsForUserPresent() {
        val serviceController = Robolectric.buildService(AppMonitorService::class.java).create()
        val service = serviceController.get()
        service.onServiceConnected()
        shadowOf(Looper.getMainLooper()).idle()

        val km = service.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        shadowOf(km).setKeyguardLocked(true)

        // Turn screen off
        service.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF))
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(service.coordinator!!.protectionState.value is ProtectionState.Suspended)

        // Turn screen on while keyguard is locked -> must remain Suspended
        service.sendBroadcast(Intent(Intent.ACTION_SCREEN_ON))
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(
            "Expected Suspended while keyguard is locked on screen on, was ${service.coordinator!!.protectionState.value}",
            service.coordinator!!.protectionState.value is ProtectionState.Suspended
        )

        // User unlocks device -> ACTION_USER_PRESENT -> transitions to Idle
        service.sendBroadcast(Intent(Intent.ACTION_USER_PRESENT))
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(
            "Expected Idle after ACTION_USER_PRESENT, was ${service.coordinator!!.protectionState.value}",
            service.coordinator!!.protectionState.value is ProtectionState.Idle
        )

        serviceController.destroy()
    }
}
