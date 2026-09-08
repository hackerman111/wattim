package io.ronesec.android.overlay

import io.ronesec.android.domain.model.InterventionConfig
import io.ronesec.android.ui.i18n.AppStrings
import io.ronesec.android.ui.theme.AppTheme
import java.time.Instant

sealed interface OverlayUiState {
    data class Intervention(
        val sessionId: Long,
        val targetAppName: String,
        val config: InterventionConfig,
        val savedTimeText: String?,
        val theme: AppTheme,
        val appStrings: AppStrings,
        val onEmergencyAccess: ((durationMs: Long?, disableTarget: Boolean) -> Unit)?,
        val onClose: () -> Unit,
        val onContinue: () -> Unit
    ) : OverlayUiState

    data class Block(
        val sessionId: Long,
        val sessionName: String,
        val until: Instant?,
        val theme: AppTheme,
        val appStrings: AppStrings,
        val onClose: () -> Unit
    ) : OverlayUiState
}
