package io.ronesec.android.ui.config

import io.ronesec.android.platform.system.PermissionState
import io.ronesec.android.platform.audio.AudioDiagnosticState
import io.ronesec.android.ui.designsystem.ThemeId

/**
 * Immutable UI state for the Configuration/Settings screen (F72-F78).
 */
data class ConfigUiState(
    val selectedTheme: ThemeId = ThemeId.NORD,
    val selectedLanguage: String = "AUTO",
    val savedSessionMinutes: Int = 7,
    val showOverlayStats: Boolean = true,
    val accessibilityState: PermissionState = PermissionState.Denied,
    val mediaControlState: PermissionState = PermissionState.Denied,
    val overlayState: PermissionState = PermissionState.Denied,
    val batteryState: PermissionState = PermissionState.Denied,
    val audioDiagnostic: AudioDiagnosticState = AudioDiagnosticState(),
    val customEmergencyMinutes: Int? = null,
    val errorMessage: String? = null
)
