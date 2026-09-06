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
import io.ronesec.android.ui.theme.LocalTerminalAccent
import io.ronesec.android.ui.theme.TerminalBackground
import io.ronesec.android.ui.theme.TerminalFontFamily
import io.ronesec.android.ui.theme.TerminalTextPrimary
import io.ronesec.android.ui.theme.TerminalTextSecondary
import io.ronesec.android.util.PermissionHelper

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val accent = LocalTerminalAccent.current

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
            .background(TerminalBackground)
            .padding(24.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "RONESEC SETUP",
                fontFamily = TerminalFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                letterSpacing = 0.2.sp,
                color = accent
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (step <= 3) "0$step / 03" else "SYSTEM READY",
                fontFamily = TerminalFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                letterSpacing = 0.15.sp,
                color = TerminalTextSecondary
            )

            Spacer(modifier = Modifier.height(32.dp))

            when (step) {
                1 -> {
                    Text(
                        text = "ACCESSIBILITY",
                        fontFamily = TerminalFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = TerminalTextPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Required to detect in real-time when protected apps are opened by the user.",
                        fontFamily = TerminalFontFamily,
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        color = TerminalTextSecondary
                    )
                }

                2 -> {
                    Text(
                        text = "DISPLAY OVER APPS",
                        fontFamily = TerminalFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = TerminalTextPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Required to display the full-screen mindfulness intervention overlay.",
                        fontFamily = TerminalFontFamily,
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        color = TerminalTextSecondary
                    )
                }

                3 -> {
                    Text(
                        text = "BATTERY OPTIMIZATION",
                        fontFamily = TerminalFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = TerminalTextPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Disable battery optimization to guarantee continuous and reliable protection without system termination.",
                        fontFamily = TerminalFontFamily,
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        color = TerminalTextSecondary
                    )
                }

                else -> {
                    TerminalCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Accessibility", fontFamily = TerminalFontFamily, color = TerminalTextPrimary)
                            TerminalBadge("OK", isActive = true)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Overlay Window", fontFamily = TerminalFontFamily, color = TerminalTextPrimary)
                            TerminalBadge("OK", isActive = true)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Battery Exemption", fontFamily = TerminalFontFamily, color = TerminalTextPrimary)
                            TerminalBadge("OK", isActive = true)
                        }
                    }
                }
            }
        }

        // Action button
        Column(modifier = Modifier.fillMaxWidth()) {
            when (step) {
                1 -> {
                    TerminalButton(
                        text = "OPEN ACCESSIBILITY SETTINGS",
                        onClick = { context.startActivity(PermissionHelper.getAccessibilitySettingsIntent()) },
                        isPrimary = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                2 -> {
                    TerminalButton(
                        text = "GRANT OVERLAY PERMISSION",
                        onClick = { context.startActivity(PermissionHelper.getOverlaySettingsIntent(context)) },
                        isPrimary = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                3 -> {
                    TerminalButton(
                        text = "DISABLE BATTERY OPTIMIZATION",
                        onClick = { context.startActivity(PermissionHelper.getBatteryOptimizationSettingsIntent(context)) },
                        isPrimary = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                else -> {
                    TerminalButton(
                        text = "CONTINUE TO RONESEC",
                        onClick = onComplete,
                        isPrimary = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (step <= 3) {
                Spacer(modifier = Modifier.height(12.dp))
                TerminalButton(
                    text = "CHECK STATUS",
                    onClick = { refresh() },
                    isPrimary = false,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
