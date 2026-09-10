package io.ronesec.domain.model

import java.time.Instant

enum class AttemptKind {
    ENTRY,
    REINTERVENTION
}

enum class AttemptOutcome {
    CONTINUED,
    ABANDONED,
    BLOCKED,
    INTERRUPTED;

    /**
     * True if outcome is visible in product/user statistics.
     * INTERRUPTED is internal only and excluded from totals.
     */
    val isProductVisible: Boolean
        get() = this != INTERRUPTED

    val isClosed: Boolean
        get() = this == ABANDONED || this == BLOCKED
}

data class AttemptRecord(
    val attemptId: String,
    val sessionId: SessionId,
    val cycle: Int,
    val packageName: String,
    val displayNameAtAttempt: String,
    val generation: Long,
    val kind: AttemptKind,
    val timestamp: Instant,
    val outcome: AttemptOutcome? = null,
    val resolvedAt: Instant? = null
) {
    val isResolved: Boolean
        get() = outcome != null
}
