package io.ronesec.android.ui.home

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.ronesec.android.data.PolicyStore
import io.ronesec.android.data.StatisticsStore
import io.ronesec.android.data.WattimDatabase
import io.ronesec.android.platform.system.PackageAppEntry
import io.ronesec.android.platform.system.PackageCatalog
import io.ronesec.domain.model.AnimationMode
import io.ronesec.domain.model.FakeWallClock
import io.ronesec.domain.model.TargetConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HomeViewModelTest {

    private lateinit var database: WattimDatabase
    private lateinit var wallClock: FakeWallClock
    private lateinit var fakeCatalog: FakePackageCatalog

    class FakePackageCatalog : PackageCatalog {
        var appsToReturn: List<PackageAppEntry> = listOf(
            PackageAppEntry("com.test.one", "Alpha App"),
            PackageAppEntry("com.test.two", "Beta App")
        )

        override suspend fun getLaunchableApps(excludedPackages: Set<String>): List<PackageAppEntry> {
            return appsToReturn.filter { it.packageName !in excludedPackages }
        }
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = WattimDatabase.createInMemory(context)
        wallClock = FakeWallClock(Instant.parse("2026-09-09T14:30:00Z"))
        fakeCatalog = FakePackageCatalog()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun createViewModel(
        scope: kotlinx.coroutines.CoroutineScope,
        dispatcher: kotlinx.coroutines.CoroutineDispatcher
    ): Pair<PolicyStore, HomeViewModel> {
        val policyStore = PolicyStore(
            database = database,
            wallClock = wallClock,
            scope = scope,
            ioDispatcher = dispatcher
        )
        val statisticsStore = StatisticsStore(
            database = database,
            wallClock = wallClock,
            ioDispatcher = dispatcher
        )
        val viewModel = HomeViewModel(
            policyStore = policyStore,
            statisticsStore = statisticsStore,
            packageCatalog = fakeCatalog,
            wallClock = wallClock,
            coroutineScope = scope,
            ioDispatcher = dispatcher
        )
        return Pair(policyStore, viewModel)
    }

    @Test
    fun homeLoadsEmptyTargetsInitially() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val (policyStore, viewModel) = createViewModel(backgroundScope, dispatcher)
        policyStore.awaitReady()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(0, state.protectedCount)
        assertTrue(state.apps.isEmpty())
        assertEquals("14:30", state.wallClockTime)
    }

    @Test
    fun homeRendersRowHierarchyAndFirstIndicator() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val (policyStore, viewModel) = createViewModel(backgroundScope, dispatcher)
        policyStore.awaitReady()

        policyStore.saveTarget(
            TargetConfig(
                packageName = "com.sample.b",
                displayName = "Beta App",
                animation = AnimationMode.FILL,
                durationMs = 8_000L
            )
        )
        policyStore.saveTarget(
            TargetConfig(
                packageName = "com.sample.a",
                displayName = "Alpha App",
                animation = AnimationMode.PULSE,
                durationMs = 15_000L
            )
        )
        advanceUntilIdle()

        val state = viewModel.uiState.first { it.apps.size == 2 }
        assertEquals(2, state.protectedCount)
        assertEquals(2, state.apps.size)

        // Sorted by displayName: Alpha App then Beta App
        val first = state.apps[0]
        assertEquals("Alpha App", first.displayName)
        assertEquals("com.sample.a", first.packageName)
        assertEquals("01", first.formattedIndex)
        assertTrue(first.isFirst) // First row has '>'
        assertEquals(15, first.durationSeconds)
        assertEquals(AnimationMode.PULSE, first.animation)

        val second = state.apps[1]
        assertEquals("Beta App", second.displayName)
        assertEquals("com.sample.b", second.packageName)
        assertEquals("02", second.formattedIndex)
        assertFalse(second.isFirst)
        assertEquals(8, second.durationSeconds)
        assertEquals(AnimationMode.FILL, second.animation)
    }

    @Test
    fun perAppQuickTogglePersistsWithoutDetail() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val (policyStore, viewModel) = createViewModel(backgroundScope, dispatcher)
        policyStore.awaitReady()

        policyStore.saveTarget(
            TargetConfig(
                packageName = "com.toggle.app",
                displayName = "Toggle App",
                enabled = true
            )
        )
        advanceUntilIdle()

        var state = viewModel.uiState.first { it.apps.any { app -> app.packageName == "com.toggle.app" } }
        assertTrue(state.apps.first { it.packageName == "com.toggle.app" }.enabled)

        // Toggle OFF (F45)
        viewModel.onToggleTarget("com.toggle.app")
        advanceUntilIdle()

        state = viewModel.uiState.first { it.apps.any { app -> app.packageName == "com.toggle.app" && !app.enabled } }
        assertFalse(state.apps.first { it.packageName == "com.toggle.app" }.enabled)

        // Toggle ON
        viewModel.onToggleTarget("com.toggle.app")
        advanceUntilIdle()

        state = viewModel.uiState.first { it.apps.any { app -> app.packageName == "com.toggle.app" && app.enabled } }
        assertTrue(state.apps.first { it.packageName == "com.toggle.app" }.enabled)
    }

    @Test
    fun addAppPersistsExactDefaultsAndClosesDialog() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val (policyStore, viewModel) = createViewModel(backgroundScope, dispatcher)
        policyStore.awaitReady()
        advanceUntilIdle()

        // Open Add App dialog
        viewModel.onOpenAddAppDialog()
        advanceUntilIdle()

        val dialogState = viewModel.uiState.first { it.addAppDialog.isOpen && !it.addAppDialog.isLoading }
        assertTrue(dialogState.addAppDialog.isOpen)
        assertEquals(2, dialogState.addAppDialog.filteredApps.size)

        // Select Alpha App
        val selected = dialogState.addAppDialog.filteredApps[0]
        viewModel.onSelectAppToAdd(selected)
        advanceUntilIdle()

        // Dialog should be closed
        val closedState = viewModel.uiState.first { !it.addAppDialog.isOpen }
        assertFalse(closedState.addAppDialog.isOpen)

        // Target should be in store with exact Android defaults (F47)
        val snapshot = policyStore.currentSnapshot
        val target = snapshot.targets[selected.packageName]
        assertNotNull(target)
        assertEquals(8_000L, target!!.durationMs)
        assertEquals(AnimationMode.FILL, target.animation)
        assertEquals(300_000L, target.reinterventionMs)
        assertEquals(0L, target.quickReturnGraceMs)
        assertFalse(target.growthConfig.enabled)
        assertEquals(20, target.growthConfig.percent)
        assertEquals(3_600_000L, target.growthConfig.windowMs)
        assertEquals(TargetConfig.DEFAULT_PHRASE, target.phrase)
    }

    @Test
    fun globalPauseDurationsAndResumeWork() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val (policyStore, viewModel) = createViewModel(backgroundScope, dispatcher)
        policyStore.awaitReady()
        advanceUntilIdle()

        // Set 15m pause
        viewModel.onSetGlobalPause(15 * 60 * 1000L)
        advanceUntilIdle()

        var state = viewModel.uiState.first { it.pauseState is HomePauseState.Active }
        val activePause = state.pauseState as HomePauseState.Active
        assertFalse(activePause.isForever)
        assertEquals("15:00", activePause.formattedRemaining)

        // Set Forever pause
        viewModel.onSetGlobalPause(null)
        advanceUntilIdle()

        state = viewModel.uiState.first { (it.pauseState as? HomePauseState.Active)?.isForever == true }
        val foreverPause = state.pauseState as HomePauseState.Active
        assertTrue(foreverPause.isForever)
        assertEquals("FOREVER", foreverPause.formattedRemaining)

        // Resume
        viewModel.onResumeGlobalPause()
        advanceUntilIdle()

        state = viewModel.uiState.first { it.pauseState is HomePauseState.Inactive }
        assertEquals(HomePauseState.Inactive, state.pauseState)
    }
}
