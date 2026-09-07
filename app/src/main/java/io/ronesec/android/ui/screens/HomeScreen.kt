package io.ronesec.android.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import io.ronesec.android.ui.i18n.LocalAppStrings
import io.ronesec.android.ui.theme.LocalAppPalette
import io.ronesec.android.ui.theme.TerminalFontFamily
import io.ronesec.android.ui.viewmodel.InstalledAppInfo
import io.ronesec.android.ui.viewmodel.TodayStats
import kotlinx.coroutines.delay
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
    modifier: Modifier = Modifier,
    protectionPausedUntil: Long? = null,
    onPauseProtection: (Int) -> Unit = {},
    onResumeProtection: () -> Unit = {}
) {
    val palette = LocalAppPalette.current
    val accent = palette.accent
    val strings = LocalAppStrings.current
    var showAddDialog by remember { mutableStateOf(false) }

    var remainingSeconds by remember(protectionPausedUntil) {
        val rem = if (protectionPausedUntil != null && protectionPausedUntil > 0L) {
            ((protectionPausedUntil - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)
        } else 0L
        mutableLongStateOf(rem)
    }

    LaunchedEffect(protectionPausedUntil) {
        if (protectionPausedUntil != null && protectionPausedUntil > 0L) {
            while (remainingSeconds > 0) {
                delay(1000L)
                remainingSeconds = ((protectionPausedUntil - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)
            }
        }
    }

    val isPaused = protectionPausedUntil != null && (protectionPausedUntil == -1L || (protectionPausedUntil > System.currentTimeMillis() && remainingSeconds > 0))

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
            .background(palette.background)
            .padding(16.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "wattim",
                fontFamily = TerminalFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                letterSpacing = 0.2.sp,
                color = accent
            )

            Text(
                text = currentTimeString,
                fontFamily = TerminalFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                color = palette.textSecondary
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Protected Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = strings.protectedSection,
                fontFamily = TerminalFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                letterSpacing = 0.15.sp,
                color = palette.textSecondary
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
                    text = strings.noProtectedApps,
                    fontFamily = TerminalFontFamily,
                    fontSize = 13.sp,
                    color = palette.textSecondary
                )
            }
            Spacer(modifier = Modifier.weight(1f))
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

        Spacer(modifier = Modifier.height(16.dp))

        // Today mindfulness stats card
        TerminalCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = strings.todayHeader,
                fontFamily = TerminalFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                letterSpacing = 0.15.sp,
                color = palette.textSecondary,
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
                        fontSize = 26.sp,
                        color = palette.textPrimary
                    )
                    Text(
                        text = strings.attemptsUnit.uppercase(),
                        fontFamily = TerminalFontFamily,
                        fontSize = 11.sp,
                        color = palette.textSecondary
                    )
                }

                Column {
                    Text(
                        text = "${todayStats.closed}",
                        fontFamily = TerminalFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 26.sp,
                        color = accent
                    )
                    Text(
                        text = strings.victoriesUnit.uppercase(),
                        fontFamily = TerminalFontFamily,
                        fontSize = 11.sp,
                        color = palette.textSecondary
                    )
                }

                Column {
                    Text(
                        text = "${todayStats.avoidedPercent}%",
                        fontFamily = TerminalFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 26.sp,
                        color = palette.textPrimary
                    )
                    Text(
                        text = strings.savedUnit.uppercase(),
                        fontFamily = TerminalFontFamily,
                        fontSize = 11.sp,
                        color = palette.textSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Protection pause status card
        if (isPaused) {
            TerminalCard(
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, palette.error)
            ) {
                Text(
                    text = strings.protectionPausedTitle,
                    fontFamily = TerminalFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 0.15.sp,
                    color = palette.error,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                if (protectionPausedUntil == -1L) {
                    Text(
                        text = strings.pauseForever,
                        fontFamily = TerminalFontFamily,
                        fontSize = 13.sp,
                        color = palette.textSecondary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                } else {
                    val formattedTime = String.format(Locale.US, "%02d:%02d", remainingSeconds / 60, remainingSeconds % 60)
                    Text(
                        text = "${strings.remainingPrefix} $formattedTime",
                        fontFamily = TerminalFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = palette.textPrimary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                TerminalButton(
                    text = strings.resumeButton,
                    onClick = onResumeProtection,
                    isPrimary = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            TerminalCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = strings.pauseProtectionTitle,
                    fontFamily = TerminalFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    letterSpacing = 0.15.sp,
                    color = palette.textSecondary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                Text(
                    text = strings.pauseProtectionTitle,
                    fontFamily = TerminalFontFamily,
                    fontSize = 12.sp,
                    color = palette.textSecondary,
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TerminalBadge(
                        text = strings.pause15m,
                        modifier = Modifier.clickable { onPauseProtection(15) }
                    )
                    TerminalBadge(
                        text = strings.pause30m,
                        modifier = Modifier.clickable { onPauseProtection(30) }
                    )
                    TerminalBadge(
                        text = strings.pause1h,
                        modifier = Modifier.clickable { onPauseProtection(60) }
                    )
                    TerminalBadge(
                        text = strings.pauseForever,
                        modifier = Modifier.clickable { onPauseProtection(-1) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Add App Button
        TerminalButton(
            text = strings.addAppButton,
            onClick = { showAddDialog = true },
            isPrimary = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
