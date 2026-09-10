package io.ronesec.android.platform.audio

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper

/**
 * Uses notification-listener authorization only to control media sessions.
 * It never receives or reads notification content.
 */
class AndroidMediaSessionPauseController(context: Context) : MediaSessionPauseController {
    private val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
    private val listenerComponent = ComponentName(context, WattimNotificationListenerService::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var targetPackage: String? = null
    private var observer: ((PlaybackObservation) -> Unit)? = null
    private var controllers: List<MediaController> = emptyList()
    private val controllerCallbacks = mutableMapOf<MediaController, MediaController.Callback>()
    private val controllerStates = mutableMapOf<MediaController, PlaybackObservation>()
    private var observationGeneration = 0L
    private var sessionsChangedListener: MediaSessionManager.OnActiveSessionsChangedListener? = null
    private var listenerRegistered = false

    override fun start(packageName: String, observer: (PlaybackObservation) -> Unit) {
        stop()
        val generation = ++observationGeneration
        targetPackage = packageName
        this.observer = observer
        val sessionManager = manager
        if (sessionManager == null) {
            observer(PlaybackObservation.FAILED)
            return
        }
        val listener = MediaSessionManager.OnActiveSessionsChangedListener { sessions ->
            if (observationGeneration == generation) {
                updateControllersSafely(sessions.orEmpty(), generation)
            }
        }
        sessionsChangedListener = listener
        try {
            sessionManager.addOnActiveSessionsChangedListener(
                listener,
                listenerComponent,
                mainHandler
            )
            listenerRegistered = true
            replaceControllers(sessionManager.getActiveSessions(listenerComponent), generation)
        } catch (_: SecurityException) {
            observationGeneration++
            clearControllerCallbacks()
            removeListenerSafely()
            observer(PlaybackObservation.ACCESS_DENIED)
        } catch (_: RuntimeException) {
            observationGeneration++
            clearControllerCallbacks()
            removeListenerSafely()
            observer(PlaybackObservation.FAILED)
        }
    }

    override fun requestPause(): PauseDispatchResult {
        if (!listenerRegistered) return PauseDispatchResult.ACCESS_DENIED
        if (controllers.isEmpty()) return PauseDispatchResult.NO_SESSION
        var sent = false
        var accessDenied = false
        var failed = false
        controllers.toList().forEach { controller ->
            try {
                controller.transportControls.pause()
                sent = true
            } catch (_: SecurityException) {
                accessDenied = true
            } catch (_: RuntimeException) {
                failed = true
            }
        }
        return when {
            accessDenied -> {
                reportAccessDeniedAndTearDown()
                PauseDispatchResult.ACCESS_DENIED
            }
            sent -> PauseDispatchResult.SENT
            failed -> PauseDispatchResult.FAILED
            else -> PauseDispatchResult.NO_SESSION
        }
    }

    override fun stop() {
        observationGeneration++
        clearControllerCallbacks()
        removeListenerSafely()
        targetPackage = null
        observer = null
    }

    private fun clearControllerCallbacks() {
        controllerCallbacks.forEach { (controller, callback) ->
            try {
                controller.unregisterCallback(callback)
            } catch (_: RuntimeException) {
                // The remote media session may already be gone.
            }
        }
        controllerCallbacks.clear()
        controllerStates.clear()
        controllers = emptyList()
    }

    private fun replaceControllers(allControllers: List<MediaController>, generation: Long) {
        clearControllerCallbacks()
        val packageName = targetPackage
        controllers = if (packageName == null) {
            emptyList()
        } else {
            allControllers.filter { it.packageName == packageName }
        }
        controllers.forEach { controller ->
            controllerStates[controller] = controller.playbackState.toObservation()
            val callback = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) {
                    if (observationGeneration != generation) return
                    controllerStates[controller] = state.toObservation()
                    emitAggregatePlaybackState()
                }

                override fun onSessionDestroyed() {
                    if (observationGeneration == generation) {
                        refreshControllers(generation)
                    }
                }
            }
            controller.registerCallback(callback, mainHandler)
            controllerCallbacks[controller] = callback
        }
        emitAggregatePlaybackState()
    }

    private fun emitAggregatePlaybackState() {
        val observation = when {
            controllers.isEmpty() -> PlaybackObservation.NO_SESSION
            controllers.any { controllerStates[it] == PlaybackObservation.PLAYING } -> {
                PlaybackObservation.PLAYING
            }
            controllers.all { controllerStates[it] == PlaybackObservation.PAUSED } -> {
                PlaybackObservation.PAUSED
            }
            else -> PlaybackObservation.OTHER
        }
        observer?.invoke(observation)
    }

    private fun updateControllersSafely(sessions: List<MediaController>, generation: Long) {
        try {
            replaceControllers(sessions, generation)
        } catch (_: SecurityException) {
            reportAccessDeniedAndTearDown()
        } catch (_: RuntimeException) {
            clearControllerCallbacks()
            observer?.invoke(PlaybackObservation.FAILED)
        }
    }

    private fun refreshControllers(generation: Long) {
        try {
            replaceControllers(manager?.getActiveSessions(listenerComponent).orEmpty(), generation)
        } catch (_: SecurityException) {
            reportAccessDeniedAndTearDown()
        } catch (_: RuntimeException) {
            clearControllerCallbacks()
            observer?.invoke(PlaybackObservation.FAILED)
        }
    }

    private fun removeListenerSafely() {
        val listener = sessionsChangedListener
        if (listenerRegistered && listener != null) {
            try {
                manager?.removeOnActiveSessionsChangedListener(listener)
            } catch (_: RuntimeException) {
                // Access may have been revoked while the lease was active.
            }
        }
        listenerRegistered = false
        sessionsChangedListener = null
    }

    private fun reportAccessDeniedAndTearDown() {
        observationGeneration++
        clearControllerCallbacks()
        removeListenerSafely()
        observer?.invoke(PlaybackObservation.ACCESS_DENIED)
    }

    private fun PlaybackState?.toObservation(): PlaybackObservation = when (this?.state) {
        PlaybackState.STATE_PLAYING,
        PlaybackState.STATE_BUFFERING,
        PlaybackState.STATE_CONNECTING,
        PlaybackState.STATE_FAST_FORWARDING,
        PlaybackState.STATE_REWINDING,
        PlaybackState.STATE_SKIPPING_TO_NEXT,
        PlaybackState.STATE_SKIPPING_TO_PREVIOUS,
        PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM -> PlaybackObservation.PLAYING

        PlaybackState.STATE_PAUSED,
        PlaybackState.STATE_STOPPED -> PlaybackObservation.PAUSED

        else -> PlaybackObservation.OTHER
    }
}
