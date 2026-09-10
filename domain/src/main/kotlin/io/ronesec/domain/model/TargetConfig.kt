package io.ronesec.domain.model

enum class AnimationMode(
    val revealsRemainingTime: Boolean = true
) {
    FILL,
    PULSE,
    CIRCLE,
    WAVE(revealsRemainingTime = false)
}

data class BackoffConfig(
    val enabled: Boolean = false,
    val percent: Int = 20,
    val windowMs: Long = 60 * 60 * 1000L // 60 minutes
) {
    init {
        require(percent in 1..200) { "Backoff growth percent must be between 1 and 200, was $percent" }
        require(windowMs > 0) { "Backoff window must be positive, was $windowMs" }
    }
}

data class TargetConfig(
    val packageName: String,
    val displayName: String,
    val enabled: Boolean = true,
    val phrase: String = DEFAULT_PHRASE,
    val animation: AnimationMode = AnimationMode.FILL,
    val durationMs: Long = DEFAULT_DURATION_MS,
    val reinterventionMs: Long = DEFAULT_REINTERVENTION_MS,
    val quickReturnGraceMs: Long = DEFAULT_GRACE_MS,
    val growthConfig: BackoffConfig = BackoffConfig(),
    val rowVersion: Long = 1L
) {
    init {
        require(packageName.isNotBlank()) { "Package name cannot be blank" }
        require(durationMs in MIN_DURATION_MS..MAX_DURATION_MS) {
            "Duration must be between $MIN_DURATION_MS and $MAX_DURATION_MS ms, was $durationMs"
        }
        require(reinterventionMs >= 0) {
            "Reintervention must be non-negative (0 means OFF), was $reinterventionMs"
        }
        require(quickReturnGraceMs >= 0) {
            "Quick Return grace must be non-negative, was $quickReturnGraceMs"
        }
    }

    companion object {
        const val DEFAULT_PHRASE = "Сделай глубокий вдох и выдох"
        const val DEFAULT_DURATION_MS = 8_000L // 8 seconds Android default
        const val MIN_DURATION_MS = 1_000L     // 1 second
        const val MAX_DURATION_MS = 120_000L   // 120 seconds base max
        const val DEFAULT_REINTERVENTION_MS = 5 * 60 * 1000L // 5 minutes
        const val DEFAULT_GRACE_MS = 0L
    }
}
