package io.ronesec.android.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ronesec.android.ui.components.TerminalBadge
import io.ronesec.android.ui.components.TerminalButton
import io.ronesec.android.ui.i18n.LocalAppStrings
import io.ronesec.android.ui.theme.LocalAppPalette
import io.ronesec.android.ui.theme.TerminalFontFamily

@Composable
fun EmergencyConfirmDialog(
    visible: Boolean,
    targetAppName: String,
    onDismiss: () -> Unit,
    onEmergencyAccess: ((durationMs: Long?, disableTarget: Boolean) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    val strings = LocalAppStrings.current
    val accent = palette.accent

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    onDismiss()
                }
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        // Consume clicks to prevent dismissing dialog when clicking content
                    },
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, palette.border),
                color = palette.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = strings.emergencyDialogTitle.uppercase(),
                        fontFamily = TerminalFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        letterSpacing = 0.15.sp,
                        color = accent
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = strings.emergencyDialogDesc,
                        fontFamily = TerminalFontFamily,
                        fontSize = 12.sp,
                        color = palette.textSecondary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    TerminalButton(
                        text = strings.emergencyEnterOnce.uppercase(),
                        onClick = {
                            onEmergencyAccess?.invoke(null, false)
                            onDismiss()
                        },
                        isPrimary = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "${strings.emergencyPauseApp.uppercase()}: $targetAppName",
                        fontFamily = TerminalFontFamily,
                        fontSize = 11.sp,
                        letterSpacing = 0.08.sp,
                        color = palette.textSecondary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                    ) {
                        TerminalBadge(
                            text = strings.pause15m,
                            modifier = Modifier.clickable {
                                onEmergencyAccess?.invoke(15 * 60_000L, false)
                                onDismiss()
                            }
                        )
                        TerminalBadge(
                            text = strings.pause30m,
                            modifier = Modifier.clickable {
                                onEmergencyAccess?.invoke(30 * 60_000L, false)
                                onDismiss()
                            }
                        )
                        TerminalBadge(
                            text = strings.pause1h,
                            modifier = Modifier.clickable {
                                onEmergencyAccess?.invoke(60 * 60_000L, false)
                                onDismiss()
                            }
                        )
                        TerminalBadge(
                            text = strings.pauseForever,
                            modifier = Modifier.clickable {
                                onEmergencyAccess?.invoke(null, true)
                                onDismiss()
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    TerminalButton(
                        text = strings.emergencyResumeBreath.uppercase(),
                        onClick = onDismiss,
                        isPrimary = false,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
