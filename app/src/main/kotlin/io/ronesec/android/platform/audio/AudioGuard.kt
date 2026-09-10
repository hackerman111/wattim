package io.ronesec.android.platform.audio

import android.media.AudioManager
import io.ronesec.android.protection.AudioPort
import io.ronesec.domain.model.SessionId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Owns focus, package-targeted media pause, and the bounded global fallback for one session. */
class AudioGuard(
    private val deviceAdapter: AudioDeviceAdapter,
    private val mediaSessionController: MediaSessionPauseController = UnavailableMediaSessionPauseController,
    private val diagnostics: AudioDiagnostics = AudioDiagnostics(),
    private val strategy: MediaSuppressionStrategy = BoundedPauseFallbackStrategy,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : AudioPort {

    companion object {
        const val MAX_RECOVERY_ATTEMPTS = 2
        const val MAX_TARGETED_COMMANDS_PER_LEASE = 8
        private val TARGETED_RETRY_DELAYS_MS = listOf(0L, 150L, 250L)
        private const val CONFIRMATION_WAIT_MS = 250L
        private const val TARGETED_PAUSE_HEAD_START_MS = 300L
    }

    @Volatile
    private var activeLease: SessionId? = null
    @Volatile
    private var activePackage: String? = null
    @Volatile
    private var mediaPauseConfirmed = false
    @Volatile
    private var targetedSessionPresent = false
    private var fallbackJob: Job? = null
    private var targetedPauseJob: Job? = null
    private var focusRequestGeneration = 0L
    private var recoveryAttempts = 0
    private var targetedCommandCount = 0

    val currentLease: SessionId?
        get() = activeLease

    override fun acquireAudioLease(sessionId: SessionId, packageName: String) {
        if (activeLease == sessionId && activePackage == packageName) return
        releaseActiveInternal()

        activeLease = sessionId
        activePackage = packageName
        recoveryAttempts = 0
        targetedCommandCount = 0
        mediaPauseConfirmed = false
        targetedSessionPresent = false
        diagnostics.begin(sessionId, packageName)

        requestFocus(sessionId, recovering = false)
        mediaSessionController.start(packageName) { observation ->
            handlePlaybackObservation(sessionId, observation)
        }
        startFallbackBurst(sessionId)
    }

    override fun releaseAudioLease(sessionId: SessionId) {
        if (activeLease != sessionId) return
        releaseActiveInternal()
    }

    fun releaseAll() {
        releaseActiveInternal()
    }

    private fun requestFocus(sessionId: SessionId, recovering: Boolean) {
        if (activeLease != sessionId) return
        if (recovering) diagnostics.setFocus(sessionId, AudioFocusStatus.RECOVERING)
        val requestGeneration = ++focusRequestGeneration
        val result = deviceAdapter.requestTransientFocus { focusChange ->
            handleFocusChange(sessionId, requestGeneration, focusChange)
        }
        if (activeLease != sessionId || focusRequestGeneration != requestGeneration) return
        diagnostics.setFocus(
            sessionId,
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                AudioFocusStatus.GRANTED
            } else {
                AudioFocusStatus.DENIED
            }
        )
    }

    private fun handlePlaybackObservation(
        sessionId: SessionId,
        observation: PlaybackObservation
    ) {
        if (activeLease != sessionId) return
        when (observation) {
            PlaybackObservation.ACCESS_DENIED -> {
                targetedSessionPresent = false
                diagnostics.setMediaPause(sessionId, MediaPauseStatus.ACCESS_DENIED)
            }
            PlaybackObservation.NO_SESSION -> {
                targetedSessionPresent = false
                if (!mediaPauseConfirmed) {
                    diagnostics.setMediaPause(sessionId, MediaPauseStatus.NO_ACTIVE_SESSION)
                }
            }
            PlaybackObservation.PLAYING -> {
                targetedSessionPresent = true
                mediaPauseConfirmed = false
                startTargetedPauseBurst(sessionId)
            }
            PlaybackObservation.PAUSED -> {
                targetedSessionPresent = true
                mediaPauseConfirmed = true
                targetedPauseJob?.cancel()
                targetedPauseJob = null
                diagnostics.setMediaPause(sessionId, MediaPauseStatus.CONFIRMED_PAUSED)
            }
            PlaybackObservation.OTHER -> {
                targetedSessionPresent = true
                mediaPauseConfirmed = false
                startTargetedPauseBurst(sessionId)
            }
            PlaybackObservation.FAILED -> {
                targetedSessionPresent = false
                diagnostics.setMediaPause(sessionId, MediaPauseStatus.FAILED)
            }
        }
    }

    private fun startTargetedPauseBurst(sessionId: SessionId) {
        if (targetedPauseJob?.isActive == true || targetedCommandCount >= MAX_TARGETED_COMMANDS_PER_LEASE) {
            return
        }
        targetedPauseJob = scope.launch(dispatcher) {
            var sentInBurst = false
            for (delayMs in TARGETED_RETRY_DELAYS_MS) {
                if (delayMs > 0L) delay(delayMs)
                if (!isActive || activeLease != sessionId || mediaPauseConfirmed) break
                if (targetedCommandCount >= MAX_TARGETED_COMMANDS_PER_LEASE) break
                when (mediaSessionController.requestPause()) {
                    PauseDispatchResult.SENT -> {
                        sentInBurst = true
                        targetedCommandCount++
                        diagnostics.recordTargetedCommand(sessionId)
                        if (mediaPauseConfirmed) {
                            diagnostics.setMediaPause(sessionId, MediaPauseStatus.CONFIRMED_PAUSED)
                        }
                    }
                    PauseDispatchResult.NO_SESSION -> {
                        diagnostics.setMediaPause(sessionId, MediaPauseStatus.NO_ACTIVE_SESSION)
                        break
                    }
                    PauseDispatchResult.ACCESS_DENIED -> {
                        diagnostics.setMediaPause(sessionId, MediaPauseStatus.ACCESS_DENIED)
                        break
                    }
                    PauseDispatchResult.FAILED -> {
                        diagnostics.setMediaPause(sessionId, MediaPauseStatus.FAILED)
                        break
                    }
                }
            }
            if (sentInBurst && activeLease == sessionId && !mediaPauseConfirmed) {
                delay(CONFIRMATION_WAIT_MS)
                if (activeLease == sessionId && !mediaPauseConfirmed) {
                    diagnostics.setMediaPause(sessionId, MediaPauseStatus.NOT_CONFIRMED)
                }
            }
        }
    }

    private fun startFallbackBurst(sessionId: SessionId) {
        if (!strategy.executeFallbackBurst) return
        fallbackJob = scope.launch(dispatcher) {
            if (targetedSessionPresent) delay(TARGETED_PAUSE_HEAD_START_MS)
            for (delayMs in strategy.burstDelaysMs) {
                if (delayMs > 0L) delay(delayMs)
                if (!isActive || activeLease != sessionId || mediaPauseConfirmed) break
                deviceAdapter.dispatchMediaPauseKey()
                diagnostics.recordFallbackCommand(sessionId)
            }
        }
    }

    private fun releaseActiveInternal() {
        val releasedLease = activeLease ?: return
        activeLease = null
        activePackage = null
        mediaPauseConfirmed = false
        targetedSessionPresent = false
        targetedPauseJob?.cancel()
        targetedPauseJob = null
        fallbackJob?.cancel()
        fallbackJob = null
        mediaSessionController.stop()
        focusRequestGeneration++
        recoveryAttempts = 0
        targetedCommandCount = 0
        deviceAdapter.abandonFocus()
        diagnostics.release(releasedLease)
    }

    private fun handleFocusChange(sessionId: SessionId, requestGeneration: Long, focusChange: Int) {
        if (activeLease != sessionId || focusRequestGeneration != requestGeneration) return
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                diagnostics.setFocus(sessionId, AudioFocusStatus.LOST)
                if (recoveryAttempts < MAX_RECOVERY_ATTEMPTS) {
                    recoveryAttempts++
                    requestFocus(sessionId, recovering = true)
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> diagnostics.setFocus(
                sessionId,
                AudioFocusStatus.GRANTED
            )
            else -> Unit
        }
    }
}
