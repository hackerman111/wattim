package io.ronesec.android.ui.intervention

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import io.ronesec.android.WattimApplication
import io.ronesec.android.platform.overlay.OverlayActionDispatcher
import io.ronesec.android.platform.overlay.OverlayHost
import io.ronesec.android.platform.overlay.OverlayHostListener
import io.ronesec.android.platform.overlay.OverlayPresenter
import io.ronesec.android.platform.overlay.WindowManagerAdapter
import io.ronesec.domain.model.AnimationMode
import io.ronesec.domain.model.SessionId
import io.ronesec.domain.policy.EffectiveInterventionConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class InterventionActivityTest {

    private class FakeActionDispatcher : OverlayActionDispatcher {
        var exitCount = 0
        var continueCount = 0
        var cancelCount = 0
        var lastSessionExited: SessionId? = null

        override fun onContinue(sessionId: SessionId, cycle: Int) {
            continueCount++
        }

        override fun onExit(sessionId: SessionId?) {
            exitCount++
            lastSessionExited = sessionId
        }

        override fun onCancel(sessionId: SessionId?) {
            cancelCount++
        }

        override fun onBreathingDeadlineReached(sessionId: SessionId, cycle: Int) {}
        override fun onEmergencyOnce(sessionId: SessionId, cycle: Int) {}
        override fun onEmergencyTimed(sessionId: SessionId, cycle: Int, durationMs: Long) {}
        override fun onEmergencyForever(sessionId: SessionId, cycle: Int) {}
    }

    private class FakeHostListener : OverlayHostListener {
        var attachedCount = 0
        var detachedCount = 0
        var lastAttachedSession: SessionId? = null
        var lastDetachedSession: SessionId? = null

        override fun onOverlayAttached(sessionId: SessionId, cycle: Int) {
            attachedCount++
            lastAttachedSession = sessionId
        }

        override fun onOverlayDetached(sessionId: SessionId) {
            detachedCount++
            lastDetachedSession = sessionId
        }

        override fun onOverlayAttachFailed(sessionId: SessionId, cycle: Int, error: Throwable) {}
    }

    private class NoOpWindowManagerAdapter : WindowManagerAdapter {
        override fun addView(view: android.view.View, params: android.view.ViewGroup.LayoutParams) {}
        override fun updateViewLayout(view: android.view.View, params: android.view.ViewGroup.LayoutParams) {}
        override fun removeView(view: android.view.View) {}
    }

    private val sampleConfig = EffectiveInterventionConfig(
        packageName = "com.zhiliaoapp.musically",
        displayName = "TikTok",
        phrase = "Take a breath",
        animation = AnimationMode.PULSE,
        durationMs = 5000L,
        reinterventionMs = 0L,
        quickReturnGraceMs = 0L,
        baseDurationMs = 5000L,
        backoffExponent = 0
    )

    @org.junit.After
    fun tearDown() {
        val app = ApplicationProvider.getApplicationContext<WattimApplication>()
        app.setPresenter(null)
        shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    @Test
    fun backPressDelegatesToPresenterExit() = runTest {
        val app = ApplicationProvider.getApplicationContext<WattimApplication>()
        val fakeDispatcher = FakeActionDispatcher()
        val presenter = OverlayPresenter(
            actionDispatcher = fakeDispatcher,
            policyStore = null,
            statisticsStore = null,
            scope = this
        )
        app.setPresenter(presenter)

        val sessionId = SessionId(1L, 1L, 1L)
        presenter.showIntervention(sessionId, 1, sampleConfig)

        val intent = Intent(app, InterventionActivity::class.java).apply {
            putExtra(InterventionActivity.EXTRA_SESSION_ID, sessionId.toString())
            putExtra(InterventionActivity.EXTRA_CYCLE, 1)
        }

        val controller = Robolectric.buildActivity(InterventionActivity::class.java, intent)
        val activity = controller.create().start().resume().get()

        assertFalse(activity.isFinishing)

        activity.onBackPressedDispatcher.onBackPressed()

        assertEquals(1, fakeDispatcher.exitCount)
        assertEquals(sessionId, fakeDispatcher.lastSessionExited)

        presenter.dismiss()
        controller.destroy()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        app.setPresenter(null)
    }

    @Test
    fun activityFinishesWhenModeBecomesNone() = runTest {
        val app = ApplicationProvider.getApplicationContext<WattimApplication>()
        val fakeDispatcher = FakeActionDispatcher()
        val presenter = OverlayPresenter(
            actionDispatcher = fakeDispatcher,
            policyStore = null,
            statisticsStore = null,
            scope = this
        )
        app.setPresenter(presenter)

        val sessionId = SessionId(1L, 1L, 2L)
        presenter.showIntervention(sessionId, 1, sampleConfig)

        val intent = Intent(app, InterventionActivity::class.java).apply {
            putExtra(InterventionActivity.EXTRA_SESSION_ID, sessionId.toString())
            putExtra(InterventionActivity.EXTRA_CYCLE, 1)
        }

        val controller = Robolectric.buildActivity(InterventionActivity::class.java, intent)
        val activity = controller.create().start().resume().get()

        assertFalse(activity.isFinishing)

        // Dismiss the intervention -> mode becomes OverlayMode.None
        presenter.dismiss()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertTrue(activity.isFinishing)

        controller.destroy()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        app.setPresenter(null)
    }

    @Test
    fun companionDismissFinishesActiveActivity() = runTest {
        val app = ApplicationProvider.getApplicationContext<WattimApplication>()
        val fakeDispatcher = FakeActionDispatcher()
        val presenter = OverlayPresenter(fakeDispatcher, null, null, this)
        app.setPresenter(presenter)

        val sessionId = SessionId(1L, 1L, 3L)
        presenter.showIntervention(sessionId, 1, sampleConfig)

        val intent = Intent(app, InterventionActivity::class.java).apply {
            putExtra(InterventionActivity.EXTRA_SESSION_ID, sessionId.toString())
            putExtra(InterventionActivity.EXTRA_CYCLE, 1)
        }

        val controller = Robolectric.buildActivity(InterventionActivity::class.java, intent)
        val activity = controller.create().start().resume().get()

        assertFalse(activity.isFinishing)

        InterventionActivity.dismiss()

        assertTrue(activity.isFinishing)

        presenter.dismiss()
        controller.destroy()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        app.setPresenter(null)
    }

    @Test
    fun overlayHostWithUseActivityLaunchesAndDismissesInterventionActivity() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val app = context as WattimApplication
        val fakeDispatcher = FakeActionDispatcher()
        val fakeListener = FakeHostListener()
        val presenter = OverlayPresenter(fakeDispatcher, null, null, this)
        app.setPresenter(presenter)

        val host = OverlayHost(
            context = context,
            windowManagerAdapter = NoOpWindowManagerAdapter(),
            presenter = presenter,
            listener = fakeListener,
            useActivity = true
        )

        val sessionId = SessionId(10L, 20L, 30L)
        host.showIntervention(sessionId, 1, sampleConfig)

        assertTrue(host.isWindowAttached)
        assertEquals(sessionId, host.currentSessionId)
        assertEquals(1, fakeListener.attachedCount)
        assertEquals(sessionId, fakeListener.lastAttachedSession)

        val startedIntent = shadowOf(context).nextStartedActivity
        assertNotNull(startedIntent)
        assertEquals(InterventionActivity::class.java.name, startedIntent.component?.className)
        assertEquals(sessionId.toString(), startedIntent.getStringExtra(InterventionActivity.EXTRA_SESSION_ID))
        assertEquals(1, startedIntent.getIntExtra(InterventionActivity.EXTRA_CYCLE, -1))

        // Dismiss
        host.dismissOverlay(sessionId)

        assertFalse(host.isWindowAttached)
        assertEquals(1, fakeListener.detachedCount)
        assertEquals(sessionId, fakeListener.lastDetachedSession)

        app.setPresenter(null)
    }
}
