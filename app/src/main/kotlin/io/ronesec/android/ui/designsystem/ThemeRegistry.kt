package io.ronesec.android.ui.designsystem

import androidx.compose.ui.graphics.Color

object ThemeRegistry {
    val CyberTerminal = WattimColors(
        background = Color(0xFF090B0D),
        surface = Color(0xFF101316),
        surfaceElevated = Color(0xFF161A1E),
        textPrimary = Color(0xFFE6E8E9),
        textSecondary = Color(0xFF737A80),
        border = Color(0xFF252A2E),
        accent = Color(0xFF00E5FF),
        error = Color(0xFFFF5252)
    )

    val Nord = WattimColors(
        background = Color(0xFF2E3440),
        surface = Color(0xFF3B4252),
        surfaceElevated = Color(0xFF434C5E),
        textPrimary = Color(0xFFECEFF4),
        textSecondary = Color(0xFFD8DEE9),
        border = Color(0xFF4C566A),
        accent = Color(0xFF88C0D0),
        error = Color(0xFFBF616A)
    )

    val Catppuccin = WattimColors(
        background = Color(0xFF1E1E2E),
        surface = Color(0xFF252538),
        surfaceElevated = Color(0xFF313244),
        textPrimary = Color(0xFFCDD6F4),
        textSecondary = Color(0xFFA6ADC8),
        border = Color(0xFF45475A),
        accent = Color(0xFFCBA6F7),
        error = Color(0xFFF38BA8)
    )

    val Dracula = WattimColors(
        background = Color(0xFF282A36),
        surface = Color(0xFF343746),
        surfaceElevated = Color(0xFF44475A),
        textPrimary = Color(0xFFF8F8F2),
        textSecondary = Color(0xFF6272A4),
        border = Color(0xFF4D5368),
        accent = Color(0xFFBD93F9),
        error = Color(0xFFFF5555)
    )

    val Gruvbox = WattimColors(
        background = Color(0xFF282828),
        surface = Color(0xFF32302F),
        surfaceElevated = Color(0xFF3C3836),
        textPrimary = Color(0xFFEBDBB2),
        textSecondary = Color(0xFFA89984),
        border = Color(0xFF504945),
        accent = Color(0xFFFABD2F),
        error = Color(0xFFFB4934)
    )

    val TokyoNight = WattimColors(
        background = Color(0xFF1A1B26),
        surface = Color(0xFF24283B),
        surfaceElevated = Color(0xFF2F354F),
        textPrimary = Color(0xFFC0CAF5),
        textSecondary = Color(0xFF7982A9),
        border = Color(0xFF414868),
        accent = Color(0xFF7AA2F7),
        error = Color(0xFFF7768E)
    )

    private val themes = mapOf(
        ThemeId.CYBER_TERMINAL to CyberTerminal,
        ThemeId.NORD to Nord,
        ThemeId.CATPPUCCIN to Catppuccin,
        ThemeId.DRACULA to Dracula,
        ThemeId.GRUVBOX to Gruvbox,
        ThemeId.TOKYO_NIGHT to TokyoNight
    )

    fun getColors(themeId: ThemeId): WattimColors {
        return themes[themeId] ?: Nord
    }

    fun getAllThemes(): Map<ThemeId, WattimColors> = themes
}
