package io.ronesec.android.ui

import androidx.test.core.app.ApplicationProvider
import io.ronesec.android.platform.system.FakePlatformPermissionChecker
import io.ronesec.android.platform.system.OnboardingStep
import io.ronesec.android.platform.system.PermissionMonitor
import io.ronesec.android.ui.designsystem.TerminalTab
import io.ronesec.android.ui.onboarding.OnboardingStepState
import io.ronesec.android.ui.onboarding.OnboardingViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class T16_NavigationAndOnboardingReachabilityTest {

    private lateinit var fakeChecker: FakePlatformPermissionChecker
    private lateinit var permissionMonitor: PermissionMonitor
    private lateinit var onboardingViewModel: OnboardingViewModel

    @Before
    fun setUp() {
        fakeChecker = FakePlatformPermissionChecker()
        permissionMonitor = PermissionMonitor(fakeChecker)
        onboardingViewModel = OnboardingViewModel(permissionMonitor)
    }

    @Test
    fun onboardingStartsAtFirstMissingStep() = runTest {
        // Step 1: Accessibility
        var state = onboardingViewModel.uiState.value
        assertEquals(OnboardingStepState.Permission(OnboardingStep.Accessibility), state.stepState)
        assertEquals("01/03", state.currentStepNumber)

        // Grant Accessibility -> Step 2
        fakeChecker.accessibilityEnabled = true
        permissionMonitor.refresh()
        state = onboardingViewModel.uiState.first { it.stepState == OnboardingStepState.Permission(OnboardingStep.Overlay) }
        assertEquals("02/03", state.currentStepNumber)

        // Grant Overlay -> Step 3
        fakeChecker.overlayAllowed = true
        permissionMonitor.refresh()
        state = onboardingViewModel.uiState.first { it.stepState == OnboardingStepState.Permission(OnboardingStep.BatteryExemption) }
        assertEquals("03/03", state.currentStepNumber)

        // Grant Battery -> SYSTEM READY
        fakeChecker.batteryOptimizationIgnored = true
        permissionMonitor.refresh()
        state = onboardingViewModel.uiState.first { it.stepState is OnboardingStepState.SystemReady }
        assertEquals("READY", state.currentStepNumber)
        assertTrue(state.permissionSnapshot.areRequiredPermissionsGranted)
    }

    @Test
    fun onboardingAutoReturnsToRevokedStep() = runTest {
        // Grant all initially -> READY
        fakeChecker.accessibilityEnabled = true
        fakeChecker.overlayAllowed = true
        fakeChecker.batteryOptimizationIgnored = true
        permissionMonitor.refresh()

        var state = onboardingViewModel.uiState.first { it.stepState is OnboardingStepState.SystemReady }
        assertEquals(OnboardingStepState.SystemReady, state.stepState)

        // Revoke Overlay -> must return to Step 2 (02/03)
        fakeChecker.overlayAllowed = false
        permissionMonitor.refresh()
        state = onboardingViewModel.uiState.first { it.stepState == OnboardingStepState.Permission(OnboardingStep.Overlay) }
        assertEquals("02/03", state.currentStepNumber)
    }

    @Test
    fun onboardingReadyCompletionAcknowledges() = runTest {
        fakeChecker.accessibilityEnabled = true
        fakeChecker.overlayAllowed = true
        fakeChecker.batteryOptimizationIgnored = true
        permissionMonitor.refresh()

        assertFalse(onboardingViewModel.uiState.value.isReadyAcknowledged)
        onboardingViewModel.onCompleteReady()
        assertTrue(onboardingViewModel.uiState.value.isReadyAcknowledged)
    }

    @Test
    fun appRouteAllTabsReachableAndSerializable() {
        for (tab in TerminalTab.entries) {
            val route = AppRoute.Main(tab)
            val bundle = AppRoute.toBundle(route)
            val restored = AppRoute.fromBundle(bundle)

            assertTrue(restored is AppRoute.Main)
            assertEquals(tab, (restored as AppRoute.Main).tab)
        }
    }

    @Test
    fun appRouteDetailPreservesPackageAndOriginTab() {
        val detailRoute = AppRoute.Detail(
            packageName = "com.sample.target",
            originTab = TerminalTab.BLOCK
        )
        val bundle = AppRoute.toBundle(detailRoute)
        val restored = AppRoute.fromBundle(bundle)

        assertTrue(restored is AppRoute.Detail)
        val restoredDetail = restored as AppRoute.Detail
        assertEquals("com.sample.target", restoredDetail.packageName)
        assertEquals(TerminalTab.BLOCK, restoredDetail.originTab)
    }

    @Test
    fun appRouteDetailPreservesCanQuickLock() {
        val detailRoute = AppRoute.Detail(
            packageName = "com.sample.target",
            originTab = TerminalTab.APPS,
            canQuickLock = true
        )
        val bundle = AppRoute.toBundle(detailRoute)
        val restored = AppRoute.fromBundle(bundle)

        assertTrue(restored is AppRoute.Detail)
        val restoredDetail = restored as AppRoute.Detail
        assertEquals("com.sample.target", restoredDetail.packageName)
        assertEquals(TerminalTab.APPS, restoredDetail.originTab)
        assertTrue(restoredDetail.canQuickLock)
    }

    @Test
    fun appRouteOnboardingSerializable() {
        val bundle = AppRoute.toBundle(AppRoute.Onboarding)
        val restored = AppRoute.fromBundle(bundle)
        assertEquals(AppRoute.Onboarding, restored)
    }
}
