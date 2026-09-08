package io.ronesec.android.ui.screens.blocks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ronesec.android.domain.model.BlockSession
import io.ronesec.android.domain.model.TargetApp
import io.ronesec.android.ui.components.TerminalBadge
import io.ronesec.android.ui.components.TerminalButton
import io.ronesec.android.ui.components.TerminalCard
import io.ronesec.android.ui.i18n.LocalAppStrings
import io.ronesec.android.ui.theme.LocalAppPalette
import io.ronesec.android.ui.theme.TerminalFontFamily
import java.util.Locale

@Composable
fun ActiveBlockSessionCard(
    session: BlockSession,
    remainingSeconds: Long,
    onStopSession: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    val strings = LocalAppStrings.current

    TerminalCard(modifier = modifier.fillMaxWidth()) {
        val hours = remainingSeconds / 3600
        val mins = (remainingSeconds % 3600) / 60
        val secs = remainingSeconds % 60
        val timeStr = String.format(Locale.US, "%02d:%02d:%02d", hours, mins, secs)

        Text(
            text = strings.focusSessionActive,
            fontFamily = TerminalFontFamily,
            fontSize = 12.sp,
            letterSpacing = 0.1.sp,
            color = palette.accent
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = timeStr,
            fontFamily = TerminalFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp,
            color = palette.textPrimary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = strings.blockedAppsCount(session.packages.size),
            fontFamily = TerminalFontFamily,
            fontSize = 12.sp,
            color = palette.textSecondary
        )

        Spacer(modifier = Modifier.height(12.dp))

        TerminalButton(
            text = strings.stopSessionButton,
            onClick = { onStopSession(session.id) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun QuickFocusLauncherCard(
    targets: List<TargetApp>,
    onStartBlock: (String, Int, Set<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    val strings = LocalAppStrings.current

    TerminalCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = strings.quickFocusSession,
            fontFamily = TerminalFontFamily,
            fontSize = 12.sp,
            color = palette.textSecondary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        var selectedQuickPackages by remember(targets) {
            mutableStateOf(targets.map { it.packageName }.toSet())
        }

        if (targets.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = strings.forAppsCount(selectedQuickPackages.size, targets.size),
                    fontFamily = TerminalFontFamily,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.textSecondary
                )

                Text(
                    text = if (selectedQuickPackages.size == targets.size) strings.deselectAll else strings.selectAll,
                    fontFamily = TerminalFontFamily,
                    fontSize = 10.sp,
                    color = palette.accent,
                    modifier = Modifier.clickable {
                        selectedQuickPackages = if (selectedQuickPackages.size == targets.size) {
                            emptySet()
                        } else {
                            targets.map { it.packageName }.toSet()
                        }
                    }
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                targets.forEach { target ->
                    val isChecked = target.packageName in selectedQuickPackages
                    TerminalBadge(
                        text = target.displayName,
                        isActive = isChecked,
                        modifier = Modifier.clickable {
                            selectedQuickPackages = if (isChecked) {
                                selectedQuickPackages - target.packageName
                            } else {
                                selectedQuickPackages + target.packageName
                            }
                        }
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val durations = listOf(
                "${15}${strings.minutesUnit.take(1)}" to 15,
                "${30}${strings.minutesUnit.take(1)}" to 30,
                "${1}${strings.hoursLabel.take(1).lowercase()}" to 60,
                "${2}${strings.hoursLabel.take(1).lowercase()}" to 120
            )
            durations.forEach { (lbl, mins) ->
                TerminalButton(
                    text = lbl,
                    onClick = {
                        val pkgs = if (selectedQuickPackages.isEmpty()) {
                            targets.map { it.packageName }.toSet()
                        } else {
                            selectedQuickPackages
                        }
                        onStartBlock(strings.defaultFocusSessionName, mins, pkgs)
                    },
                    isPrimary = (mins == 30),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
