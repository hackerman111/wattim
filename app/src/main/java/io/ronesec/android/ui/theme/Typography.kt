package io.ronesec.android.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val TerminalFontFamily = FontFamily.Monospace

val TerminalTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = TerminalFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        letterSpacing = 0.05.sp,
        color = TerminalTextPrimary
    ),
    titleLarge = TextStyle(
        fontFamily = TerminalFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        letterSpacing = 0.05.sp,
        color = TerminalTextPrimary
    ),
    titleMedium = TextStyle(
        fontFamily = TerminalFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        letterSpacing = 0.15.sp,
        color = TerminalTextPrimary
    ),
    bodyLarge = TextStyle(
        fontFamily = TerminalFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        letterSpacing = 0.02.sp,
        color = TerminalTextPrimary
    ),
    bodyMedium = TextStyle(
        fontFamily = TerminalFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        letterSpacing = 0.02.sp,
        color = TerminalTextPrimary
    ),
    labelSmall = TextStyle(
        fontFamily = TerminalFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        letterSpacing = 0.1.sp,
        color = TerminalTextSecondary
    )
)
