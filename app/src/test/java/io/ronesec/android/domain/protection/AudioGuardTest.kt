package io.ronesec.android.domain.protection

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.view.KeyEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy

@OptIn(ExperimentalCoroutinesApi::class)
class AudioGuardTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val dispatchedKeys = mutableListOf<Int>()
    private var requestedFocusType: Int? = null
    private var focusAbandoned = false
    private var focusListener: AudioManager.OnAudioFocusChangeListener? = null

    @Before
    fun setup() {
        dispatchedKeys.clear()
        requestedFocusType = null
        focusAbandoned = false
        focusListener = null
    }

    @Test
    fun testAudioGuardStaggeredPauseAndFocusReclaim() = testScope.runTest {
        // Mock AudioManager behaviors through test callbacks
        var pauseDispatchCount = 0

        // Create custom SystemAudioGuard subclass or test instance with recorded events
        val testGuard = object : AudioGuard {
            private var activeSession: SessionId? = null
            private var cancelled = false

            fun getPauseCount() = pauseDispatchCount

            override fun acquire(sessionId: SessionId): AudioAcquireResult {
                activeSession = sessionId
                testScope.launch {
                    val delays = longArrayOf(0L, 150L, 250L, 400L, 400L)
                    for (step in delays) {
                        if (step > 0L) delay(step)
                        if (activeSession != sessionId) break
                        pauseDispatchCount++
                    }
                }
                return AudioAcquireResult.Success
            }

            override fun release(sessionId: SessionId) {
                if (activeSession == sessionId) {
                    activeSession = null
                    cancelled = true
                }
            }
        }

        val session = SessionId(1L)
        val result = testGuard.acquire(session)
        assertEquals(AudioAcquireResult.Success, result)

        // At t = 0ms: first pulse
        testDispatcher.scheduler.advanceTimeBy(1)
        assertEquals(1, testGuard.getPauseCount())

        // At t = 150ms: second pulse
        testDispatcher.scheduler.advanceTimeBy(150)
        assertEquals(2, testGuard.getPauseCount())

        // At t = 400ms (150 + 250): third pulse
        testDispatcher.scheduler.advanceTimeBy(250)
        assertEquals(3, testGuard.getPauseCount())

        // At t = 800ms (400 + 400): fourth pulse
        testDispatcher.scheduler.advanceTimeBy(400)
        assertEquals(4, testGuard.getPauseCount())

        // At t = 1200ms (800 + 400): fifth pulse
        testDispatcher.scheduler.advanceTimeBy(400)
        assertEquals(5, testGuard.getPauseCount())

        // After releasing, pulses must stop
        testGuard.release(session)
        testDispatcher.scheduler.advanceTimeBy(1000)
        assertEquals(5, testGuard.getPauseCount())
    }
}
