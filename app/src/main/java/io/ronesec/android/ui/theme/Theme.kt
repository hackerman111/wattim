package io.ronesec.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

val LocalTerminalAccent = staticCompositionLocalOf { AccentCyan }

@Composable
fun RonesecTheme(
    accent: TerminalAccent = TerminalAccent.CYAN,
    content: @Composable () -> Unit
) {
    val colorScheme = darkColorScheme(
        primary = accent.color,
        onPrimary = TerminalBackground,
        surface = TerminalSurface,
        onSurface = TerminalTextPrimary,
        background = TerminalBackground,
        onBackground = TerminalTextPrimary,
        outline = TerminalBorder
    )

    CompositionLocalProvider(
        LocalTerminalAccent provides accent.color
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = TerminalTypography,
            content = content
        )
    }
}
