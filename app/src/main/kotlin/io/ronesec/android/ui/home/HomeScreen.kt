package io.ronesec.android.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.ronesec.android.R
import io.ronesec.android.ui.designsystem.WattimTheme
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onVisible: () -> Unit,
    onInvisible: () -> Unit,
    onOpenDetail: (packageName: String) -> Unit,
    onToggleEnabled: (packageName: String) -> Unit,
    onSetPause: (durationMillis: Long?) -> Unit,
    onResumePause: () -> Unit,
    onOpenAddApp: () -> Unit,
    onDismissAddApp: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSelectAppToAdd: (io.ronesec.android.platform.system.PackageAppEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = WattimTheme.colors
    val typography = WattimTheme.typography

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> onVisible()
                Lifecycle.Event.ON_PAUSE -> onInvisible()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onVisible()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            onInvisible()
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        val compactHeight = maxHeight < 700.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .then(
                    if (compactHeight) Modifier.verticalScroll(rememberScrollState())
                    else Modifier
                )
        ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.app_name),
                fontFamily = typography.bodyMedium.fontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                letterSpacing = 0.2.sp,
                color = colors.accent
            )
            Text(
                text = uiState.wallClockTime,
                fontFamily = typography.bodyMedium.fontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                color = colors.textSecondary
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        ProtectedAppsSection(
            apps = uiState.apps,
            onOpenDetail = onOpenDetail,
            onToggleEnabled = onToggleEnabled,
            scrollApps = !compactHeight,
            modifier = if (compactHeight) Modifier else Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        TodayStatsHeroCard(stats = uiState.todayHero)

        Spacer(modifier = Modifier.height(16.dp))

        GlobalPauseCard(
            pauseState = uiState.pauseState,
            onSetPause = onSetPause,
            onResume = onResumePause
        )

        Spacer(modifier = Modifier.height(16.dp))

        io.ronesec.android.ui.designsystem.TerminalButton(
            text = stringResource(R.string.action_add_app),
            onClick = onOpenAddApp,
            isPrimary = true,
            modifier = Modifier.fillMaxWidth()
        )
        }
    }

    // Add App Dialog (F46, F47)
    AddAppDialog(
        state = uiState.addAppDialog,
        onQueryChange = onSearchQueryChange,
        onSelectApp = onSelectAppToAdd,
        onDismiss = onDismissAddApp
    )
}
