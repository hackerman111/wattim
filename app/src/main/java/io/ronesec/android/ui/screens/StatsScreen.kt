package io.ronesec.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ronesec.android.ui.components.TerminalCard
import io.ronesec.android.ui.i18n.LocalAppStrings
import io.ronesec.android.ui.theme.LocalAppPalette
import io.ronesec.android.ui.theme.TerminalFontFamily
import io.ronesec.android.ui.viewmodel.AppStatRow
import io.ronesec.android.ui.viewmodel.TodayStats

@Composable
fun StatsScreen(
    todayStats: TodayStats,
    appStats: List<AppStatRow>,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    val accent = palette.accent
    val strings = LocalAppStrings.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
            .padding(16.dp)
    ) {
        Text(
            text = strings.statsTitle,
            fontFamily = TerminalFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            letterSpacing = 0.15.sp,
            color = accent
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Life Time Saved Hero Card
        TerminalCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = strings.lifeTimeSavedCardTitle,
                fontFamily = TerminalFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                letterSpacing = 0.1.sp,
                color = palette.textSecondary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = strings.formatSavedTime(todayStats.allTimeSavedMinutes),
                fontFamily = TerminalFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                letterSpacing = 0.05.sp,
                color = accent
            )

            Text(
                text = strings.totalImpulsiveAvoided(todayStats.allTimeAvoided),
                fontFamily = TerminalFontFamily,
                fontSize = 11.sp,
                color = palette.textSecondary,
                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = strings.todaySaved,
                    fontFamily = TerminalFontFamily,
                    fontSize = 13.sp,
                    color = palette.textSecondary
                )
                Text(
                    text = strings.formatSavedTime(todayStats.savedMinutes),
                    fontFamily = TerminalFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = accent
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Summary Card
        TerminalCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = strings.todaySummaryTitle,
                fontFamily = TerminalFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                letterSpacing = 0.1.sp,
                color = palette.textSecondary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(strings.metricOpenAttempts, fontFamily = TerminalFontFamily, fontSize = 13.sp, color = palette.textSecondary)
                Text("${todayStats.openAttempts}", fontFamily = TerminalFontFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = palette.textPrimary)
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(strings.metricContinued, fontFamily = TerminalFontFamily, fontSize = 13.sp, color = palette.textSecondary)
                Text("${todayStats.continued}", fontFamily = TerminalFontFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = palette.textPrimary)
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(strings.metricClosed, fontFamily = TerminalFontFamily, fontSize = 13.sp, color = palette.textSecondary)
                Text("${todayStats.closed}", fontFamily = TerminalFontFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = accent)
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(strings.metricMindfulness, fontFamily = TerminalFontFamily, fontSize = 13.sp, color = palette.textSecondary)
                Text("${todayStats.avoidedPercent}%", fontFamily = TerminalFontFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = accent)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = strings.perAppTitle,
            fontFamily = TerminalFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            letterSpacing = 0.15.sp,
            color = palette.textSecondary
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Header Row
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(strings.tableHeaderApp, fontFamily = TerminalFontFamily, fontSize = 12.sp, color = palette.textSecondary, modifier = Modifier.weight(1f))
            Text(strings.tableHeaderOpen, fontFamily = TerminalFontFamily, fontSize = 12.sp, color = palette.textSecondary, modifier = Modifier.padding(horizontal = 16.dp))
            Text(strings.tableHeaderClosed, fontFamily = TerminalFontFamily, fontSize = 12.sp, color = palette.textSecondary)
        }

        if (appStats.isEmpty()) {
            TerminalCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = strings.noActivityToday,
                    fontFamily = TerminalFontFamily,
                    fontSize = 13.sp,
                    color = palette.textSecondary
                )
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                items(appStats, key = { it.packageName }) { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = row.displayName,
                            fontFamily = TerminalFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = palette.textPrimary,
                            modifier = Modifier.weight(1f)
                        )

                        Text(
                            text = "${row.openCount}",
                            fontFamily = TerminalFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = palette.textPrimary,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )

                        Text(
                            text = "${row.closedCount}",
                            fontFamily = TerminalFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = accent
                        )
                    }
                }
            }
        }
    }
}
