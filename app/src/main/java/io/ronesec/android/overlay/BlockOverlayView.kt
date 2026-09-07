package io.ronesec.android.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ronesec.android.ui.components.TerminalButton
import io.ronesec.android.ui.theme.LocalAppPalette
import io.ronesec.android.ui.theme.TerminalFontFamily
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.util.Locale

@Composable
fun BlockOverlayContent(
    sessionName: String,
    until: Instant?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    val accent = palette.accent

    var remainingSeconds by remember(until) {
        val rem = if (until != null) Duration.between(Instant.now(), until).seconds else 0L
        mutableLongStateOf(rem.coerceAtLeast(0L))
    }

    LaunchedEffect(until) {
        while (remainingSeconds > 0) {
            delay(1000L)
            val rem = if (until != null) Duration.between(Instant.now(), until).seconds else 0L
            remainingSeconds = rem.coerceAtLeast(0L)
        }
    }

    val hours = remainingSeconds / 3600
    val minutes = (remainingSeconds % 3600) / 60
    val seconds = remainingSeconds % 60
    val timeFormatted = String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "ПРИЛОЖЕНИЕ ЗАБЛОКИРОВАНО",
                fontFamily = TerminalFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                letterSpacing = 0.15.sp,
                color = accent
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = timeFormatted,
                    fontFamily = TerminalFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 36.sp,
                    letterSpacing = 0.08.sp,
                    color = palette.textPrimary
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = sessionName.uppercase(),
                    fontFamily = TerminalFontFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 13.sp,
                    letterSpacing = 0.15.sp,
                    color = palette.textSecondary
                )
            }

            TerminalButton(
                text = "ВЕРНУТЬСЯ НА ГЛАВНЫЙ ЭКРАН",
                onClick = onClose,
                isPrimary = false,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
