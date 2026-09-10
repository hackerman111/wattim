package io.ronesec.android.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import io.ronesec.domain.model.AnimationMode

@Composable
fun ProtectedAppsSection(
    apps: List<ProtectedAppRowItem>,
    onOpenDetail: (packageName: String) -> Unit,
    onToggleEnabled: (packageName: String) -> Unit,
    scrollApps: Boolean = false,
    modifier: Modifier = Modifier
) {
    val colors = WattimTheme.colors
    val typography = WattimTheme.typography

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Section Header: Protected count
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.home_header_protected_count),
                fontFamily = typography.bodyMedium.fontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                letterSpacing = 0.15.sp,
                color = colors.textSecondary
            )

            Text(
                text = String.format(java.util.Locale.US, "%02d", apps.size),
                fontFamily = typography.bodyMedium.fontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = colors.accent
            )
        }

        if (apps.isEmpty()) {
            TerminalCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.home_empty_title),
                    fontFamily = typography.bodyMedium.fontFamily,
                    fontSize = 13.sp,
                    color = colors.textSecondary
                )
            }
        } else if (scrollApps) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(apps, key = { it.packageName }) { appItem ->
                    ProtectedAppRow(
                        item = appItem,
                        onRowClick = { onOpenDetail(appItem.packageName) },
                        onToggleClick = { onToggleEnabled(appItem.packageName) }
                    )
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                apps.forEach { appItem ->
                    ProtectedAppRow(
                        item = appItem,
                        onRowClick = { onOpenDetail(appItem.packageName) },
                        onToggleClick = { onToggleEnabled(appItem.packageName) }
                    )
                }
            }
        }
    }
}

@Composable
fun ProtectedAppRow(
    item: ProtectedAppRowItem,
    onRowClick: () -> Unit,
    onToggleClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = WattimTheme.colors
    val typography = WattimTheme.typography

    val animName = when (item.animation) {
        AnimationMode.FILL -> stringResource(R.string.anim_fill)
        AnimationMode.PULSE -> stringResource(R.string.anim_pulse)
        AnimationMode.CIRCLE -> stringResource(R.string.anim_circle)
        AnimationMode.WAVE -> stringResource(R.string.anim_wave)
    }
    val secUnit = stringResource(R.string.unit_sec)
    val summaryText = "${item.durationSeconds} $secUnit · $animName"

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                role = Role.Button,
                onClick = onRowClick
            )
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (item.isFirst) "> " else "  ",
                fontFamily = typography.bodyMedium.fontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = colors.accent
            )

            Text(
                text = "${item.formattedIndex}  ",
                fontFamily = typography.bodyMedium.fontFamily,
                fontSize = 13.sp,
                color = colors.textSecondary
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.displayName,
                    fontFamily = typography.bodyMedium.fontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = summaryText,
                    fontFamily = typography.bodyMedium.fontFamily,
                    fontSize = 11.sp,
                    color = colors.textSecondary
                )
            }
        }

        val badgeText = if (item.enabled) {
            stringResource(R.string.status_on)
        } else {
            stringResource(R.string.status_off)
        }

        TerminalBadge(
            text = badgeText,
            isActive = item.enabled,
            onClick = onToggleClick
        )
    }
}
