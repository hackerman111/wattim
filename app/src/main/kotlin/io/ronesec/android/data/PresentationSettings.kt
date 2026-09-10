package io.ronesec.android.data

import io.ronesec.android.ui.designsystem.ThemeId

data class PresentationSettings(
    val themeId: ThemeId = ThemeId.DEFAULT,
    val language: String = "AUTO",
    val showOverlayStats: Boolean = true,
    val savedSessionMinutes: Int = 7,
    val customEmergencyMinutes: Int? = null
)
