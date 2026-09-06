package io.ronesec.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ronesec.android.ui.theme.LocalTerminalAccent
import io.ronesec.android.ui.theme.TerminalBorder
import io.ronesec.android.ui.theme.TerminalFontFamily
import io.ronesec.android.ui.theme.TerminalSurfaceElevated
import io.ronesec.android.ui.theme.TerminalTextSecondary

@Composable
fun TerminalBadge(
    text: String,
    isActive: Boolean = false,
    modifier: Modifier = Modifier
) {
    val accent = LocalTerminalAccent.current
    val borderColor = if (isActive) accent.copy(alpha = 0.6f) else TerminalBorder
    val textColor = if (isActive) accent else TerminalTextSecondary

    Box(
        modifier = modifier
            .border(1.dp, borderColor, RoundedCornerShape(3.dp))
            .background(TerminalSurfaceElevated, RoundedCornerShape(3.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            fontFamily = TerminalFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            letterSpacing = 0.08.sp,
            color = textColor
        )
    }
}
