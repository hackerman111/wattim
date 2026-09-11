package io.ronesec.domain.model

enum class AnimationMode(
    val revealsRemainingTime: Boolean = true
) {
    FILL,
    PULSE,
    CIRCLE,
    WAVE(revealsRemainingTime = false),
    FILL_2(revealsRemainingTime = false)
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
    val rowVersion: Long = 1L,
    val twoStageUnlock: Boolean = false,
    val unlockCodeLength: Int = 4,
    val requireEmergencyCode: Boolean = false,
    val randomDurationEnabled: Boolean = false,
    val randomMaxDurationMs: Long = DEFAULT_DURATION_MS,
    val attentionChecksEnabled: Boolean = false,
    val attentionCheckCount: Int = 1,
    val attentionCheckCodeLength: Int = 4,
    val attentionCheckTimeoutMs: Long = DEFAULT_ATTENTION_CHECK_TIMEOUT_MS
) {
    init {
        require(packageName.isNotBlank()) { "Package name cannot be blank" }
        require(unlockCodeLength in 1..10) { "Unlock code length must be between 1 and 10" }
        require(durationMs in MIN_DURATION_MS..MAX_DURATION_MS) {
            "Duration must be between $MIN_DURATION_MS and $MAX_DURATION_MS ms, was $durationMs"
        }
        require(randomMaxDurationMs in 0L..MAX_DURATION_MS) {
            "Random max duration must be between 0 and $MAX_DURATION_MS ms, was $randomMaxDurationMs"
        }
        require(reinterventionMs >= 0) {
            "Reintervention must be non-negative (0 means OFF), was $reinterventionMs"
        }
        require(quickReturnGraceMs >= 0) {
            "Quick Return grace must be non-negative, was $quickReturnGraceMs"
        }
        require(attentionCheckCount in MIN_ATTENTION_CHECK_COUNT..MAX_ATTENTION_CHECK_COUNT) {
            "Attention check count must be between $MIN_ATTENTION_CHECK_COUNT and $MAX_ATTENTION_CHECK_COUNT, was $attentionCheckCount"
        }
        require(attentionCheckCodeLength in MIN_ATTENTION_CHECK_CODE_LENGTH..MAX_ATTENTION_CHECK_CODE_LENGTH) {
            "Attention check code length must be between $MIN_ATTENTION_CHECK_CODE_LENGTH and $MAX_ATTENTION_CHECK_CODE_LENGTH, was $attentionCheckCodeLength"
        }
        require(attentionCheckTimeoutMs in MIN_ATTENTION_CHECK_TIMEOUT_MS..MAX_ATTENTION_CHECK_TIMEOUT_MS) {
            "Attention check timeout must be between $MIN_ATTENTION_CHECK_TIMEOUT_MS and $MAX_ATTENTION_CHECK_TIMEOUT_MS ms, was $attentionCheckTimeoutMs"
        }
    }

    companion object {
        const val DEFAULT_PHRASE = "Сделай глубокий вдох и выдох"
        const val DEFAULT_DURATION_MS = 8_000L // 8 seconds Android default
        const val MIN_DURATION_MS = 1_000L     // 1 second
        const val MAX_DURATION_MS = 120_000L   // 120 seconds base max
        const val DEFAULT_REINTERVENTION_MS = 5 * 60 * 1000L // 5 minutes
        const val DEFAULT_GRACE_MS = 0L

        const val MIN_ATTENTION_CHECK_COUNT = 1
        const val MAX_ATTENTION_CHECK_COUNT = 5
        const val MIN_ATTENTION_CHECK_CODE_LENGTH = 3
        const val MAX_ATTENTION_CHECK_CODE_LENGTH = 8
        const val MIN_ATTENTION_CHECK_TIMEOUT_MS = 3_000L
        const val MAX_ATTENTION_CHECK_TIMEOUT_MS = 30_000L
        const val DEFAULT_ATTENTION_CHECK_TIMEOUT_MS = 5_000L
    }
}
