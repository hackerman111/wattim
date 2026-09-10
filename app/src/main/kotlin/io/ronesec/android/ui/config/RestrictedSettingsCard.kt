package io.ronesec.android.ui.config

import androidx.compose.foundation.BorderStroke
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
import io.ronesec.android.ui.designsystem.TerminalButton
import io.ronesec.android.ui.designsystem.TerminalButtonVariant
import io.ronesec.android.ui.designsystem.TerminalCard
import io.ronesec.android.ui.designsystem.WattimTheme

/**
 * F77 Restricted-settings help: Android 13+ bypass guidance and Open App Info button.
 */
@Composable
fun RestrictedSettingsCard(
    onOpenAppInfo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = WattimTheme.colors
    val typography = WattimTheme.typography

    TerminalCard(
        modifier = modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.5f))
    ) {
        Text(
            text = stringResource(R.string.restricted_settings_title),
            fontFamily = typography.bodyMedium.fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            letterSpacing = 0.1.sp,
            color = colors.accent,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        Text(
            text = stringResource(R.string.restricted_settings_desc),
            fontFamily = typography.bodyMedium.fontFamily,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            color = colors.textSecondary,
            modifier = Modifier.padding(bottom = 10.dp)
        )

        TerminalButton(
            text = stringResource(R.string.action_open_app_info),
            onClick = onOpenAppInfo,
            variant = TerminalButtonVariant.SECONDARY,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
