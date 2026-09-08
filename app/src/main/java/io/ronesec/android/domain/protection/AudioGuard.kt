package io.ronesec.android.domain.protection

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.view.KeyEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

interface AudioGuard {
    fun acquire(sessionId: SessionId): AudioAcquireResult
    fun release(sessionId: SessionId)
}

class SystemAudioGuard(
    context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) : AudioGuard {
    private val audioManager: AudioManager? = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var activeSessionId: SessionId? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var pausePulseJob: Job? = null

    override fun acquire(sessionId: SessionId): AudioAcquireResult {
        if (activeSessionId == sessionId) return AudioAcquireResult.Success
        activeSessionId?.let { release(it) }
        activeSessionId = sessionId

        val am = audioManager ?: return AudioAcquireResult.Failed("AudioManager unavailable")
        return try {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(false)
                .setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener { focusChange ->
                    handleAudioFocusChange(sessionId, focusChange)
                }
                .build()

            audioFocusRequest = request
            val result = am.requestAudioFocus(request)

            startStaggeredPausePulses(sessionId)

            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                AudioAcquireResult.Success
            } else {
                AudioAcquireResult.FocusDenied
            }
        } catch (e: Exception) {
            AudioAcquireResult.Failed(e.message ?: "Unknown audio error")
        }
    }

    override fun release(sessionId: SessionId) {
        if (activeSessionId != null && activeSessionId != sessionId && sessionId != SessionId.NONE) return
        activeSessionId = null
        pausePulseJob?.cancel()
        pausePulseJob = null
        val am = audioManager ?: return
        try {
            audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
            audioFocusRequest = null
        } catch (_: Exception) {}
    }

    fun acquire(sessionId: Long): AudioAcquireResult = acquire(SessionId(sessionId))
    fun release(sessionId: Long) = release(SessionId(sessionId))

    private fun handleAudioFocusChange(sessionId: SessionId, focusChange: Int) {
        if (activeSessionId != sessionId) return
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                val am = audioManager ?: return
                audioFocusRequest?.let { am.requestAudioFocus(it) }
                dispatchMediaPause()
            }
        }
    }

    private fun startStaggeredPausePulses(sessionId: SessionId) {
        pausePulseJob?.cancel()
        pausePulseJob = scope.launch {
            val delays = longArrayOf(0L, 150L, 250L, 400L, 400L)
            for (stepDelay in delays) {
                if (stepDelay > 0L) {
                    delay(stepDelay)
                }
                if (activeSessionId != sessionId) break
                dispatchMediaPause()
            }
        }
    }

    private fun dispatchMediaPause() {
        val am = audioManager ?: return
        try {
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE))
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE))
        } catch (_: Exception) {}
    }
}
