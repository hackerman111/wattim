package io.ronesec.android.ui.target

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.ronesec.android.data.PolicyStore
import io.ronesec.android.data.WattimDatabase
import io.ronesec.android.ui.designsystem.TerminalTab
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TargetSettingsViewModelTest {

    private lateinit var database: WattimDatabase
    private lateinit var wallClock: FakeWallClock
    private val targetPackage = "com.sample.target"

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = WattimDatabase.createInMemory(context)
        wallClock = FakeWallClock(Instant.parse("2026-09-09T15:00:00Z"))
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun createViewModel(
        scope: kotlinx.coroutines.CoroutineScope,
        dispatcher: kotlinx.coroutines.CoroutineDispatcher
    ): Pair<PolicyStore, TargetSettingsViewModel> {
        val store = PolicyStore(database, wallClock, scope, dispatcher)
        val viewModel = TargetSettingsViewModel(
            packageName = targetPackage,
            originTab = TerminalTab.APPS,
            canQuickLock = false,
            policyStore = store,
            coroutineScope = scope,
            ioDispatcher = dispatcher
        )
        return Pair(store, viewModel)
    }

    @Test
    fun initialDraftLoadsExistingTargetOrDefaults() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val (store, viewModel) = createViewModel(backgroundScope, dispatcher)
        store.awaitReady()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(targetPackage, state.packageName)
        assertEquals(targetPackage, state.displayName)
        assertEquals(8, state.draft.durationSeconds)
        assertEquals(AnimationMode.FILL, state.draft.animation)
        assertEquals(10, state.calculatedDelays.size)
        // First delay is base (8000ms)
        assertEquals(8000L, state.calculatedDelays[0])
    }

    @Test
    fun editingDurationAndBackoffUpdatesCalculator() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val (store, viewModel) = createViewModel(backgroundScope, dispatcher)
        store.awaitReady()
        advanceUntilIdle()

        // Change duration to 10s
        viewModel.onDurationChange(10)
        // Change backoff to 50%
        viewModel.onBackoffPercentChange(50)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(10, state.draft.durationSeconds)
        assertEquals(50, state.draft.backoffPercent)

        // Verify live delay calculation: N=0..9
        // Attempt 1 (N=0): 10,000ms
        // Attempt 2 (N=1): round(10000 * 1.5) = 15,000ms
        // Attempt 3 (N=2): round(10000 * 2.25) = 22,500ms
        assertEquals(10_000L, state.calculatedDelays[0])
        assertEquals(15_000L, state.calculatedDelays[1])
        assertEquals(22_500L, state.calculatedDelays[2])
    }

    @Test
    fun phraseIsCappedAt80Chars() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val (store, viewModel) = createViewModel(backgroundScope, dispatcher)
        store.awaitReady()
        advanceUntilIdle()

        val longPhrase = "A".repeat(100)
        viewModel.onPhraseChange(longPhrase)

        val state = viewModel.uiState.value
        assertEquals(80, state.draft.phrase.length)
        assertEquals("A".repeat(80), state.draft.phrase)
    }

    @Test
    fun atomicSavePersistsAllFieldsAndSignalsSaved() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val (store, viewModel) = createViewModel(backgroundScope, dispatcher)
        store.awaitReady()
        advanceUntilIdle()

        viewModel.onPhraseChange("Breathe deeply")
        viewModel.onAnimationChange(AnimationMode.WAVE)
        viewModel.onDurationChange(15)
        viewModel.onReinterventionChoice(ReinterventionChoice.MIN_10)
        viewModel.onQuickReturnChange(30_000L)
        viewModel.onBackoffEnabledChange(true)
        viewModel.onBackoffPercentChange(30)
        viewModel.onBackoffWindowChange(120 * 60 * 1000L)

        viewModel.onSave()
        advanceUntilIdle()

        val state = viewModel.uiState.first { it.isSaved }
        assertTrue(state.isSaved)

        val saved = store.currentSnapshot.targets[targetPackage]
        assertNotNull(saved)
        assertEquals("Breathe deeply", saved!!.phrase)
        assertEquals(AnimationMode.WAVE, saved.animation)
        assertEquals(15_000L, saved.durationMs)
        assertEquals(600_000L, saved.reinterventionMs)
        assertEquals(30_000L, saved.quickReturnGraceMs)
        assertTrue(saved.growthConfig.enabled)
        assertEquals(30, saved.growthConfig.percent)
        assertEquals(120 * 60 * 1000L, saved.growthConfig.windowMs)
    }

    @Test
    fun selectingFill2AnimationPersistsCorrectly() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val (store, viewModel) = createViewModel(backgroundScope, dispatcher)
        store.awaitReady()
        advanceUntilIdle()

        viewModel.onAnimationChange(AnimationMode.FILL_2)
        assertEquals(AnimationMode.FILL_2, viewModel.uiState.value.draft.animation)

        viewModel.onSave()
        advanceUntilIdle()

        val state = viewModel.uiState.first { it.isSaved }
        assertTrue(state.isSaved)

        val saved = store.currentSnapshot.targets[targetPackage]
        assertNotNull(saved)
        assertEquals(AnimationMode.FILL_2, saved!!.animation)
    }

    @Test
    fun immediateRemoveCleansTargetWithoutConfirmation() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val (store, viewModel) = createViewModel(backgroundScope, dispatcher)
        store.awaitReady()

        store.saveTarget(TargetConfig(packageName = targetPackage, displayName = "Sample Target"))
        advanceUntilIdle()

        assertNotNull(store.currentSnapshot.targets[targetPackage])

        viewModel.onRemove()
        advanceUntilIdle()

        val state = viewModel.uiState.first { it.isRemoved }
        assertTrue(state.isRemoved)
        assertNull(store.currentSnapshot.targets[targetPackage])
    }

    @Test
    fun previewDialogToggles() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val (store, viewModel) = createViewModel(backgroundScope, dispatcher)
        store.awaitReady()

        assertFalse(viewModel.uiState.value.isPreviewOpen)
        viewModel.onOpenPreview()
        assertTrue(viewModel.uiState.value.isPreviewOpen)
        viewModel.onDismissPreview()
        assertFalse(viewModel.uiState.value.isPreviewOpen)
    }

    @Test
    fun quickLockIgnoredWhenCanQuickLockIsFalse() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val (store, viewModel) = createViewModel(backgroundScope, dispatcher)
        store.awaitReady()
        advanceUntilIdle()

        viewModel.onQuickLock(15 * 60 * 1000L)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isQuickLocked)
        assertTrue(store.currentSnapshot.activeBlockSessions.isEmpty())
    }

    @Test
    fun quickLockCreatesBlockSessionAndSetsQuickLockedWhenPermitted() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = PolicyStore(database, wallClock, backgroundScope, dispatcher)
        val viewModel = TargetSettingsViewModel(
            packageName = targetPackage,
            originTab = TerminalTab.BLOCK,
            canQuickLock = true,
            policyStore = store,
            wallClock = wallClock,
            coroutineScope = backgroundScope,
            ioDispatcher = dispatcher
        )
        store.awaitReady()
        store.saveTarget(TargetConfig(packageName = targetPackage, displayName = "Sample Target"))
        advanceUntilIdle()

        // 30 minute quick lock (F60)
        val durationMs = 30 * 60 * 1000L
        viewModel.onQuickLock(durationMs)

        val state = viewModel.uiState.first { it.isQuickLocked }
        assertTrue(state.isQuickLocked)
        val sessions = store.currentSnapshot.activeBlockSessions
        assertEquals(1, sessions.size)
        val session = sessions.first()
        assertTrue(session.active)
        assertEquals(setOf(targetPackage), session.targetPackages)
        assertEquals(wallClock.now().plusMillis(durationMs), session.endTime)
    }

    @Test
    fun randomDurationToggleAndMaxDurationEditing() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val (store, viewModel) = createViewModel(backgroundScope, dispatcher)
        store.awaitReady()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.draft.randomDurationEnabled)
        assertEquals(8, viewModel.uiState.value.draft.randomMaxDurationSeconds)

        viewModel.onToggleRandomDuration()
        assertTrue(viewModel.uiState.value.draft.randomDurationEnabled)

        viewModel.onRandomMaxDurationChange(20)
        assertEquals(20, viewModel.uiState.value.draft.randomMaxDurationSeconds)

        viewModel.onSave()
        advanceUntilIdle()

        val state = viewModel.uiState.first { it.isSaved }
        assertTrue(state.isSaved)

        val saved = store.currentSnapshot.targets[targetPackage]
        assertNotNull(saved)
        assertTrue(saved!!.randomDurationEnabled)
        assertEquals(20_000L, saved.randomMaxDurationMs)
    }

    @Test
    fun randomDurationAdditionIsIndependentOfBaseDuration() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val (store, viewModel) = createViewModel(backgroundScope, dispatcher)
        store.awaitReady()
        advanceUntilIdle()

        viewModel.onToggleRandomDuration()
        viewModel.onDurationChange(10)
        viewModel.onRandomMaxDurationChange(15)
        assertEquals(15, viewModel.uiState.value.draft.randomMaxDurationSeconds)

        // Change base duration to 25s > 15s - random addition must remain 15s
        viewModel.onDurationChange(25)
        assertEquals(25, viewModel.uiState.value.draft.durationSeconds)
        assertEquals(15, viewModel.uiState.value.draft.randomMaxDurationSeconds)

        // Set random addition to 5s < 25s - must be allowed
        viewModel.onRandomMaxDurationChange(5)
        assertEquals(5, viewModel.uiState.value.draft.randomMaxDurationSeconds)
    }
}
