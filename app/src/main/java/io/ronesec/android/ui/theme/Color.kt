package io.ronesec.android.ui.theme

import androidx.compose.ui.graphics.Color

// Neo-Terminal Base Palette
val TerminalBackground = Color(0xFF090B0D)
val TerminalSurface = Color(0xFF101316)
val TerminalSurfaceElevated = Color(0xFF161A1E)
val TerminalTextPrimary = Color(0xFFE6E8E9)
val TerminalTextSecondary = Color(0xFF737A80)
val TerminalBorder = Color(0xFF252A2E)
val TerminalBorderActive = Color(0xFF3B4348)
val TerminalError = Color(0xFFFF5252)

// Accent Choices
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
