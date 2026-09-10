package io.ronesec.android.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ronesec.android.R
import io.ronesec.android.data.AppStatsRow
import io.ronesec.android.ui.designsystem.TerminalCard
import io.ronesec.android.ui.designsystem.WattimTheme

@Composable
fun AppStatsTable(
    appStats: List<AppStatsRow>,
    scrollRows: Boolean = true,
    modifier: Modifier = Modifier
) {
    val colors = WattimTheme.colors
    val typography = WattimTheme.typography

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.stats_apps_today_title),
            fontFamily = typography.bodyMedium.fontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            letterSpacing = 0.15.sp,
            color = colors.textSecondary,
            modifier = Modifier.padding(bottom = 10.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.stats_col_app),
                fontFamily = typography.bodyMedium.fontFamily,
                fontSize = 12.sp,
                color = colors.textSecondary,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(R.string.stats_col_open),
                fontFamily = typography.bodyMedium.fontFamily,
                fontSize = 12.sp,
                color = colors.textSecondary,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Text(
                text = stringResource(R.string.stats_col_closed),
                fontFamily = typography.bodyMedium.fontFamily,
                fontSize = 12.sp,
                color = colors.textSecondary
            )
        }

        if (appStats.isEmpty()) {
            TerminalCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.stats_no_activity_today),
                    fontFamily = typography.bodyMedium.fontFamily,
                    fontSize = 13.sp,
                    color = colors.textSecondary
                )
            }
        } else if (scrollRows) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(appStats, key = { it.packageName }) { stat ->
                    AppStatsRowContent(stat)
                }
            }
        } else {
            Column {
                appStats.forEach { stat -> AppStatsRowContent(stat) }
            }
        }
    }
}

@Composable
private fun AppStatsRowContent(stat: AppStatsRow) {
    val colors = WattimTheme.colors
    val typography = WattimTheme.typography

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stat.displayName.ifBlank { stat.packageName },
            fontFamily = typography.bodyMedium.fontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = stat.totalOpenings.toString(),
            fontFamily = typography.bodyMedium.fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = colors.textPrimary,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Text(
            text = stat.totalClosed.toString(),
            fontFamily = typography.bodyMedium.fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = colors.accent
        )
    }
}
