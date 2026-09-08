package io.ronesec.android.ui.screens.target

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ronesec.android.ui.components.TerminalBadge
import io.ronesec.android.ui.components.TerminalCard
import io.ronesec.android.ui.components.TerminalInputField
import io.ronesec.android.ui.i18n.LocalAppStrings
import io.ronesec.android.ui.theme.LocalAppPalette
import io.ronesec.android.ui.theme.TerminalFontFamily

@Composable
fun TargetStatusCard(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    val strings = LocalAppStrings.current
    val accent = palette.accent

    TerminalCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = strings.targetProtectionStatus,
                    fontFamily = TerminalFontFamily,
                    fontSize = 11.sp,
                    color = palette.textSecondary
                )
                Text(
                    text = if (enabled) strings.targetStatusActive else strings.targetStatusDisabled,
                    fontFamily = TerminalFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = if (enabled) accent else palette.textSecondary
                )
            }

            TerminalBadge(
                text = if (enabled) strings.onLabel else strings.offLabel,
                isActive = enabled,
                modifier = Modifier.clickable { onEnabledChange(!enabled) }
            )
        }
    }
}

@Composable
fun TargetPhraseCard(
    phrase: String,
    onPhraseChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    val strings = LocalAppStrings.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    TerminalCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = strings.targetMindfulnessPhrase,
            fontFamily = TerminalFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            color = palette.textSecondary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        TerminalInputField(
            value = phrase,
            onValueChange = onPhraseChange,
            maxLength = 80,
            maxLines = 3,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                focusManager.clearFocus()
                keyboardController?.hide()
            })
        )
    }
}
