package io.ronesec.android.domain.protection

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.view.KeyEvent

class AudioGuard(private val context: Context) {
    private val audioManager: AudioManager? = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var activeSessionId: Long? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    fun acquire(sessionId: Long) {
        if (activeSessionId == sessionId) return
        activeSessionId?.let { release(it) }
        activeSessionId = sessionId

        val am = audioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val playbackAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(false)
                    .setWillPauseWhenDucked(true)
                    .setOnAudioFocusChangeListener { /* transient focus held */ }
                    .build()
                audioFocusRequest = request
                am.requestAudioFocus(request)
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(
                    null,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                )
            }
        } catch (_: Exception) {}

        dispatchMediaPause()
    }

    fun release(sessionId: Long) {
        if (activeSessionId != null && activeSessionId != sessionId) return
        activeSessionId = null
        val am = audioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
                audioFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(null)
            }
        } catch (_: Exception) {}
    }

    private fun dispatchMediaPause() {
        val am = audioManager ?: return
        try {
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE))
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE))
        } catch (_: Exception) {}
    }
}
