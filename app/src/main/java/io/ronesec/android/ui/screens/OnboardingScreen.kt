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
            .padding(24.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "WATTIM НАСТРОЙКА",
                fontFamily = TerminalFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                letterSpacing = 0.2.sp,
                color = palette.accent
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (step <= 3) "0$step / 03" else "СИСТЕМА ГОТОВА",
                fontFamily = TerminalFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                letterSpacing = 0.15.sp,
                color = palette.textSecondary
            )

            Spacer(modifier = Modifier.height(32.dp))

            when (step) {
                1 -> {
                    Text(
                        text = "СПЕЦИАЛЬНЫЕ ВОЗМОЖНОСТИ",
                        fontFamily = TerminalFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = palette.textPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Необходимо для отслеживания запуска защищаемых приложений в реальном времени.",
                        fontFamily = TerminalFontFamily,
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        color = palette.textSecondary
                    )
                }

                2 -> {
                    Text(
                        text = "ОТОБРАЖЕНИЕ ПОВЕРХ ДРУГИХ ПРИЛОЖЕНИЙ",
                        fontFamily = TerminalFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = palette.textPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Необходимо для показа полноэкранного экрана осознанности и дыхания перед входом.",
                        fontFamily = TerminalFontFamily,
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        color = palette.textSecondary
                    )
                }

                3 -> {
                    Text(
                        text = "РАБОТА В ФОНЕ (БАТАРЕЯ)",
                        fontFamily = TerminalFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = palette.textPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Отключите оптимизацию батареи для wattim, чтобы Android не останавливал службу защиты.",
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
                            Text("Специальные возможности", fontFamily = TerminalFontFamily, color = palette.textPrimary)
                            TerminalBadge("OK", isActive = true)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Отображение поверх окон", fontFamily = TerminalFontFamily, color = palette.textPrimary)
                            TerminalBadge("OK", isActive = true)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Работа без ограничений", fontFamily = TerminalFontFamily, color = palette.textPrimary)
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
                        text = "ОТКРЫТЬ СПЕЦ. ВОЗМОЖНОСТИ",
                        onClick = { context.startActivity(PermissionHelper.getAccessibilitySettingsIntent()) },
                        isPrimary = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                2 -> {
                    TerminalButton(
                        text = "РАЗРЕШИТЬ ОТОБРАЖЕНИЕ",
                        onClick = { context.startActivity(PermissionHelper.getOverlaySettingsIntent(context)) },
                        isPrimary = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                3 -> {
                    TerminalButton(
                        text = "ОТКЛЮЧИТЬ ОПТИМИЗАЦИЮ БАТАРЕИ",
                        onClick = { context.startActivity(PermissionHelper.getBatteryOptimizationSettingsIntent(context)) },
                        isPrimary = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                else -> {
                    TerminalButton(
                        text = "ПЕРЕЙТИ В WATTIM",
                        onClick = onComplete,
                        isPrimary = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (step <= 3) {
                Spacer(modifier = Modifier.height(12.dp))
                TerminalButton(
                    text = "ПРОВЕРИТЬ СТАТУС",
                    onClick = { refresh() },
                    isPrimary = false,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
