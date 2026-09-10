package io.ronesec.android.platform.audio

enum class PlaybackObservation {
    ACCESS_DENIED,
    NO_SESSION,
    PLAYING,
    PAUSED,
    OTHER,
    FAILED
}

enum class PauseDispatchResult {
    SENT,
    NO_SESSION,
    ACCESS_DENIED,
    FAILED
}

interface MediaSessionPauseController {
    /** Starts observing only media sessions owned by [packageName]. */
    fun start(packageName: String, observer: (PlaybackObservation) -> Unit)

    /** Requests pause from every currently active session of the observed package. */
    fun requestPause(): PauseDispatchResult

    fun stop()
}

object UnavailableMediaSessionPauseController : MediaSessionPauseController {
    override fun start(packageName: String, observer: (PlaybackObservation) -> Unit) {
        observer(PlaybackObservation.ACCESS_DENIED)
    }

    override fun requestPause(): PauseDispatchResult = PauseDispatchResult.ACCESS_DENIED

    override fun stop() = Unit
}
