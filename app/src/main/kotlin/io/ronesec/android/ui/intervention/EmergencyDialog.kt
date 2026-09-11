package io.ronesec.android.ui.intervention

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ronesec.android.R
import io.ronesec.android.ui.designsystem.TerminalBadge
import io.ronesec.android.ui.designsystem.TerminalButton
import io.ronesec.android.ui.designsystem.WattimTheme

/**
 * Emergency Access dialog matching app_1 EmergencyConfirmDialog.
 * Satisfies §2.9, F37, F38, F39:
 * - 75% black scrim.
 * - Title: "ВЫ УВЕРЕНЫ?" / "ARE YOU SURE?".
 * - Confirmation copy.
 * - "ВОЙТИ ОДИН РАЗ" button.
 * - Subhead: "ПРИОСТАНОВИТЬ ЗАЩИТУ ПРИЛОЖЕНИЯ: $targetName".
 * - Horizontal row of badges: 15m, 30m, 1h, forever.
 * - Return button: "ВЕРНУТЬСЯ К ДЫХАНИЮ".
 * - Outside scrim tap dismisses dialog.
 */
@Composable
fun EmergencyDialog(
    targetName: String,
    onDismissRequest: () -> Unit,
    onEmergencyOnce: () -> Unit = {},
    onEmergencyTimed: (Long) -> Unit,
    onEmergencyForever: () -> Unit,
    customEmergencyMinutes: Int? = null,
    modifier: Modifier = Modifier,
    requireCode: Boolean = false,
    emergencyCode: String? = null,
    codeError: Boolean = false,
    onOnceWithCode: ((String) -> Unit)? = null,
    onTimedWithCode: ((Long, String) -> Unit)? = null,
    onForeverWithCode: ((String) -> Unit)? = null
) {
    val colors = WattimTheme.colors
    val typography = WattimTheme.typography
    var code by remember { mutableStateOf("") }
    val isUnlocked = !requireCode || (emergencyCode != null && code.trim() == emergencyCode.trim())
    val timedAction: (Long) -> Unit = { duration ->
        if (onTimedWithCode != null) onTimedWithCode(duration, code) else onEmergencyTimed(duration)
    }

    // 75% black scrim as in app_1
    val scrimColor = Color.Black.copy(alpha = 0.75f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .background(scrimColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismissRequest
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { /* Consume clicks inside dialog */ }
                ),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, colors.border),
            color = colors.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Dialog title
                Text(
                    text = stringResource(R.string.emergency_dialog_title).uppercase(),
                    fontFamily = typography.bodyMedium.fontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    letterSpacing = 0.15.sp,
                    color = colors.accent,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Confirmation copy
                Text(
                    text = stringResource(R.string.emergency_dialog_message),
                    fontFamily = typography.bodyMedium.fontFamily,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Enter once button (primary)
                if (requireCode) {
                    if (emergencyCode != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(BorderStroke(1.dp, colors.accent.copy(alpha = 0.60f)), RoundedCornerShape(4.dp))
                                .background(colors.surfaceElevated, RoundedCornerShape(4.dp))
                                .padding(vertical = 10.dp, horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.code_emergency_display, emergencyCode),
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                letterSpacing = 0.1.sp,
                                color = colors.accent,
                                textAlign = TextAlign.Center
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    DigitCodeInput(code, 10, { code = it }, stringResource(R.string.code_emergency_enter))
                    if (codeError) Text(stringResource(R.string.code_invalid), color = colors.error)
                    Spacer(modifier = Modifier.height(16.dp))
                }
                TerminalButton(
                    text = stringResource(R.string.emergency_enter_once).uppercase(),
                    onClick = { if (onOnceWithCode != null) onOnceWithCode(code) else onEmergencyOnce() },
                    isPrimary = true,
                    enabled = isUnlocked,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Subhead
                Text(
                    text = "${stringResource(R.string.emergency_pause_app).uppercase()}: ${targetName.uppercase()}",
                    fontFamily = typography.bodyMedium.fontFamily,
                    fontSize = 11.sp,
                    letterSpacing = 0.08.sp,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Timed options row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(if (isUnlocked) 1f else 0.38f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
                ) {
                    TerminalBadge(
                        text = stringResource(R.string.emergency_15m),
                        enabled = isUnlocked,
                        onClick = { if (isUnlocked) timedAction(15L * 60L * 1000L) }
                    )
                    TerminalBadge(
                        text = stringResource(R.string.emergency_30m),
                        enabled = isUnlocked,
                        onClick = { if (isUnlocked) timedAction(30L * 60L * 1000L) }
                    )
                    TerminalBadge(
                        text = stringResource(R.string.emergency_1h),
                        enabled = isUnlocked,
                        onClick = { if (isUnlocked) timedAction(60L * 60L * 1000L) }
                    )
                    if (customEmergencyMinutes != null) {
                        TerminalBadge(
                            text = stringResource(R.string.emergency_custom_badge_format, customEmergencyMinutes),
                            enabled = isUnlocked,
                            onClick = { if (isUnlocked) timedAction(customEmergencyMinutes * 60L * 1000L) }
                        )
                    }
                    TerminalBadge(
                        text = stringResource(R.string.emergency_forever),
                        enabled = isUnlocked,
                        onClick = {
                            if (isUnlocked) {
                                if (onForeverWithCode != null) onForeverWithCode(code) else onEmergencyForever()
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Return to breathing button (secondary)
                TerminalButton(
                    text = stringResource(R.string.emergency_resume_breath).uppercase(),
                    onClick = onDismissRequest,
                    isPrimary = false,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
