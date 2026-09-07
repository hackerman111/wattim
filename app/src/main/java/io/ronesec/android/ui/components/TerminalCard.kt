package io.ronesec.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.ronesec.android.ui.theme.LocalAppPalette

@Composable
fun TerminalCard(
    modifier: Modifier = Modifier,
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val palette = LocalAppPalette.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        border = border ?: BorderStroke(1.dp, palette.border),
        color = palette.surface
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}
