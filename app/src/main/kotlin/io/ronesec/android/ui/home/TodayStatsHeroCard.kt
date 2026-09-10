package io.ronesec.android.ui.home

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
import io.ronesec.android.ui.designsystem.TerminalCard
import io.ronesec.android.ui.designsystem.WattimTheme

@Composable
fun TodayStatsHeroCard(
    stats: TodayHeroState,
    modifier: Modifier = Modifier
) {
    val colors = WattimTheme.colors
    val typography = WattimTheme.typography

    TerminalCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.today_stats_title).uppercase(),
                fontFamily = typography.bodyMedium.fontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                letterSpacing = 0.15.sp,
                color = colors.textSecondary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricColumn(
                    value = stats.totalAttempts.toString(),
                    label = stringResource(R.string.today_stats_attempts)
                )

                MetricColumn(
                    value = stats.closedCount.toString(),
                    label = stringResource(R.string.today_stats_closed),
                    isAccent = true
                )

                MetricColumn(
                    value = "${stats.preventedPercent}%",
                    label = stringResource(R.string.today_stats_prevented)
                )
            }
        }
    }
}

@Composable
private fun MetricColumn(
    value: String,
    label: String,
    isAccent: Boolean = false,
    modifier: Modifier = Modifier
) {
    val colors = WattimTheme.colors
    val typography = WattimTheme.typography

    Column(modifier = modifier) {
        Text(
            text = value,
            fontFamily = typography.bodyMedium.fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 26.sp,
            color = if (isAccent) colors.accent else colors.textPrimary
        )
        Text(
            text = label.uppercase(),
            fontFamily = typography.bodyMedium.fontFamily,
            fontSize = 11.sp,
            color = colors.textSecondary
        )
    }
}
