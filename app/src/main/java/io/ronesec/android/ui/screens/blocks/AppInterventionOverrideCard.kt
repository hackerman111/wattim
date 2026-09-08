package io.ronesec.android.ui.screens.blocks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ronesec.android.domain.model.ScheduleAppOverride
import io.ronesec.android.domain.model.TargetApp
import io.ronesec.android.ui.components.TerminalBadge
import io.ronesec.android.ui.components.TerminalButton
import io.ronesec.android.ui.i18n.LocalAppStrings
import io.ronesec.android.ui.theme.LocalAppPalette
import io.ronesec.android.ui.theme.TerminalFontFamily

@Composable
fun AppInterventionOverrideCard(
    target: TargetApp,
    override: ScheduleAppOverride?,
    onOverrideChange: (ScheduleAppOverride) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    val accent = palette.accent
    val focusManager = LocalFocusManager.current

    val effectiveDurationMs = override?.durationMs ?: target.intervention.durationMs
    val effectiveDurationSec = (effectiveDurationMs / 1000L).coerceAtLeast(1L)
    val effectiveReinterventionMs = if (override != null) override.reinterventionMs else target.intervention.reinterventionMs

    var isExpanded by remember { mutableStateOf(true) }
    var durationText by remember(effectiveDurationSec) { mutableStateOf(effectiveDurationSec.toString()) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(palette.surfaceElevated)
            .border(1.dp, palette.border, RoundedCornerShape(6.dp))
            .padding(10.dp)
    ) {
        val strings = LocalAppStrings.current
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = strings.customRulesTitle(target.displayName),
                fontFamily = TerminalFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = accent
            )
            Text(
                text = if (isExpanded) "▲" else "▼",
                fontFamily = TerminalFontFamily,
                fontSize = 10.sp,
                color = palette.textSecondary
            )
        }

        if (isExpanded) {
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = strings.pauseDurationTitle,
                fontFamily = TerminalFontFamily,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.textSecondary
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val sUnit = strings.secondsShort.take(1)

                TerminalButton(
                    text = "-5$sUnit",
                    onClick = {
                        val newSec = (effectiveDurationSec - 5L).coerceAtLeast(1L)
                        durationText = newSec.toString()
                        onOverrideChange(
                            ScheduleAppOverride(
                                durationMs = newSec * 1000L,
                                reinterventionMs = effectiveReinterventionMs
                            )
                        )
                    },
                    modifier = Modifier.weight(1f)
                )

                TerminalButton(
                    text = "-1$sUnit",
                    onClick = {
                        val newSec = (effectiveDurationSec - 1L).coerceAtLeast(1L)
                        durationText = newSec.toString()
                        onOverrideChange(
                            ScheduleAppOverride(
                                durationMs = newSec * 1000L,
                                reinterventionMs = effectiveReinterventionMs
                            )
                        )
                    },
                    modifier = Modifier.weight(1f)
                )

                Box(
                    modifier = Modifier
                        .weight(1.4f)
                        .border(1.dp, palette.border, RoundedCornerShape(4.dp))
                        .background(palette.surface, RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        BasicTextField(
                            value = durationText,
                            onValueChange = { input ->
                                val filtered = input.filter { it.isDigit() }.take(3)
                                durationText = filtered
                                val parsed = filtered.toLongOrNull()
                                if (parsed != null && parsed > 0) {
                                    val sec = parsed.coerceIn(1L, 300L)
                                    onOverrideChange(
                                        ScheduleAppOverride(
                                            durationMs = sec * 1000L,
                                            reinterventionMs = effectiveReinterventionMs
                                        )
                                    )
                                }
                            },
                            textStyle = TextStyle(
                                fontFamily = TerminalFontFamily,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.textPrimary,
                                textAlign = TextAlign.Center
                            ),
                            cursorBrush = SolidColor(accent),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (durationText.isBlank() || durationText == "0") {
                                        durationText = effectiveDurationSec.toString()
                                    }
                                    focusManager.clearFocus()
                                }
                            ),
                            singleLine = true,
                            modifier = Modifier.width(32.dp)
                        )
                        Text(
                            text = sUnit,
                            fontFamily = TerminalFontFamily,
                            fontSize = 11.sp,
                            color = palette.textSecondary
                        )
                    }
                }

                TerminalButton(
                    text = "+1$sUnit",
                    onClick = {
                        val newSec = (effectiveDurationSec + 1L).coerceAtMost(300L)
                        durationText = newSec.toString()
                        onOverrideChange(
                            ScheduleAppOverride(
                                durationMs = newSec * 1000L,
                                reinterventionMs = effectiveReinterventionMs
                            )
                        )
                    },
                    modifier = Modifier.weight(1f)
                )

                TerminalButton(
                    text = "+5$sUnit",
                    onClick = {
                        val newSec = (effectiveDurationSec + 5L).coerceAtMost(300L)
                        durationText = newSec.toString()
                        onOverrideChange(
                            ScheduleAppOverride(
                                durationMs = newSec * 1000L,
                                reinterventionMs = effectiveReinterventionMs
                            )
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = strings.repeatInterventionTitle,
                fontFamily = TerminalFontFamily,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.textSecondary
            )

            Spacer(modifier = Modifier.height(4.dp))

            val mUnit = strings.minutesUnit.take(1)
            val intervalChips = listOf(
                strings.optionOff to null,
                "1$mUnit" to 60_000L,
                "3$mUnit" to 180_000L,
                "5$mUnit" to 300_000L,
                "10$mUnit" to 600_000L
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                intervalChips.forEach { (label, ms) ->
                    val isSelected = effectiveReinterventionMs == ms
                    TerminalBadge(
                        text = label,
                        isActive = isSelected,
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                onOverrideChange(
                                    ScheduleAppOverride(
                                        durationMs = effectiveDurationSec * 1000L,
                                        reinterventionMs = ms
                                    )
                                )
                            }
                    )
                }
            }
        }
    }
}
