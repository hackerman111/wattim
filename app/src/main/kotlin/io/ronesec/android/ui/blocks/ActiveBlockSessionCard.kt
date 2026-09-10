package io.ronesec.android.ui.blocks

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ronesec.android.R
import io.ronesec.android.ui.designsystem.TerminalButton
import io.ronesec.android.ui.designsystem.TerminalCard
import io.ronesec.android.ui.designsystem.WattimTheme
import java.util.Locale

@Composable
fun ActiveBlockSessionCard(
    session: ActiveBlockSessionUiModel,
    onStop: (sessionId: String) -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = WattimTheme.colors
    val typography = WattimTheme.typography

    TerminalCard(modifier = modifier.fillMaxWidth()) {
        val hours = session.remainingSeconds / 3600
        val mins = (session.remainingSeconds % 3600) / 60
        val secs = session.remainingSeconds % 60
        val timeStr = String.format(Locale.US, "%02d:%02d:%02d", hours, mins, secs)

        Text(
            text = stringResource(R.string.active_session_title),
            fontFamily = typography.bodyMedium.fontFamily,
            fontSize = 12.sp,
            letterSpacing = 0.1.sp,
            color = colors.accent
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = timeStr,
            fontFamily = typography.bodyMedium.fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp,
            color = colors.textPrimary
        )

        Spacer(modifier = Modifier.height(8.dp))

        val appCount = session.targetPackages.size
        Text(
            text = stringResource(R.string.apps_count_format, appCount),
            fontFamily = typography.bodyMedium.fontFamily,
            fontSize = 12.sp,
            color = colors.textSecondary
        )

        Spacer(modifier = Modifier.height(12.dp))

        TerminalButton(
            text = stringResource(R.string.action_stop),
            onClick = { onStop(session.id) },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
