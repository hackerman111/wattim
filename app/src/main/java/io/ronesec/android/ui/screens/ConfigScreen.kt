package io.ronesec.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ronesec.android.ui.i18n.AppLanguage
import io.ronesec.android.ui.i18n.LocalAppStrings
import io.ronesec.android.ui.screens.config.GoogleRestrictedSettingsCard
import io.ronesec.android.ui.screens.config.LanguageSelectionCard
import io.ronesec.android.ui.screens.config.PrivacyCard
import io.ronesec.android.ui.screens.config.SessionDurationCard
import io.ronesec.android.ui.screens.config.StatsOnOverlayCard
import io.ronesec.android.ui.screens.config.SystemPermissionsCard
import io.ronesec.android.ui.screens.config.ThemeSelectionCard
import io.ronesec.android.ui.theme.AppTheme
import io.ronesec.android.ui.theme.LocalAppPalette
import io.ronesec.android.ui.theme.TerminalFontFamily
import io.ronesec.android.util.PermissionHelper

@Composable
fun ConfigScreen(
    currentTheme: AppTheme,
    onSelectTheme: (AppTheme) -> Unit,
    currentLanguage: AppLanguage = AppLanguage.SYSTEM,
    onSelectLanguage: (AppLanguage) -> Unit = {},
    sessionMinutes: Int,
    onSelectSessionMinutes: (Int) -> Unit,
    showSavedTimeStats: Boolean = true,
    onToggleShowSavedTimeStats: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val palette = LocalAppPalette.current
    val accent = palette.accent
    val strings = LocalAppStrings.current

    var isAccessibilityOk by remember { mutableStateOf(false) }
    var isOverlayOk by remember { mutableStateOf(false) }
    var isBatteryOk by remember { mutableStateOf(false) }

    fun refreshPermissions() {
        isAccessibilityOk = PermissionHelper.isAccessibilityEnabled(context)
        isOverlayOk = PermissionHelper.isOverlayEnabled(context)
        isBatteryOk = PermissionHelper.isBatteryOptimizationIgnored(context)
    }

    LaunchedEffect(Unit) {
        refreshPermissions()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = strings.settingsTitle,
            fontFamily = TerminalFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            letterSpacing = 0.15.sp,
            color = accent
        )

        Spacer(modifier = Modifier.height(20.dp))

        LanguageSelectionCard(
            currentLanguage = currentLanguage,
            onSelectLanguage = onSelectLanguage
        )

        Spacer(modifier = Modifier.height(20.dp))

        ThemeSelectionCard(
            currentTheme = currentTheme,
            onSelectTheme = onSelectTheme
        )

        Spacer(modifier = Modifier.height(20.dp))

        SessionDurationCard(
            sessionMinutes = sessionMinutes,
            onSelectSessionMinutes = onSelectSessionMinutes
        )

        Spacer(modifier = Modifier.height(20.dp))

        StatsOnOverlayCard(
            showSavedTimeStats = showSavedTimeStats,
            onToggleShowSavedTimeStats = onToggleShowSavedTimeStats
        )

        Spacer(modifier = Modifier.height(20.dp))

        SystemPermissionsCard(
            context = context,
            isAccessibilityOk = isAccessibilityOk,
            isOverlayOk = isOverlayOk,
            isBatteryOk = isBatteryOk
        )

        Spacer(modifier = Modifier.height(20.dp))

        GoogleRestrictedSettingsCard(context = context)

        Spacer(modifier = Modifier.height(20.dp))

        PrivacyCard()
    }
}
