package io.ronesec.android.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ronesec.android.domain.model.TargetApp
import io.ronesec.android.ui.components.TerminalBadge
import io.ronesec.android.ui.components.TerminalCard
import io.ronesec.android.ui.i18n.LocalAppStrings
import io.ronesec.android.ui.theme.LocalAppPalette
import io.ronesec.android.ui.theme.TerminalFontFamily
import java.util.Locale

@Composable
fun ProtectedAppsSection(
    targets: List<TargetApp>,
    onSelectTarget: (TargetApp) -> Unit,
    onToggleTarget: (TargetApp, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    val accent = palette.accent
    val strings = LocalAppStrings.current

    if (targets.isEmpty()) {
        TerminalCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = strings.noProtectedApps,
                fontFamily = TerminalFontFamily,
                fontSize = 13.sp,
                color = palette.textSecondary
            )
        }
        Spacer(modifier = modifier)
    } else {
        LazyColumn(
            modifier = modifier.fillMaxWidth()
        ) {
            itemsIndexed(targets, key = { _, item -> item.packageName }) { index, target ->
                val indexFormatted = String.format(Locale.US, "%02d", index + 1)
                val isFirst = index == 0

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectTarget(target) }
                        .padding(vertical = 12.dp, horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = if (isFirst) "> " else "  ",
                            fontFamily = TerminalFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = accent
                        )
                        Text(
                            text = "$indexFormatted  ",
                            fontFamily = TerminalFontFamily,
                            fontSize = 13.sp,
                            color = palette.textSecondary
                        )
                        Column {
                            Text(
                                text = target.displayName,
                                fontFamily = TerminalFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                color = palette.textPrimary
                            )
                            Text(
                                text = "${target.intervention.durationMs / 1000} ${strings.secondsShort} · ${strings.animationName(target.intervention.animation)}",
                                fontFamily = TerminalFontFamily,
                                fontSize = 11.sp,
                                color = palette.textSecondary
                            )
                        }
                    }

                    TerminalBadge(
                        text = if (target.enabled) strings.onLabel else strings.offLabel,
                        isActive = target.enabled,
                        modifier = Modifier.clickable {
                            onToggleTarget(target, !target.enabled)
                        }
                    )
                }
            }
        }
    }
}
