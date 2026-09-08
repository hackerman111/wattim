package io.ronesec.android.ui.screens.target

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

@Composable
fun TargetQuickReturnCard(
    quickReturnGraceSec: Long,
    onQuickReturnGraceChange: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    val strings = LocalAppStrings.current

    TerminalCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = strings.targetQuickReturn,
            fontFamily = TerminalFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            color = palette.textSecondary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val graces = listOf(
                0L to "0${strings.secondsUnitShort}",
                15L to "15${strings.secondsUnitShort}",
                30L to "30${strings.secondsUnitShort}",
                60L to "1${strings.minutesShort}",
                120L to "2${strings.minutesShort}",
                300L to "5${strings.minutesShort}"
            )
            graces.forEach { (sec, lbl) ->
                val isSelected = quickReturnGraceSec == sec
                TerminalBadge(
                    text = lbl,
                    isActive = isSelected,
                    modifier = Modifier.clickable { onQuickReturnGraceChange(sec) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (quickReturnGraceSec == 0L)
                strings.targetQuickReturn0s
            else
                strings.targetQuickReturnGrace(quickReturnGraceSec),
            fontFamily = TerminalFontFamily,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            color = palette.textSecondary
        )
    }
}

@Composable
fun TargetQuickLockCard(
    targetDisplayName: String,
    targetPackageName: String,
    onStartHardBlock: (String, Int, Set<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    val strings = LocalAppStrings.current

    TerminalCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = strings.targetQuickLock,
            fontFamily = TerminalFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            color = palette.textSecondary,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Text(
            text = strings.targetQuickLockDesc(targetDisplayName),
            fontFamily = TerminalFontFamily,
            fontSize = 11.sp,
            color = palette.textSecondary,
            modifier = Modifier.padding(bottom = 10.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val durations = listOf(
                "15${strings.minutesShort}" to 15,
                "30${strings.minutesShort}" to 30,
                "1${strings.hoursShort}" to 60,
                "2${strings.hoursShort}" to 120
            )
            durations.forEach { (lbl, mins) ->
                TerminalButton(
                    text = lbl,
                    onClick = {
                        onStartHardBlock(
                            strings.targetQuickLockFocusPrefix(targetDisplayName),
                            mins,
                            setOf(targetPackageName)
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
