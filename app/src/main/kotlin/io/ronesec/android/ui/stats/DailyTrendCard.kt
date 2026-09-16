package io.ronesec.android.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ronesec.android.R
import io.ronesec.android.data.DailyActivityPoint
import io.ronesec.android.ui.designsystem.TerminalCard
import io.ronesec.android.ui.designsystem.WattimTheme
import kotlin.math.max

@Composable
fun DailyTrendCard(
    dailyActivity: List<DailyActivityPoint>,
    modifier: Modifier = Modifier
) {
    if (dailyActivity.isEmpty()) return

    val colors = WattimTheme.colors
    val typography = WattimTheme.typography

    TerminalCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.stats_daily_trend_title),
                    style = typography.labelSmall,
                    color = colors.textSecondary
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Canvas(modifier = Modifier.height(8.dp).fillMaxWidth(0.04f)) {
                            drawCircle(color = colors.border)
                        }
                        Text(
                            text = stringResource(R.string.stats_trend_openings),
                            style = typography.labelSmall,
                            fontSize = 11.sp,
                            color = colors.textSecondary
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Canvas(modifier = Modifier.height(8.dp).fillMaxWidth(0.04f)) {
                            drawCircle(color = colors.accent)
                        }
                        Text(
                            text = stringResource(R.string.stats_trend_prevented),
                            style = typography.labelSmall,
                            fontSize = 11.sp,
                            color = colors.accent
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val maxVal = max(5, dailyActivity.maxOfOrNull { it.totalOpenings } ?: 5)

            // Bar chart row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                for (point in dailyActivity) {
                    val openingFraction = (point.totalOpenings.toFloat() / maxVal.toFloat()).coerceIn(0f, 1f)
                    val closedFraction = (point.totalClosed.toFloat() / maxVal.toFloat()).coerceIn(0f, 1f)

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .height(80.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            Canvas(modifier = Modifier.matchParentSize()) {
                                val barWidth = size.width.coerceAtLeast(3f)
                                val x = (size.width - barWidth) / 2f

                                val openHeight = size.height * openingFraction
                                val closedHeight = size.height * closedFraction

                                if (openHeight > 0f) {
                                    // Total openings (background bar)
                                    drawRoundRect(
                                        color = colors.border.copy(alpha = 0.5f),
                                        topLeft = Offset(x, size.height - openHeight),
                                        size = Size(barWidth, openHeight),
                                        cornerRadius = CornerRadius(2f, 2f)
                                    )
                                }

                                if (closedHeight > 0f) {
                                    // Prevented count (accent bar)
                                    drawRoundRect(
                                        color = colors.accent,
                                        topLeft = Offset(x, size.height - closedHeight),
                                        size = Size(barWidth, closedHeight),
                                        cornerRadius = CornerRadius(2f, 2f)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = point.label,
                            style = typography.labelSmall,
                            fontSize = if (dailyActivity.size > 14) 8.sp else 10.sp,
                            color = colors.textSecondary,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
