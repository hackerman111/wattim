package io.ronesec.android.ui.screens

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ronesec.android.ui.components.TerminalBadge
import io.ronesec.android.ui.components.TerminalButton
import io.ronesec.android.ui.components.TerminalCard
import io.ronesec.android.ui.i18n.AppLanguage
import io.ronesec.android.ui.i18n.LocalAppStrings
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

        // Language Selection Card
        TerminalCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = strings.languageSection,
                fontFamily = TerminalFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                letterSpacing = 0.1.sp,
                color = palette.textSecondary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppLanguage.entries.forEach { lang ->
                    val isSelected = lang == currentLanguage
                    TerminalButton(
                        text = when (lang) {
                            AppLanguage.SYSTEM -> "AUTO"
                            AppLanguage.EN -> "ENGLISH"
                            AppLanguage.RU -> "РУССКИЙ"
                        },
                        onClick = { onSelectLanguage(lang) },
                        isPrimary = isSelected,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Theme Selection Card
        TerminalCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = strings.themeSection,
                fontFamily = TerminalFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                letterSpacing = 0.1.sp,
                color = palette.textSecondary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppTheme.entries.forEach { theme ->
                    val isSelected = theme == currentTheme
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isSelected) palette.surfaceElevated else palette.surface)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) accent else palette.border,
                                shape = RoundedCornerShape(4.dp)
                            )
                            .clickable { onSelectTheme(theme) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Swatches: background + accent
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .background(theme.palette.background, CircleShape)
                                    .border(1.dp, palette.border, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .background(theme.palette.accent, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = theme.displayName,
                                fontFamily = TerminalFontFamily,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp,
                                color = if (isSelected) accent else palette.textPrimary
                            )
                        }

                        if (isSelected) {
                            TerminalBadge(text = strings.activeBadge, isActive = true)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Session Duration Estimation Card
        TerminalCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = strings.sessionDurationSection,
                fontFamily = TerminalFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                letterSpacing = 0.1.sp,
                color = palette.textSecondary,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Text(
                text = strings.sessionDurationDesc,
                fontFamily = TerminalFontFamily,
                fontSize = 11.sp,
                color = palette.textSecondary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(5, 7, 10, 15).forEach { mins ->
                    val isSelected = mins == sessionMinutes
                    TerminalButton(
                        text = "$mins ${strings.minutesUnit}",
                        onClick = { onSelectSessionMinutes(mins) },
                        isPrimary = isSelected,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Saved Time Banner On Overlay Toggle Card
        TerminalCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = strings.statsOnOverlaySection,
                        fontFamily = TerminalFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        letterSpacing = 0.1.sp,
                        color = palette.textSecondary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = strings.statsOnOverlayDesc,
                        fontFamily = TerminalFontFamily,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = palette.textSecondary
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TerminalBadge(
                        text = strings.onLabel,
                        isActive = showSavedTimeStats,
                        modifier = Modifier.clickable { onToggleShowSavedTimeStats(true) }
                    )
                    TerminalBadge(
                        text = strings.offLabel,
                        isActive = !showSavedTimeStats,
                        modifier = Modifier.clickable { onToggleShowSavedTimeStats(false) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Permission Watchdog Status Card
        TerminalCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = strings.systemPermissionsSection,
                fontFamily = TerminalFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                letterSpacing = 0.1.sp,
                color = palette.textSecondary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Accessibility
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(strings.permAccessibility, fontFamily = TerminalFontFamily, fontSize = 13.sp, color = palette.textPrimary)
                    Text(strings.permAccessibilityDesc, fontFamily = TerminalFontFamily, fontSize = 11.sp, color = palette.textSecondary)
                }

                if (isAccessibilityOk) {
                    TerminalBadge(text = strings.readyBadge, isActive = true)
                } else {
                    TerminalButton(
                        text = strings.enableButton,
                        onClick = { context.startActivity(PermissionHelper.getAccessibilitySettingsIntent()) },
                        isPrimary = true
                    )
                }
            }

            // Overlay
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(strings.permOverlay, fontFamily = TerminalFontFamily, fontSize = 13.sp, color = palette.textPrimary)
                    Text(strings.permOverlayDesc, fontFamily = TerminalFontFamily, fontSize = 11.sp, color = palette.textSecondary)
                }

                if (isOverlayOk) {
                    TerminalBadge(text = strings.readyBadge, isActive = true)
                } else {
                    TerminalButton(
                        text = strings.enableButton,
                        onClick = { context.startActivity(PermissionHelper.getOverlaySettingsIntent(context)) },
                        isPrimary = true
                    )
                }
            }

            // Battery
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(strings.permBattery, fontFamily = TerminalFontFamily, fontSize = 13.sp, color = palette.textPrimary)
                    Text(strings.permBatteryDesc, fontFamily = TerminalFontFamily, fontSize = 11.sp, color = palette.textSecondary)
                }

                if (isBatteryOk) {
                    TerminalBadge(text = strings.readyBadge, isActive = true)
                } else {
                    TerminalButton(
                        text = strings.enableButton,
                        onClick = { context.startActivity(PermissionHelper.getBatteryOptimizationSettingsIntent(context)) },
                        isPrimary = true
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Google Play Protect & Restricted Settings Card
        TerminalCard(
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, palette.accent.copy(alpha = 0.5f))
        ) {
            Text(
                text = strings.googleWarningTitle,
                fontFamily = TerminalFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 0.1.sp,
                color = palette.accent,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Text(
                text = strings.googleWarningDesc,
                fontFamily = TerminalFontFamily,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = palette.textSecondary,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            TerminalButton(
                text = strings.openAppSettingsButton,
                onClick = { context.startActivity(PermissionHelper.getAppSettingsIntent(context)) },
                isPrimary = false,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Privacy & Architecture Card
        TerminalCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = strings.privacySection,
                fontFamily = TerminalFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                letterSpacing = 0.1.sp,
                color = palette.textSecondary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = strings.privacyBullets,
                fontFamily = TerminalFontFamily,
                fontSize = 12.sp,
                lineHeight = 20.sp,
                color = accent
            )
        }
    }
}
