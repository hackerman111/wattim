package io.ronesec.android.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ronesec.android.ui.components.TerminalCard
import io.ronesec.android.ui.i18n.LocalAppStrings
import io.ronesec.android.ui.theme.LocalAppPalette
import io.ronesec.android.ui.theme.TerminalFontFamily
import io.ronesec.android.ui.viewmodel.TodayStats

@Composable
fun TodayStatsHeroCard(
    todayStats: TodayStats,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    val accent = palette.accent
    val strings = LocalAppStrings.current

    TerminalCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = strings.todayHeader,
            fontFamily = TerminalFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            letterSpacing = 0.15.sp,
            color = palette.textSecondary,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "${todayStats.openAttempts}",
                    fontFamily = TerminalFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp,
                    color = palette.textPrimary
                )
                Text(
                    text = strings.attemptsUnit.uppercase(),
                    fontFamily = TerminalFontFamily,
                    fontSize = 11.sp,
                    color = palette.textSecondary
                )
            }

            Column {
                Text(
                    text = "${todayStats.closed}",
                    fontFamily = TerminalFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp,
                    color = accent
                )
                Text(
                    text = strings.victoriesUnit.uppercase(),
                    fontFamily = TerminalFontFamily,
                    fontSize = 11.sp,
                    color = palette.textSecondary
                )
            }

            Column {
                Text(
                    text = "${todayStats.avoidedPercent}%",
                    fontFamily = TerminalFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp,
                    color = palette.textPrimary
                )
                Text(
                    text = strings.savedUnit.uppercase(),
                    fontFamily = TerminalFontFamily,
                    fontSize = 11.sp,
                    color = palette.textSecondary
                )
            }
        }
    }
}
