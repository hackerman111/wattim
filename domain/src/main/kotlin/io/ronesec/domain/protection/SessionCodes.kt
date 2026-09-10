package io.ronesec.domain.protection

enum class CodeTravel { NONE, TO_WATTIM, IN_WATTIM, RETURNING }

data class SessionCodes(
    val unlockCode: String? = null,
    val unlockExpiresElapsedMs: Long? = null,
    val emergencyCode: String? = null,
    val travel: CodeTravel = CodeTravel.NONE,
    val error: Boolean = false,
    val emergencyError: Boolean = false
) {
    override fun toString(): String = "SessionCodes(redacted, travel=$travel)"
}

data class CodeChallengeUi(
    val gate: Boolean,
    val generated: Boolean,
    val codeLength: Int,
    val emergencyCode: String?,
    val error: Boolean,
    val breathingStartElapsedMs: Long?,
    val emergencyError: Boolean = false
) {
    override fun toString(): String = "CodeChallengeUi(gate=$gate, generated=$generated, redacted)"
}

fun ProtectionState.Intervening.codeChallengeUi() = CodeChallengeUi(
    gate = substate is InterveningSubstate.CodeGate ||
        (substate is InterveningSubstate.AwaitingAttachment && session.effectiveConfig?.twoStageUnlock == true),
    generated = codes.unlockCode != null,
    codeLength = session.effectiveConfig?.unlockCodeLength ?: 4,
    emergencyCode = codes.emergencyCode,
    error = codes.error,
    breathingStartElapsedMs = when (substate) {
        is InterveningSubstate.Breathing -> substate.startElapsedMs
        is InterveningSubstate.Complete -> substate.startElapsedMs
        else -> null
    },
    emergencyError = codes.emergencyError
)
