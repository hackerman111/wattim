package io.ronesec.android.ui.intervention

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.ronesec.android.R
import io.ronesec.android.ui.designsystem.TerminalButton
import io.ronesec.android.ui.designsystem.WattimTheme

@Composable
fun CodeGateContent(
    targetName: String,
    generated: Boolean,
    length: Int,
    error: Boolean,
    emergencyCode: String?,
    onGenerate: () -> Unit,
    onSubmit: (String) -> Unit,
    onExit: () -> Unit,
    onEmergency: () -> Unit
) {
    var input by remember(generated) { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().background(WattimTheme.colors.background)
            .systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(targetName, style = WattimTheme.typography.titleLarge, color = WattimTheme.colors.textPrimary)
        Text(stringResource(R.string.code_gate_instruction), color = WattimTheme.colors.textSecondary)
        if (!generated) {
            TerminalButton(text = stringResource(R.string.code_generate), onClick = onGenerate, modifier = Modifier.fillMaxWidth())
        } else {
            DigitCodeInput(input, length, { input = it }, stringResource(R.string.code_enter))
            if (error) Text(stringResource(R.string.code_invalid), color = WattimTheme.colors.error)
            TerminalButton(text = stringResource(R.string.code_confirm), onClick = { onSubmit(input) }, modifier = Modifier.fillMaxWidth())
        }
        EmergencyCodeLabel(emergencyCode)
        TerminalButton(text = stringResource(R.string.action_emergency), onClick = onEmergency, modifier = Modifier.fillMaxWidth())
        TerminalButton(text = stringResource(R.string.action_exit), onClick = onExit, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun EmergencyCodeLabel(code: String?) {
    if (code != null) {
        Text(stringResource(R.string.code_emergency_display, code),
            style = WattimTheme.typography.bodyMedium, color = WattimTheme.colors.textPrimary)
    }
}
