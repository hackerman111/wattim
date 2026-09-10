package io.ronesec.android.ui.config

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import io.ronesec.android.data.PolicyStore
import io.ronesec.android.data.WattimDatabase
import io.ronesec.android.platform.audio.AudioDiagnostics
import io.ronesec.android.platform.audio.AudioFocusStatus
import io.ronesec.android.platform.audio.MediaPauseStatus
import io.ronesec.android.platform.system.FakePlatformPermissionChecker
import io.ronesec.android.platform.system.PermissionMonitor
import io.ronesec.android.platform.system.PermissionState
import io.ronesec.android.platform.system.SettingsIntentAdapter
import io.ronesec.android.ui.designsystem.ThemeId
import io.ronesec.domain.model.FakeWallClock
import io.ronesec.domain.model.SessionId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ConfigViewModelTest {

    private lateinit var database: WattimDatabase
    private lateinit var wallClock: FakeWallClock
    private lateinit var fakeChecker: FakePlatformPermissionChecker
    private lateinit var permissionMonitor: PermissionMonitor
    private lateinit var settingsAdapter: TestSettingsIntentAdapter

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = WattimDatabase.createInMemory(context)
        wallClock = FakeWallClock(Instant.parse("2026-09-09T12:00:00Z"), ZoneId.of("UTC"))
        fakeChecker = FakePlatformPermissionChecker()
        permissionMonitor = PermissionMonitor(fakeChecker)
        settingsAdapter = TestSettingsIntentAdapter(context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `initial state reflects default presentation settings and permission status`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val policyStore = PolicyStore(database, wallClock, backgroundScope, dispatcher)
        policyStore.awaitReady()
        advanceUntilIdle()

        val viewModel = ConfigViewModel(
            policyStore = policyStore,
            permissionMonitor = permissionMonitor,
            settingsIntentAdapter = settingsAdapter,
            coroutineScope = backgroundScope,
            ioDispatcher = dispatcher
        )

        val state = viewModel.uiState.first { it.selectedTheme == ThemeId.NORD }
        assertEquals(ThemeId.NORD, state.selectedTheme)
        assertEquals("AUTO", state.selectedLanguage)
        assertEquals(7, state.savedSessionMinutes)
        assertTrue(state.showOverlayStats)
        assertEquals(PermissionState.Denied, state.accessibilityState)
        assertEquals(PermissionState.Denied, state.mediaControlState)
        assertEquals(PermissionState.Denied, state.overlayState)
        assertEquals(PermissionState.Denied, state.batteryState)
        assertNull(state.errorMessage)
    }

    @Test
    fun `onSelectTheme updates policyStore and uiState`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val policyStore = PolicyStore(database, wallClock, backgroundScope, dispatcher)
        policyStore.awaitReady()
        advanceUntilIdle()

        val viewModel = ConfigViewModel(
            policyStore = policyStore,
            permissionMonitor = permissionMonitor,
            settingsIntentAdapter = settingsAdapter,
            coroutineScope = backgroundScope,
            ioDispatcher = dispatcher
        )

        viewModel.onSelectTheme(ThemeId.CYBER_TERMINAL)
        advanceUntilIdle()

        val state = viewModel.uiState.first { it.selectedTheme == ThemeId.CYBER_TERMINAL }
        assertEquals(ThemeId.CYBER_TERMINAL, state.selectedTheme)
    }

    @Test
    fun `onSelectLanguage updates policyStore and uiState`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val policyStore = PolicyStore(database, wallClock, backgroundScope, dispatcher)
        policyStore.awaitReady()
        advanceUntilIdle()

        val viewModel = ConfigViewModel(
            policyStore = policyStore,
            permissionMonitor = permissionMonitor,
            settingsIntentAdapter = settingsAdapter,
            coroutineScope = backgroundScope,
            ioDispatcher = dispatcher
        )

        viewModel.onSelectLanguage("РУССКИЙ")
        advanceUntilIdle()

        val state = viewModel.uiState.first { it.selectedLanguage == "РУССКИЙ" }
        assertEquals("РУССКИЙ", state.selectedLanguage)
    }

    @Test
    fun `onSelectSavedSessionMinutes updates policyStore and uiState`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val policyStore = PolicyStore(database, wallClock, backgroundScope, dispatcher)
        policyStore.awaitReady()
        advanceUntilIdle()

        val viewModel = ConfigViewModel(
            policyStore = policyStore,
            permissionMonitor = permissionMonitor,
            settingsIntentAdapter = settingsAdapter,
            coroutineScope = backgroundScope,
            ioDispatcher = dispatcher
        )

        viewModel.onSelectSavedSessionMinutes(15)
        advanceUntilIdle()

        val state = viewModel.uiState.first { it.savedSessionMinutes == 15 }
        assertEquals(15, state.savedSessionMinutes)
    }

    @Test
    fun `onToggleShowOverlayStats updates policyStore and uiState`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val policyStore = PolicyStore(database, wallClock, backgroundScope, dispatcher)
        policyStore.awaitReady()
        advanceUntilIdle()

        val viewModel = ConfigViewModel(
            policyStore = policyStore,
            permissionMonitor = permissionMonitor,
            settingsIntentAdapter = settingsAdapter,
            coroutineScope = backgroundScope,
            ioDispatcher = dispatcher
        )

        viewModel.onToggleShowOverlayStats(false)
        advanceUntilIdle()

        val state = viewModel.uiState.first { !it.showOverlayStats }
        assertFalse(state.showOverlayStats)
    }

    @Test
    fun `permission monitor status updates reflected in uiState`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val policyStore = PolicyStore(database, wallClock, backgroundScope, dispatcher)
        policyStore.awaitReady()
        advanceUntilIdle()

        val viewModel = ConfigViewModel(
            policyStore = policyStore,
            permissionMonitor = permissionMonitor,
            settingsIntentAdapter = settingsAdapter,
            coroutineScope = backgroundScope,
            ioDispatcher = dispatcher
        )

        fakeChecker.accessibilityEnabled = true
        fakeChecker.mediaControlEnabled = true
        fakeChecker.overlayAllowed = true
        fakeChecker.batteryOptimizationIgnored = true
        viewModel.onRefreshPermissions()
        advanceUntilIdle()

        val state = viewModel.uiState.first { it.accessibilityState == PermissionState.Granted }
        assertEquals(PermissionState.Granted, state.accessibilityState)
        assertEquals(PermissionState.Granted, state.mediaControlState)
        assertEquals(PermissionState.Granted, state.overlayState)
        assertEquals(PermissionState.Granted, state.batteryState)
    }

    @Test
    fun `permission actions launch settings intents via adapter`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val policyStore = PolicyStore(database, wallClock, backgroundScope, dispatcher)
        policyStore.awaitReady()
        advanceUntilIdle()

        val viewModel = ConfigViewModel(
            policyStore = policyStore,
            permissionMonitor = permissionMonitor,
            settingsIntentAdapter = settingsAdapter,
            coroutineScope = backgroundScope,
            ioDispatcher = dispatcher
        )

        viewModel.onEnableAccessibility()
        assertEquals(1, settingsAdapter.launchedIntents.size)

        viewModel.onEnableMediaControl()
        assertEquals(2, settingsAdapter.launchedIntents.size)

        viewModel.onEnableOverlay()
        assertEquals(3, settingsAdapter.launchedIntents.size)

        viewModel.onEnableBattery()
        assertEquals(4, settingsAdapter.launchedIntents.size)

        viewModel.onOpenAppInfo()
        assertEquals(5, settingsAdapter.launchedIntents.size)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `audio diagnostics remain visible after lease release`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val diagnostics = AudioDiagnostics()
        val sessionId = SessionId(1L, 2L, 3L)
        val viewModelScope = CoroutineScope(SupervisorJob() + dispatcher)
        val viewModel = ConfigViewModel(
            policyStore = null,
            permissionMonitor = permissionMonitor,
            audioDiagnostics = diagnostics,
            settingsIntentAdapter = settingsAdapter,
            coroutineScope = viewModelScope,
            ioDispatcher = dispatcher
        )
        testScheduler.runCurrent()

        diagnostics.begin(sessionId, "org.telegram.messenger")
        diagnostics.setFocus(sessionId, AudioFocusStatus.DENIED)
        diagnostics.setMediaPause(sessionId, MediaPauseStatus.NOT_CONFIRMED)
        diagnostics.release(sessionId)
        advanceUntilIdle()

        assertEquals("org.telegram.messenger", viewModel.uiState.value.audioDiagnostic.packageName)
        assertEquals(AudioFocusStatus.DENIED, viewModel.uiState.value.audioDiagnostic.focusStatus)
        assertEquals(MediaPauseStatus.NOT_CONFIRMED, viewModel.uiState.value.audioDiagnostic.mediaPauseStatus)
        assertFalse(viewModel.uiState.value.audioDiagnostic.active)
        viewModelScope.cancel()
    }

    @Test
    fun `intent launch failure sets error message and onDismissError clears it`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val policyStore = PolicyStore(database, wallClock, backgroundScope, dispatcher)
        policyStore.awaitReady()
        advanceUntilIdle()

        settingsAdapter.shouldFailLaunch = true

        val viewModel = ConfigViewModel(
            policyStore = policyStore,
            permissionMonitor = permissionMonitor,
            settingsIntentAdapter = settingsAdapter,
            coroutineScope = backgroundScope,
            ioDispatcher = dispatcher
        )

        viewModel.onEnableAccessibility()
        assertNotNull(viewModel.uiState.value.errorMessage)

        viewModel.onDismissError()
        assertNull(viewModel.uiState.value.errorMessage)
    }

    private class TestSettingsIntentAdapter(context: Context) : SettingsIntentAdapter(context) {
        val launchedIntents = mutableListOf<Intent>()
        var shouldFailLaunch: Boolean = false

        override fun launchSafely(intent: Intent): Result<Unit> {
            return if (shouldFailLaunch) {
                Result.failure(SecurityException("Activity not found"))
            } else {
                launchedIntents.add(intent)
                Result.success(Unit)
            }
        }
    }
}
