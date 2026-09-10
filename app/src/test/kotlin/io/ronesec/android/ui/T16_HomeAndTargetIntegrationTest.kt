package io.ronesec.android.ui

import android.content.Context
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import io.ronesec.android.data.PolicyStore
import io.ronesec.android.data.StatisticsStore
import io.ronesec.android.data.WattimDatabase
import io.ronesec.android.platform.system.FakePlatformPermissionChecker
import io.ronesec.android.platform.system.PackageAppEntry
import io.ronesec.android.platform.system.PackageCatalog
import io.ronesec.android.platform.system.PermissionMonitor
import io.ronesec.android.platform.system.SettingsIntentAdapter
import io.ronesec.android.ui.designsystem.TerminalTab
import io.ronesec.android.ui.designsystem.WattimTheme
import io.ronesec.domain.model.AnimationMode
import io.ronesec.domain.model.FakeWallClock
import io.ronesec.domain.model.GlobalPause
import io.ronesec.domain.model.TargetConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class T16_HomeAndTargetIntegrationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private lateinit var database: WattimDatabase
    private lateinit var wallClock: FakeWallClock
    private lateinit var policyStore: PolicyStore
    private lateinit var statisticsStore: StatisticsStore
    private lateinit var packageCatalog: FakePackageCatalog
    private lateinit var permissionChecker: FakePlatformPermissionChecker
    private lateinit var permissionMonitor: PermissionMonitor
    private lateinit var settingsAdapter: SettingsIntentAdapter

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = WattimDatabase.createInMemory(context)
        wallClock = FakeWallClock(Instant.parse("2026-09-09T14:30:00Z"))
        policyStore = PolicyStore(database, wallClock, applicationScope, Dispatchers.IO)
        statisticsStore = StatisticsStore(database, wallClock, Dispatchers.IO)
        packageCatalog = FakePackageCatalog()
        permissionChecker = FakePlatformPermissionChecker().apply {
            accessibilityEnabled = true
            overlayAllowed = true
            batteryOptimizationIgnored = true
        }
        permissionMonitor = PermissionMonitor(permissionChecker)
        settingsAdapter = SettingsIntentAdapter(context)

        runBlocking {
            policyStore.awaitReady()
        }
    }

    @After
    fun tearDown() {
        applicationScope.cancel()
        database.close()
    }

    @Test
    fun homeScreenRendersHeaderListHeroAndPause() {
        val target = TargetConfig(
            packageName = "com.sample.social",
            displayName = "Social App",
            enabled = true,
            durationMs = 8000L
        )
        runBlocking {
            policyStore.saveTarget(target)
        }

        composeTestRule.setContent {
            WattimTheme {
                WattimNavHost(
                    permissionMonitor = permissionMonitor,
                    settingsAdapter = settingsAdapter,
                    initialRoute = AppRoute.Main(TerminalTab.APPS),
                    onStartFgs = {},
                    policyStore = policyStore,
                    statisticsStore = statisticsStore,
                    packageCatalog = packageCatalog,
                    wallClock = wallClock
                )
            }
        }

        // Wait until policy snapshot loads in HomeViewModel and updates the app_1 count (F44)
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodes(hasText("PROTECTED APPS")).fetchSemanticsNodes().isNotEmpty() &&
                composeTestRule.onAllNodes(hasText("01")).fetchSemanticsNodes().isNotEmpty()
        }

        // Home Header (F44) - app_name is "wattim", time is "14:30"
        composeTestRule.onNodeWithText("wattim").assertIsDisplayed()
        composeTestRule.onNodeWithText("14:30").assertIsDisplayed()
        composeTestRule.onNodeWithText("PROTECTED APPS").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("01").assertIsDisplayed()

        // Hero Card (F48)
        composeTestRule.onNodeWithText("TODAY").performScrollTo().assertIsDisplayed()

        // Row item (F44: first >, two-digit row 01, name, duration/animation summary)
        composeTestRule.onNodeWithText("> ").assertIsDisplayed()
        composeTestRule.onNodeWithText("Social App").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("8 SEC · FILL").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun homeScreenQuickTogglePersistsWithoutOpeningDetail() {
        val target = TargetConfig(
            packageName = "com.sample.chat",
            displayName = "Chat App",
            enabled = true,
            durationMs = 10000L
        )
        runBlocking {
            policyStore.saveTarget(target)
        }

        var currentNavRoute: AppRoute? = null

        composeTestRule.setContent {
            WattimTheme {
                WattimNavHost(
                    permissionMonitor = permissionMonitor,
                    settingsAdapter = settingsAdapter,
                    initialRoute = AppRoute.Main(TerminalTab.APPS),
                    onStartFgs = {},
                    policyStore = policyStore,
                    statisticsStore = statisticsStore,
                    packageCatalog = packageCatalog,
                    wallClock = wallClock,
                    onRouteChange = { currentNavRoute = it }
                )
            }
        }

        // Wait for target rows to load with independent ON/OFF badge (F45)
        val toggleMatcher = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch) and hasText("ON")
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodes(toggleMatcher).fetchSemanticsNodes().isNotEmpty()
        }

        // Toggle ON badge on the row to OFF (F45)
        composeTestRule.onNode(toggleMatcher).performScrollTo().performClick()

        // Verify policy store updated directly
        composeTestRule.waitUntil(5000) {
            policyStore.currentSnapshot.targets["com.sample.chat"]?.enabled == false
        }

        val updated = policyStore.currentSnapshot.targets["com.sample.chat"]
        assertNotNull(updated)
        assertFalse("Target should now be disabled", updated!!.enabled)

        // Route did NOT navigate to Detail
        assertTrue("Route should remain Main", currentNavRoute is AppRoute.Main)
    }

    @Test
    fun homeScreenGlobalPauseSetsAndResumes() {
        composeTestRule.setContent {
            WattimTheme {
                WattimNavHost(
                    permissionMonitor = permissionMonitor,
                    settingsAdapter = settingsAdapter,
                    initialRoute = AppRoute.Main(TerminalTab.APPS),
                    onStartFgs = {},
                    policyStore = policyStore,
                    statisticsStore = statisticsStore,
                    packageCatalog = packageCatalog,
                    wallClock = wallClock
                )
            }
        }

        // Click 15M preset (F59)
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodes(hasText("15M")).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("15M").performScrollTo().performClick()

        // Verify global pause active in policyStore
        composeTestRule.waitUntil(5000) {
            policyStore.currentSnapshot.globalPause is GlobalPause.Until
        }
        val pause = policyStore.currentSnapshot.globalPause
        assertTrue(pause is GlobalPause.Until)

        // Resume button visible, click to clear
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodes(hasText("RESUME")).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("RESUME").performScrollTo().performClick()

        composeTestRule.waitUntil(5000) {
            policyStore.currentSnapshot.globalPause is GlobalPause.None
        }
        assertTrue(policyStore.currentSnapshot.globalPause is GlobalPause.None)
    }

    @Test
    fun addAppDialogAddsDefaultProtection() {
        packageCatalog.apps = listOf(
            PackageAppEntry("com.sample.browser", "Browser", false)
        )

        composeTestRule.setContent {
            WattimTheme {
                WattimNavHost(
                    permissionMonitor = permissionMonitor,
                    settingsAdapter = settingsAdapter,
                    initialRoute = AppRoute.Main(TerminalTab.APPS),
                    onStartFgs = {},
                    policyStore = policyStore,
                    statisticsStore = statisticsStore,
                    packageCatalog = packageCatalog,
                    wallClock = wallClock
                )
            }
        }

        // Click ADD APP button (F46)
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodes(hasText("ADD APP")).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("ADD APP").performScrollTo().performClick()

        // Dialog shows Browser, click it to add (F47)
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodes(hasText("Browser")).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Browser").performClick()

        // Verify default config added
        composeTestRule.waitUntil(5000) {
            policyStore.currentSnapshot.targets["com.sample.browser"] != null
        }
        val target = policyStore.currentSnapshot.targets["com.sample.browser"]
        assertNotNull(target)
        assertEquals(8000L, target!!.durationMs)
        assertEquals(AnimationMode.FILL, target.animation)
        assertEquals(300000L, target.reinterventionMs) // 5m default
        assertEquals(0L, target.quickReturnGraceMs)
        assertFalse(target.growthConfig.enabled)
    }

    @Test
    fun targetSettingsEditsPreviewAndSaves() {
        val target = TargetConfig(
            packageName = "com.sample.video",
            displayName = "Video App",
            enabled = true,
            durationMs = 8000L
        )
        runBlocking {
            policyStore.saveTarget(target)
        }

        var currentNavRoute: AppRoute? = null

        composeTestRule.setContent {
            WattimTheme {
                WattimNavHost(
                    permissionMonitor = permissionMonitor,
                    settingsAdapter = settingsAdapter,
                    initialRoute = AppRoute.Detail("com.sample.video"),
                    onStartFgs = {},
                    policyStore = policyStore,
                    statisticsStore = statisticsStore,
                    packageCatalog = packageCatalog,
                    wallClock = wallClock,
                    onRouteChange = { currentNavRoute = it }
                )
            }
        }

        // Header and editors displayed (F49-F57)
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodes(hasText("← VIDEO APP")).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("← VIDEO APP").assertIsDisplayed()

        // Preview Dialog (F53) - renders exact InterventionContent with EXIT button during breathing
        composeTestRule.onNodeWithText("PREVIEW").performScrollTo().performClick()
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodes(hasText("EXIT")).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("EXIT").assertIsDisplayed()
        composeTestRule.onNodeWithText("EXIT").performClick()

        // Adjust duration with stepper (+5) -> 13s (F52)
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodes(hasText("+5S")).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("+5S").performScrollTo().performClick()

        val exact13Matcher = hasText("13", substring = false)
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodes(exact13Matcher).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNode(exact13Matcher).performScrollTo().assertIsDisplayed()

        // Save target (F58)
        composeTestRule.onNodeWithText("SAVE", substring = true).performScrollTo().performClick()

        // Returns to Main route
        composeTestRule.waitUntil(5000) { currentNavRoute is AppRoute.Main }
        assertTrue(currentNavRoute is AppRoute.Main)

        // Saved target has updated duration
        composeTestRule.waitUntil(5000) {
            policyStore.currentSnapshot.targets["com.sample.video"]?.durationMs == 13000L
        }
        val saved = policyStore.currentSnapshot.targets["com.sample.video"]
        assertNotNull(saved)
        assertEquals(13000L, saved!!.durationMs)
    }

    @Test
    fun targetSettingsRemoveImmediatelyDeletesTarget() {
        val target = TargetConfig(
            packageName = "com.sample.removeme",
            displayName = "Remove App",
            enabled = true
        )
        runBlocking {
            policyStore.saveTarget(target)
        }

        var currentNavRoute: AppRoute? = null

        composeTestRule.setContent {
            WattimTheme {
                WattimNavHost(
                    permissionMonitor = permissionMonitor,
                    settingsAdapter = settingsAdapter,
                    initialRoute = AppRoute.Detail("com.sample.removeme"),
                    onStartFgs = {},
                    policyStore = policyStore,
                    statisticsStore = statisticsStore,
                    packageCatalog = packageCatalog,
                    wallClock = wallClock,
                    onRouteChange = { currentNavRoute = it }
                )
            }
        }

        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodes(hasText("REMOVE FROM PROTECTION")).fetchSemanticsNodes().isNotEmpty()
        }

        // Click REMOVE (F58 - immediate without confirm dialog)
        composeTestRule.onNodeWithText("REMOVE FROM PROTECTION").performScrollTo().performClick()

        // Returns to Main route
        composeTestRule.waitUntil(5000) { currentNavRoute is AppRoute.Main }
        assertTrue(currentNavRoute is AppRoute.Main)

        // Target deleted from policy store
        assertNull(policyStore.currentSnapshot.targets["com.sample.removeme"])
    }

    private class FakePackageCatalog : PackageCatalog {
        var apps: List<PackageAppEntry> = emptyList()

        override suspend fun getLaunchableApps(excludedPackages: Set<String>): List<PackageAppEntry> {
            return apps.filter { it.packageName !in excludedPackages }
        }
    }
}
