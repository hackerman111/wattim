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
import io.ronesec.android.ui.theme.LocalTerminalAccent
import io.ronesec.android.ui.theme.TerminalBackground
import io.ronesec.android.ui.theme.TerminalFontFamily
import io.ronesec.android.ui.theme.TerminalTextPrimary
import io.ronesec.android.ui.theme.TerminalTextSecondary
import io.ronesec.android.ui.viewmodel.AppStatRow
import io.ronesec.android.ui.viewmodel.TodayStats

@Composable
fun StatsScreen(
    todayStats: TodayStats,
    appStats: List<AppStatRow>,
    modifier: Modifier = Modifier
) {
    val accent = LocalTerminalAccent.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TerminalBackground)
            .padding(16.dp)
    ) {
        Text(
            text = "STATISTICS",
            fontFamily = TerminalFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            letterSpacing = 0.15.sp,
            color = accent
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Summary Card
        TerminalCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "TODAY SUMMARY",
                fontFamily = TerminalFontFamily,
                fontSize = 12.sp,
                color = TerminalTextSecondary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("OPEN ATTEMPTS", fontFamily = TerminalFontFamily, fontSize = 13.sp, color = TerminalTextSecondary)
                Text("${todayStats.openAttempts}", fontFamily = TerminalFontFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TerminalTextPrimary)
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("CONTINUED", fontFamily = TerminalFontFamily, fontSize = 13.sp, color = TerminalTextSecondary)
                Text("${todayStats.continued}", fontFamily = TerminalFontFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TerminalTextPrimary)
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("CLOSED", fontFamily = TerminalFontFamily, fontSize = 13.sp, color = TerminalTextSecondary)
                Text("${todayStats.closed}", fontFamily = TerminalFontFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = accent)
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("AVOIDED", fontFamily = TerminalFontFamily, fontSize = 13.sp, color = TerminalTextSecondary)
                Text("${todayStats.avoidedPercent}%", fontFamily = TerminalFontFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = accent)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "BY APPLICATION",
            fontFamily = TerminalFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            letterSpacing = 0.15.sp,
            color = TerminalTextSecondary
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Header Row
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("APP", fontFamily = TerminalFontFamily, fontSize = 12.sp, color = TerminalTextSecondary, modifier = Modifier.weight(1f))
            Text("OPEN", fontFamily = TerminalFontFamily, fontSize = 12.sp, color = TerminalTextSecondary, modifier = Modifier.padding(horizontal = 16.dp))
            Text("CLOSED", fontFamily = TerminalFontFamily, fontSize = 12.sp, color = TerminalTextSecondary)
        }

        if (appStats.isEmpty()) {
            TerminalCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "NO ACTIVITY RECORDED TODAY",
                    fontFamily = TerminalFontFamily,
                    fontSize = 13.sp,
                    color = TerminalTextSecondary
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
                            color = TerminalTextPrimary,
                            modifier = Modifier.weight(1f)
                        )

                        Text(
                            text = "${row.openCount}",
                            fontFamily = TerminalFontFamily,
                            fontSize = 14.sp,
                            color = TerminalTextPrimary,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )

                        Text(
                            text = "${row.closedCount}",
                            fontFamily = TerminalFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = accent
                        )
                    }
                }
            }
        }
    }
}
