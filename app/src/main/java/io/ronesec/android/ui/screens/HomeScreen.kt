package io.ronesec.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import io.ronesec.android.domain.model.TargetApp
import io.ronesec.android.ui.components.TerminalBadge
import io.ronesec.android.ui.components.TerminalButton
import io.ronesec.android.ui.components.TerminalCard
import io.ronesec.android.ui.theme.LocalTerminalAccent
import io.ronesec.android.ui.theme.TerminalBackground
import io.ronesec.android.ui.theme.TerminalFontFamily
import io.ronesec.android.ui.theme.TerminalTextPrimary
import io.ronesec.android.ui.theme.TerminalTextSecondary
import io.ronesec.android.ui.viewmodel.InstalledAppInfo
import io.ronesec.android.ui.viewmodel.TodayStats
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(
    targets: List<TargetApp>,
    todayStats: TodayStats,
    installedApps: List<InstalledAppInfo>,
    onSelectTarget: (TargetApp) -> Unit,
    onToggleTarget: (TargetApp, Boolean) -> Unit,
    onAddApp: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalTerminalAccent.current
    var showAddDialog by remember { mutableStateOf(false) }

    val currentTimeString = remember {
        LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
    }

    if (showAddDialog) {
        AddAppDialog(
            installedApps = installedApps,
            alreadyProtectedPackages = targets.map { it.packageName }.toSet(),
            onSelectApp = { app ->
                onAddApp(app.packageName, app.label)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TerminalBackground)
            .padding(16.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "FOCUS",
                fontFamily = TerminalFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                letterSpacing = 0.2.sp,
                color = accent
            )

            Text(
                text = currentTimeString,
                fontFamily = TerminalFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                color = TerminalTextSecondary
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Protected Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "PROTECTED",
                fontFamily = TerminalFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                letterSpacing = 0.15.sp,
                color = TerminalTextSecondary
            )

            Text(
                text = String.format(Locale.US, "%02d", targets.size),
                fontFamily = TerminalFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = accent
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Protected Apps List
        if (targets.isEmpty()) {
            TerminalCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "NO APPS CONFIGURED",
                    fontFamily = TerminalFontFamily,
                    fontSize = 13.sp,
                    color = TerminalTextSecondary
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
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
                                color = TerminalTextSecondary
                            )
                            Column {
                                Text(
                                    text = target.displayName,
                                    fontFamily = TerminalFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                    color = TerminalTextPrimary
                                )
                                Text(
                                    text = "${target.intervention.durationMs / 1000}s · ${target.intervention.animation.name}",
                                    fontFamily = TerminalFontFamily,
                                    fontSize = 11.sp,
                                    color = TerminalTextSecondary
                                )
                            }
                        }

                        TerminalBadge(
                            text = if (target.enabled) "ACTIVE" else "OFF",
                            isActive = target.enabled,
                            modifier = Modifier.clickable {
                                onToggleTarget(target, !target.enabled)
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Today mindfulness stats card
        TerminalCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "TODAY",
                fontFamily = TerminalFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                letterSpacing = 0.15.sp,
                color = TerminalTextSecondary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "${todayStats.openAttempts}",
                        fontFamily = TerminalFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp,
                        color = TerminalTextPrimary
                    )
                    Text(
                        text = "OPEN",
                        fontFamily = TerminalFontFamily,
                        fontSize = 11.sp,
                        color = TerminalTextSecondary
                    )
                }

                Column {
                    Text(
                        text = "${todayStats.closed}",
                        fontFamily = TerminalFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp,
                        color = accent
                    )
                    Text(
                        text = "CLOSED",
                        fontFamily = TerminalFontFamily,
                        fontSize = 11.sp,
                        color = TerminalTextSecondary
                    )
                }

                Column {
                    Text(
                        text = "${todayStats.avoidedPercent}%",
                        fontFamily = TerminalFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp,
                        color = TerminalTextPrimary
                    )
                    Text(
                        text = "AVOIDED",
                        fontFamily = TerminalFontFamily,
                        fontSize = 11.sp,
                        color = TerminalTextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Add App Button
        TerminalButton(
            text = "+ ADD APP",
            onClick = { showAddDialog = true },
            isPrimary = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
