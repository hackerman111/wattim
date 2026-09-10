package io.ronesec.android.ui.blocks

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.ronesec.android.data.PolicyStore
import io.ronesec.android.data.WattimDatabase
import io.ronesec.domain.model.CompiledSchedule
import io.ronesec.domain.model.FakeWallClock
import io.ronesec.domain.model.ScheduleType
import io.ronesec.domain.model.TargetConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BlocksViewModelTest {

    private lateinit var database: WattimDatabase
    private lateinit var wallClock: FakeWallClock

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = WattimDatabase.createInMemory(context)
        wallClock = FakeWallClock(Instant.parse("2026-09-09T12:00:00Z"))
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun createViewModel(
        testScope: TestScope,
        store: PolicyStore,
        dispatcher: kotlinx.coroutines.CoroutineDispatcher
    ): BlocksViewModel {
        return BlocksViewModel(
            policyStore = store,
            wallClock = wallClock,
            coroutineScope = testScope.backgroundScope,
            ioDispatcher = dispatcher
        )
    }

    @Test
    fun initialStateDefaultsToAllProtectedAppsAnd30Minutes() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = PolicyStore(database, wallClock, backgroundScope, dispatcher)
        store.awaitReady()

        store.saveTarget(TargetConfig(packageName = "com.sample.app1", displayName = "App 1"))
        store.saveTarget(TargetConfig(packageName = "com.sample.app2", displayName = "App 2"))
        advanceUntilIdle()

        val viewModel = createViewModel(this, store, dispatcher)
        advanceUntilIdle()

        val state = viewModel.uiState.first { it.protectedApps.size == 2 }
        assertEquals(2, state.protectedApps.size)
        // All protected apps preselected by default on first entry (F61)
        assertEquals(setOf("com.sample.app1", "com.sample.app2"), state.selectedPackagesForFocus)
        // 30 minutes visually primary default (F61)
        assertEquals(30 * 60 * 1000L, state.selectedFocusDurationMs)
        assertTrue(state.isQuickFocusEnabled)
        assertNull(state.activeSession)
    }

    @Test
    fun selectionControlsUpdateFocusState() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = PolicyStore(database, wallClock, backgroundScope, dispatcher)
        store.awaitReady()

        store.saveTarget(TargetConfig(packageName = "com.sample.app1", displayName = "App 1"))
        store.saveTarget(TargetConfig(packageName = "com.sample.app2", displayName = "App 2"))
        advanceUntilIdle()

        val viewModel = createViewModel(this, store, dispatcher)
        viewModel.uiState.first { it.protectedApps.size == 2 }

        // Select None -> disabled launch
        viewModel.onSelectNonePackagesForFocus()
        val stateNone = viewModel.uiState.value
        assertTrue(stateNone.selectedPackagesForFocus.isEmpty())
        assertFalse(stateNone.isQuickFocusEnabled)

        // Toggle app1 back on
        viewModel.onTogglePackageForFocus("com.sample.app1")
        val stateOne = viewModel.uiState.value
        assertEquals(setOf("com.sample.app1"), stateOne.selectedPackagesForFocus)
        assertTrue(stateOne.isQuickFocusEnabled)

        // Select All
        viewModel.onSelectAllPackagesForFocus()
        val stateAll = viewModel.uiState.value
        assertEquals(setOf("com.sample.app1", "com.sample.app2"), stateAll.selectedPackagesForFocus)

        // Select Duration 1h
        viewModel.onSelectFocusDuration(60 * 60 * 1000L)
        assertEquals(60 * 60 * 1000L, viewModel.uiState.value.selectedFocusDurationMs)
    }

    @Test
    fun startQuickFocusCreatesSessionAndRendersActiveSession() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = PolicyStore(database, wallClock, backgroundScope, dispatcher)
        store.awaitReady()

        store.saveTarget(TargetConfig(packageName = "com.sample.app1", displayName = "App 1"))
        advanceUntilIdle()

        val viewModel = createViewModel(this, store, dispatcher)
        viewModel.uiState.first { it.protectedApps.size == 1 }

        viewModel.onSelectFocusDuration(15 * 60 * 1000L)
        viewModel.onStartQuickFocus()

        val stateWithSession = viewModel.uiState.first { it.activeSession != null }
        assertNotNull(stateWithSession.activeSession)
        assertEquals("Quick Focus", stateWithSession.activeSession!!.name)
        assertEquals(setOf("com.sample.app1"), stateWithSession.activeSession!!.targetPackages)
        assertEquals(15 * 60L, stateWithSession.activeSession!!.remainingSeconds)
        assertTrue(stateWithSession.hasActiveSession)
    }

    @Test
    fun stopActiveSessionDeactivatesSession() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = PolicyStore(database, wallClock, backgroundScope, dispatcher)
        store.awaitReady()

        store.saveTarget(TargetConfig(packageName = "com.sample.app1", displayName = "App 1"))
        advanceUntilIdle()

        val viewModel = createViewModel(this, store, dispatcher)
        viewModel.uiState.first { it.protectedApps.size == 1 }

        viewModel.onStartQuickFocus()
        val stateWithSession = viewModel.uiState.first { it.activeSession != null }
        val sessionId = stateWithSession.activeSession!!.id

        viewModel.onStopActiveSession(sessionId)
        val stateStopped = viewModel.uiState.first { it.activeSession == null }
        assertNull(stateStopped.activeSession)
        assertFalse(stateStopped.hasActiveSession)
    }

    @Test
    fun multipleOverlappingSessionsDefensivelyUsesMaxEndTime() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = PolicyStore(database, wallClock, backgroundScope, dispatcher)
        store.awaitReady()

        store.saveTarget(TargetConfig(packageName = "com.sample.app1", displayName = "App 1"))
        advanceUntilIdle()

        // Create session 1: ends in 15m
        val now = wallClock.now()
        store.createBlockSession("Session 1", now, now.plusSeconds(900), setOf("com.sample.app1"))
        // Create session 2: ends in 30m (defensive overlap)
        store.createBlockSession("Session 2", now, now.plusSeconds(1800), setOf("com.sample.app1"))
        advanceUntilIdle()

        val viewModel = createViewModel(this, store, dispatcher)
        val state = viewModel.uiState.first { it.activeSession != null }

        // Max end time should be chosen (1800s) (F62)
        assertEquals("Session 2", state.activeSession!!.name)
        assertEquals(1800L, state.activeSession!!.remainingSeconds)
    }

    @Test
    fun schedulesListToggleAndSummaryFormatters() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = PolicyStore(database, wallClock, backgroundScope, dispatcher)
        store.awaitReady()

        store.saveTarget(TargetConfig(packageName = "com.sample.app1", displayName = "App 1"))
        advanceUntilIdle()

        // Create schedule: Mon-Fri (mask 0x1F), 09:00 - 18:00 (540..1080)
        val schedule = CompiledSchedule(
            id = 0L,
            name = "Work Hours",
            weekdayMask = 0x1F,
            startMinute = 540,
            endMinute = 1080,
            enabled = true,
            type = ScheduleType.HARD_BLOCK,
            targetPackages = setOf("com.sample.app1")
        )
        val scheduleId = store.saveSchedule(schedule).getOrThrow()
        advanceUntilIdle()

        val viewModel = createViewModel(this, store, dispatcher)
        val state = viewModel.uiState.first { it.schedules.isNotEmpty() }
        assertEquals(1, state.schedules.size)
        val item = state.schedules.first()
        assertEquals("Work Hours", item.name)
        assertEquals("WEEKDAYS", item.daysSummary)
        assertEquals("09:00 – 18:00", item.timeSummary)
        assertFalse(item.isOvernight)
        assertTrue(item.enabled)
        assertEquals(ScheduleType.HARD_BLOCK, item.type)

        // Toggle to disabled (F68)
        viewModel.onToggleSchedule(scheduleId, false)
        val toggledState = viewModel.uiState.first { !it.schedules.first().enabled }
        assertFalse(toggledState.schedules.first().enabled)

        // Delete schedule (F68)
        viewModel.onDeleteSchedule(scheduleId)
        val deletedState = viewModel.uiState.first { it.schedules.isEmpty() }
        assertTrue(deletedState.schedules.isEmpty())
    }
}
