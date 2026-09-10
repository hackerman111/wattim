package io.ronesec.android.ui.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ronesec.android.R
import io.ronesec.android.ui.designsystem.TerminalBadge
import io.ronesec.android.ui.designsystem.TerminalCard
import io.ronesec.android.ui.designsystem.WattimTheme

/**
 * F75 Overlay statistics toggle: ON/OFF controls leaf saved-time badge in overlay.
 */
@Composable
fun OverlayStatsCard(
    showOverlayStats: Boolean,
    onToggleShowOverlayStats: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = WattimTheme.colors
    val typography = WattimTheme.typography

    TerminalCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
            ) {
                Text(
                    text = stringResource(R.string.config_overlay_stats_title),
                    fontFamily = typography.bodyMedium.fontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    letterSpacing = 0.1.sp,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                Text(
                    text = stringResource(R.string.config_overlay_stats_desc),
                    fontFamily = typography.bodyMedium.fontFamily,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = colors.textSecondary
                )
            }

            TerminalBadge(
                text = if (showOverlayStats) stringResource(R.string.status_on) else stringResource(R.string.status_off),
                isActive = showOverlayStats,
                onClick = { onToggleShowOverlayStats(!showOverlayStats) }
            )
        }
    }
}
