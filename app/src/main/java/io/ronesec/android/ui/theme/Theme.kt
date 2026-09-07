package io.ronesec.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

val LocalAppPalette = staticCompositionLocalOf { AppTheme.NORD.palette }
val LocalTerminalAccent = staticCompositionLocalOf { AppTheme.NORD.palette.accent }

@Composable
fun RonesecTheme(
    theme: AppTheme = AppTheme.NORD,
    accent: TerminalAccent? = null,
    content: @Composable () -> Unit
) {
    val palette = if (accent != null) {
        theme.palette.copy(accent = accent.color)
    } else {
        theme.palette
    }

    val colorScheme = darkColorScheme(
        primary = palette.accent,
        onPrimary = palette.background,
        surface = palette.surface,
        onSurface = palette.textPrimary,
        background = palette.background,
        onBackground = palette.textPrimary,
        outline = palette.border
    )

    CompositionLocalProvider(
        LocalAppPalette provides palette,
        LocalTerminalAccent provides palette.accent
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = TerminalTypography,
            content = content
        )
    }
}
