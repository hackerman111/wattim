package io.ronesec.android.ui.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import java.util.Locale

@Composable
fun PauseProtectionCard(
    isPaused: Boolean,
    protectionPausedUntil: Long?,
    remainingSeconds: Long,
    onPauseProtection: (Int) -> Unit,
    onResumeProtection: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    val strings = LocalAppStrings.current

    if (isPaused) {
        TerminalCard(
            modifier = modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, palette.error)
        ) {
            Text(
                text = strings.protectionPausedTitle,
                fontFamily = TerminalFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 0.15.sp,
                color = palette.error,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            if (protectionPausedUntil == -1L) {
                Text(
                    text = strings.pauseForever,
                    fontFamily = TerminalFontFamily,
                    fontSize = 13.sp,
                    color = palette.textSecondary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            } else {
                val formattedTime = String.format(Locale.US, "%02d:%02d", remainingSeconds / 60, remainingSeconds % 60)
                Text(
                    text = "${strings.remainingPrefix} $formattedTime",
                    fontFamily = TerminalFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = palette.textPrimary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            TerminalButton(
                text = strings.resumeButton,
                onClick = onResumeProtection,
                isPrimary = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    } else {
        TerminalCard(modifier = modifier.fillMaxWidth()) {
            Text(
                text = strings.pauseProtectionTitle,
                fontFamily = TerminalFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                letterSpacing = 0.15.sp,
                color = palette.textSecondary,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            Text(
                text = strings.pauseProtectionTitle,
                fontFamily = TerminalFontFamily,
                fontSize = 12.sp,
                color = palette.textSecondary,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TerminalBadge(
                    text = strings.pause15m,
                    modifier = Modifier.clickable { onPauseProtection(15) }
                )
                TerminalBadge(
                    text = strings.pause30m,
                    modifier = Modifier.clickable { onPauseProtection(30) }
                )
                TerminalBadge(
                    text = strings.pause1h,
                    modifier = Modifier.clickable { onPauseProtection(60) }
                )
                TerminalBadge(
                    text = strings.pauseForever,
                    modifier = Modifier.clickable { onPauseProtection(-1) }
                )
            }
        }
    }
}
