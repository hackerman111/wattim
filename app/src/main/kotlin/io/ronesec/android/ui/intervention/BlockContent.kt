package io.ronesec.android.ui.intervention

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ronesec.android.R
import io.ronesec.android.ui.designsystem.TerminalButton
import io.ronesec.android.ui.designsystem.WattimTheme
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * Hard-block overlay UI matching app_1 BlockOverlayView.
 * Fullscreen screen with:
 * - Title: ПРИЛОЖЕНИЕ ЗАБЛОКИРОВАНО / APPLICATION BLOCKED (17sp Bold accent)
 * - Large countdown: HH:MM:SS (36sp Bold textPrimary)
 * - Session/Target name: 13sp uppercase textSecondary
 * - Full-width action button: ВЕРНУТЬСЯ НА ГЛАВНЫЙ ЭКРАН / RETURN TO HOME SCREEN
 */
@Composable
fun BlockContent(
    targetName: String,
    until: Instant?,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    now: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.systemDefault()
) {
    val colors = WattimTheme.colors
    val typography = WattimTheme.typography

    var remainingSeconds by remember(until) {
        val rem = if (until != null) Duration.between(now, until).seconds else 0L
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
            .background(colors.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.block_overlay_title),
                fontFamily = typography.bodyMedium.fontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                letterSpacing = 0.15.sp,
                color = colors.accent
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = timeFormatted,
                    fontFamily = typography.bodyMedium.fontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 36.sp,
                    letterSpacing = 0.08.sp,
                    color = colors.textPrimary
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = targetName.uppercase(),
                    fontFamily = typography.bodyMedium.fontFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 13.sp,
                    letterSpacing = 0.15.sp,
                    color = colors.textSecondary
                )
            }

            TerminalButton(
                text = stringResource(R.string.block_overlay_close),
                onClick = onExit,
                isPrimary = false,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
