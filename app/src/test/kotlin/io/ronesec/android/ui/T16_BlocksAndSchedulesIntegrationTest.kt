package io.ronesec.android.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
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
import io.ronesec.domain.model.CompiledSchedule
import io.ronesec.domain.model.FakeWallClock
import io.ronesec.domain.model.ScheduleType
import io.ronesec.domain.model.TargetConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
class T16_BlocksAndSchedulesIntegrationTest {

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
    fun quickLockCardAppearsWhenOpenedFromBlockTabAndCreatesBlockSession() {
        val target = TargetConfig(
            packageName = "com.sample.target",
            displayName = "Target App",
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
                    initialRoute = AppRoute.Detail(
                        packageName = "com.sample.target",
                        originTab = TerminalTab.BLOCK,
                        canQuickLock = true
                    ),
                    onStartFgs = {},
                    policyStore = policyStore,
                    statisticsStore = statisticsStore,
                    packageCatalog = packageCatalog,
                    wallClock = wallClock,
                    onRouteChange = { currentNavRoute = it }
                )
            }
        }

        // Wait until Quick Lock card is displayed
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodes(hasText("QUICK LOCK")).fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("QUICK LOCK").performScrollTo().assertIsDisplayed()

        // Click 15M Quick Lock (F60)
        composeTestRule.onNodeWithText("15M").performScrollTo().performClick()

        // Navigates back to Main(BLOCK)
        composeTestRule.waitUntil(5000) { currentNavRoute is AppRoute.Main }
        val mainRoute = currentNavRoute as? AppRoute.Main
        assertNotNull(mainRoute)
        assertEquals(TerminalTab.BLOCK, mainRoute!!.tab)

        // Verify block session created in PolicyStore
        val session = policyStore.currentSnapshot.activeBlockSessions.firstOrNull()
        assertNotNull(session)
        assertTrue(session!!.targetPackages.contains("com.sample.target"))
        assertEquals(15 * 60 * 1000L, session.endTime.toEpochMilli() - session.startTime.toEpochMilli())
    }

    @Test
    fun quickFocusLaunchStartsBlockSessionAndHidesLauncher() {
        runBlocking {
            policyStore.saveTarget(TargetConfig("com.focus.app1", "Focus App 1"))
            policyStore.saveTarget(TargetConfig("com.focus.app2", "Focus App 2"))
        }

        composeTestRule.setContent {
            WattimTheme {
                WattimNavHost(
                    permissionMonitor = permissionMonitor,
                    settingsAdapter = settingsAdapter,
                    initialRoute = AppRoute.Main(TerminalTab.BLOCK),
                    onStartFgs = {},
                    policyStore = policyStore,
                    statisticsStore = statisticsStore,
                    packageCatalog = packageCatalog,
                    wallClock = wallClock
                )
            }
        }

        // Wait until targets are loaded and preselected (F61)
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodes(hasText("2/2", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }

        // Start focus with default 30M and all targets preselected (F61)
        composeTestRule.onNodeWithText("30M").performScrollTo().performClick()

        // Active session card appears with remaining countdown and STOP button (F62)
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodes(hasText("ACTIVE BLOCK SESSION")).fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("ACTIVE BLOCK SESSION").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("STOP").performScrollTo().assertIsDisplayed()

        // Verify active block session in PolicyStore
        val session = policyStore.currentSnapshot.activeBlockSessions.firstOrNull()
        assertNotNull(session)
        assertEquals(2, session!!.targetPackages.size)
    }

    @Test
    fun activeSessionStopTerminatesSessionAndRestoresLauncher() {
        val now = wallClock.now()
        runBlocking {
            policyStore.saveTarget(TargetConfig("com.focus.app", "Focus App"))
            policyStore.createBlockSession(
                name = "Active Lock",
                startTime = now,
                endTime = now.plusSeconds(1800),
                targetPackages = setOf("com.focus.app")
            )
        }

        composeTestRule.setContent {
            WattimTheme {
                WattimNavHost(
                    permissionMonitor = permissionMonitor,
                    settingsAdapter = settingsAdapter,
                    initialRoute = AppRoute.Main(TerminalTab.BLOCK),
                    onStartFgs = {},
                    policyStore = policyStore,
                    statisticsStore = statisticsStore,
                    packageCatalog = packageCatalog,
                    wallClock = wallClock
                )
            }
        }

        // Wait for ActiveBlockSessionCard
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodes(hasText("ACTIVE BLOCK SESSION")).fetchSemanticsNodes().isNotEmpty()
        }

        // Click STOP (F62)
        composeTestRule.onNodeWithText("STOP").performScrollTo().performClick()

        // Session terminated, Quick Focus launcher restored
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodes(hasText("QUICK FOCUS", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("QUICK FOCUS", substring = true).performScrollTo().assertIsDisplayed()
        assertTrue(policyStore.currentSnapshot.activeBlockSessions.none { it.active })
    }

    @Test
    fun scheduleEditorCreatesNewScheduleWithWorkPreset() {
        runBlocking {
            policyStore.saveTarget(TargetConfig("com.work.app", "Work App"))
        }

        var currentNavRoute: AppRoute? = null

        composeTestRule.setContent {
            WattimTheme {
                WattimNavHost(
                    permissionMonitor = permissionMonitor,
                    settingsAdapter = settingsAdapter,
                    initialRoute = AppRoute.Main(TerminalTab.BLOCK),
                    onStartFgs = {},
                    policyStore = policyStore,
                    statisticsStore = statisticsStore,
                    packageCatalog = packageCatalog,
                    wallClock = wallClock,
                    onRouteChange = { currentNavRoute = it }
                )
            }
        }

        // Wait until CREATE button in schedules header is ready (F63)
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodes(hasText("+ CREATE SCHEDULE")).fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("+ CREATE SCHEDULE").performScrollTo().performClick()

        // Wait until ScheduleEditorScreen is open
        composeTestRule.waitUntil(5000) { currentNavRoute is AppRoute.ScheduleEditor }
        composeTestRule.onNodeWithText("NEW SCHEDULE").assertIsDisplayed()

        // Fill name (F66)
        composeTestRule.onNode(
            hasSetTextAction() and hasText("e.g. Work Hours, Deep Sleep")
        ).performTextInput("Office Schedule")

        // Click WORK preset (09:00 - 18:00) (F66)
        composeTestRule.onNodeWithText("WORK").performScrollTo().performClick()

        // Save schedule
        composeTestRule.onNodeWithText("SAVE").performScrollTo().performClick()

        // Returns to Blocks tab
        composeTestRule.waitUntil(5000) { currentNavRoute is AppRoute.Main }

        // Verify schedule in list and store
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodes(hasText("Office Schedule", ignoreCase = true)).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Office Schedule", ignoreCase = true).performScrollTo().assertIsDisplayed()

        val schedule = policyStore.currentSnapshot.activeSchedules.find { it.name == "Office Schedule" }
        assertNotNull(schedule)
        assertEquals(9 * 60, schedule!!.startMinute)
        assertEquals(18 * 60, schedule.endMinute)
    }

    @Test
    fun scheduleListToggleAndDirectDelete() {
        runBlocking {
            policyStore.saveTarget(TargetConfig("com.app.delete", "Delete App"))
            val sched = CompiledSchedule(
                id = 0L,
                name = "Temp Schedule",
                weekdayMask = 31,
                startMinute = 9 * 60,
                endMinute = 17 * 60,
                enabled = true,
                type = ScheduleType.HARD_BLOCK,
                targetPackages = setOf("com.app.delete")
            )
            policyStore.saveSchedule(sched)
        }

        composeTestRule.setContent {
            WattimTheme {
                WattimNavHost(
                    permissionMonitor = permissionMonitor,
                    settingsAdapter = settingsAdapter,
                    initialRoute = AppRoute.Main(TerminalTab.BLOCK),
                    onStartFgs = {},
                    policyStore = policyStore,
                    statisticsStore = statisticsStore,
                    packageCatalog = packageCatalog,
                    wallClock = wallClock
                )
            }
        }

        // Wait until Temp Schedule is visible in list
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodes(hasText("Temp Schedule", ignoreCase = true)).fetchSemanticsNodes().isNotEmpty()
        }

        // Click DELETE directly on schedule card without opening editor (F68)
        composeTestRule.onNodeWithText("DELETE").performScrollTo().performClick()

        // Verify deleted from PolicyStore
        composeTestRule.waitUntil(5000) {
            policyStore.currentSnapshot.activeSchedules.none { it.name == "Temp Schedule" }
        }
        assertNull(policyStore.currentSnapshot.activeSchedules.find { it.name == "Temp Schedule" })
    }

    private class FakePackageCatalog : PackageCatalog {
        var apps: List<PackageAppEntry> = emptyList()

        override suspend fun getLaunchableApps(excludedPackages: Set<String>): List<PackageAppEntry> {
            return apps.filter { it.packageName !in excludedPackages }
        }
    }
}
