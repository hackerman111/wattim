package io.ronesec.domain.protection

enum class CodeTravel { NONE, TO_WATTIM, IN_WATTIM, RETURNING }

data class SessionCodes(
    val unlockCode: String? = null,
    val unlockExpiresElapsedMs: Long? = null,
    val unlockRequestRevision: Long = 0L,
    val emergencyCode: String? = null,
    val travel: CodeTravel = CodeTravel.NONE,
    val error: Boolean = false,
    val emergencyError: Boolean = false
) {
    override fun toString(): String = "SessionCodes(redacted, travel=$travel)"
}

data class AttentionCheckUi(
    val active: Boolean,
    val code: String = "",
    val deadlineElapsedMs: Long = 0L,
    val timeoutMs: Long = 0L,
    val pausedElapsedProgressMs: Long = 0L,
    val totalDurationMs: Long = 0L,
    val hasError: Boolean = false
) {
    override fun toString(): String = "AttentionCheckUi(active=$active, redacted)"
}

data class CodeChallengeUi(
    val gate: Boolean,
    val generated: Boolean,
    val unlockRequestRevision: Long,
    val codeLength: Int,
    val emergencyCode: String?,
    val error: Boolean,
    val breathingStartElapsedMs: Long?,
    val emergencyError: Boolean = false,
    val attentionCheck: AttentionCheckUi? = null
) {
    override fun toString(): String = "CodeChallengeUi(gate=$gate, generated=$generated, redacted)"
}

fun ProtectionState.Intervening.codeChallengeUi() = CodeChallengeUi(
    gate = substate is InterveningSubstate.CodeGate ||
        (substate is InterveningSubstate.AwaitingAttachment && session.effectiveConfig?.twoStageUnlock == true),
    generated = codes.unlockCode != null,
    unlockRequestRevision = codes.unlockRequestRevision,
    codeLength = session.effectiveConfig?.unlockCodeLength ?: 4,
    emergencyCode = codes.emergencyCode,
    error = codes.error,
    breathingStartElapsedMs = when (substate) {
        is InterveningSubstate.Breathing -> substate.startElapsedMs
        is InterveningSubstate.Complete -> substate.startElapsedMs
        else -> null
    },
    emergencyError = codes.emergencyError,
    attentionCheck = if (substate is InterveningSubstate.AttentionCheck) {
        AttentionCheckUi(
            active = true,
            code = substate.code,
            deadlineElapsedMs = substate.deadlineElapsedMs,
            timeoutMs = substate.timeoutMs,
            pausedElapsedProgressMs = substate.pausedElapsedProgressMs,
            totalDurationMs = substate.durationMs,
            hasError = substate.hasError
        )
    } else null
)
