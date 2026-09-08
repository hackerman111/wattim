package io.ronesec.android.ui.screens.blocks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ronesec.android.ui.components.TerminalBadge
import io.ronesec.android.ui.i18n.LocalAppStrings
import io.ronesec.android.ui.theme.LocalAppPalette
import io.ronesec.android.ui.theme.TerminalFontFamily
import java.time.DayOfWeek

@Composable
fun ScheduleDaysSection(
    selectedDays: Set<DayOfWeek>,
    onDaysChange: (Set<DayOfWeek>) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    val strings = LocalAppStrings.current

    val weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
    val weekends = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    val allDays = DayOfWeek.values().toSet()

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = strings.daysOfWeekTitle,
            fontFamily = TerminalFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            color = palette.textSecondary
        )

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TerminalBadge(
                text = strings.weekdaysBadge,
                isActive = selectedDays == weekdays,
                modifier = Modifier.clickable { onDaysChange(weekdays) }
            )
            TerminalBadge(
                text = strings.weekendsBadge,
                isActive = selectedDays == weekends,
                modifier = Modifier.clickable { onDaysChange(weekends) }
            )
            TerminalBadge(
                text = strings.allDaysBadge,
                isActive = selectedDays == allDays,
                modifier = Modifier.clickable { onDaysChange(allDays) }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            DayOfWeek.values().forEach { day ->
                val isSelected = selectedDays.contains(day)
                TerminalBadge(
                    text = strings.dayChipName(day),
                    isActive = isSelected,
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            val newSet = if (isSelected) {
                                if (selectedDays.size > 1) selectedDays - day else selectedDays
                            } else {
                                selectedDays + day
                            }
                            onDaysChange(newSet)
                        }
                )
            }
        }
    }
}
