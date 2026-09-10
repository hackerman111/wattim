package io.ronesec.android.platform.system

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

class FakePlatformPermissionChecker(
    var accessibilityEnabled: Boolean = false,
    var overlayAllowed: Boolean = false,
    var batteryOptimizationIgnored: Boolean = false,
    var notificationsEnabled: Boolean = false,
    var mediaControlEnabled: Boolean = false
) : PlatformPermissionChecker {
    override fun isAccessibilityEnabled(): Boolean = accessibilityEnabled
    override fun isMediaControlEnabled(): Boolean = mediaControlEnabled
    override fun canDrawOverlays(): Boolean = overlayAllowed
    override fun isIgnoringBatteryOptimizations(): Boolean = batteryOptimizationIgnored
    override fun areNotificationsEnabled(): Boolean = notificationsEnabled
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class T12_PermissionMonitorAndFgsTest {

    private lateinit var context: Context
    private lateinit var fakeChecker: FakePlatformPermissionChecker
    private lateinit var permissionMonitor: PermissionMonitor

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        fakeChecker = FakePlatformPermissionChecker()
        permissionMonitor = PermissionMonitor(fakeChecker)
    }

    @Test
    fun permissionMonitorEvaluatesDefaultDeniedState() = runTest {
        val snapshot = permissionMonitor.statusFlow.first()
        assertEquals(PermissionState.Denied, snapshot.accessibility)
        assertEquals(PermissionState.Denied, snapshot.mediaControl)
        assertEquals(PermissionState.Denied, snapshot.overlay)
        assertEquals(PermissionState.Denied, snapshot.batteryExemption)
        assertEquals(PermissionState.Denied, snapshot.notifications)
        assertFalse(snapshot.areRequiredPermissionsGranted)
        assertFalse(snapshot.isProtectionActive)
        assertEquals(OnboardingStep.Accessibility, snapshot.firstMissingStep)
    }

    @Test
    fun firstMissingStepAdvancesInStrictOrder() {
        // Step 1: Accessibility missing
        fakeChecker.accessibilityEnabled = false
        fakeChecker.overlayAllowed = false
        fakeChecker.batteryOptimizationIgnored = false
        assertEquals(OnboardingStep.Accessibility, permissionMonitor.refresh().firstMissingStep)

        // Step 2: Accessibility granted -> Overlay missing
        fakeChecker.accessibilityEnabled = true
        assertEquals(OnboardingStep.Overlay, permissionMonitor.refresh().firstMissingStep)

        // Step 3: Overlay granted -> Battery missing
        fakeChecker.overlayAllowed = true
        assertEquals(OnboardingStep.BatteryExemption, permissionMonitor.refresh().firstMissingStep)

        // All granted -> No missing step (Ready)
        fakeChecker.batteryOptimizationIgnored = true
        val readySnapshot = permissionMonitor.refresh()
        assertNull(readySnapshot.firstMissingStep)
        assertTrue(readySnapshot.areRequiredPermissionsGranted)
    }

    @Test
    fun notificationsDenialDoesNotBlockRequiredPermissions_F22() {
        fakeChecker.accessibilityEnabled = true
        fakeChecker.overlayAllowed = true
        fakeChecker.batteryOptimizationIgnored = true
        fakeChecker.notificationsEnabled = false

        val snapshot = permissionMonitor.refresh()
        assertEquals(PermissionState.Denied, snapshot.notifications)
        assertTrue("Notifications denial must not block areRequiredPermissionsGranted", snapshot.areRequiredPermissionsGranted)
        assertNull(snapshot.firstMissingStep)
    }

    @Test
    fun mediaControlAccessIsReportedButDoesNotBlockCoreProtection() {
        fakeChecker.accessibilityEnabled = true
        fakeChecker.overlayAllowed = true
        fakeChecker.batteryOptimizationIgnored = true
        fakeChecker.mediaControlEnabled = false

        val withoutMediaControl = permissionMonitor.refresh()
        assertEquals(PermissionState.Denied, withoutMediaControl.mediaControl)
        assertTrue(withoutMediaControl.areRequiredPermissionsGranted)

        fakeChecker.mediaControlEnabled = true
        assertEquals(PermissionState.Granted, permissionMonitor.refresh().mediaControl)
    }

    @Test
    fun accessibilityConnectedDistinctionIsRespected() {
        fakeChecker.accessibilityEnabled = true
        fakeChecker.overlayAllowed = true
        fakeChecker.batteryOptimizationIgnored = true
        permissionMonitor.setFgsStatus(FgsStatus.Running)

        var snapshot = permissionMonitor.refresh()
        assertTrue(snapshot.areRequiredPermissionsGranted)
        assertFalse("Protection must not be active without accessibility connection", snapshot.isProtectionActive)

        permissionMonitor.setAccessibilityConnected(true)
        permissionMonitor.setProtectionOperational(true)
        snapshot = permissionMonitor.statusFlow.value
        assertTrue(snapshot.isAccessibilityConnected)
        assertTrue("Protection active when required granted, connected, and FGS running", snapshot.isProtectionActive)

        // Revocation of accessibility also resets connection
        fakeChecker.accessibilityEnabled = false
        val revoked = permissionMonitor.refresh()
        assertEquals(PermissionState.Denied, revoked.accessibility)
        assertFalse(revoked.isAccessibilityConnected)
        assertFalse(revoked.isProtectionActive)
    }

    @Test
    fun internalProtectionFailureMakesStatusDegradedDespiteGrantedPermissions() {
        fakeChecker.accessibilityEnabled = true
        fakeChecker.overlayAllowed = true
        fakeChecker.batteryOptimizationIgnored = true
        permissionMonitor.refresh()
        permissionMonitor.setAccessibilityConnected(true)
        permissionMonitor.setProtectionOperational(true)
        permissionMonitor.setFgsStatus(FgsStatus.Running)
        assertTrue(permissionMonitor.statusFlow.value.isProtectionActive)

        permissionMonitor.setProtectionOperational(false)

        val failed = permissionMonitor.statusFlow.value
        assertFalse(failed.isProtectionOperational)
        assertFalse(failed.isProtectionActive)
    }

    @Test
    fun focusForegroundServiceCreatesLowPriorityOngoingNotification() {
        val serviceController = Robolectric.buildService(FocusForegroundService::class.java)
        val service = serviceController.create().startCommand(0, 0).get()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowNm = shadowOf(notificationManager)

        val channel = notificationManager.getNotificationChannel(FocusForegroundService.CHANNEL_ID)
        assertNotNull("Protection status notification channel must exist", channel)
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)

        val notification = shadowNm.getNotification(FocusForegroundService.NOTIFICATION_ID)
        assertNotNull("Protection status notification must be posted", notification)
        assertTrue("Status notification must be ongoing", notification.flags and android.app.Notification.FLAG_ONGOING_EVENT != 0)

        serviceController.destroy()
        assertEquals(FgsStatus.Stopped, FocusForegroundService.status.value)
    }
}
