package io.ronesec.android.ui.screens.config

import android.content.Context
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ronesec.android.ui.components.TerminalBadge
import io.ronesec.android.ui.components.TerminalButton
import io.ronesec.android.ui.components.TerminalCard
import io.ronesec.android.ui.i18n.LocalAppStrings
import io.ronesec.android.ui.theme.LocalAppPalette
import io.ronesec.android.ui.theme.TerminalFontFamily
import io.ronesec.android.util.PermissionHelper

@Composable
fun SystemPermissionsCard(
    context: Context,
    isAccessibilityOk: Boolean,
    isOverlayOk: Boolean,
    isBatteryOk: Boolean,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    val strings = LocalAppStrings.current

    TerminalCard(modifier = modifier.fillMaxWidth()) {
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
}

@Composable
fun GoogleRestrictedSettingsCard(
    context: Context,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    val strings = LocalAppStrings.current

    TerminalCard(
        modifier = modifier.fillMaxWidth(),
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
}

@Composable
fun PrivacyCard(modifier: Modifier = Modifier) {
    val palette = LocalAppPalette.current
    val strings = LocalAppStrings.current
    val accent = palette.accent

    TerminalCard(modifier = modifier.fillMaxWidth()) {
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
