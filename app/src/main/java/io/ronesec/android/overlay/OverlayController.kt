package io.ronesec.android.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

class OverlayController(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var pauseJob: Job? = null
    private var savedVolume: Int? = null
    private var isMuted = false

    val isShowing: Boolean
        get() = overlayView != null

    private fun silenceAudio() {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (!isMuted) {
                val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                savedVolume = currentVol
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
                isMuted = true
            }
        } catch (e: Exception) {
            // Volume adjustment fallback
        }
    }

    private fun restoreAudio() {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (isMuted) {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
                savedVolume?.let { vol ->
                    if (vol > 0) {
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, vol, 0)
                    }
                }
                isMuted = false
                savedVolume = null
            }
        } catch (e: Exception) {
            // Volume restore fallback
        }
    }

    private fun dispatchMediaPause() {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE))
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE))
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun acquireAudioFocus() {
        silenceAudio()
        dispatchMediaPause()

        // Periodically dispatch pause key during the first 2.5s to catch TikTok when its ExoPlayer asynchronously loads the first video
        pauseJob?.cancel()
        pauseJob = CoroutineScope(Dispatchers.Main).launch {
            delay(300)
            if (isShowing) {
                silenceAudio()
                dispatchMediaPause()
            }
            delay(500)
            if (isShowing) {
                silenceAudio()
                dispatchMediaPause()
            }
            delay(1000)
            if (isShowing) {
                silenceAudio()
                dispatchMediaPause()
            }
        }

        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val playbackAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(false)
                    .setWillPauseWhenDucked(true)
                    .setOnAudioFocusChangeListener { focusChange ->
                        if (isShowing) {
                            silenceAudio()
                            dispatchMediaPause()
                        }
                    }
                    .build()
                audioFocusRequest = request
                audioManager.requestAudioFocus(request)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    { focusChange ->
                        if (isShowing) {
                            silenceAudio()
                            dispatchMediaPause()
                        }
                    },
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                )
            }
        } catch (e: Exception) {
            // Audio focus fallback
        }
    }

    private fun abandonAudioFocus() {
        pauseJob?.cancel()
        pauseJob = null
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
                audioFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
        } catch (e: Exception) {
            // Ignore
        }
        restoreAudio()
    }

    fun showIntervention(
        targetAppName: String,
        config: InterventionConfig,
        savedTimeText: String? = null,
        theme: AppTheme = AppTheme.NORD,
        onEmergencyAccess: ((durationMs: Long?, disableTarget: Boolean) -> Unit)? = null,
        onClose: () -> Unit,
        onContinue: () -> Unit
    ) {
        dismiss()
        acquireAudioFocus()

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

            // Intercept Back key to safely close overlay and return to Home
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
                RonesecTheme(theme = theme) {
                    InterventionOverlayContent(
                        targetAppName = targetAppName,
                        config = config,
                        savedTimeText = savedTimeText,
                        onEmergencyAccess = if (onEmergencyAccess != null) {
                            { durationMs, disableTarget ->
                                dismiss()
                                onEmergencyAccess(durationMs, disableTarget)
                            }
                        } else null,
                        onClose = {
                            dismiss()
                            onClose()
                        },
                        onContinue = {
                            dismiss()
                            onContinue()
                        }
                    )
                }
            }
        }

        val params = createLayoutParams()
        overlayView = composeView
        windowManager.addView(composeView, params)
    }

    fun showBlock(
        sessionName: String,
        until: Instant?,
        theme: AppTheme = AppTheme.NORD,
        onClose: () -> Unit
    ) {
        dismiss()
        acquireAudioFocus()

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
                RonesecTheme(theme = theme) {
                    BlockOverlayContent(
                        sessionName = sessionName,
                        until = until,
                        onClose = {
                            dismiss()
                            onClose()
                        }
                    )
                }
            }
        }

        val params = createLayoutParams()
        overlayView = composeView
        windowManager.addView(composeView, params)
    }

    fun dismiss() {
        abandonAudioFocus()
        overlayView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (e: Exception) {
                // View already detached
            }
            overlayView = null
        }
        lifecycleOwner?.let { owner ->
            owner.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            owner.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            owner.destroy()
            lifecycleOwner = null
        }
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
