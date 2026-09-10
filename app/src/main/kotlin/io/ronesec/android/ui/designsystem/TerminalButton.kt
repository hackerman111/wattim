package io.ronesec.android.ui.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class TerminalButtonVariant {
    PRIMARY,
    SECONDARY,
    DANGER
}

@Composable
fun TerminalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: TerminalButtonVariant = TerminalButtonVariant.SECONDARY,
    isPrimary: Boolean? = null,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    val colors = WattimTheme.colors
    val effectiveVariant = if (isPrimary != null) {
        if (isPrimary) TerminalButtonVariant.PRIMARY else TerminalButtonVariant.SECONDARY
    } else {
        variant
    }

    val baseColor = when (effectiveVariant) {
        TerminalButtonVariant.PRIMARY -> colors.accent
        TerminalButtonVariant.SECONDARY -> colors.textPrimary
        TerminalButtonVariant.DANGER -> colors.error
    }

    val baseBorderColor = when (effectiveVariant) {
        TerminalButtonVariant.PRIMARY -> colors.accent
        TerminalButtonVariant.SECONDARY -> colors.border
        TerminalButtonVariant.DANGER -> colors.error
    }

    val textColor = if (enabled) baseColor else baseColor.copy(alpha = 0.40f)
    val borderColor = if (enabled) baseBorderColor else colors.border.copy(alpha = 0.50f)

    val shape = RoundedCornerShape(4.dp)

    Box(
        modifier = modifier
            .defaultMinSize(
                minWidth = 48.dp,
                minHeight = 48.dp
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled && !isLoading,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .border(BorderStroke(1.dp, borderColor), shape)
                .background(colors.surface, shape)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            val displayText = if (isLoading) "..." else text.uppercase()
            Text(
                text = displayText,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                fontSize = 13.sp,
                letterSpacing = 0.1.sp,
                color = textColor,
                textAlign = TextAlign.Center
            )
        }
    }
}
