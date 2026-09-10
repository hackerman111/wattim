package io.ronesec.android.ui.target

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import java.util.Locale
import kotlin.math.pow

@Composable
fun BackoffCalculatorCard(
    delays: List<Long>,
    baseDurationSeconds: Int,
    growthPercent: Int,
    windowMs: Long,
    showContainer: Boolean = true,
    modifier: Modifier = Modifier
) {
    val colors = WattimTheme.colors
    val dimensions = WattimTheme.dimensions
    val typography = WattimTheme.typography

    val windowText = when (windowMs) {
        15 * 60 * 1000L -> "15m"
        30 * 60 * 1000L -> "30m"
        60 * 60 * 1000L -> "1h"
        2 * 60 * 60 * 1000L -> "2h"
        24 * 60 * 60 * 1000L -> "24h"
        else -> "${windowMs / 60000}m"
    }

    val isRu = stringResource(R.string.unit_sec).equals("СЕК", ignoreCase = true)
    val unitStr = if (isRu) "сек" else "s"

    val baseNote = stringResource(
        R.string.backoff_base_note,
        baseDurationSeconds,
        growthPercent,
        windowText
    )

    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(dimensions.space8)
        ) {
            Text(
                text = stringResource(R.string.backoff_calculator_title).uppercase(),
                style = typography.labelSmall,
                color = colors.textSecondary
            )

            // Two columns: 1..5 and 6..10 inside elevated rounded container matching app_1
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surfaceElevated, RoundedCornerShape(6.dp))
                    .border(1.dp, colors.border, RoundedCornerShape(6.dp))
                    .padding(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Column 1: attempts 1 to 5
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (i in 0 until 5) {
                            val delaySec = if (i < delays.size) {
                                delays[i] / 1000.0
                            } else {
                                baseDurationSeconds * (1.0 + growthPercent / 100.0).pow((i + 1).toDouble())
                            }
                            AttemptDelayRow(attemptNum = i + 1, delaySeconds = delaySec, unitText = unitStr)
                        }
                    }

                    // Column 2: attempts 6 to 10
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (i in 5 until 10) {
                            val delaySec = if (i < delays.size) {
                                delays[i] / 1000.0
                            } else {
                                baseDurationSeconds * (1.0 + growthPercent / 100.0).pow((i + 1).toDouble())
                            }
                            AttemptDelayRow(attemptNum = i + 1, delaySeconds = delaySec, unitText = unitStr)
                        }
                    }
                }
            }

            // Base-value note
            Text(
                text = baseNote,
                fontFamily = typography.bodyMedium.fontFamily,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
    if (showContainer) {
        TerminalCard(modifier = modifier.fillMaxWidth()) { content() }
    } else {
        content()
    }
}

@Composable
private fun AttemptDelayRow(
    attemptNum: Int,
    delaySeconds: Double,
    unitText: String,
    modifier: Modifier = Modifier
) {
    val colors = WattimTheme.colors
    val typography = WattimTheme.typography

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "#%02d".format(attemptNum),
            fontFamily = typography.bodyMedium.fontFamily,
            fontSize = 11.sp,
            color = colors.textSecondary
        )
        Text(
            text = String.format(Locale.US, "%.1f %s", delaySeconds, unitText),
            fontFamily = typography.bodyMedium.fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            color = colors.accent
        )
    }
}
