package io.ronesec.android.ui.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun TerminalCard(
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    border: BorderStroke? = null,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = WattimTheme.colors

    val effectiveBorder = border ?: BorderStroke(1.dp, if (isError) colors.error else colors.border)
    val shape = RoundedCornerShape(4.dp)

    Surface(
        modifier = modifier
            .then(
                if (onClick != null) {
                    Modifier
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                }
            ),
        shape = shape,
        color = colors.surface,
        border = effectiveBorder
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}
