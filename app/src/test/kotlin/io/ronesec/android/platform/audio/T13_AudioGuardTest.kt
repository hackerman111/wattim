package io.ronesec.android.platform.audio

import android.media.AudioManager
import io.ronesec.domain.model.SessionId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class T13_AudioGuardTest {

    private class FakeAudioDeviceAdapter : AudioDeviceAdapter {
        var focusRequestCount = 0
        var abandonFocusCount = 0
        var pauseKeyCount = 0
        var focusResult = AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        var lastFocusListener: ((Int) -> Unit)? = null
        val focusListeners = mutableListOf<(Int) -> Unit>()

        override fun requestTransientFocus(onFocusChange: (Int) -> Unit): Int {
            focusRequestCount++
            lastFocusListener = onFocusChange
            focusListeners += onFocusChange
            return focusResult
        }

        override fun abandonFocus(): Int {
            abandonFocusCount++
            lastFocusListener = null
            return AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }

        override fun dispatchMediaPauseKey() {
            pauseKeyCount++
        }
    }

    private class FakeMediaSessionPauseController(
        private val initialObservation: PlaybackObservation
    ) : MediaSessionPauseController {
        var observedPackage: String? = null
        var pauseRequestCount = 0
        var stopCount = 0
        var pauseResult = PauseDispatchResult.SENT
        private var observer: ((PlaybackObservation) -> Unit)? = null

        override fun start(packageName: String, observer: (PlaybackObservation) -> Unit) {
            observedPackage = packageName
            this.observer = observer
            observer(initialObservation)
        }

        override fun requestPause(): PauseDispatchResult {
            pauseRequestCount++
            return pauseResult
        }

        override fun stop() {
            stopCount++
        }

        fun emit(observation: PlaybackObservation) {
            observer?.invoke(observation)
        }
    }

    @Test
    fun `acquireAudioLease requests focus and dispatches 5 pause keys at exact intervals`() = runTest {
        val fakeAdapter = FakeAudioDeviceAdapter()
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val audioGuard = AudioGuard(
            deviceAdapter = fakeAdapter,
            strategy = BoundedPauseFallbackStrategy,
            scope = this,
            dispatcher = testDispatcher
        )

        val session = SessionId(1L, 1L, 1L)
        audioGuard.acquireAudioLease(session, "com.example.target")

        assertEquals(session, audioGuard.currentLease)
        assertEquals(1, fakeAdapter.focusRequestCount)
        assertEquals(0, fakeAdapter.abandonFocusCount)

        // Offset 0ms: first pause key dispatched immediately
        testScheduler.runCurrent()
        assertEquals(1, fakeAdapter.pauseKeyCount)

        // Advance 150ms -> 2nd key
        advanceTimeBy(150L)
        testScheduler.runCurrent()
        assertEquals(2, fakeAdapter.pauseKeyCount)

        // Advance 250ms (offset 400ms) -> 3rd key
        advanceTimeBy(250L)
        testScheduler.runCurrent()
        assertEquals(3, fakeAdapter.pauseKeyCount)

        // Advance 400ms (offset 800ms) -> 4th key
        advanceTimeBy(400L)
        testScheduler.runCurrent()
        assertEquals(4, fakeAdapter.pauseKeyCount)

        // Advance 400ms (offset 1200ms) -> 5th key
        advanceTimeBy(400L)
        testScheduler.runCurrent()
        assertEquals(5, fakeAdapter.pauseKeyCount)

        // Advancing further doesn't send more keys
        advanceTimeBy(1000L)
        testScheduler.runCurrent()
        assertEquals(5, fakeAdapter.pauseKeyCount)
    }

    @Test
    fun `releaseAudioLease cancels pending pause keys and abandons focus`() = runTest {
        val fakeAdapter = FakeAudioDeviceAdapter()
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val audioGuard = AudioGuard(
            deviceAdapter = fakeAdapter,
            strategy = BoundedPauseFallbackStrategy,
            scope = this,
            dispatcher = testDispatcher
        )

        val session = SessionId(1L, 1L, 1L)
        audioGuard.acquireAudioLease(session, "com.example.target")
        testScheduler.runCurrent()
        assertEquals(1, fakeAdapter.pauseKeyCount)

        // Release at 200ms (after first pause key, before 3rd)
        advanceTimeBy(200L)
        testScheduler.runCurrent()
        assertEquals(2, fakeAdapter.pauseKeyCount) // key at 0ms and 150ms

        audioGuard.releaseAudioLease(session)
        assertNull(audioGuard.currentLease)
        assertEquals(1, fakeAdapter.abandonFocusCount)

        // Advance time - no further keys should be dispatched
        advanceTimeBy(2000L)
        testScheduler.runCurrent()
        assertEquals(2, fakeAdapter.pauseKeyCount)
    }

    @Test
    fun `stale release from different sessionId does not cancel active lease`() = runTest {
        val fakeAdapter = FakeAudioDeviceAdapter()
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val audioGuard = AudioGuard(
            deviceAdapter = fakeAdapter,
            strategy = BoundedPauseFallbackStrategy,
            scope = this,
            dispatcher = testDispatcher
        )

        val session1 = SessionId(1L, 1L, 1L)
        val session2 = SessionId(1L, 1L, 2L)

        audioGuard.acquireAudioLease(session1, "com.example.target")
        testScheduler.runCurrent()

        // Attempt to release with different sessionId
        audioGuard.releaseAudioLease(session2)

        // Active lease should still be session1
        assertEquals(session1, audioGuard.currentLease)
        assertEquals(0, fakeAdapter.abandonFocusCount)
    }

    @Test
    fun `focus loss triggers bounded recovery up to maximum attempts`() = runTest {
        val fakeAdapter = FakeAudioDeviceAdapter()
        val diagnostics = AudioDiagnostics()
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val audioGuard = AudioGuard(
            deviceAdapter = fakeAdapter,
            diagnostics = diagnostics,
            strategy = FocusOnlyStrategy,
            scope = this,
            dispatcher = testDispatcher
        )

        val session = SessionId(1L, 1L, 1L)
        audioGuard.acquireAudioLease(session, "com.example.target")
        assertEquals(1, fakeAdapter.focusRequestCount)
        assertEquals(0, fakeAdapter.pauseKeyCount) // FocusOnlyStrategy sends 0 pause keys
        assertEquals(AudioFocusStatus.GRANTED, diagnostics.state.value.focusStatus)

        // 1st transient loss -> recovery attempt 1
        fakeAdapter.lastFocusListener?.invoke(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        assertEquals(2, fakeAdapter.focusRequestCount)

        // A callback queued by the replaced request must not start another recovery.
        fakeAdapter.focusListeners.first().invoke(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        assertEquals(2, fakeAdapter.focusRequestCount)

        // 2nd transient loss -> recovery attempt 2
        fakeAdapter.lastFocusListener?.invoke(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        assertEquals(3, fakeAdapter.focusRequestCount)

        // 3rd transient loss -> bounded limit reached, no infinite war
        fakeAdapter.lastFocusListener?.invoke(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        assertEquals(3, fakeAdapter.focusRequestCount)
        assertEquals(AudioFocusStatus.LOST, diagnostics.state.value.focusStatus)
    }

    @Test
    fun `targeted controller pauses only protected package and confirms callback`() = runTest {
        val device = FakeAudioDeviceAdapter()
        val media = FakeMediaSessionPauseController(PlaybackObservation.PLAYING)
        val diagnostics = AudioDiagnostics()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val guard = AudioGuard(
            deviceAdapter = device,
            mediaSessionController = media,
            diagnostics = diagnostics,
            strategy = FocusOnlyStrategy,
            scope = this,
            dispatcher = dispatcher
        )
        val session = SessionId(2L, 3L, 4L)

        guard.acquireAudioLease(session, "org.telegram.messenger")
        testScheduler.runCurrent()

        assertEquals("org.telegram.messenger", media.observedPackage)
        assertEquals(1, media.pauseRequestCount)
        assertEquals(MediaPauseStatus.COMMAND_SENT, diagnostics.state.value.mediaPauseStatus)

        media.emit(PlaybackObservation.PAUSED)
        assertEquals(MediaPauseStatus.CONFIRMED_PAUSED, diagnostics.state.value.mediaPauseStatus)
    }

    @Test
    fun `late session and resumed playback trigger bounded targeted pause while lease is active`() = runTest {
        val media = FakeMediaSessionPauseController(PlaybackObservation.NO_SESSION)
        val diagnostics = AudioDiagnostics()
        val guard = AudioGuard(
            deviceAdapter = FakeAudioDeviceAdapter(),
            mediaSessionController = media,
            diagnostics = diagnostics,
            strategy = FocusOnlyStrategy,
            scope = this,
            dispatcher = StandardTestDispatcher(testScheduler)
        )
        val session = SessionId(2L, 3L, 5L)
        guard.acquireAudioLease(session, "org.telegram.messenger")
        testScheduler.runCurrent()
        assertEquals(0, media.pauseRequestCount)
        assertEquals(MediaPauseStatus.NO_ACTIVE_SESSION, diagnostics.state.value.mediaPauseStatus)

        media.emit(PlaybackObservation.PLAYING)
        testScheduler.runCurrent()
        assertEquals(1, media.pauseRequestCount)
        media.emit(PlaybackObservation.PAUSED)

        media.emit(PlaybackObservation.PLAYING)
        testScheduler.runCurrent()
        assertEquals(2, media.pauseRequestCount)

        repeat(AudioGuard.MAX_TARGETED_COMMANDS_PER_LEASE - 2) {
            media.emit(PlaybackObservation.PAUSED)
            media.emit(PlaybackObservation.PLAYING)
            testScheduler.runCurrent()
        }
        assertEquals(AudioGuard.MAX_TARGETED_COMMANDS_PER_LEASE, media.pauseRequestCount)

        media.emit(PlaybackObservation.PAUSED)
        media.emit(PlaybackObservation.PLAYING)
        advanceTimeBy(2_000L)
        testScheduler.runCurrent()
        assertEquals(AudioGuard.MAX_TARGETED_COMMANDS_PER_LEASE, media.pauseRequestCount)

        guard.releaseAudioLease(session)
        assertEquals(1, media.stopCount)
        media.emit(PlaybackObservation.PLAYING)
        testScheduler.runCurrent()
        assertEquals(AudioGuard.MAX_TARGETED_COMMANDS_PER_LEASE, media.pauseRequestCount)
    }

    @Test
    fun `focus denial and unconfirmed media pause remain diagnostic outcomes`() = runTest {
        val device = FakeAudioDeviceAdapter().apply {
            focusResult = AudioManager.AUDIOFOCUS_REQUEST_FAILED
        }
        val media = FakeMediaSessionPauseController(PlaybackObservation.PLAYING)
        val diagnostics = AudioDiagnostics()
        val guard = AudioGuard(
            deviceAdapter = device,
            mediaSessionController = media,
            diagnostics = diagnostics,
            strategy = FocusOnlyStrategy,
            scope = this,
            dispatcher = StandardTestDispatcher(testScheduler)
        )

        guard.acquireAudioLease(SessionId(2L, 3L, 6L), "org.telegram.messenger")
        advanceTimeBy(1_000L)
        testScheduler.runCurrent()

        assertEquals(AudioFocusStatus.DENIED, diagnostics.state.value.focusStatus)
        assertEquals(MediaPauseStatus.NOT_CONFIRMED, diagnostics.state.value.mediaPauseStatus)
        assertTrue(diagnostics.state.value.targetedCommands in 1..AudioGuard.MAX_TARGETED_COMMANDS_PER_LEASE)
    }

    @Test
    fun `target session with unknown playback state is paused but not falsely confirmed`() = runTest {
        val media = FakeMediaSessionPauseController(PlaybackObservation.OTHER)
        val diagnostics = AudioDiagnostics()
        val guard = AudioGuard(
            deviceAdapter = FakeAudioDeviceAdapter(),
            mediaSessionController = media,
            diagnostics = diagnostics,
            strategy = FocusOnlyStrategy,
            scope = this,
            dispatcher = StandardTestDispatcher(testScheduler)
        )

        guard.acquireAudioLease(SessionId(2L, 3L, 7L), "org.telegram.messenger")
        advanceTimeBy(1_000L)
        testScheduler.runCurrent()

        assertTrue(media.pauseRequestCount > 0)
        assertEquals(MediaPauseStatus.NOT_CONFIRMED, diagnostics.state.value.mediaPauseStatus)
    }
}
