package io.ronesec.domain.protection

import io.ronesec.domain.model.RuntimePolicySnapshot
import io.ronesec.domain.model.SessionId

sealed interface ProtectionEvent {
    data class GenerateUnlockCode(val sessionId: SessionId, val cycle: Int) : ProtectionEvent
    data class CodeTripFailed(val sessionId: SessionId, val cycle: Int) : ProtectionEvent
    data class SubmitUnlockCode(val sessionId: SessionId, val cycle: Int, val code: String) : ProtectionEvent {
        override fun toString(): String = "SubmitUnlockCode(sessionId=$sessionId, cycle=$cycle, redacted)"
    }
    data class ForegroundCandidate(
        val packageName: String,
        val sourceUptimeMs: Long,
        val eventSequence: Long,
        val isLauncher: Boolean = false
    ) : ProtectionEvent

    data class CoherentReady(
        val snapshot: RuntimePolicySnapshot
    ) : ProtectionEvent

    data class OverlayAttached(
        val sessionId: SessionId,
        val cycle: Int
    ) : ProtectionEvent

    data class OverlayAttachFailed(
        val sessionId: SessionId,
        val cycle: Int,
        val errorType: String
    ) : ProtectionEvent

    data class OverlayDetached(
        val sessionId: SessionId
    ) : ProtectionEvent

    data class BreathingDeadlineReached(
        val sessionId: SessionId,
        val cycle: Int
    ) : ProtectionEvent

    data class ActionContinue(
        val sessionId: SessionId,
        val cycle: Int
    ) : ProtectionEvent

    data class ActionExit(
        val sessionId: SessionId?
    ) : ProtectionEvent

    data class ActionCancel(
        val sessionId: SessionId?
    ) : ProtectionEvent

    data class ActionEmergencyOnce(
        val sessionId: SessionId,
        val cycle: Int,
        val code: String? = null
    ) : ProtectionEvent {
        override fun toString(): String = "ActionEmergencyOnce(sessionId=$sessionId, cycle=$cycle, redacted)"
    }

    data class ActionEmergencyTimed(
        val sessionId: SessionId,
        val cycle: Int,
        val durationMs: Long,
        val code: String? = null
    ) : ProtectionEvent {
        override fun toString(): String = "ActionEmergencyTimed(sessionId=$sessionId, cycle=$cycle, redacted)"
    }

    data class ActionEmergencyForever(
        val sessionId: SessionId,
        val cycle: Int,
        val code: String? = null
    ) : ProtectionEvent {
        override fun toString(): String = "ActionEmergencyForever(sessionId=$sessionId, cycle=$cycle, redacted)"
    }

    data object ScreenOff : ProtectionEvent
    data object ScreenOnLocked : ProtectionEvent
    data class ScreenUnlocked(
        val generation: Long
    ) : ProtectionEvent

    data object ServiceDisconnected : ProtectionEvent
    data class ServiceConnected(val generation: Long) : ProtectionEvent

    data class PolicyCommitted(
        val revision: Long,
        val snapshot: RuntimePolicySnapshot
    ) : ProtectionEvent

    data class TemporalBoundaryReached(val boundaryToken: Long) : ProtectionEvent

    data class DepartureConfirmed(val packageName: String) : ProtectionEvent
    data class ResyncRequested(val generation: Long, val requestSequence: Long) : ProtectionEvent
}
