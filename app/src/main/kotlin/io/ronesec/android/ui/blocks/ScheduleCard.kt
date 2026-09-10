package io.ronesec.android.ui.blocks

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ronesec.android.R
import io.ronesec.android.ui.designsystem.TerminalBadge
import io.ronesec.android.ui.designsystem.TerminalCard
import io.ronesec.android.ui.designsystem.WattimTheme
import io.ronesec.domain.model.ScheduleType

@Composable
fun ScheduleCard(
    schedule: ScheduleItemUiModel,
    onEdit: (scheduleId: Long) -> Unit,
    onToggle: (scheduleId: Long, enabled: Boolean) -> Unit,
    onDelete: (scheduleId: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = WattimTheme.colors
    val typography = WattimTheme.typography

    TerminalCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clickable(role = Role.Button, onClick = { onEdit(schedule.id) })
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Row 1: Name and 3 badges (EDIT, ACTIVE/OFF, DELETE)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = schedule.name.uppercase(),
                    fontFamily = typography.bodyMedium.fontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = colors.textPrimary,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(end = 6.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TerminalBadge(
                        text = stringResource(R.string.action_edit),
                        isActive = false,
                        onClick = { onEdit(schedule.id) }
                    )

                    TerminalBadge(
                        text = if (schedule.enabled) stringResource(R.string.status_active) else stringResource(R.string.status_off),
                        isActive = schedule.enabled,
                        onClick = { onToggle(schedule.id, !schedule.enabled) }
                    )

                    TerminalBadge(
                        text = stringResource(R.string.action_delete),
                        isActive = false,
                        onClick = { onDelete(schedule.id) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Row 2: Type label
            val typeLabel = if (schedule.type == ScheduleType.HARD_BLOCK) {
                stringResource(R.string.full_block_badge)
            } else {
                stringResource(R.string.scheduled_interventions_badge)
            }
            Text(
                text = typeLabel,
                fontFamily = typography.bodyMedium.fontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                color = colors.textSecondary
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Row 3: Days and time range
            val overnightText = if (schedule.isOvernight) " (${stringResource(R.string.overnight_badge)})" else ""
            Text(
                text = "⏰ ${schedule.daysSummary} · ${schedule.timeSummary}$overnightText",
                fontFamily = typography.bodyMedium.fontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = colors.accent
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Row 4: Target apps prefix & list
            val appPrefix = if (schedule.type == ScheduleType.HARD_BLOCK) {
                stringResource(R.string.blocked_prefix)
            } else {
                stringResource(R.string.interventions_prefix)
            }
            Text(
                text = "$appPrefix: ${schedule.targetsSummary}",
                fontFamily = typography.bodyMedium.fontFamily,
                fontSize = 11.sp,
                color = colors.textSecondary,
                maxLines = 2
            )
        }
    }
}
