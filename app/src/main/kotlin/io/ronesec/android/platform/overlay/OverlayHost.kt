package io.ronesec.android.platform.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import io.ronesec.android.protection.OverlayPort
import io.ronesec.domain.model.SessionId
import io.ronesec.domain.policy.EffectiveInterventionConfig
import java.time.Instant

/**
 * Window manager abstraction for testability.
 */
interface WindowManagerAdapter {
    fun addView(view: View, params: ViewGroup.LayoutParams)
    fun updateViewLayout(view: View, params: ViewGroup.LayoutParams)
    fun removeView(view: View)
}

class AndroidWindowManagerAdapter(private val windowManager: WindowManager) : WindowManagerAdapter {
    override fun addView(view: View, params: ViewGroup.LayoutParams) {
        windowManager.addView(view, params)
    }

    override fun updateViewLayout(view: View, params: ViewGroup.LayoutParams) {
        windowManager.updateViewLayout(view, params)
    }

    override fun removeView(view: View) {
        windowManager.removeView(view)
    }
}

/**
 * Window container FrameLayout intercepting system Back key events to trigger protection Exit.
 * Satisfies §2.5: System Back remains the overlay's Exit action even when dialogs are open.
 */
class OverlayWindowLayout(
    context: Context,
    private val onBack: () -> Unit
) : FrameLayout(context) {

    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        if (event?.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
            onBack()
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}

interface OverlayHostListener {
    fun onOverlayAttached(sessionId: SessionId, cycle: Int)
    fun onOverlayDetached(sessionId: SessionId)
    fun onOverlayAttachFailed(sessionId: SessionId, cycle: Int, error: Throwable)
}

/**
 * Manages one TYPE_APPLICATION_OVERLAY window attached to WindowManager.
 * Satisfies F33, F36, F41, Section 6, and Section 3.1.
 *
 * Rules:
 * 1. At most one real overlay window is attached at any time.
 * 2. Window add/update/remove performed on Main thread only.
 * 3. Same-session updates reuse the existing window without recreation.
 * 4. Intercepts Back key and routes to Exit action.
 * 5. Cleans up partial attaches on error.
 */
class OverlayHost(
    private val context: Context,
    private val windowManagerAdapter: WindowManagerAdapter,
    private val presenter: OverlayPresenter,
    private val listener: OverlayHostListener,
    private val contentRenderer: (ComposeView) -> Unit = {},
    private val useActivity: Boolean = false
) : OverlayPort {

    private var activeSessionId: SessionId? = null
    private var activeLayout: OverlayWindowLayout? = null
    private var activeComposeView: ComposeView? = null
    private var activeLifecycleOwner: OverlayLifecycleOwner? = null

    val isWindowAttached: Boolean
        get() = activeLayout != null || (useActivity && activeSessionId != null)

    val currentSessionId: SessionId?
        get() = activeSessionId

    override fun showIntervention(sessionId: SessionId, cycle: Int, config: EffectiveInterventionConfig) {
        if (useActivity) {
            if (activeSessionId == sessionId) {
                // Same-session update: reuse existing Activity
                presenter.showIntervention(sessionId, cycle, config)
                listener.onOverlayAttached(sessionId, cycle)
                return
            }
            activeSessionId = sessionId
            presenter.showIntervention(sessionId, cycle, config)
            try {
                io.ronesec.android.ui.intervention.InterventionActivity.start(context, sessionId, cycle)
                listener.onOverlayAttached(sessionId, cycle)
            } catch (e: Throwable) {
                activeSessionId = null
                listener.onOverlayAttachFailed(sessionId, cycle, e)
            }
            return
        }

        if (isWindowAttached && activeSessionId == sessionId) {
            // Same-session update: reuse existing window
            presenter.showIntervention(sessionId, cycle, config)
            listener.onOverlayAttached(sessionId, cycle)
            return
        }

        if (isWindowAttached) {
            detachCurrentWindowInternal()
        }

        val layout = OverlayWindowLayout(context, onBack = { presenter.onBackExit() })
        val composeView = ComposeView(context)
        layout.addView(
            composeView,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )
        val lifecycleOwner = OverlayLifecycleOwner()

        val params = WindowManager.LayoutParams().apply {
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.CENTER
            flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        var viewAdded = false
        try {
            lifecycleOwner.attachToView(layout)
            contentRenderer(composeView)
            windowManagerAdapter.addView(layout, params)
            viewAdded = true

            activeLayout = layout
            activeComposeView = composeView
            activeLifecycleOwner = lifecycleOwner
            activeSessionId = sessionId

            presenter.showIntervention(sessionId, cycle, config)
            listener.onOverlayAttached(sessionId, cycle)
        } catch (e: Throwable) {
            if (viewAdded) {
                try {
                    windowManagerAdapter.removeView(layout)
                } catch (_: Exception) {}
            }
            try {
                composeView.disposeComposition()
                lifecycleOwner.detachFromView()
            } catch (_: Exception) {}
            activeLayout = null
            activeComposeView = null
            activeLifecycleOwner = null
            activeSessionId = null
            listener.onOverlayAttachFailed(sessionId, cycle, e)
        }
    }

    override fun showBlock(sessionId: SessionId, cycle: Int, packageName: String, until: Instant?) {
        if (useActivity) {
            if (activeSessionId == sessionId) {
                presenter.showBlock(packageName, until)
                listener.onOverlayAttached(sessionId, cycle)
                return
            }
            activeSessionId = sessionId
            presenter.showBlock(packageName, until)
            try {
                io.ronesec.android.ui.intervention.InterventionActivity.start(context, sessionId, cycle)
                listener.onOverlayAttached(sessionId, cycle)
            } catch (e: Throwable) {
                activeSessionId = null
                listener.onOverlayAttachFailed(sessionId, cycle, e)
            }
            return
        }

        if (isWindowAttached && activeSessionId == sessionId) {
            presenter.showBlock(packageName, until)
            listener.onOverlayAttached(sessionId, cycle)
            return
        }

        if (isWindowAttached) {
            detachCurrentWindowInternal()
        }

        if (!isWindowAttached) {
            val layout = OverlayWindowLayout(context, onBack = { presenter.onBackExit() })
            val composeView = ComposeView(context)
            layout.addView(
                composeView,
                FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            )
            val lifecycleOwner = OverlayLifecycleOwner()

            val params = WindowManager.LayoutParams().apply {
                width = WindowManager.LayoutParams.MATCH_PARENT
                height = WindowManager.LayoutParams.MATCH_PARENT
                type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                format = PixelFormat.TRANSLUCENT
                gravity = Gravity.CENTER
                flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }

            var viewAdded = false
            try {
                lifecycleOwner.attachToView(layout)
                contentRenderer(composeView)
                windowManagerAdapter.addView(layout, params)
                viewAdded = true

                activeLayout = layout
                activeComposeView = composeView
                activeLifecycleOwner = lifecycleOwner
                activeSessionId = sessionId
                presenter.showBlock(packageName, until)
                listener.onOverlayAttached(sessionId, cycle)
                return
            } catch (e: Throwable) {
                if (viewAdded) {
                    try {
                        windowManagerAdapter.removeView(layout)
                    } catch (_: Exception) {}
                }
                try {
                    composeView.disposeComposition()
                    lifecycleOwner.detachFromView()
                } catch (_: Exception) {}
                activeLayout = null
                activeComposeView = null
                activeLifecycleOwner = null
                activeSessionId = null
                listener.onOverlayAttachFailed(sessionId, cycle, e)
                return
            }
        }

        presenter.showBlock(packageName, until)
    }

    override fun updateOverlayComplete(sessionId: SessionId, cycle: Int) {
        presenter.updateOverlayComplete(sessionId, cycle)
    }

    override fun dismissOverlay(sessionId: SessionId?) {
        if (sessionId == null || sessionId == activeSessionId) {
            val detachedSession = activeSessionId
            if (useActivity) {
                activeSessionId = null
                presenter.dismiss()
                io.ronesec.android.ui.intervention.InterventionActivity.dismiss()
                if (detachedSession != null) {
                    listener.onOverlayDetached(detachedSession)
                }
                return
            }
            detachCurrentWindowInternal()
            if (detachedSession != null) {
                listener.onOverlayDetached(detachedSession)
            }
        }
    }

    private fun detachCurrentWindowInternal() {
        val layout = activeLayout
        val composeView = activeComposeView
        val lifecycleOwner = activeLifecycleOwner

        activeLayout = null
        activeComposeView = null
        activeLifecycleOwner = null
        activeSessionId = null

        presenter.dismiss()

        if (composeView != null) {
            try {
                composeView.disposeComposition()
            } catch (_: Exception) {}
        }

        if (layout != null) {
            try {
                windowManagerAdapter.removeView(layout)
            } catch (_: Exception) {}
        }

        lifecycleOwner?.detachFromView()
    }
}
