package io.ronesec.android.domain.protection

import java.time.Instant

sealed interface AccessPermit {
    data class Session(
        val sessionId: SessionId
    ) : AccessPermit

    data class Timed(
        val expiresAt: Instant
    ) : AccessPermit
}
