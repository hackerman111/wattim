package io.ronesec.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.ronesec.android.R
import io.ronesec.android.ui.designsystem.TerminalBadge
import io.ronesec.android.ui.designsystem.TerminalButton
import io.ronesec.android.ui.designsystem.TerminalButtonVariant
import io.ronesec.android.ui.designsystem.TerminalCard
import io.ronesec.android.ui.designsystem.TerminalTab
import io.ronesec.android.ui.designsystem.WattimTheme

@Composable
fun DetailShell(
    packageName: String,
    originTab: TerminalTab,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = WattimTheme.colors
    val dimensions = WattimTheme.dimensions
    val typography = WattimTheme.typography

    BackHandler(onBack = onBack)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(dimensions.space16),
        verticalArrangement = Arrangement.spacedBy(dimensions.space16)
    ) {
        // Detail Header: Return button + Origin tab indicator
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TerminalButton(
                text = stringResource(R.string.action_return),
                onClick = onBack,
                variant = TerminalButtonVariant.SECONDARY
            )

            TerminalBadge(
                text = originTab.name,
                isActive = true
            )
        }

        Spacer(modifier = Modifier.height(dimensions.space8))

        TerminalCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(dimensions.space12)) {
                Text(
                    text = packageName,
                    style = typography.titleLarge,
                    color = colors.accent
                )
                Text(
                    text = stringResource(R.string.status_protected),
                    style = typography.bodyMedium,
                    color = colors.textPrimary
                )
            }
        }
    }
}
