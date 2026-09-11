package io.ronesec.android.ui.codes

import androidx.compose.runtime.Immutable
import io.ronesec.domain.protection.ProtectionState

@Immutable
sealed interface CodesPanelState {
    data object Empty : CodesPanelState

    data class Active(
        val label: String,
        val code: String
    ) : CodesPanelState
}

internal fun ProtectionState?.toCodesPanelState(isActivityResumed: Boolean): CodesPanelState {
    if (!isActivityResumed) return CodesPanelState.Empty

    val intervening = this as? ProtectionState.Intervening ?: return CodesPanelState.Empty
    val unlockCode = intervening.codes.unlockCode?.takeIf(String::isNotBlank)
        ?: return CodesPanelState.Empty
    val session = intervening.session
    val label = session.effectiveConfig
        ?.displayName
        ?.takeIf(String::isNotBlank)
        ?: session.packageName

    return CodesPanelState.Active(label = label, code = unlockCode)
}
