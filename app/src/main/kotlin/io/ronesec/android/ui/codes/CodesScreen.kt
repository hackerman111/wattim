package io.ronesec.android.ui.codes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import io.ronesec.android.R
import io.ronesec.android.ui.designsystem.TerminalCard
import io.ronesec.android.ui.designsystem.WattimTheme
import io.ronesec.domain.model.SessionId

@Composable
fun CodesScreen(
    state: CodesPanelState,
    onCodeVisible: (SessionId, Int, Long) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val colors = WattimTheme.colors
    val dimensions = WattimTheme.dimensions
    val typography = WattimTheme.typography

    LaunchedEffect(state) {
        if (state is CodesPanelState.Active) {
            onCodeVisible(state.sessionId, state.cycle, state.requestRevision)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(dimensions.space16),
        verticalArrangement = Arrangement.spacedBy(dimensions.space16)
    ) {
        Text(
            text = stringResource(R.string.codes_screen_title),
            style = typography.titleMedium,
            color = colors.accent,
            modifier = Modifier.semantics { heading() }
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            when (state) {
                CodesPanelState.Empty -> Text(
                    text = stringResource(R.string.codes_empty_message),
                    style = typography.bodyLarge,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center
                )

                is CodesPanelState.Active -> ActiveCodeCard(state)
            }
        }
    }
}

@Composable
private fun ActiveCodeCard(state: CodesPanelState.Active) {
    val colors = WattimTheme.colors
    val dimensions = WattimTheme.dimensions
    val typography = WattimTheme.typography
    val spokenCode = state.code.toCharArray().joinToString(separator = " ")

    TerminalCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(dimensions.space16)
        ) {
            Text(
                text = stringResource(R.string.codes_active_label, state.label),
                style = typography.bodyMedium,
                color = colors.textSecondary,
                textAlign = TextAlign.Center
            )
            Text(
                text = state.code,
                style = typography.displayLarge,
                color = colors.accent,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics {
                    contentDescription = spokenCode
                }
            )
        }
    }
}
