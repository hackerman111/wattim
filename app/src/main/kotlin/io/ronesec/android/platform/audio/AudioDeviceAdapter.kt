package io.ronesec.android.platform.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.view.KeyEvent

/**
 * Port abstracting audio system interactions for testability.
 */
interface AudioDeviceAdapter {
    fun requestTransientFocus(onFocusChange: (Int) -> Unit): Int
    fun abandonFocus(): Int
    fun dispatchMediaPauseKey()
}

/**
 * Production Android adapter using AudioManager public APIs.
 */
class AndroidAudioDeviceAdapter(context: Context) : AudioDeviceAdapter {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var currentRequest: AudioFocusRequest? = null

    override fun requestTransientFocus(onFocusChange: (Int) -> Unit): Int {
        val am = audioManager ?: return AudioManager.AUDIOFOCUS_REQUEST_FAILED
        currentRequest?.let { previousRequest ->
            try {
                am.abandonAudioFocusRequest(previousRequest)
            } catch (_: RuntimeException) {
                // The previous request may already have been invalidated by the platform.
            }
            currentRequest = null
        }
        val playbackAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(playbackAttributes)
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener { focusChange -> onFocusChange(focusChange) }
            .build()
        return try {
            am.requestAudioFocus(request).also { result ->
                if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    currentRequest = request
                }
            }
        } catch (_: RuntimeException) {
            AudioManager.AUDIOFOCUS_REQUEST_FAILED
        }
    }

    override fun abandonFocus(): Int {
        val am = audioManager ?: return AudioManager.AUDIOFOCUS_REQUEST_FAILED
        val req = currentRequest ?: return AudioManager.AUDIOFOCUS_REQUEST_FAILED
        currentRequest = null
        return try {
            am.abandonAudioFocusRequest(req)
        } catch (_: RuntimeException) {
            AudioManager.AUDIOFOCUS_REQUEST_FAILED
        }
    }

    override fun dispatchMediaPauseKey() {
        val am = audioManager ?: return
        val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE)
        val upEvent = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE)
        am.dispatchMediaKeyEvent(downEvent)
        am.dispatchMediaKeyEvent(upEvent)
    }
}
