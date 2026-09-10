package io.ronesec.android.ui.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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

/**
 * F74 Saved-session duration: 5/7/10/15 minutes, default 7.
 * Changes saved-time statistics only, never intervention duration.
 */
@Composable
fun SavedSessionDurationCard(
    selectedMinutes: Int,
    onSelectMinutes: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = WattimTheme.colors
    val typography = WattimTheme.typography

    val options = listOf(5, 7, 10, 15)
    val unitMin = stringResource(R.string.unit_min)

    TerminalCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.config_saved_session_title),
            fontFamily = typography.bodyMedium.fontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            letterSpacing = 0.1.sp,
            color = colors.textSecondary,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        Text(
            text = stringResource(R.string.config_saved_session_desc),
            fontFamily = typography.bodyMedium.fontFamily,
            fontSize = 11.sp,
            color = colors.textSecondary,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { minutes ->
                val isSelected = selectedMinutes == minutes
                TerminalButton(
                    text = "$minutes $unitMin",
                    onClick = { onSelectMinutes(minutes) },
                    isPrimary = isSelected,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
