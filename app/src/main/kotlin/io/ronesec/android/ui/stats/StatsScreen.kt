package io.ronesec.android.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ronesec.android.R
import io.ronesec.android.ui.designsystem.WattimTheme

@Composable
fun StatsScreen(
    uiState: StatsUiState,
    onVisible: () -> Unit,
    onInvisible: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = WattimTheme.colors
    val typography = WattimTheme.typography

    DisposableEffect(Unit) {
        onVisible()
        onDispose {
            onInvisible()
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        val compactHeight = maxHeight < 650.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .then(
                    if (compactHeight) Modifier.verticalScroll(rememberScrollState())
                    else Modifier
                )
        ) {
        Text(
            text = stringResource(R.string.stats_screen_title),
            fontFamily = typography.bodyMedium.fontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            letterSpacing = 0.15.sp,
            color = colors.accent
        )

        Spacer(modifier = Modifier.height(16.dp))

        AllTimeSavedLifeCard(
            allTimeDuration = uiState.allTimeSavedDuration,
            avoidedCount = uiState.allTimeAvoidedCount,
            savedTodayDuration = uiState.savedTodayDuration,
            multiplierMinutes = uiState.multiplierMinutes
        )

        Spacer(modifier = Modifier.height(16.dp))

        TodaySummaryCard(
            totalAttempts = uiState.todayTotalAttempts,
            continuedCount = uiState.todayContinuedCount,
            closedCount = uiState.todayClosedCount,
            avoidedPercent = uiState.todayAvoidedPercent
        )

        Spacer(modifier = Modifier.height(20.dp))

        AppStatsTable(
            appStats = uiState.appStatsToday,
            scrollRows = !compactHeight,
            modifier = if (compactHeight) Modifier else Modifier.weight(1f)
        )
        }
    }
}
