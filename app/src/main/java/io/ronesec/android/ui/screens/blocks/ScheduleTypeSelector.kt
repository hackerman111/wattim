package io.ronesec.android.ui.screens.blocks

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ronesec.android.domain.model.ScheduleType
import io.ronesec.android.ui.components.TerminalBadge
import io.ronesec.android.ui.i18n.LocalAppStrings
import io.ronesec.android.ui.theme.LocalAppPalette
import io.ronesec.android.ui.theme.TerminalFontFamily

@Composable
fun ScheduleTypeSelector(
    scheduleType: ScheduleType,
    onScheduleTypeChange: (ScheduleType) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    val accent = palette.accent
    val strings = LocalAppStrings.current

    Column(modifier = modifier) {
        Text(
            text = strings.scheduleTypeLabel,
            fontFamily = TerminalFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            color = palette.textSecondary
        )

        Spacer(modifier = Modifier.height(6.dp))

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val isHardBlock = scheduleType == ScheduleType.HARD_BLOCK
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isHardBlock) palette.surfaceElevated else palette.surface)
                    .border(1.dp, if (isHardBlock) accent else palette.border, RoundedCornerShape(6.dp))
                    .clickable { onScheduleTypeChange(ScheduleType.HARD_BLOCK) }
                    .padding(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.hardBlockTypeTitle,
                        fontFamily = TerminalFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = if (isHardBlock) accent else palette.textPrimary
                    )
                    TerminalBadge(
                        text = if (isHardBlock) strings.selectedBadge else "—",
                        isActive = isHardBlock
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = strings.hardBlockTypeDesc,
                    fontFamily = TerminalFontFamily,
                    fontSize = 10.sp,
                    color = palette.textSecondary
                )
            }

            val isIntervention = scheduleType == ScheduleType.INTERVENTION
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isIntervention) palette.surfaceElevated else palette.surface)
                    .border(1.dp, if (isIntervention) accent else palette.border, RoundedCornerShape(6.dp))
                    .clickable { onScheduleTypeChange(ScheduleType.INTERVENTION) }
                    .padding(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.customInterventionsTitle,
                        fontFamily = TerminalFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = if (isIntervention) accent else palette.textPrimary
                    )
                    TerminalBadge(
                        text = if (isIntervention) strings.selectedBadge else "—",
                        isActive = isIntervention
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = strings.customInterventionsDesc,
                    fontFamily = TerminalFontFamily,
                    fontSize = 10.sp,
                    color = palette.textSecondary
                )
            }
        }
    }
}

@Composable
fun SchedulePresetTimeChips(
    startHour: Int,
    startMinute: Int,
    endHour: Int,
    endMinute: Int,
    onSelectPreset: (startH: Int, startM: Int, endH: Int, endM: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        listOf(
            strings.presetWork to (9 to 18),
            strings.presetNight to (23 to 7),
            strings.presetMorning to (7 to 12),
            strings.presetEvening to (18 to 23)
        ).forEach { (lbl, range) ->
            val isActive = startHour == range.first && startMinute == 0 && endHour == range.second && endMinute == 0
            TerminalBadge(
                text = lbl,
                isActive = isActive,
                modifier = Modifier.clickable {
                    onSelectPreset(range.first, 0, range.second, 0)
                }
            )
        }
    }
}
