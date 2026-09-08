package io.ronesec.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import io.ronesec.android.ui.components.TerminalButton
import io.ronesec.android.ui.i18n.LocalAppStrings
import io.ronesec.android.ui.screens.home.PauseProtectionCard
import io.ronesec.android.ui.screens.home.ProtectedAppsSection
import io.ronesec.android.ui.screens.home.TodayStatsHeroCard
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
        ProtectedAppsSection(
            targets = targets,
            onSelectTarget = onSelectTarget,
            onToggleTarget = onToggleTarget,
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Today mindfulness stats card
        TodayStatsHeroCard(todayStats = todayStats)

        Spacer(modifier = Modifier.height(16.dp))

        // Protection pause status card
        PauseProtectionCard(
            isPaused = isPaused,
            protectionPausedUntil = protectionPausedUntil,
            remainingSeconds = remainingSeconds,
            onPauseProtection = onPauseProtection,
            onResumeProtection = onResumeProtection
        )

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
