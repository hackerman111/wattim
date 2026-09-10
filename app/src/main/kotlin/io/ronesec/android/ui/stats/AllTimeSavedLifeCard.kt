package io.ronesec.android.ui.stats

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ronesec.android.R
import io.ronesec.android.ui.designsystem.TerminalCard
import io.ronesec.android.ui.designsystem.WattimTheme

@Composable
fun AllTimeSavedLifeCard(
    allTimeDuration: String,
    avoidedCount: Int,
    savedTodayDuration: String,
    multiplierMinutes: Int,
    modifier: Modifier = Modifier
) {
    val colors = WattimTheme.colors
    val dimensions = WattimTheme.dimensions
    val typography = WattimTheme.typography

    TerminalCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.stats_saved_life_title).uppercase(),
                fontFamily = typography.bodyMedium.fontFamily,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                fontSize = 12.sp,
                letterSpacing = 0.1.sp,
                color = colors.textSecondary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Hero duration
            Text(
                text = allTimeDuration,
                fontFamily = typography.bodyMedium.fontFamily,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                fontSize = 24.sp,
                letterSpacing = 0.05.sp,
                color = colors.accent
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.stats_avoided_impulses).uppercase(),
                    fontFamily = typography.bodyMedium.fontFamily,
                    fontSize = 11.sp,
                    color = colors.textSecondary
                )
                Text(
                    text = avoidedCount.toString(),
                    fontFamily = typography.bodyMedium.fontFamily,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    fontSize = 13.sp,
                    color = colors.textPrimary
                )
            }

            // Saved today row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.stats_saved_today).uppercase(),
                    fontFamily = typography.bodyMedium.fontFamily,
                    fontSize = 13.sp,
                    color = colors.textSecondary
                )
                Text(
                    text = savedTodayDuration,
                    fontFamily = typography.bodyMedium.fontFamily,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    fontSize = 14.sp,
                    color = colors.accent
                )
            }

            // Multiplier note
            Text(
                text = stringResource(R.string.stats_multiplier_note, multiplierMinutes),
                fontFamily = typography.bodyMedium.fontFamily,
                fontSize = 11.sp,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
