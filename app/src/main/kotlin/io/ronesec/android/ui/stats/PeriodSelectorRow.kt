package io.ronesec.android.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.ronesec.android.R
import io.ronesec.android.ui.designsystem.TerminalButton
import io.ronesec.android.ui.designsystem.TerminalButtonVariant
import io.ronesec.domain.model.StatsPeriod

@Composable
fun PeriodSelectorRow(
    selectedPeriod: StatsPeriod,
    onPeriodSelect: (StatsPeriod) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val periods = listOf(
            StatsPeriod.TODAY to stringResource(R.string.stats_period_today),
            StatsPeriod.WEEK to stringResource(R.string.stats_period_week),
            StatsPeriod.MONTH to stringResource(R.string.stats_period_month),
            StatsPeriod.ALL_TIME to stringResource(R.string.stats_period_all_time)
        )

        for ((period, label) in periods) {
            val isSelected = selectedPeriod == period
            TerminalButton(
                text = label,
                onClick = { onPeriodSelect(period) },
                variant = if (isSelected) TerminalButtonVariant.PRIMARY else TerminalButtonVariant.SECONDARY,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
