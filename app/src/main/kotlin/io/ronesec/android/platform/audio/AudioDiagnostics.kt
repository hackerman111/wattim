package io.ronesec.android.platform.audio

import io.ronesec.domain.model.SessionId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AudioFocusStatus {
    IDLE,
    GRANTED,
    DENIED,
    LOST,
    RECOVERING
}

enum class MediaPauseStatus {
    IDLE,
    ACCESS_DENIED,
    NO_ACTIVE_SESSION,
    COMMAND_SENT,
    CONFIRMED_PAUSED,
    NOT_CONFIRMED,
    FAILED
}

data class AudioDiagnosticState(
    val packageName: String? = null,
    val active: Boolean = false,
    val focusStatus: AudioFocusStatus = AudioFocusStatus.IDLE,
    val mediaPauseStatus: MediaPauseStatus = MediaPauseStatus.IDLE,
    val targetedCommands: Int = 0,
    val fallbackCommands: Int = 0
)

/** Process-owned, content-free diagnostics for the latest audio suppression lease. */
class AudioDiagnostics {
    private val stateLock = Any()
    private var activeSessionId: SessionId? = null
    private val _state = MutableStateFlow(AudioDiagnosticState())
    val state: StateFlow<AudioDiagnosticState> = _state.asStateFlow()

    fun begin(sessionId: SessionId, packageName: String) = synchronized(stateLock) {
        activeSessionId = sessionId
        _state.value = AudioDiagnosticState(packageName = packageName, active = true)
    }

    fun setFocus(sessionId: SessionId, status: AudioFocusStatus) = update(sessionId) {
        it.copy(focusStatus = status)
    }

    fun setMediaPause(sessionId: SessionId, status: MediaPauseStatus) = update(sessionId) {
        it.copy(mediaPauseStatus = status)
    }

    fun recordTargetedCommand(sessionId: SessionId) = update(sessionId) {
        it.copy(
            mediaPauseStatus = MediaPauseStatus.COMMAND_SENT,
            targetedCommands = it.targetedCommands + 1
        )
    }

    fun recordFallbackCommand(sessionId: SessionId) = update(sessionId) {
        it.copy(fallbackCommands = it.fallbackCommands + 1)
    }

    fun release(sessionId: SessionId) = synchronized(stateLock) {
        if (activeSessionId == sessionId) {
            activeSessionId = null
            _state.value = _state.value.copy(active = false)
        }
    }

    private inline fun update(
        sessionId: SessionId,
        transform: (AudioDiagnosticState) -> AudioDiagnosticState
    ) = synchronized(stateLock) {
        if (activeSessionId == sessionId) {
            _state.value = transform(_state.value)
        }
    }
}
