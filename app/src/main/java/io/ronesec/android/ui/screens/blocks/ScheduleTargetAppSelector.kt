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
import io.ronesec.android.domain.model.ScheduleAppOverride
import io.ronesec.android.domain.model.ScheduleType
import io.ronesec.android.domain.model.TargetApp
import io.ronesec.android.ui.components.TerminalBadge
import io.ronesec.android.ui.components.TerminalButton
import io.ronesec.android.ui.i18n.LocalAppStrings
import io.ronesec.android.ui.theme.LocalAppPalette
import io.ronesec.android.ui.theme.TerminalFontFamily

@Composable
fun ScheduleTargetAppSelector(
    targets: List<TargetApp>,
    selectedPackages: Set<String>,
    scheduleType: ScheduleType,
    appOverrides: Map<String, ScheduleAppOverride>,
    onSelectedPackagesChange: (Set<String>) -> Unit,
    onAppOverridesChange: (Map<String, ScheduleAppOverride>) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    val accent = palette.accent
    val strings = LocalAppStrings.current
    val enabledTargets = targets.filter { it.enabled }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = strings.forAppsCount(selectedPackages.size, enabledTargets.size),
            fontFamily = TerminalFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            color = palette.textSecondary
        )

        Spacer(modifier = Modifier.height(6.dp))

        if (enabledTargets.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TerminalButton(
                    text = strings.selectAll,
                    onClick = { onSelectedPackagesChange(enabledTargets.map { it.packageName }.toSet()) },
                    modifier = Modifier.weight(1f)
                )
                TerminalButton(
                    text = strings.deselectAll,
                    onClick = { onSelectedPackagesChange(emptySet()) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                enabledTargets.forEach { target ->
                    val isSelected = selectedPackages.contains(target.packageName)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isSelected) palette.surfaceElevated else palette.surface)
                            .border(1.dp, if (isSelected) accent else palette.border, RoundedCornerShape(4.dp))
                            .clickable {
                                val newSet = if (isSelected) selectedPackages - target.packageName else selectedPackages + target.packageName
                                onSelectedPackagesChange(newSet)
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = target.displayName,
                            fontFamily = TerminalFontFamily,
                            fontSize = 12.sp,
                            color = if (isSelected) palette.textPrimary else palette.textSecondary
                        )
                        TerminalBadge(text = if (isSelected) "✓" else "+", isActive = isSelected)
                    }

                    if (scheduleType == ScheduleType.INTERVENTION && isSelected) {
                        AppInterventionOverrideCard(
                            target = target,
                            override = appOverrides[target.packageName],
                            onOverrideChange = { newOverride ->
                                onAppOverridesChange(appOverrides + (target.packageName to newOverride))
                            },
                            modifier = Modifier.padding(start = 12.dp, top = 2.dp, bottom = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
