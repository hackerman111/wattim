package io.ronesec.domain.protection

import io.ronesec.domain.model.AttemptCycleId
import io.ronesec.domain.model.SessionId
import io.ronesec.domain.policy.AllowReason
import io.ronesec.domain.policy.EffectiveInterventionConfig
import java.time.Instant

enum class AttachmentStatus {
    AWAITING_ATTACHMENT,
    ATTACHED
}

data class ActiveSession(
    val sessionId: SessionId,
    val packageName: String,
    val cycle: Int,
    val attemptId: String,
    val effectiveConfig: EffectiveInterventionConfig? = null
) {
    val attemptCycleId: AttemptCycleId
        get() = AttemptCycleId(sessionId, cycle)
}

sealed interface InterveningSubstate {
    data object CodeGate : InterveningSubstate
    data object AwaitingAttachment : InterveningSubstate
    data class Breathing(
        val startElapsedMs: Long,
        val durationMs: Long,
        val deadlineElapsedMs: Long,
        val remainingCheckOffsetsMs: List<Long> = emptyList()
    ) : InterveningSubstate
    data class AttentionCheck(
        val pausedElapsedProgressMs: Long,
        val durationMs: Long,
        val code: String,
        val deadlineElapsedMs: Long,
        val timeoutMs: Long,
        val remainingCheckOffsetsMs: List<Long> = emptyList(),
        val hasError: Boolean = false
    ) : InterveningSubstate
    data class Complete(
        val startElapsedMs: Long,
        val durationMs: Long
    ) : InterveningSubstate
    data class CommittingAccess(
        val substate: InterveningSubstate
    ) : InterveningSubstate
}

sealed interface ProtectionState {
    data class Loading(
        val latestCandidatePackage: String? = null
    ) : ProtectionState

    data class Unavailable(
        val reason: String = "Service not connected"
    ) : ProtectionState

    data object Idle : ProtectionState

    data class Evaluating(
        val session: ActiveSession,
        val candidatePackage: String
    ) : ProtectionState

    data class Intervening(
        val session: ActiveSession,
        val substate: InterveningSubstate,
        val codes: SessionCodes = SessionCodes()
    ) : ProtectionState

    data class Blocked(
        val session: ActiveSession,
        val until: Instant?,
        val attachmentStatus: AttachmentStatus
    ) : ProtectionState {
        val packageName: String
            get() = session.packageName
    }

    data class Granted(
        val session: ActiveSession?,
        val packageName: String,
        val reason: AllowReason
    ) : ProtectionState

    data class Exiting(
        val session: ActiveSession?,
        val targetPackage: String,
        val retryable: Boolean = false
    ) : ProtectionState

    data class Suspended(
        val locked: Boolean
    ) : ProtectionState
}
