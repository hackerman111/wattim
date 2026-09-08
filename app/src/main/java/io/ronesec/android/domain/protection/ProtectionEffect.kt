package io.ronesec.android.domain.protection

import io.ronesec.android.domain.model.AttemptOutcome
import io.ronesec.android.domain.model.InterventionConfig
import java.time.Instant

sealed interface ProtectionEffect {
    data class ShowInterventionOverlay(
        val sessionId: SessionId,
        val targetPackage: String,
        val appLabel: String,
        val config: InterventionConfig,
        val savedTimeText: String?
    ) : ProtectionEffect

    data class ShowBlockOverlay(
        val sessionId: SessionId,
        val targetPackage: String,
        val sessionName: String,
        val until: Instant?
    ) : ProtectionEffect

    data class DismissOverlay(val sessionId: SessionId) : ProtectionEffect

    data class AcquireAudio(val sessionId: SessionId) : ProtectionEffect
    data class ReleaseAudio(val sessionId: SessionId) : ProtectionEffect

    data class ScheduleBoundary(
        val sessionId: SessionId,
        val targetPackage: String,
        val boundaryType: BoundaryType,
        val delayMs: Long
    ) : ProtectionEffect

    data class CancelBoundary(val sessionId: SessionId) : ProtectionEffect

    data class PerformGlobalHome(val sessionId: SessionId) : ProtectionEffect

    data class PersistAttempt(
        val targetPackage: String,
        val outcome: AttemptOutcome,
        val timestamp: Instant
    ) : ProtectionEffect

    data class PersistGrant(
        val targetPackage: String,
        val durationMs: Long?,
        val sessionId: SessionId
    ) : ProtectionEffect

    data class PersistRevoke(
        val targetPackage: String,
        val sessionId: SessionId
    ) : ProtectionEffect

    data class PersistTargetDisabled(
        val targetPackage: String
    ) : ProtectionEffect

    data class UpdateAdaptiveSubscription(
        val targetPackages: Set<String>,
        val isTargetActive: Boolean
    ) : ProtectionEffect
}
