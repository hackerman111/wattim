package io.ronesec.android.ui.theme

import androidx.compose.ui.graphics.Color

data class AppPalette(
    val id: String,
    val name: String,
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val border: Color,
    val borderActive: Color,
    val accent: Color,
    val error: Color
)

enum class AppTheme(
    val id: String,
    val displayName: String,
    val palette: AppPalette
) {
    CYBER_TERMINAL(
        id = "cyber_terminal",
        displayName = "Cyber Terminal",
        palette = AppPalette(
            id = "cyber_terminal",
            name = "Cyber Terminal",
            background = Color(0xFF090B0D),
            surface = Color(0xFF101316),
            surfaceElevated = Color(0xFF161A1E),
            textPrimary = Color(0xFFE6E8E9),
            textSecondary = Color(0xFF737A80),
            border = Color(0xFF252A2E),
            borderActive = Color(0xFF3B4348),
            accent = Color(0xFF00E5FF),
            error = Color(0xFFFF5252)
        )
    ),
    NORD(
        id = "nord",
        displayName = "Nord",
        palette = AppPalette(
            id = "nord",
            name = "Nord",
            background = Color(0xFF2E3440),
            surface = Color(0xFF3B4252),
            surfaceElevated = Color(0xFF434C5E),
            textPrimary = Color(0xFFECEFF4),
            textSecondary = Color(0xFFD8DEE9),
            border = Color(0xFF4C566A),
            borderActive = Color(0xFF88C0D0),
            accent = Color(0xFF88C0D0),
            error = Color(0xFFBF616A)
        )
    ),
    CATPPUCCIN(
        id = "catppuccin",
        displayName = "Catppuccin",
        palette = AppPalette(
            id = "catppuccin",
            name = "Catppuccin",
            background = Color(0xFF1E1E2E),
            surface = Color(0xFF252538),
            surfaceElevated = Color(0xFF313244),
            textPrimary = Color(0xFFCDD6F4),
            textSecondary = Color(0xFFA6ADC8),
            border = Color(0xFF45475A),
            borderActive = Color(0xFFCBA6F7),
            accent = Color(0xFFCBA6F7),
            error = Color(0xFFF38BA8)
        )
    ),
    DRACULA(
        id = "dracula",
        displayName = "Dracula",
        palette = AppPalette(
            id = "dracula",
            name = "Dracula",
            background = Color(0xFF282A36),
            surface = Color(0xFF343746),
            surfaceElevated = Color(0xFF44475A),
            textPrimary = Color(0xFFF8F8F2),
            textSecondary = Color(0xFF6272A4),
            border = Color(0xFF4D5368),
            borderActive = Color(0xFFBD93F9),
            accent = Color(0xFFBD93F9),
            error = Color(0xFFFF5555)
        )
    ),
    GRUVBOX(
        id = "gruvbox",
        displayName = "Gruvbox",
        palette = AppPalette(
            id = "gruvbox",
            name = "Gruvbox",
            background = Color(0xFF282828),
            surface = Color(0xFF32302F),
            surfaceElevated = Color(0xFF3C3836),
            textPrimary = Color(0xFFEBDBB2),
            textSecondary = Color(0xFFA89984),
            border = Color(0xFF504945),
            borderActive = Color(0xFFFABD2F),
            accent = Color(0xFFFABD2F),
            error = Color(0xFFFB4934)
        )
    ),
    TOKYO_NIGHT(
        id = "tokyo_night",
        displayName = "Tokyo Night",
        palette = AppPalette(
            id = "tokyo_night",
            name = "Tokyo Night",
            background = Color(0xFF1A1B26),
            surface = Color(0xFF24283B),
            surfaceElevated = Color(0xFF2F354F),
            textPrimary = Color(0xFFC0CAF5),
            textSecondary = Color(0xFF7982A9),
            border = Color(0xFF414868),
            borderActive = Color(0xFF7AA2F7),
            accent = Color(0xFF7AA2F7),
            error = Color(0xFFF7768E)
        )
    );

    companion object {
        fun fromId(id: String?): AppTheme =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) || it.name.equals(id, ignoreCase = true) }
                ?: NORD
    }
}

// Fallbacks for legacy references
val TerminalBackground = Color(0xFF090B0D)
val TerminalSurface = Color(0xFF101316)
val TerminalSurfaceElevated = Color(0xFF161A1E)
val TerminalTextPrimary = Color(0xFFE6E8E9)
val TerminalTextSecondary = Color(0xFF737A80)
val TerminalBorder = Color(0xFF252A2E)
val TerminalBorderActive = Color(0xFF3B4348)
val TerminalError = Color(0xFFFF5252)

val AccentCyan = Color(0xFF00E5FF)
val AccentGreen = Color(0xFF00E676)
val AccentOrange = Color(0xFFFF9100)
val AccentViolet = Color(0xFFD500F9)

enum class TerminalAccent(val label: String, val color: Color) {
    CYAN("CYAN", AccentCyan),
    GREEN("GREEN", AccentGreen),
    ORANGE("ORANGE", AccentOrange),
    VIOLET("VIOLET", AccentViolet);

    companion object {
        fun fromName(name: String?): TerminalAccent =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: CYAN
    }
}
