package io.ronesec.android.ui.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import io.ronesec.android.ui.designsystem.TerminalCard
import io.ronesec.android.ui.designsystem.WattimTheme

/**
 * F78 Privacy UI: Static cards explaining 100% offline, local storage, no tracking, safe accessibility.
 */
@Composable
fun PrivacyCard(
    modifier: Modifier = Modifier
) {
    val colors = WattimTheme.colors
    val typography = WattimTheme.typography

    val items = listOf(
        R.string.config_privacy_offline to R.string.config_privacy_offline_desc,
        R.string.config_privacy_local to R.string.config_privacy_local_desc,
        R.string.config_privacy_telemetry to R.string.config_privacy_telemetry_desc,
        R.string.config_privacy_accessibility to R.string.config_privacy_accessibility_desc
    )

    TerminalCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.config_privacy_title),
            fontFamily = typography.bodyMedium.fontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            letterSpacing = 0.1.sp,
            color = colors.textSecondary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items.forEach { (titleRes, descRes) ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(titleRes),
                        fontFamily = typography.bodyMedium.fontFamily,
                        fontSize = 12.sp,
                        color = colors.textPrimary
                    )
                    Text(
                        text = stringResource(descRes),
                        fontFamily = typography.bodyMedium.fontFamily,
                        fontSize = 11.sp,
                        color = colors.textSecondary
                    )
                }
            }
        }
    }
}
