package io.ronesec.android.ui.screens.target

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ronesec.android.ui.components.TerminalBadge
import io.ronesec.android.ui.components.TerminalButton
import io.ronesec.android.ui.components.TerminalCard
import io.ronesec.android.ui.i18n.LocalAppStrings
import io.ronesec.android.ui.theme.LocalAppPalette
import io.ronesec.android.ui.theme.TerminalFontFamily
import java.util.Locale
import kotlin.math.pow

@Composable
fun TargetExponentialGrowthCard(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    growthPercent: Int,
    onGrowthPercentChange: (Int) -> Unit,
    growthPeriodMinutes: Int,
    onGrowthPeriodMinutesChange: (Int) -> Unit,
    durationSeconds: Float,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    val strings = LocalAppStrings.current
    val accent = palette.accent

    TerminalCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = strings.targetExponentialGrowth,
                fontFamily = TerminalFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                color = palette.textSecondary
            )

            TerminalBadge(
                text = if (enabled) strings.onLabel else strings.offLabel,
                isActive = enabled,
                modifier = Modifier.clickable { onEnabledChange(!enabled) }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = strings.targetExponentialGrowthDesc,
            fontFamily = TerminalFontFamily,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            color = palette.textSecondary
        )

        if (enabled) {
            Spacer(modifier = Modifier.height(14.dp))

            // Growth percent section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = strings.targetGrowthPercent,
                    fontFamily = TerminalFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    color = palette.textSecondary
                )
                Text(
                    text = "+$growthPercent%",
                    fontFamily = TerminalFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = accent
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Steppers row: [-5%], [-1%], [+1%], [+5%]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(-5 to "-5%", -1 to "-1%", 1 to "+1%", 5 to "+5%").forEach { (delta, label) ->
                    TerminalButton(
                        text = label,
                        onClick = {
                            onGrowthPercentChange((growthPercent + delta).coerceIn(1, 200))
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Preset badges row: 10%, 20%, 30%, 50%
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(10, 20, 30, 50).forEach { pct ->
                    val isSelected = growthPercent == pct
                    TerminalBadge(
                        text = "$pct%",
                        isActive = isSelected,
                        modifier = Modifier.clickable { onGrowthPercentChange(pct) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Rolling window section
            Text(
                text = strings.targetRollingWindow,
                fontFamily = TerminalFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                color = palette.textSecondary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val periods = listOf(
                    "15${strings.minutesShort}" to 15,
                    "30${strings.minutesShort}" to 30,
                    "1${strings.hoursShort}" to 60,
                    "2${strings.hoursShort}" to 120,
                    "24${strings.hoursShort}" to 1440
                )
                periods.forEach { (lbl, mins) ->
                    val isSelected = growthPeriodMinutes == mins
                    TerminalBadge(
                        text = lbl,
                        isActive = isSelected,
                        modifier = Modifier.clickable { onGrowthPeriodMinutesChange(mins) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Interactive projection (first 10 opens)
            Text(
                text = strings.targetProjectionTitle(growthPercent),
                fontFamily = TerminalFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                color = palette.textSecondary
            )

            Spacer(modifier = Modifier.height(8.dp))

            val baseSec = durationSeconds

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.surfaceElevated, RoundedCornerShape(6.dp))
                    .border(1.dp, palette.border, RoundedCornerShape(6.dp))
                    .padding(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Left column (1..5)
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (k in 1..5) {
                            val timeSec = baseSec * (1.0 + growthPercent / 100.0).pow(k.toDouble())
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = strings.targetOpenNumber(k),
                                    fontFamily = TerminalFontFamily,
                                    fontSize = 11.sp,
                                    color = palette.textSecondary
                                )
                                Text(
                                    text = String.format(Locale.US, "%.1f %s", timeSec, strings.secondsShort),
                                    fontFamily = TerminalFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = accent
                                )
                            }
                        }
                    }

                    // Right column (6..10)
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (k in 6..10) {
                            val timeSec = baseSec * (1.0 + growthPercent / 100.0).pow(k.toDouble())
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = strings.targetOpenNumber(k),
                                    fontFamily = TerminalFontFamily,
                                    fontSize = 11.sp,
                                    color = palette.textSecondary
                                )
                                Text(
                                    text = String.format(Locale.US, "%.1f %s", timeSec, strings.secondsShort),
                                    fontFamily = TerminalFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = accent
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = strings.targetProjectionBaseHint(baseSec.toInt()),
                fontFamily = TerminalFontFamily,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                color = palette.textSecondary
            )
        }
    }
}
