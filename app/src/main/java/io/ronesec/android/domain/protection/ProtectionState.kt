package io.ronesec.android.domain.protection

import java.time.Instant

sealed interface ProtectionState {
    data object Idle : ProtectionState

    data class Evaluating(
        val session: TargetSession
    ) : ProtectionState

    data class Intervening(
        val session: TargetSession
    ) : ProtectionState

    data class Granted(
        val session: TargetSession,
        val permit: AccessPermit
    ) : ProtectionState

    data class Blocked(
        val session: TargetSession,
        val until: Instant
    ) : ProtectionState

    data class Exiting(
        val session: TargetSession
    ) : ProtectionState

    data object Suspended : ProtectionState
}

data class TargetSession(
    val id: SessionId,
    val packageName: String,
    val startedAt: Instant
)
