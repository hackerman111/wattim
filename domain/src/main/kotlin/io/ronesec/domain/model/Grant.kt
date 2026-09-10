package io.ronesec.domain.model

import java.time.Instant

/**
 * Unique identity of a foreground protection session for an enabled target.
 * Generated as (processNonce, serviceGeneration, entryCounter).
 */
data class SessionId(
    val processNonce: Long,
    val serviceGeneration: Long,
    val entryCounter: Long
)

/**
 * Identity of a specific challenge cycle within a session.
 * Reintervention creates a new cycle within the same session.
 */
data class AttemptCycleId(
    val sessionId: SessionId,
    val cycle: Int
)

/**
 * Transient in-memory permit granting foreground access to a package for a specific session.
 * If [expiresElapsedMs] is null, access lasts until departure (reintervention OFF).
 * If non-null, access expires at monotonic elapsed time (reintervention > 0).
 */
data class SessionPermit(
    val packageName: String,
    val sessionId: SessionId,
    val expiresElapsedMs: Long? = null
) {
    fun isExpired(nowElapsedMs: Long): Boolean {
        return expiresElapsedMs != null && nowElapsedMs >= expiresElapsedMs
    }
}

enum class GrantOrigin {
    REINTERVENTION,
    EMERGENCY
}

/**
 * Persistent timed grant surviving application departure and process restart until [expiresAt].
 */
data class TimedGrant(
    val packageName: String,
    val grantId: String,
    val origin: GrantOrigin,
    val createdAt: Instant,
    val expiresAt: Instant,
    val grantVersion: Long = 1L
) {
    fun isExpired(now: Instant): Boolean {
        return !now.isBefore(expiresAt) // half-open: now >= expiresAt means expired
    }
}
