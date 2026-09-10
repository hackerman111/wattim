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
 * F72 Language selection: AUTO / ENGLISH / РУССКИЙ.
 */
@Composable
fun LanguageSelectorCard(
    selectedLanguage: String,
    onSelectLanguage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = WattimTheme.colors
    val typography = WattimTheme.typography

    val options = listOf(
        "AUTO" to stringResource(R.string.lang_auto),
        "ENGLISH" to stringResource(R.string.lang_english),
        "РУССКИЙ" to stringResource(R.string.lang_russian)
    )

    TerminalCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.config_language_title),
            fontFamily = typography.bodyMedium.fontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            letterSpacing = 0.1.sp,
            color = colors.textSecondary,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { (languageCode, label) ->
                val isSelected = selectedLanguage.equals(languageCode, ignoreCase = true)
                TerminalButton(
                    text = label,
                    onClick = { onSelectLanguage(languageCode) },
                    isPrimary = isSelected,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
