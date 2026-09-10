package io.ronesec.android.ui.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class WattimColors(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val border: Color,
    val accent: Color,
    val error: Color
) {
    companion object {
        // Shared emergency scrim is specified black at 0.75 alpha (theme-independent overlay token)
        val EmergencyScrim: Color = Color.Black.copy(alpha = 0.75f)
    }
}
