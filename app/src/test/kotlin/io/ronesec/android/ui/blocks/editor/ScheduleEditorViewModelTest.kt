package io.ronesec.android.ui.blocks.editor

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.ronesec.android.R
import io.ronesec.android.data.PolicyStore
import io.ronesec.android.data.WattimDatabase
import io.ronesec.domain.model.CompiledSchedule
import io.ronesec.domain.model.FakeWallClock
import io.ronesec.domain.model.ScheduleOverride
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
import java.time.DayOfWeek
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ScheduleEditorViewModelTest {

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

    private fun createStore(testScope: TestScope, dispatcher: kotlinx.coroutines.CoroutineDispatcher): PolicyStore {
        return PolicyStore(
            database = database,
            wallClock = wallClock,
            scope = testScope.backgroundScope,
            ioDispatcher = dispatcher
        )
    }

    private fun createViewModel(
        testScope: TestScope,
        store: PolicyStore,
        scheduleId: Long? = null,
        dispatcher: kotlinx.coroutines.CoroutineDispatcher
    ): ScheduleEditorViewModel {
        return ScheduleEditorViewModel(
            scheduleId = scheduleId,
            policyStore = store,
            coroutineScope = testScope.backgroundScope,
            ioDispatcher = dispatcher
        )
    }

    @Test
    fun initializationWithNoScheduleIdDefaultsToWorkWeekAndAllTargets() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = createStore(this, dispatcher)

        store.saveTarget(TargetConfig("com.app.one", "App One")).getOrThrow()
        store.saveTarget(TargetConfig("com.app.two", "App Two")).getOrThrow()
        advanceUntilIdle()

        val viewModel = createViewModel(this, store, scheduleId = null, dispatcher = dispatcher)
        advanceUntilIdle()

        val state = viewModel.uiState.first { it.availableTargets.size == 2 }
        assertEquals("", state.draft.name)
        assertEquals(ScheduleType.HARD_BLOCK, state.draft.type)
        assertEquals(
            setOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY
            ),
            state.draft.selectedDays
        )
        assertEquals(9, state.draft.startHour)
        assertEquals(0, state.draft.startMinute)
        assertEquals(18, state.draft.endHour)
        assertEquals(0, state.draft.endMinute)
        assertEquals(setOf("com.app.one", "com.app.two"), state.draft.selectedPackages)
        assertFalse(state.isOvernight)
        assertFalse(state.canSave) // Empty name
        assertEquals(R.string.error_empty_name, state.validationErrorResId)
    }

    @Test
    fun initializationWithExistingScheduleLoadsAllFields() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = createStore(this, dispatcher)

        store.saveTarget(TargetConfig("com.app.one", "App One")).getOrThrow()
        store.saveTarget(TargetConfig("com.app.two", "App Two")).getOrThrow()

        // Weekdays mask: Saturday (32) + Sunday (64) = 96
        val existing = CompiledSchedule(
            id = 0L,
            name = "Weekend Focus",
            weekdayMask = 96,
            startMinute = 10 * 60 + 30,
            endMinute = 20 * 60,
            enabled = true,
            type = ScheduleType.INTERVENTION,
            targetPackages = setOf("com.app.one"),
            overrides = mapOf("com.app.one" to ScheduleOverride(durationMs = 15000L, reinterventionMs = 60000L))
        )
        val savedId = store.saveSchedule(existing).getOrThrow()
        advanceUntilIdle()

        val viewModel = createViewModel(this, store, scheduleId = savedId, dispatcher = dispatcher)
        advanceUntilIdle()

        val state = viewModel.uiState.first { it.draft.scheduleId == savedId }
        assertEquals("Weekend Focus", state.draft.name)
        assertEquals(ScheduleType.INTERVENTION, state.draft.type)
        assertEquals(setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), state.draft.selectedDays)
        assertEquals(10, state.draft.startHour)
        assertEquals(30, state.draft.startMinute)
        assertEquals(20, state.draft.endHour)
        assertEquals(0, state.draft.endMinute)
        assertEquals(setOf("com.app.one"), state.draft.selectedPackages)
        assertEquals(15000L, state.draft.overrides["com.app.one"]?.durationMs)
        assertEquals(60000L, state.draft.overrides["com.app.one"]?.reinterventionMs)
        assertTrue(state.canSave)
    }

    @Test
    fun switchingTypePreservesOverrides() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = createStore(this, dispatcher)
        store.saveTarget(TargetConfig("com.app.one", "App One")).getOrThrow()
        advanceUntilIdle()

        val viewModel = createViewModel(this, store, scheduleId = null, dispatcher = dispatcher)
        advanceUntilIdle()

        viewModel.onTypeChange(ScheduleType.INTERVENTION)
        viewModel.onSetDurationOverride("com.app.one", 12000L)
        viewModel.onSetRepeatOverride("com.app.one", 180000L)

        var state = viewModel.uiState.value
        assertEquals(12000L, state.draft.overrides["com.app.one"]?.durationMs)
        assertEquals(180000L, state.draft.overrides["com.app.one"]?.reinterventionMs)

        // Switch to HARD_BLOCK: overrides preserved in draft
        viewModel.onTypeChange(ScheduleType.HARD_BLOCK)
        state = viewModel.uiState.value
        assertEquals(ScheduleType.HARD_BLOCK, state.draft.type)
        assertEquals(12000L, state.draft.overrides["com.app.one"]?.durationMs)

        // Switch back to INTERVENTION: still intact
        viewModel.onTypeChange(ScheduleType.INTERVENTION)
        state = viewModel.uiState.value
        assertEquals(ScheduleType.INTERVENTION, state.draft.type)
        assertEquals(12000L, state.draft.overrides["com.app.one"]?.durationMs)
    }

    @Test
    fun applyingPresetUpdatesStartAndEndTimesAndOvernightState() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = createStore(this, dispatcher)
        val viewModel = createViewModel(this, store, scheduleId = null, dispatcher = dispatcher)
        advanceUntilIdle()

        // Apply NIGHT preset (23:00 - 07:00)
        viewModel.onApplyPreset(SchedulePreset.NIGHT)
        var state = viewModel.uiState.value
        assertEquals(23, state.draft.startHour)
        assertEquals(0, state.draft.startMinute)
        assertEquals(7, state.draft.endHour)
        assertEquals(0, state.draft.endMinute)
        assertTrue(state.isOvernight)

        // Apply MORNING preset (07:00 - 12:00)
        viewModel.onApplyPreset(SchedulePreset.MORNING)
        state = viewModel.uiState.value
        assertEquals(7, state.draft.startHour)
        assertEquals(12, state.draft.endHour)
        assertFalse(state.isOvernight)
    }

    @Test
    fun validationRulesPreventSaveWhenInvalid() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = createStore(this, dispatcher)
        store.saveTarget(TargetConfig("com.app.one", "App One")).getOrThrow()
        advanceUntilIdle()

        val viewModel = createViewModel(this, store, scheduleId = null, dispatcher = dispatcher)
        viewModel.uiState.first { it.availableTargets.isNotEmpty() }

        // Initially invalid due to empty name
        assertFalse(viewModel.uiState.value.canSave)
        assertEquals(R.string.error_empty_name, viewModel.uiState.value.validationErrorResId)

        // Set name -> now valid
        viewModel.onNameChange("Valid Name")
        assertTrue(viewModel.uiState.value.canSave)
        assertNull(viewModel.uiState.value.validationErrorResId)

        // Clear all days -> invalid
        val currentDays = viewModel.uiState.value.draft.selectedDays.toList()
        currentDays.forEach { viewModel.onToggleDay(it) }
        assertFalse(viewModel.uiState.value.canSave)
        assertEquals(R.string.error_empty_days, viewModel.uiState.value.validationErrorResId)

        // Restore one day
        viewModel.onToggleDay(DayOfWeek.MONDAY)
        assertTrue(viewModel.uiState.value.canSave)

        // Equal times -> invalid
        viewModel.onStartTimeChange(10, 0)
        viewModel.onEndTimeChange(10, 0)
        assertFalse(viewModel.uiState.value.canSave)
        assertEquals(R.string.error_equal_times, viewModel.uiState.value.validationErrorResId)

        // Fix time
        viewModel.onEndTimeChange(11, 0)
        assertTrue(viewModel.uiState.value.canSave)

        // Empty targets -> invalid
        viewModel.onSelectNoneTargets()
        assertFalse(viewModel.uiState.value.canSave)
        assertEquals(R.string.error_empty_targets, viewModel.uiState.value.validationErrorResId)
    }

    @Test
    fun savingSchedulePersistsToPolicyStoreAndSignalsSaved() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = createStore(this, dispatcher)
        store.saveTarget(TargetConfig("com.app.one", "App One")).getOrThrow()
        advanceUntilIdle()

        val viewModel = createViewModel(this, store, scheduleId = null, dispatcher = dispatcher)
        advanceUntilIdle()

        viewModel.onNameChange("Night Lock")
        viewModel.onApplyPreset(SchedulePreset.NIGHT)
        viewModel.onToggleTarget("com.app.one") // Ensure selected
        assertTrue(viewModel.uiState.value.canSave)

        viewModel.onSave()
        advanceUntilIdle()

        val state = viewModel.uiState.first { it.isSaved }
        assertTrue(state.isSaved)
        assertFalse(state.isLoading)

        // Verify in database
        val snapshot = store.snapshotFlow.first { it.activeSchedules.any { s -> s.name == "Night Lock" } }
        val created = snapshot.activeSchedules.find { it.name == "Night Lock" }
        assertNotNull(created)
        assertEquals(23 * 60, created!!.startMinute)
        assertEquals(7 * 60, created.endMinute)
        assertTrue(created.targetPackages.contains("com.app.one"))
    }

    @Test
    fun removingOverrideRestoresInheritance() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = createStore(this, dispatcher)
        val viewModel = createViewModel(this, store, scheduleId = null, dispatcher = dispatcher)
        advanceUntilIdle()

        // Set custom duration and repeat
        viewModel.onSetDurationOverride("com.app.one", 10000L)
        viewModel.onSetRepeatOverride("com.app.one", 60000L)
        var overrides = viewModel.uiState.value.draft.overrides
        assertEquals(10000L, overrides["com.app.one"]?.durationMs)
        assertEquals(60000L, overrides["com.app.one"]?.reinterventionMs)

        // Clear duration (inherits)
        viewModel.onSetDurationOverride("com.app.one", null)
        overrides = viewModel.uiState.value.draft.overrides
        assertNull(overrides["com.app.one"]?.durationMs)
        assertEquals(60000L, overrides["com.app.one"]?.reinterventionMs)

        // Clear repeat (inherits) -> entry removed completely
        viewModel.onSetRepeatOverride("com.app.one", null)
        overrides = viewModel.uiState.value.draft.overrides
        assertNull(overrides["com.app.one"])
    }
}
