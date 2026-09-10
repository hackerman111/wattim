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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TerminalBadge(
    text: String,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    val colors = WattimTheme.colors

    val textColor = if (isActive) colors.accent else colors.textSecondary
    val borderColor = if (isActive) colors.accent.copy(alpha = 0.60f) else colors.border
    val shape = RoundedCornerShape(3.dp)

    val badgeContent = @Composable {
        Box(
            modifier = Modifier
                .border(BorderStroke(1.dp, borderColor), shape)
                .background(colors.surfaceElevated, shape)
                .padding(horizontal = 6.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                fontSize = 11.sp,
                letterSpacing = 0.08.sp,
                color = textColor,
                textAlign = TextAlign.Center
            )
        }
    }

    if (onClick != null) {
        Box(
            modifier = modifier
                .defaultMinSize(
                    minWidth = 48.dp,
                    minHeight = 48.dp
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    role = Role.Switch,
                    onClick = onClick
                )
                .semantics(mergeDescendants = true) {
                    stateDescription = if (isActive) "Active" else "Inactive"
                },
            contentAlignment = Alignment.Center
        ) {
            badgeContent()
        }
    } else {
        Box(
            modifier = modifier.semantics(mergeDescendants = true) {
                stateDescription = if (isActive) "Active" else "Inactive"
            },
            contentAlignment = Alignment.Center
        ) {
            badgeContent()
        }
    }
}
