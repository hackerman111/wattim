package io.ronesec.android.ui

import android.content.Context
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
import io.ronesec.android.data.entity.OpenAttemptEntity
import io.ronesec.android.platform.system.FakePlatformPermissionChecker
import io.ronesec.android.platform.system.PackageAppEntry
import io.ronesec.android.platform.system.PackageCatalog
import io.ronesec.android.platform.system.PermissionMonitor
import io.ronesec.android.platform.system.SettingsIntentAdapter
import io.ronesec.android.ui.designsystem.TerminalTab
import io.ronesec.android.ui.designsystem.ThemeId
import io.ronesec.android.ui.designsystem.WattimTheme
import io.ronesec.domain.model.FakeWallClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class T16_StatsAndConfigIntegrationTest {

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
    fun statsTabRendersTruthfulAggregatesAndAppStats() {
        val now = wallClock.now().toEpochMilli()
        val dao = database.openAttemptDao()
        runBlocking {
            dao.insertIdempotent(
                OpenAttemptEntity(
                    attemptId = "att-1",
                    sessionId = "s-1",
                    cycle = 1,
                    packageName = "com.sample.social",
                    displayNameAtAttempt = "Social App",
                    generation = 1L,
                    kind = "ENTRY",
                    timestamp = now - 10000,
                    outcome = "ABANDONED",
                    resolvedAt = now - 10000
                )
            )
            dao.insertIdempotent(
                OpenAttemptEntity(
                    attemptId = "att-2",
                    sessionId = "s-2",
                    cycle = 1,
                    packageName = "com.sample.social",
                    displayNameAtAttempt = "Social App",
                    generation = 1L,
                    kind = "ENTRY",
                    timestamp = now - 8000,
                    outcome = "CONTINUED",
                    resolvedAt = now - 8000
                )
            )
            dao.insertIdempotent(
                OpenAttemptEntity(
                    attemptId = "att-3",
                    sessionId = "s-3",
                    cycle = 1,
                    packageName = "com.sample.browser",
                    displayNameAtAttempt = "Browser",
                    generation = 1L,
                    kind = "ENTRY",
                    timestamp = now - 5000,
                    outcome = "BLOCKED",
                    resolvedAt = now - 5000
                )
            )
        }

        composeTestRule.setContent {
            WattimTheme {
                WattimNavHost(
                    permissionMonitor = permissionMonitor,
                    settingsAdapter = settingsAdapter,
                    initialRoute = AppRoute.Main(TerminalTab.STATS),
                    onStartFgs = {},
                    policyStore = policyStore,
                    statisticsStore = statisticsStore,
                    packageCatalog = packageCatalog,
                    wallClock = wallClock
                )
            }
        }

        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodes(hasText("ALL-TIME SAVED LIFE")).fetchSemanticsNodes().isNotEmpty()
        }

        // Hero card headings and labels
        composeTestRule.onNodeWithText("ALL-TIME SAVED LIFE").assertIsDisplayed()
        composeTestRule.onNodeWithText("AVOIDED IMPULSES").assertIsDisplayed()
        composeTestRule.onNodeWithText("SAVED TODAY").assertIsDisplayed()

        // Today summary
        composeTestRule.onNodeWithText("TODAY").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("ATTEMPTS").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("CONTINUED").performScrollTo().assertIsDisplayed()
        composeTestRule.onAllNodes(hasText("CLOSED"))[0].performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("PREVENTED").performScrollTo().assertIsDisplayed()

        // Per-app statistics table
        composeTestRule.onNodeWithText("APPLICATIONS TODAY").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Social App").performScrollTo().assertIsDisplayed()
        composeTestRule.onAllNodes(hasText("CLOSED"))[1].performScrollTo().assertIsDisplayed()
    }

    @Test
    fun emptyDatabaseInStatsTabShowsZeroActivityNotice() {
        composeTestRule.setContent {
            WattimTheme {
                WattimNavHost(
                    permissionMonitor = permissionMonitor,
                    settingsAdapter = settingsAdapter,
                    initialRoute = AppRoute.Main(TerminalTab.STATS),
                    onStartFgs = {},
                    policyStore = policyStore,
                    statisticsStore = statisticsStore,
                    packageCatalog = packageCatalog,
                    wallClock = wallClock
                )
            }
        }

        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodes(hasText("ALL-TIME SAVED LIFE")).fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("ALL-TIME SAVED LIFE").assertIsDisplayed()
        composeTestRule.onNodeWithText("NO ACTIVITY RECORDED TODAY").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun configTabRendersAllSectionsAndAllowsThemeSelection() {
        composeTestRule.setContent {
            WattimTheme {
                WattimNavHost(
                    permissionMonitor = permissionMonitor,
                    settingsAdapter = settingsAdapter,
                    initialRoute = AppRoute.Main(TerminalTab.CONFIG),
                    onStartFgs = {},
                    policyStore = policyStore,
                    statisticsStore = statisticsStore,
                    packageCatalog = packageCatalog,
                    wallClock = wallClock
                )
            }
        }

        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodes(hasText("THEME")).fetchSemanticsNodes().isNotEmpty()
        }

        // Verify config sections
        composeTestRule.onNodeWithText("THEME").assertIsDisplayed()
        composeTestRule.onNodeWithText("LANGUAGE").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("SAVED SESSION DURATION").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("OVERLAY STATISTICS").performScrollTo().assertIsDisplayed()

        // Select Theme: CYBER TERMINAL
        composeTestRule.onNodeWithText("CYBER TERMINAL").performScrollTo().performClick()

        composeTestRule.waitUntil(5000) {
            policyStore.presentationSettings.value.themeId == ThemeId.CYBER_TERMINAL
        }
        assertEquals(ThemeId.CYBER_TERMINAL, policyStore.presentationSettings.value.themeId)
    }

    @Test
    fun configTabAllowsSavedDurationAndOverlayStatsAndLanguageChanges() {
        composeTestRule.setContent {
            WattimTheme {
                WattimNavHost(
                    permissionMonitor = permissionMonitor,
                    settingsAdapter = settingsAdapter,
                    initialRoute = AppRoute.Main(TerminalTab.CONFIG),
                    onStartFgs = {},
                    policyStore = policyStore,
                    statisticsStore = statisticsStore,
                    packageCatalog = packageCatalog,
                    wallClock = wallClock
                )
            }
        }

        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodes(hasText("SAVED SESSION DURATION")).fetchSemanticsNodes().isNotEmpty()
        }

        // Select 15 min duration (displayed as "15 MIN" by TerminalBadge)
        composeTestRule.onNodeWithText("15 MIN").performScrollTo().performClick()
        composeTestRule.waitUntil(5000) {
            policyStore.presentationSettings.value.savedSessionMinutes == 15
        }
        assertEquals(15, policyStore.presentationSettings.value.savedSessionMinutes)

        // Toggle Overlay stats (first "ON" badge on screen)
        composeTestRule.onAllNodes(hasText("ON"))[0].performScrollTo().performClick()
        composeTestRule.waitUntil(5000) {
            !policyStore.presentationSettings.value.showOverlayStats
        }
        assertFalse(policyStore.presentationSettings.value.showOverlayStats)

        // Select language: РУССКИЙ
        composeTestRule.onNodeWithText("РУССКИЙ").performScrollTo().performClick()
        composeTestRule.waitUntil(5000) {
            policyStore.presentationSettings.value.language == "РУССКИЙ"
        }
        assertEquals("РУССКИЙ", policyStore.presentationSettings.value.language)
    }

    @Test
    fun configTabDisplaysPermissionsRestrictedSettingsAndPrivacy() {
        composeTestRule.setContent {
            WattimTheme {
                WattimNavHost(
                    permissionMonitor = permissionMonitor,
                    settingsAdapter = settingsAdapter,
                    initialRoute = AppRoute.Main(TerminalTab.CONFIG),
                    onStartFgs = {},
                    policyStore = policyStore,
                    statisticsStore = statisticsStore,
                    packageCatalog = packageCatalog,
                    wallClock = wallClock
                )
            }
        }

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("PERMISSIONS & STATUS").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("RESTRICTED SETTINGS WARNING").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("OPEN APP INFO").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("PRIVACY & SECURITY").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("100% OFFLINE").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("LOCAL STORAGE").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("NO TRACKING").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("SAFE ACCESSIBILITY").performScrollTo().assertIsDisplayed()
    }

    private class FakePackageCatalog : PackageCatalog {
        override suspend fun getLaunchableApps(excludedPackages: Set<String>): List<PackageAppEntry> = emptyList()
    }
}
