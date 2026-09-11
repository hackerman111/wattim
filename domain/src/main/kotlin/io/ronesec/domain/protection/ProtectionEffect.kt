package io.ronesec.domain.protection

import io.ronesec.domain.model.AttemptOutcome
import io.ronesec.domain.model.AttemptRecord
import io.ronesec.domain.model.SessionId
import io.ronesec.domain.model.TimedGrant
import io.ronesec.domain.policy.EffectiveInterventionConfig
import java.time.Instant

sealed interface ProtectionEffect {
    data class UpdateCodeChallenge(
        val sessionId: SessionId,
        val cycle: Int,
        val snapshot: CodeChallengeUi
    ) : ProtectionEffect

    data class OpenWattim(
        val sessionId: SessionId,
        val cycle: Int,
        val requestRevision: Long
    ) : ProtectionEffect
    data class ShowIntervention(
        val sessionId: SessionId,
        val cycle: Int,
        val config: EffectiveInterventionConfig
    ) : ProtectionEffect

    data class ShowBlock(
        val sessionId: SessionId,
        val cycle: Int,
        val packageName: String,
        val until: Instant?
    ) : ProtectionEffect

    data class UpdateOverlayComplete(
        val sessionId: SessionId,
        val cycle: Int
    ) : ProtectionEffect

    data class DismissOverlay(
        val sessionId: SessionId?
    ) : ProtectionEffect

    data class AcquireAudioLease(
        val sessionId: SessionId,
        val packageName: String
    ) : ProtectionEffect

    data class ReleaseAudioLease(
        val sessionId: SessionId
    ) : ProtectionEffect

    data object SendToHome : ProtectionEffect

    data class SetProtectionOperational(
        val operational: Boolean
    ) : ProtectionEffect

    data class RecordAttempt(
        val record: AttemptRecord
    ) : ProtectionEffect

    data class CommitAttemptOutcome(
        val attemptId: String,
        val outcome: AttemptOutcome,
        val resolvedAt: Instant
    ) : ProtectionEffect

    data class CommitAccessGrant(
        val grant: TimedGrant
    ) : ProtectionEffect

    data class RevokeGrant(
        val packageName: String,
        val grantId: String
    ) : ProtectionEffect

    data class DisableTarget(
        val packageName: String
    ) : ProtectionEffect

    data class ScheduleTemporalBoundary(
        val delayMs: Long,
        val boundaryToken: Long
    ) : ProtectionEffect

    data class UpdateServiceSubscription(
        val targetPackages: Set<String>,
        val trackAll: Boolean
    ) : ProtectionEffect

    data class ResyncRequired(
        val generation: Long,
        val requestSequence: Long
    ) : ProtectionEffect
}
