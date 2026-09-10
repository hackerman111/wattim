package io.ronesec.android.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ronesec.android.R
import io.ronesec.android.ui.designsystem.TerminalCard
import io.ronesec.android.ui.designsystem.WattimTheme

@Composable
fun TodaySummaryCard(
    totalAttempts: Int,
    continuedCount: Int,
    closedCount: Int,
    avoidedPercent: Int,
    modifier: Modifier = Modifier
) {
    val colors = WattimTheme.colors
    val typography = WattimTheme.typography

    TerminalCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.today_stats_title).uppercase(),
                fontFamily = typography.bodyMedium.fontFamily,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                fontSize = 12.sp,
                letterSpacing = 0.1.sp,
                color = colors.textSecondary
            )

            MetricRow(
                label = stringResource(R.string.today_stats_attempts),
                value = totalAttempts.toString()
            )
            MetricRow(
                label = stringResource(R.string.today_stats_continued),
                value = continuedCount.toString()
            )
            MetricRow(
                label = stringResource(R.string.today_stats_closed),
                value = closedCount.toString(),
                isAccent = true
            )
            MetricRow(
                label = stringResource(R.string.today_stats_prevented),
                value = "$avoidedPercent%",
                isAccent = true,
                emphasized = true
            )
        }
    }
}

@Composable
private fun MetricRow(
    label: String,
    value: String,
    isAccent: Boolean = false,
    emphasized: Boolean = false
) {
    val colors = WattimTheme.colors
    val typography = WattimTheme.typography

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontFamily = typography.bodyMedium.fontFamily,
            fontSize = 13.sp,
            color = colors.textSecondary
        )
        Text(
            text = value,
            fontFamily = typography.bodyMedium.fontFamily,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            fontSize = if (emphasized) 16.sp else 14.sp,
            color = if (isAccent) colors.accent else colors.textPrimary
        )
    }
}
