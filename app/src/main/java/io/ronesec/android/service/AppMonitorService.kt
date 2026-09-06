package io.ronesec.android.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityEvent
import io.ronesec.android.RonesecApplication
import io.ronesec.android.domain.engine.RuleEngine
import io.ronesec.android.domain.model.AttemptOutcome
import io.ronesec.android.domain.model.Decision
import io.ronesec.android.overlay.OverlayController
import io.ronesec.android.ui.theme.TerminalAccent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.Instant

class AppMonitorService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val ruleEngine = RuleEngine()
    private lateinit var overlayController: OverlayController

    private var currentForegroundPackage: String? = null
    private var currentAccent: TerminalAccent = TerminalAccent.CYAN

    override fun onCreate() {
        super.onCreate()
        overlayController = OverlayController(this)

        val repository = (application as RonesecApplication).repository
        scope.launch {
            repository.getSettingFlow("accent_color").collectLatest { accentName ->
                currentAccent = TerminalAccent.fromName(accentName)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Ensure Foreground Service is also running for persistent OOM priority
        FocusForegroundService.start(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val eventType = event.eventType
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) {
            return
        }

        val rawPackage = event.packageName?.toString() ?: return

        // Ignore our own app and system overlays
        if (rawPackage == packageName ||
            rawPackage == "com.android.systemui" ||
            rawPackage.contains("inputmethod")
        ) {
            return
        }

        if (rawPackage == currentForegroundPackage) {
            return
        }

        val previousPackage = currentForegroundPackage
        currentForegroundPackage = rawPackage
        val now = Instant.now()

        val repository = (application as RonesecApplication).repository

        // Record exit time for previous app to measure Quick Return Grace
        if (previousPackage != null && previousPackage != packageName) {
            repository.recordExit(previousPackage, now)
        }

        val state = repository.getHotRuntimeState()
        val decision = ruleEngine.evaluate(rawPackage, now, state)

        when (decision) {
            is Decision.Allow -> {
                if (overlayController.isShowing) {
                    overlayController.dismiss()
                }
            }

            is Decision.Block -> {
                val appLabel = getAppLabel(rawPackage)
                overlayController.showBlock(
                    sessionName = appLabel,
                    until = decision.until,
                    accent = currentAccent,
                    onClose = {
                        performGlobalAction(GLOBAL_ACTION_HOME)
                        scope.launch(Dispatchers.IO) {
                            repository.recordAttempt(rawPackage, AttemptOutcome.BLOCKED, now)
                        }
                    }
                )
            }

            is Decision.Intervention -> {
                val appLabel = getAppLabel(rawPackage)
                overlayController.showIntervention(
                    targetAppName = appLabel,
                    config = decision.config,
                    accent = currentAccent,
                    onClose = {
                        performGlobalAction(GLOBAL_ACTION_HOME)
                        scope.launch(Dispatchers.IO) {
                            repository.recordAttempt(rawPackage, AttemptOutcome.ABANDONED, now)
                        }
                    },
                    onContinue = {
                        scope.launch(Dispatchers.IO) {
                            repository.grantAccess(rawPackage, decision.config.reinterventionMs)
                            repository.recordAttempt(rawPackage, AttemptOutcome.CONTINUED, now)
                        }
                    }
                )
            }
        }
    }

    override fun onInterrupt() {
        overlayController.dismiss()
    }

    override fun onDestroy() {
        overlayController.dismiss()
        scope.cancel()
        super.onDestroy()
    }

    private fun getAppLabel(pkg: String): String {
        return try {
            val appInfo = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            pkg.substringAfterLast('.')
        }
    }
}
