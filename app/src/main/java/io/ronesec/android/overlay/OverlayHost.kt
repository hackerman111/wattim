package io.ronesec.android.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import io.ronesec.android.domain.model.InterventionConfig
import io.ronesec.android.ui.i18n.AppLanguage
import io.ronesec.android.ui.i18n.AppStrings
import io.ronesec.android.ui.i18n.LocalAppStrings
import io.ronesec.android.ui.i18n.resolveAppStrings
import io.ronesec.android.ui.theme.AppTheme
import io.ronesec.android.ui.theme.RonesecTheme
import java.time.Instant

class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val _viewModelStore = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = _viewModelStore

    init {
        savedStateRegistryController.performRestore(Bundle())
    }

    fun handleLifecycleEvent(event: Lifecycle.Event) {
        lifecycleRegistry.handleLifecycleEvent(event)
    }

    fun destroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        _viewModelStore.clear()
    }
}

class OverlayHost(private val context: Context) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var activeSessionId: Long? = null

    val isShowing: Boolean
        get() = overlayView != null

    fun isShowingSession(sessionId: Long): Boolean = isShowing && activeSessionId == sessionId

    fun showIntervention(
        sessionId: Long,
        targetAppName: String,
        config: InterventionConfig,
        savedTimeText: String? = null,
        theme: AppTheme = AppTheme.NORD,
        appStrings: AppStrings = resolveAppStrings(AppLanguage.SYSTEM),
        onEmergencyAccess: ((durationMs: Long?, disableTarget: Boolean) -> Unit)? = null,
        onClose: () -> Unit,
        onContinue: () -> Unit
    ) {
        if (activeSessionId == sessionId && isShowing) {
            // Already showing for this session
            return
        }
        dismiss()
        activeSessionId = sessionId

        val owner = OverlayLifecycleOwner()
        lifecycleOwner = owner
        owner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        owner.handleLifecycleEvent(Lifecycle.Event.ON_START)
        owner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(owner)

            @Suppress("DEPRECATION")
            systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )

            isFocusable = true
            isFocusableInTouchMode = true
            requestFocus()
            setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    if (event.action == KeyEvent.ACTION_UP) {
                        onClose()
                    }
                    true
                } else {
                    false
                }
            }

            setContent {
                CompositionLocalProvider(LocalAppStrings provides appStrings) {
                    RonesecTheme(theme = theme) {
                        InterventionOverlayContent(
                            targetAppName = targetAppName,
                            config = config,
                            savedTimeText = savedTimeText,
                            onEmergencyAccess = onEmergencyAccess,
                            onClose = onClose,
                            onContinue = onContinue
                        )
                    }
                }
            }
        }

        val params = createLayoutParams()
        overlayView = composeView
        windowManager.addView(composeView, params)
    }

    fun showBlock(
        sessionId: Long,
        sessionName: String,
        until: Instant?,
        theme: AppTheme = AppTheme.NORD,
        appStrings: AppStrings = resolveAppStrings(AppLanguage.SYSTEM),
        onClose: () -> Unit
    ) {
        if (activeSessionId == sessionId && isShowing) {
            return
        }
        dismiss()
        activeSessionId = sessionId

        val owner = OverlayLifecycleOwner()
        lifecycleOwner = owner
        owner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        owner.handleLifecycleEvent(Lifecycle.Event.ON_START)
        owner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(owner)

            @Suppress("DEPRECATION")
            systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )

            isFocusable = true
            isFocusableInTouchMode = true
            requestFocus()
            setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    if (event.action == KeyEvent.ACTION_UP) {
                        onClose()
                    }
                    true
                } else {
                    false
                }
            }

            setContent {
                CompositionLocalProvider(LocalAppStrings provides appStrings) {
                    RonesecTheme(theme = theme) {
                        BlockOverlayContent(
                            sessionName = sessionName,
                            until = until,
                            onClose = onClose
                        )
                    }
                }
            }
        }

        val params = createLayoutParams()
        overlayView = composeView
        windowManager.addView(composeView, params)
    }

    fun dismiss(sessionId: Long? = null) {
        if (sessionId != null && activeSessionId != null && activeSessionId != sessionId) {
            return
        }
        overlayView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (_: Exception) {}
            overlayView = null
        }
        lifecycleOwner?.let { owner ->
            owner.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            owner.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            owner.destroy()
            lifecycleOwner = null
        }
        activeSessionId = null
    }

    private fun createLayoutParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }
}
