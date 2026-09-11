package io.ronesec.android.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.ronesec.android.WattimApplication
import io.ronesec.android.data.PresentationSettings
import io.ronesec.android.platform.system.FocusForegroundService
import io.ronesec.android.platform.system.SettingsIntentAdapter
import io.ronesec.android.protection.InterventionCoordinator
import io.ronesec.android.ui.designsystem.WattimTheme
import io.ronesec.android.ui.designsystem.TerminalTab
import io.ronesec.android.ui.codes.CodesPanelState
import io.ronesec.android.ui.codes.toCodesPanelState
import io.ronesec.android.ui.locale.ProvideWattimLocale
import io.ronesec.domain.protection.ProtectionState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_OPEN_CODES = "io.ronesec.android.extra.OPEN_CODES"
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Notification permission granted or denied.
        // As per F22 and spec: denial leaves FGS eligible to run; no repeated prompt loop.
        val app = application as? WattimApplication
        app?.permissionMonitor?.refresh()
    }

    private var hasRequestedNotificationPermission = false

    private var activeRoute: AppRoute? = null
    private val isActivityResumed = MutableStateFlow(false)
    private val routeRequests = Channel<AppRoute>(capacity = Channel.CONFLATED)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // If launched as an empty host by createAndroidComposeRule, skip setContent so test rule can set it
        val isComposeTestRule = Thread.currentThread().stackTrace.any { element ->
            element.className.contains("AndroidComposeTestRule") ||
            element.className.contains("AndroidComposeUiTestEnvironment")
        }
        if (isComposeTestRule) {
            return
        }

        val app = applicationContext as? WattimApplication
        val permissionMonitor = app?.permissionMonitor
        val policyStore = app?.policyStore
        val statisticsStore = app?.statisticsStore
        val packageCatalog = app?.packageCatalog
        val wallClock = app?.wallClock
        val settingsAdapter = SettingsIntentAdapter(this)
        val activeCoordinator = app?.activeCoordinator
            ?: MutableStateFlow<InterventionCoordinator?>(null)
        val routeRequestFlow = routeRequests.receiveAsFlow()

        // Request POST_NOTIFICATIONS once on API 33+ if needed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasRequestedNotificationPermission) {
            hasRequestedNotificationPermission = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Restore saved route if available, otherwise determine based on permissions
        val restoredRoute = intent.codesRouteOrNull() ?: AppRoute.fromBundle(savedInstanceState)
        activeRoute = restoredRoute

        setContent {
            val fallbackSettingsFlow = remember { MutableStateFlow(PresentationSettings()) }
            val presentationSettings by (policyStore?.presentationSettings ?: fallbackSettingsFlow).collectAsState()
            val coordinator by activeCoordinator.collectAsState()
            val fallbackProtectionState = remember { MutableStateFlow<ProtectionState?>(null) }
            val protectionState by (coordinator?.protectionState ?: fallbackProtectionState).collectAsState()
            val activityResumed by isActivityResumed.collectAsState()
            val codesPanelState = protectionState.toCodesPanelState(activityResumed)

            ProvideWattimLocale(language = presentationSettings.language) {
                WattimTheme(themeId = presentationSettings.themeId) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        if (permissionMonitor != null) {
                            WattimNavHost(
                                permissionMonitor = permissionMonitor,
                                settingsAdapter = settingsAdapter,
                                initialRoute = restoredRoute,
                                policyStore = policyStore,
                                statisticsStore = statisticsStore,
                                packageCatalog = packageCatalog,
                                wallClock = wallClock,
                                audioDiagnostics = app.audioDiagnostics,
                                codesPanelState = codesPanelState,
                                onCodeVisible = { sessionId, cycle, requestRevision ->
                                    coordinator?.onCodePanelShown(sessionId, cycle, requestRevision)
                                },
                                routeRequests = routeRequestFlow,
                                onRouteChange = { activeRoute = it },
                                onStartFgs = { FocusForegroundService.start(this) }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isActivityResumed.value = true
        (applicationContext as? WattimApplication)?.permissionMonitor?.refresh()
    }

    override fun onPause() {
        isActivityResumed.value = false
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.codesRouteOrNull()?.let(routeRequests::trySend)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        activeRoute?.let { route ->
            outState.putAll(AppRoute.toBundle(route))
        }
    }


    private fun Intent.codesRouteOrNull(): AppRoute? =
        if (getBooleanExtra(EXTRA_OPEN_CODES, false)) {
            AppRoute.Main(TerminalTab.CODES)
        } else {
            null
        }
}
