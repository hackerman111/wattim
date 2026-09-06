package io.ronesec.android.ui.screens

import android.content.Context
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import io.ronesec.android.ui.theme.LocalTerminalAccent
import io.ronesec.android.ui.theme.TerminalAccent
import io.ronesec.android.ui.theme.TerminalBackground
import io.ronesec.android.ui.theme.TerminalFontFamily
import io.ronesec.android.ui.theme.TerminalTextPrimary
import io.ronesec.android.ui.theme.TerminalTextSecondary
import io.ronesec.android.util.PermissionHelper

@Composable
fun ConfigScreen(
    currentAccent: TerminalAccent,
    onSelectAccent: (TerminalAccent) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val accent = LocalTerminalAccent.current

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
            .background(TerminalBackground)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "CONFIGURATION",
            fontFamily = TerminalFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            letterSpacing = 0.15.sp,
            color = accent
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Terminal Accent Selection
        TerminalCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "TERMINAL ACCENT",
                fontFamily = TerminalFontFamily,
                fontSize = 12.sp,
                letterSpacing = 0.1.sp,
                color = TerminalTextSecondary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TerminalAccent.entries.forEach { acc ->
                    val isSelected = acc == currentAccent
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { onSelectAccent(acc) }
                            .padding(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(acc.color, CircleShape)
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(
                            text = acc.label,
                            fontFamily = TerminalFontFamily,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 12.sp,
                            color = if (isSelected) acc.color else TerminalTextSecondary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Permission Watchdog Status Card
        TerminalCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "SYSTEM WATCHDOG",
                fontFamily = TerminalFontFamily,
                fontSize = 12.sp,
                letterSpacing = 0.1.sp,
                color = TerminalTextSecondary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Accessibility
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("ACCESSIBILITY", fontFamily = TerminalFontFamily, fontSize = 13.sp, color = TerminalTextPrimary)
                    Text("Foreground detection", fontFamily = TerminalFontFamily, fontSize = 11.sp, color = TerminalTextSecondary)
                }

                if (isAccessibilityOk) {
                    TerminalBadge(text = "OK", isActive = true)
                } else {
                    TerminalButton(
                        text = "FIX",
                        onClick = { context.startActivity(PermissionHelper.getAccessibilitySettingsIntent()) }
                    )
                }
            }

            // Overlay
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("OVERLAY WINDOW", fontFamily = TerminalFontFamily, fontSize = 13.sp, color = TerminalTextPrimary)
                    Text("Full-screen intervention", fontFamily = TerminalFontFamily, fontSize = 11.sp, color = TerminalTextSecondary)
                }

                if (isOverlayOk) {
                    TerminalBadge(text = "OK", isActive = true)
                } else {
                    TerminalButton(
                        text = "FIX",
                        onClick = { context.startActivity(PermissionHelper.getOverlaySettingsIntent(context)) }
                    )
                }
            }

            // Battery
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("BATTERY OPTIMIZATION", fontFamily = TerminalFontFamily, fontSize = 13.sp, color = TerminalTextPrimary)
                    Text("Background persistence", fontFamily = TerminalFontFamily, fontSize = 11.sp, color = TerminalTextSecondary)
                }

                if (isBatteryOk) {
                    TerminalBadge(text = "OK", isActive = true)
                } else {
                    TerminalButton(
                        text = "FIX",
                        onClick = { context.startActivity(PermissionHelper.getBatteryOptimizationSettingsIntent(context)) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Privacy & Architecture Card (AC-13, AC-14)
        TerminalCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "SECURITY & PRIVACY",
                fontFamily = TerminalFontFamily,
                fontSize = 12.sp,
                letterSpacing = 0.1.sp,
                color = TerminalTextSecondary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = "• 100% OFFLINE (NO INTERNET PERMISSION)\n• ZERO TELEMETRY OR TRACKING\n• ALL LOGS STORED IN LOCAL ROOM DB",
                fontFamily = TerminalFontFamily,
                fontSize = 12.sp,
                lineHeight = 20.sp,
                color = accent
            )
        }
    }
}
