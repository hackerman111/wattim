package io.ronesec.android.ui.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

val LocalWattimColors = staticCompositionLocalOf { ThemeRegistry.Nord }
val LocalWattimTypography = staticCompositionLocalOf { WattimTypography() }
val LocalWattimDimensions = staticCompositionLocalOf { WattimDimensions() }

object WattimTheme {
    val colors: WattimColors
        @Composable
        @ReadOnlyComposable
        get() = LocalWattimColors.current

    val typography: WattimTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalWattimTypography.current

    val dimensions: WattimDimensions
        @Composable
        @ReadOnlyComposable
        get() = LocalWattimDimensions.current
}

@Composable
fun WattimTheme(
    themeId: ThemeId = ThemeId.DEFAULT,
    colors: WattimColors = ThemeRegistry.getColors(themeId),
    typography: WattimTypography = WattimTypography(),
    dimensions: WattimDimensions = WattimDimensions(),
    content: @Composable () -> Unit
) {
    val materialColorScheme = darkColorScheme(
        primary = colors.accent,
        onPrimary = colors.background,
        surface = colors.surface,
        onSurface = colors.textPrimary,
        surfaceVariant = colors.surfaceElevated,
        onSurfaceVariant = colors.textSecondary,
        background = colors.background,
        onBackground = colors.textPrimary,
        error = colors.error,
        onError = colors.background,
        outline = colors.border
    )

    val materialShapes = Shapes(
        extraSmall = RoundedCornerShape(dimensions.badgeRadius),
        small = RoundedCornerShape(dimensions.cardRadius),
        medium = RoundedCornerShape(dimensions.cardRadius),
        large = RoundedCornerShape(dimensions.cardRadius),
        extraLarge = RoundedCornerShape(dimensions.cardRadius)
    )

    val materialTypography = Typography(
        displayLarge = typography.displayLarge,
        titleLarge = typography.titleLarge,
        titleMedium = typography.titleMedium,
        bodyLarge = typography.bodyLarge,
        bodyMedium = typography.bodyMedium,
        labelLarge = typography.button,
        labelSmall = typography.labelSmall
    )

    CompositionLocalProvider(
        LocalWattimColors provides colors,
        LocalWattimTypography provides typography,
        LocalWattimDimensions provides dimensions
    ) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            shapes = materialShapes,
            typography = materialTypography,
            content = content
        )
    }
}
