package io.ronesec.android.platform.system

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import io.ronesec.android.WattimApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class T11_BootReceiverTest {

    private lateinit var app: WattimApplication
    private lateinit var receiver: BootReceiver
    private lateinit var fakeChecker: FakePlatformPermissionChecker

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        fakeChecker = FakePlatformPermissionChecker()
        app.customPermissionChecker = fakeChecker
        receiver = BootReceiver()
        FocusForegroundService.stop(app)
    }

    @Test
    fun bootCompletedRehydratesPolicyStoreAndStartsFgsWhenPermissionsIntact() {
        fakeChecker.accessibilityEnabled = true
        fakeChecker.overlayAllowed = true
        fakeChecker.batteryOptimizationIgnored = true

        val intent = Intent(Intent.ACTION_BOOT_COMPLETED)
        receiver.onReceive(app, intent)

        // Snapshot must be accessible and FGS must be starting/running
        val snapshot = app.policyStore.currentSnapshot
        org.junit.Assert.assertNotNull(snapshot)
        assertNotEquals(FgsStatus.Stopped, FocusForegroundService.status.value)
    }

    @Test
    fun myPackageReplacedRehydratesPolicyStoreAndStartsFgsWhenPermissionsIntact() {
        fakeChecker.accessibilityEnabled = true
        fakeChecker.overlayAllowed = true
        fakeChecker.batteryOptimizationIgnored = true

        val intent = Intent(Intent.ACTION_MY_PACKAGE_REPLACED)
        receiver.onReceive(app, intent)

        val snapshot = app.policyStore.currentSnapshot
        org.junit.Assert.assertNotNull(snapshot)
        assertNotEquals(FgsStatus.Stopped, FocusForegroundService.status.value)
    }

    @Test
    fun bootWithoutPermissionsDoesNotStartFgsAndNeverFakesAccessibility() {
        fakeChecker.accessibilityEnabled = false
        fakeChecker.overlayAllowed = false
        fakeChecker.batteryOptimizationIgnored = false

        // Stop FGS first if running
        FocusForegroundService.stop(app)

        val intent = Intent(Intent.ACTION_BOOT_COMPLETED)
        receiver.onReceive(app, intent)

        // Accessibility must remain disabled (user/OS controlled)
        val permSnapshot = app.permissionMonitor.statusFlow.value
        assertEquals(PermissionState.Denied, permSnapshot.accessibility)
        assertEquals(FgsStatus.Stopped, FocusForegroundService.status.value)
    }

    @Test
    fun unrelatedActionIsIgnored() {
        FocusForegroundService.stop(app)
        val intent = Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED)
        receiver.onReceive(app, intent)

        assertEquals(FgsStatus.Stopped, FocusForegroundService.status.value)
    }
}
