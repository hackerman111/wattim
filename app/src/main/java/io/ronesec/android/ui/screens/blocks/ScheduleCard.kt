package io.ronesec.android.ui.screens.blocks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ronesec.android.domain.model.BlockSchedule
import io.ronesec.android.domain.model.ScheduleType
import io.ronesec.android.domain.model.TargetApp
import io.ronesec.android.ui.components.TerminalBadge
import io.ronesec.android.ui.components.TerminalCard
import io.ronesec.android.ui.i18n.AppStrings
import io.ronesec.android.ui.i18n.LocalAppStrings
import io.ronesec.android.ui.theme.LocalAppPalette
import io.ronesec.android.ui.theme.TerminalFontFamily
import java.time.DayOfWeek

private fun formatDays(days: Set<DayOfWeek>, strings: AppStrings): String {
    return days.sorted().joinToString(", ") { strings.dayShortName(it) }
}

@Composable
fun ScheduleCard(
    schedule: BlockSchedule,
    targets: List<TargetApp>,
    onEdit: () -> Unit,
    onToggleEnable: (BlockSchedule) -> Unit,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    val strings = LocalAppStrings.current
    val targetMap = targets.associateBy { it.packageName }

    TerminalCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clickable { onEdit() }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = schedule.name.uppercase(),
                    fontFamily = TerminalFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = palette.textPrimary,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(end = 6.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TerminalBadge(
                        text = strings.editButton,
                        isActive = false,
                        modifier = Modifier.clickable { onEdit() }
                    )

                    TerminalBadge(
                        text = if (schedule.enabled) strings.activeBadge else strings.offLabel,
                        isActive = schedule.enabled,
                        modifier = Modifier.clickable {
                            onToggleEnable(schedule.copy(enabled = !schedule.enabled))
                        }
                    )

                    TerminalBadge(
                        text = strings.deleteButton,
                        isActive = false,
                        modifier = Modifier.clickable {
                            onDelete(schedule.id)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            val typeLabel = if (schedule.scheduleType == ScheduleType.HARD_BLOCK) {
                strings.fullBlockBadge
            } else {
                strings.scheduledInterventionsBadge
            }
            Text(
                text = typeLabel,
                fontFamily = TerminalFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                color = palette.textSecondary
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "⏰ ${formatDays(schedule.days, strings)} · ${schedule.start} → ${schedule.end}",
                fontFamily = TerminalFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = palette.accent
            )

            Spacer(modifier = Modifier.height(4.dp))

            val appNames = if (schedule.packages.size == targets.size && targets.isNotEmpty()) {
                strings.allAppsBadge(targets.size)
            } else {
                val names = schedule.packages.mapNotNull { targetMap[it]?.displayName ?: it.substringAfterLast('.') }
                if (names.isEmpty()) strings.noneSelectedLabel else names.joinToString(", ")
            }

            val appPrefix = if (schedule.scheduleType == ScheduleType.HARD_BLOCK) strings.blockedPrefix else strings.interventionsPrefix
            Text(
                text = "$appPrefix: $appNames",
                fontFamily = TerminalFontFamily,
                fontSize = 11.sp,
                color = palette.textSecondary,
                maxLines = 2
            )
        }
    }
}
