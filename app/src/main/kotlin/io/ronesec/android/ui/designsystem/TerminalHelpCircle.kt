package io.ronesec.android.ui.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Terminal-styled circular question mark button for feature explanations.
 * Hit target: 48x48dp for accessibility.
 * Visual diameter: 20x20dp.
 */
@Composable
fun TerminalHelpCircle(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescriptionText: String = "Help"
) {
    val colors = WattimTheme.colors

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .semantics {
                contentDescription = contentDescriptionText
            }
            .clickable(
                role = Role.Button,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .border(BorderStroke(1.dp, colors.accent.copy(alpha = 0.6f)), CircleShape)
                .background(colors.surfaceElevated, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "?",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = colors.accent,
                textAlign = TextAlign.Center
            )
        }
    }
}
