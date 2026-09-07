package io.ronesec.android.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
fun OnboardingScreen(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val palette = LocalAppPalette.current
    val strings = LocalAppStrings.current

    var isAccessibilityOk by remember { mutableStateOf(false) }
    var isOverlayOk by remember { mutableStateOf(false) }
    var isBatteryOk by remember { mutableStateOf(false) }

    fun refresh() {
        isAccessibilityOk = PermissionHelper.isAccessibilityEnabled(context)
        isOverlayOk = PermissionHelper.isOverlayEnabled(context)
        isBatteryOk = PermissionHelper.isBatteryOptimizationIgnored(context)
    }

    LaunchedEffect(Unit) {
        refresh()
    }

    val step = when {
        !isAccessibilityOk -> 1
        !isOverlayOk -> 2
        !isBatteryOk -> 3
        else -> 4
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = strings.onboardingTitle,
                fontFamily = TerminalFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                letterSpacing = 0.2.sp,
                color = palette.accent
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (step <= 3) "0$step / 03" else strings.systemReadyTitle,
                fontFamily = TerminalFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                letterSpacing = 0.15.sp,
                color = palette.textSecondary
            )

            Spacer(modifier = Modifier.height(24.dp))

            when (step) {
                1 -> {
                    Text(
                        text = strings.stepAccessibilityTitle,
                        fontFamily = TerminalFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = palette.textPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = strings.stepAccessibilityDesc,
                        fontFamily = TerminalFontFamily,
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        color = palette.textSecondary
                    )
                }

                2 -> {
                    Text(
                        text = strings.stepOverlayTitle,
                        fontFamily = TerminalFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = palette.textPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = strings.stepOverlayDesc,
                        fontFamily = TerminalFontFamily,
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        color = palette.textSecondary
                    )
                }

                3 -> {
                    Text(
                        text = strings.stepBatteryTitle,
                        fontFamily = TerminalFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = palette.textPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = strings.stepBatteryDesc,
                        fontFamily = TerminalFontFamily,
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        color = palette.textSecondary
                    )
                }

                else -> {
                    TerminalCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(strings.permAccessibility, fontFamily = TerminalFontFamily, color = palette.textPrimary)
                            TerminalBadge(strings.readyBadge, isActive = true)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(strings.permOverlay, fontFamily = TerminalFontFamily, color = palette.textPrimary)
                            TerminalBadge(strings.readyBadge, isActive = true)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(strings.permBattery, fontFamily = TerminalFontFamily, color = palette.textPrimary)
                            TerminalBadge(strings.readyBadge, isActive = true)
                        }
                    }
                }
            }

            if (step <= 2) {
                Spacer(modifier = Modifier.height(16.dp))
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
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Action button
        Column(modifier = Modifier.fillMaxWidth()) {
            when (step) {
                1 -> {
                    TerminalButton(
                        text = strings.openAccessibilityButton,
                        onClick = { context.startActivity(PermissionHelper.getAccessibilitySettingsIntent()) },
                        isPrimary = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                2 -> {
                    TerminalButton(
                        text = strings.allowOverlayButton,
                        onClick = { context.startActivity(PermissionHelper.getOverlaySettingsIntent(context)) },
                        isPrimary = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                3 -> {
                    TerminalButton(
                        text = strings.disableBatteryOptButton,
                        onClick = { context.startActivity(PermissionHelper.getBatteryOptimizationSettingsIntent(context)) },
                        isPrimary = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                else -> {
                    TerminalButton(
                        text = strings.startWattimButton,
                        onClick = onComplete,
                        isPrimary = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (step <= 3) {
                Spacer(modifier = Modifier.height(12.dp))
                TerminalButton(
                    text = strings.checkStatusButton,
                    onClick = { refresh() },
                    isPrimary = false,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
