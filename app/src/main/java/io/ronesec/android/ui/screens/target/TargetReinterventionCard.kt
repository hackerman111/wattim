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
import io.ronesec.android.domain.util.TimeFormatUtils
import io.ronesec.android.ui.components.TerminalBadge
import io.ronesec.android.ui.components.TerminalCard
import io.ronesec.android.ui.i18n.LocalAppStrings
import io.ronesec.android.ui.theme.LocalAppPalette
import io.ronesec.android.ui.theme.TerminalFontFamily

@Composable
fun TargetReinterventionCard(
    remindAgainMs: Long?,
    isCustomReintervention: Boolean,
    customMinutesInput: String,
    customSecondsInput: String,
    onRemindAgainChange: (Long?) -> Unit,
    onCustomModeChange: (Boolean) -> Unit,
    onCustomInputsChange: (minutes: String, seconds: String, totalMs: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    val strings = LocalAppStrings.current

    TerminalCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = strings.targetReintercept,
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
            val intervals = listOf(
                strings.offLabel to null,
                "1${strings.minutesShort}" to 60_000L,
                "3${strings.minutesShort}" to 180_000L,
                "5${strings.minutesShort}" to 300_000L,
                "10${strings.minutesShort}" to 600_000L
            )
            intervals.forEach { (lbl, ms) ->
                val isSelected = !isCustomReintervention && remindAgainMs == ms
                TerminalBadge(
                    text = lbl,
                    isActive = isSelected,
                    modifier = Modifier.clickable {
                        onCustomModeChange(false)
                        onRemindAgainChange(ms)
                    }
                )
            }

            TerminalBadge(
                text = strings.targetCustomOption,
                isActive = isCustomReintervention,
                modifier = Modifier.clickable {
                    onCustomModeChange(true)
                    val total = TimeFormatUtils.parseDuration(customMinutesInput, customSecondsInput, minMs = 5_000L)
                    onRemindAgainChange(total)
                }
            )
        }

        if (isCustomReintervention) {
            Spacer(modifier = Modifier.height(12.dp))

            TargetCustomReinterventionSection(
                customMinutesInput = customMinutesInput,
                customSecondsInput = customSecondsInput,
                onCustomInputsChange = onCustomInputsChange
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        val descText = if (remindAgainMs == null && !isCustomReintervention) {
            strings.targetReinterceptOffDesc
        } else {
            val effectiveMs = if (isCustomReintervention) {
                val m = customMinutesInput.filter { it.isDigit() }.toLongOrNull() ?: 0L
                val s = customSecondsInput.filter { it.isDigit() }.toLongOrNull() ?: 0L
                m * 60_000L + s * 1000L
            } else {
                remindAgainMs ?: 0L
            }
            val timeStr = strings.formatDuration(effectiveMs)
            strings.targetReinterceptOnDesc(timeStr)
        }

        Text(
            text = descText,
            fontFamily = TerminalFontFamily,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            color = palette.textSecondary
        )
    }
}
