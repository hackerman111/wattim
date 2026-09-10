package io.ronesec.android.platform.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import io.ronesec.domain.model.AnimationMode
import io.ronesec.domain.model.SessionId
import io.ronesec.domain.policy.EffectiveInterventionConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class T10_OverlayHostAndLifecycleTest {

    private class FakeWindowManagerAdapter : WindowManagerAdapter {
        val views = mutableListOf<View>()
        val paramsList = mutableListOf<ViewGroup.LayoutParams>()
        var removeCount = 0
        var shouldThrowOnAdd = false

        override fun addView(view: View, params: ViewGroup.LayoutParams) {
            if (shouldThrowOnAdd) {
                throw SecurityException("Permission denial")
            }
            views.add(view)
            paramsList.add(params)
        }

        override fun updateViewLayout(view: View, params: ViewGroup.LayoutParams) {}

        override fun removeView(view: View) {
            views.remove(view)
            removeCount++
        }
    }

    private class FakeActionDispatcher : OverlayActionDispatcher {
        var continueCount = 0
        var exitCount = 0
        var cancelCount = 0
        var emergencyOnceCount = 0
        var emergencyTimedCount = 0
        var emergencyForeverCount = 0
        var deadlineReachedCount = 0

        override fun onContinue(sessionId: SessionId, cycle: Int) {
            continueCount++
        }

        override fun onExit(sessionId: SessionId?) {
            exitCount++
        }

        override fun onCancel(sessionId: SessionId?) {
            cancelCount++
        }

        override fun onBreathingDeadlineReached(sessionId: SessionId, cycle: Int) {
            deadlineReachedCount++
        }

        override fun onEmergencyOnce(sessionId: SessionId, cycle: Int) {
            emergencyOnceCount++
        }

        override fun onEmergencyTimed(sessionId: SessionId, cycle: Int, durationMs: Long) {
            emergencyTimedCount++
        }

        override fun onEmergencyForever(sessionId: SessionId, cycle: Int) {
            emergencyForeverCount++
        }
    }

    private class FakeHostListener : OverlayHostListener {
        var attachedCount = 0
        var detachedCount = 0
        var failedCount = 0
        var shouldThrowOnAttached = false
        var lastAttachedSession: SessionId? = null
        var lastDetachedSession: SessionId? = null

        override fun onOverlayAttached(sessionId: SessionId, cycle: Int) {
            if (shouldThrowOnAttached) {
                throw IllegalStateException("Late attach callback failure")
            }
            attachedCount++
            lastAttachedSession = sessionId
        }

        override fun onOverlayDetached(sessionId: SessionId) {
            detachedCount++
            lastDetachedSession = sessionId
        }

        override fun onOverlayAttachFailed(sessionId: SessionId, cycle: Int, error: Throwable) {
            failedCount++
        }
    }

    private val sampleConfig = EffectiveInterventionConfig(
        packageName = "com.example.app",
        displayName = "Example",
        phrase = "Deep breath",
        animation = AnimationMode.PULSE,
        durationMs = 5000L,
        reinterventionMs = 0L,
        quickReturnGraceMs = 0L,
        baseDurationMs = 5000L,
        backoffExponent = 0
    )

    @Test
    fun `showIntervention attaches single window with exact parameters and emits attached`() = runTest {
        val fakeWm = FakeWindowManagerAdapter()
        val fakeDispatcher = FakeActionDispatcher()
        val fakeListener = FakeHostListener()
        val context = ApplicationProvider.getApplicationContext<Context>()

        val presenter = OverlayPresenter(
            actionDispatcher = fakeDispatcher,
            policyStore = null,
            statisticsStore = null,
            scope = this
        )

        val host = OverlayHost(
            context = context,
            windowManagerAdapter = fakeWm,
            presenter = presenter,
            listener = fakeListener
        )

        val session = SessionId(1L, 1L, 1L)
        host.showIntervention(session, 1, sampleConfig)

        assertTrue(host.isWindowAttached)
        assertEquals(session, host.currentSessionId)
        assertEquals(1, fakeWm.views.size)
        assertEquals(1, fakeListener.attachedCount)
        assertEquals(session, fakeListener.lastAttachedSession)

        val params = fakeWm.paramsList.first() as WindowManager.LayoutParams
        assertEquals(WindowManager.LayoutParams.MATCH_PARENT, params.width)
        assertEquals(WindowManager.LayoutParams.MATCH_PARENT, params.height)
        assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, params.type)
        assertEquals(PixelFormat.TRANSLUCENT, params.format)
        assertEquals(WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES, params.layoutInDisplayCutoutMode)
    }

    @Test
    fun `same-session showIntervention reuses window without recreation`() = runTest {
        val fakeWm = FakeWindowManagerAdapter()
        val fakeDispatcher = FakeActionDispatcher()
        val fakeListener = FakeHostListener()
        val context = ApplicationProvider.getApplicationContext<Context>()

        val presenter = OverlayPresenter(
            actionDispatcher = fakeDispatcher,
            policyStore = null,
            statisticsStore = null,
            scope = this
        )

        val host = OverlayHost(
            context = context,
            windowManagerAdapter = fakeWm,
            presenter = presenter,
            listener = fakeListener
        )

        val session = SessionId(1L, 1L, 1L)
        host.showIntervention(session, 1, sampleConfig)
        assertEquals(1, fakeWm.views.size)
        assertEquals(1, fakeListener.attachedCount)

        // Same session update
        host.showIntervention(session, 1, sampleConfig.copy(phrase = "New phrase"))
        assertEquals(1, fakeWm.views.size)
        assertEquals(2, fakeListener.attachedCount) // Re-acknowledged without reattaching
        assertEquals(0, fakeWm.removeCount)
    }

    @Test
    fun `showBlock attaches identity-tagged window and emits attached`() = runTest {
        val fakeWm = FakeWindowManagerAdapter()
        val fakeListener = FakeHostListener()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val presenter = OverlayPresenter(FakeActionDispatcher(), null, null, this)
        val host = OverlayHost(context, fakeWm, presenter, fakeListener)
        val session = SessionId(1L, 1L, 2L)

        host.showBlock(session, 3, sampleConfig.packageName, Instant.MAX)

        assertTrue(host.isWindowAttached)
        assertEquals(session, host.currentSessionId)
        assertEquals(session, fakeListener.lastAttachedSession)
        assertEquals(1, fakeListener.attachedCount)
    }

    @Test
    fun `same-session intervention to block reuses window and acknowledges block`() = runTest {
        val fakeWm = FakeWindowManagerAdapter()
        val fakeListener = FakeHostListener()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val presenter = OverlayPresenter(FakeActionDispatcher(), null, null, this)
        val host = OverlayHost(context, fakeWm, presenter, fakeListener)
        val session = SessionId(1L, 1L, 5L)

        host.showIntervention(session, 1, sampleConfig)
        host.showBlock(session, 1, sampleConfig.packageName, Instant.MAX)

        assertEquals(1, fakeWm.views.size)
        assertEquals(0, fakeWm.removeCount)
        assertEquals(2, fakeListener.attachedCount)
        assertEquals(session, fakeListener.lastAttachedSession)
    }

    @Test
    fun `dismissOverlay removes view and emits detached`() = runTest {
        val fakeWm = FakeWindowManagerAdapter()
        val fakeDispatcher = FakeActionDispatcher()
        val fakeListener = FakeHostListener()
        val context = ApplicationProvider.getApplicationContext<Context>()

        val presenter = OverlayPresenter(
            actionDispatcher = fakeDispatcher,
            policyStore = null,
            statisticsStore = null,
            scope = this
        )

        val host = OverlayHost(
            context = context,
            windowManagerAdapter = fakeWm,
            presenter = presenter,
            listener = fakeListener
        )

        val session = SessionId(1L, 1L, 1L)
        host.showIntervention(session, 1, sampleConfig)

        // Stale dismiss from different session does nothing
        host.dismissOverlay(SessionId(1L, 1L, 2L))
        assertTrue(host.isWindowAttached)
        assertEquals(0, fakeListener.detachedCount)

        // Matching dismiss
        host.dismissOverlay(session)
        assertFalse(host.isWindowAttached)
        assertNull(host.currentSessionId)
        assertEquals(1, fakeListener.detachedCount)
        assertEquals(session, fakeListener.lastDetachedSession)
    }

    @Test
    fun `partial attach failure cleans up and reports error`() = runTest {
        val fakeWm = FakeWindowManagerAdapter().apply { shouldThrowOnAdd = true }
        val fakeDispatcher = FakeActionDispatcher()
        val fakeListener = FakeHostListener()
        val context = ApplicationProvider.getApplicationContext<Context>()

        val presenter = OverlayPresenter(
            actionDispatcher = fakeDispatcher,
            policyStore = null,
            statisticsStore = null,
            scope = this
        )

        val host = OverlayHost(
            context = context,
            windowManagerAdapter = fakeWm,
            presenter = presenter,
            listener = fakeListener
        )

        val session = SessionId(1L, 1L, 1L)
        host.showIntervention(session, 1, sampleConfig)

        assertFalse(host.isWindowAttached)
        assertNull(host.currentSessionId)
        assertEquals(1, fakeListener.failedCount)
        assertEquals(0, fakeListener.attachedCount)
    }

    @Test
    fun `late attach failure removes a window already added to WindowManager`() = runTest {
        val fakeWm = FakeWindowManagerAdapter()
        val fakeListener = FakeHostListener().apply { shouldThrowOnAttached = true }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val presenter = OverlayPresenter(FakeActionDispatcher(), null, null, this)
        val host = OverlayHost(context, fakeWm, presenter, fakeListener)

        host.showIntervention(SessionId(1L, 1L, 3L), 1, sampleConfig)

        assertFalse(host.isWindowAttached)
        assertTrue(fakeWm.views.isEmpty())
        assertEquals(1, fakeWm.removeCount)
        assertEquals(1, fakeListener.failedCount)
    }

    @Test
    fun `block attach failure reports error and retains no window`() = runTest {
        val fakeWm = FakeWindowManagerAdapter().apply { shouldThrowOnAdd = true }
        val fakeListener = FakeHostListener()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val presenter = OverlayPresenter(FakeActionDispatcher(), null, null, this)
        val host = OverlayHost(context, fakeWm, presenter, fakeListener)

        host.showBlock(SessionId(1L, 1L, 4L), 1, sampleConfig.packageName, Instant.MAX)

        assertFalse(host.isWindowAttached)
        assertEquals(1, fakeListener.failedCount)
        assertEquals(0, fakeListener.attachedCount)
    }

    @Test
    fun `presenter dispatches continue only when complete`() = runTest {
        val fakeDispatcher = FakeActionDispatcher()
        val presenter = OverlayPresenter(
            actionDispatcher = fakeDispatcher,
            policyStore = null,
            statisticsStore = null,
            scope = this
        )

        val session = SessionId(1L, 1L, 1L)
        presenter.showIntervention(session, 1, sampleConfig)

        // Early Continue before completion is rejected
        presenter.onContinueClick()
        assertEquals(0, fakeDispatcher.continueCount)

        // Mark complete
        presenter.updateOverlayComplete(session, 1)
        val state = presenter.uiState.value
        val mode = state.mode as OverlayMode.Intervention
        assertTrue(mode.isComplete)

        // Now continue succeeds
        presenter.onContinueClick()
        assertEquals(1, fakeDispatcher.continueCount)
    }

    @Test
    fun `presenter handles emergency dialog and actions`() = runTest {
        val fakeDispatcher = FakeActionDispatcher()
        val presenter = OverlayPresenter(
            actionDispatcher = fakeDispatcher,
            policyStore = null,
            statisticsStore = null,
            scope = this
        )

        val session = SessionId(1L, 1L, 1L)
        presenter.showIntervention(session, 1, sampleConfig)

        assertFalse(presenter.uiState.value.isEmergencyDialogOpen)
        presenter.openEmergencyDialog()
        assertTrue(presenter.uiState.value.isEmergencyDialogOpen)

        presenter.dismissEmergencyDialog()
        assertFalse(presenter.uiState.value.isEmergencyDialogOpen)

        // Test emergency once
        presenter.openEmergencyDialog()
        presenter.onEmergencyOnce()
        assertFalse(presenter.uiState.value.isEmergencyDialogOpen)
        assertEquals(1, fakeDispatcher.emergencyOnceCount)

        // Test emergency timed
        presenter.openEmergencyDialog()
        presenter.onEmergencyTimed(15 * 60 * 1000L)
        assertFalse(presenter.uiState.value.isEmergencyDialogOpen)
        assertEquals(1, fakeDispatcher.emergencyTimedCount)

        // Test emergency forever
        presenter.openEmergencyDialog()
        presenter.onEmergencyForever()
        assertFalse(presenter.uiState.value.isEmergencyDialogOpen)
        assertEquals(1, fakeDispatcher.emergencyForeverCount)
    }

    @Test
    fun `OverlayLifecycleOwner transitions through correct lifecycle states on attach and detach`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val view = View(context)
        val lifecycleOwner = OverlayLifecycleOwner()

        assertEquals(Lifecycle.State.INITIALIZED, lifecycleOwner.lifecycle.currentState)

        lifecycleOwner.attachToView(view)
        assertEquals(Lifecycle.State.RESUMED, lifecycleOwner.lifecycle.currentState)

        lifecycleOwner.detachFromView()
        assertEquals(Lifecycle.State.DESTROYED, lifecycleOwner.lifecycle.currentState)
    }

    @Test
    fun `OverlayWindowLayout intercepts KEYCODE_BACK and triggers onBack`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var backTriggered = false
        val layout = OverlayWindowLayout(context, onBack = { backTriggered = true })

        // Non-back key event does not trigger back
        val enterEvent = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER)
        assertFalse(layout.dispatchKeyEvent(enterEvent))
        assertFalse(backTriggered)

        // Action down back key does not trigger back
        val backDownEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)
        assertFalse(layout.dispatchKeyEvent(backDownEvent))
        assertFalse(backTriggered)

        // Action up back key triggers onBack and returns true
        val backUpEvent = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK)
        assertTrue(layout.dispatchKeyEvent(backUpEvent))
        assertTrue(backTriggered)
    }
}
