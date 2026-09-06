package io.ronesec.android.domain.model

import java.time.Instant

data class OpenAttempt(
    val id: Long = 0,
    val packageName: String,
    val timestamp: Instant,
    val outcome: AttemptOutcome
)
