package io.ronesec.android.ui.target

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.ronesec.android.R
import androidx.compose.foundation.clickable
import androidx.compose.ui.unit.dp
import io.ronesec.android.ui.designsystem.TerminalBadge
import io.ronesec.android.ui.designsystem.TerminalCard
import io.ronesec.android.ui.designsystem.TerminalHelpCircle
import io.ronesec.android.ui.designsystem.WattimTheme

@Composable
fun QuickReturnEditor(
    graceMs: Long,
    onGraceChange: (Long) -> Unit,
    onHelpClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = WattimTheme.colors
    val dimensions = WattimTheme.dimensions
    val typography = WattimTheme.typography

    val options = listOf(
        Pair(0L, "0"),
        Pair(15_000L, "15S"),
        Pair(30_000L, "30S"),
        Pair(60_000L, "1M"),
        Pair(120_000L, "2M"),
        Pair(300_000L, "5M")
    )

    TerminalCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(dimensions.space12)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.quick_return_label).uppercase(),
                    style = typography.labelSmall,
                    color = colors.textSecondary
                )
                if (onHelpClick != null) {
                    TerminalHelpCircle(
                        onClick = onHelpClick,
                        contentDescriptionText = stringResource(R.string.quick_return_label)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                options.forEach { (ms, label) ->
                    val isSelected = (graceMs == ms)
                    TerminalBadge(
                        text = label,
                        isActive = isSelected,
                        modifier = Modifier.clickable { onGraceChange(ms) }
                    )
                }
            }
        }
    }
}
