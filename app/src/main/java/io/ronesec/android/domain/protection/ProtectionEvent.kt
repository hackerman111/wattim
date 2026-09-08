package io.ronesec.android.domain.protection

import io.ronesec.android.domain.engine.RuntimeState
import java.time.Instant

enum class BoundaryType {
    REINTERVENTION,
    TIMED_PERMIT_EXPIRY,
    GLOBAL_PAUSE_EXPIRY,
    SCHEDULE_BOUNDARY
}

sealed interface UserProtectionAction {
    data class EmergencyAccess(val durationMs: Long?, val disableTarget: Boolean) : UserProtectionAction
    data object Close : UserProtectionAction
    data object Continue : UserProtectionAction
}

sealed interface ProtectionEvent {
    data class ForegroundChanged(
        val packageName: String,
        val timestamp: Instant = Instant.now()
    ) : ProtectionEvent

    data class TemporalBoundaryReached(
        val sessionId: Long,
        val targetPackage: String,
        val boundaryType: BoundaryType,
        val timestamp: Instant = Instant.now()
    ) : ProtectionEvent

    data class UserAction(
        val sessionId: Long,
        val targetPackage: String,
        val action: UserProtectionAction,
        val timestamp: Instant = Instant.now()
    ) : ProtectionEvent

    data class PolicySnapshotUpdated(
        val snapshot: RuntimeState
    ) : ProtectionEvent

    data object ScreenOff : ProtectionEvent
    data object ServiceInterrupted : ProtectionEvent
}
