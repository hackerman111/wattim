package io.ronesec.android.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import io.ronesec.android.RonesecApplication
import io.ronesec.android.data.repository.RonesecRepository
import io.ronesec.android.domain.engine.RuleEngine
import io.ronesec.android.domain.model.AttemptOutcome
import io.ronesec.android.domain.model.Decision
import io.ronesec.android.domain.model.InterventionConfig
import io.ronesec.android.domain.util.TimeFormatUtils
import io.ronesec.android.overlay.OverlayController
import io.ronesec.android.ui.theme.AppTheme
import io.ronesec.android.ui.theme.TerminalAccent
import io.ronesec.android.ui.viewmodel.formatSavedTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

class AppMonitorService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val ruleEngine = RuleEngine()
    private lateinit var overlayController: OverlayController

    private var currentForegroundPackage: String? = null
    private var currentTheme: AppTheme = AppTheme.NORD
    private var reinterventionJob: Job? = null

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                val currentPkg = currentForegroundPackage
                if (currentPkg != null && currentPkg != packageName) {
                    cancelReintervention(currentPkg)
                    val repository = (application as RonesecApplication).repository
                    repository.recordExit(currentPkg, Instant.now())
                    currentForegroundPackage = null
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        overlayController = OverlayController(this)

        try {
            ContextCompat.registerReceiver(
                this,
                screenOffReceiver,
                IntentFilter(Intent.ACTION_SCREEN_OFF),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } catch (e: Exception) {
            // Fallback for devices where receiver flag is not strictly required
            registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        }

        val repository = (application as RonesecApplication).repository
        scope.launch {
            repository.getSettingFlow("app_theme").collectLatest { themeId ->
                currentTheme = AppTheme.fromId(themeId)
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

        // If switching away from a protected app or to another app, cancel re-intervention timer & record exit
        if (previousPackage != null && previousPackage != rawPackage && previousPackage != packageName) {
            cancelReintervention(previousPackage)
            repository.recordExit(previousPackage, now)
        }

        val state = repository.getHotRuntimeState()
        scope.launch {
            val target = state.targets[rawPackage]
            val count = if (target != null && target.enabled && target.intervention.exponentialGrowthEnabled) {
                withContext(Dispatchers.IO) {
                    repository.getRecentAttemptsCount(rawPackage, target.intervention.growthPeriodMinutes)
                }
            } else {
                0
            }

            if (currentForegroundPackage != rawPackage) {
                return@launch
            }

            val decision = ruleEngine.evaluate(rawPackage, now, state, recentAttemptsCount = count)

            when (decision) {
                is Decision.Allow -> {
                    if (overlayController.isShowing) {
                        overlayController.dismiss()
                    }
                    // If returning to a protected target app within grace, re-grant access & reschedule reintervention
                    if (target != null && target.enabled) {
                        scope.launch(Dispatchers.IO) {
                            repository.grantAccess(rawPackage, target.intervention.reinterventionMs)
                        }
                        scheduleReintervention(rawPackage, target.intervention)
                    }
                }

                is Decision.Block -> {
                    val appLabel = getAppLabel(rawPackage)
                    overlayController.showBlock(
                        sessionName = appLabel,
                        until = decision.until,
                        theme = currentTheme,
                        onClose = {
                            performGlobalAction(GLOBAL_ACTION_HOME)
                            scope.launch(Dispatchers.IO) {
                                repository.recordAttempt(rawPackage, AttemptOutcome.BLOCKED, now)
                            }
                            currentForegroundPackage = null
                        }
                    )
                }

                is Decision.Intervention -> {
                    val appLabel = getAppLabel(rawPackage)
                    val savedText = getSavedTimeTextIfEnabled(repository)

                    overlayController.showIntervention(
                        targetAppName = appLabel,
                        config = decision.config,
                        savedTimeText = savedText,
                        theme = currentTheme,
                        onEmergencyAccess = { durationMs, disableTarget ->
                            scope.launch(Dispatchers.IO) {
                                if (disableTarget) {
                                    repository.updateTargetEnabled(rawPackage, false)
                                    repository.grantAccess(rawPackage, null)
                                } else {
                                    val grantDuration = durationMs ?: target?.intervention?.reinterventionMs
                                    repository.grantAccess(rawPackage, grantDuration)
                                }
                                repository.recordAttempt(rawPackage, AttemptOutcome.CONTINUED, now)
                            }
                            if (durationMs != null) {
                                cancelReintervention(rawPackage)
                            } else if (!disableTarget) {
                                scheduleReintervention(rawPackage, decision.config, 1)
                            }
                        },
                        onClose = {
                            performGlobalAction(GLOBAL_ACTION_HOME)
                            cancelReintervention(rawPackage)
                            scope.launch(Dispatchers.IO) {
                                repository.recordAttempt(rawPackage, AttemptOutcome.ABANDONED, now)
                                repository.revokeAccess(rawPackage)
                            }
                            repository.recordExit(rawPackage, now)
                            currentForegroundPackage = null
                        },
                        onContinue = {
                            scope.launch(Dispatchers.IO) {
                                repository.grantAccess(rawPackage, decision.config.reinterventionMs)
                                repository.recordAttempt(rawPackage, AttemptOutcome.CONTINUED, now)
                            }
                            scheduleReintervention(rawPackage, decision.config)
                        }
                    )
                }
            }
        }
    }

    private suspend fun getSavedTimeTextIfEnabled(repository: RonesecRepository): String? {
        val showStatsSetting = withContext(Dispatchers.IO) {
            repository.getSetting("show_saved_time_stats")
        }
        val showStats = showStatsSetting == null || showStatsSetting == "true"
        if (!showStats) return null

        val allAvoided = withContext(Dispatchers.IO) { repository.getAvoidedCountAllTime() }
        val sessionMinutesSetting = withContext(Dispatchers.IO) { repository.getSetting("session_minutes") }
        val sessionMins = sessionMinutesSetting?.toIntOrNull() ?: 7
        val totalSavedMinutes = allAvoided.toLong() * sessionMins
        return if (totalSavedMinutes > 0) {
            "Вы уже сберегли ${formatSavedTime(totalSavedMinutes)}"
        } else {
            null
        }
    }

    private fun scheduleReintervention(targetPackage: String, config: InterventionConfig, cycle: Int = 1) {
        reinterventionJob?.cancel()
        val delayMs = config.reinterventionMs ?: return
        if (delayMs <= 0) return

        reinterventionJob = scope.launch {
            delay(delayMs)
            if (currentForegroundPackage == targetPackage && !overlayController.isShowing) {
                triggerReintervention(targetPackage, config, cycle)
            }
        }
    }

    private fun cancelReintervention(targetPackage: String? = null) {
        reinterventionJob?.cancel()
        reinterventionJob = null
    }

    private fun triggerReintervention(targetPackage: String, config: InterventionConfig, cycle: Int) {
        val appLabel = getAppLabel(targetPackage)
        val totalSpentMs = (config.reinterventionMs ?: 0L) * cycle
        val timeStr = TimeFormatUtils.formatDurationRu(totalSpentMs)
        val phrase = "Вы уже провели в $appLabel $timeStr.\nХотите продолжить?"

        // If exponential growth is enabled, scale duration for this re-intervention:
        val nextDurationMs = if (config.exponentialGrowthEnabled) {
            (config.durationMs * (1.0 + config.growthPercent / 100.0))
                .toLong()
                .coerceIn(1_000L, 300_000L)
        } else {
            config.durationMs
        }
        val reinterventionConfig = config.copy(phrase = phrase, durationMs = nextDurationMs)

        val repository = (application as RonesecApplication).repository
        scope.launch {
            val savedText = getSavedTimeTextIfEnabled(repository)

            overlayController.showIntervention(
                targetAppName = appLabel,
                config = reinterventionConfig,
                savedTimeText = savedText,
                theme = currentTheme,
                onEmergencyAccess = { durationMs, disableTarget ->
                    val emergencyTime = Instant.now()
                    scope.launch(Dispatchers.IO) {
                        if (disableTarget) {
                            repository.updateTargetEnabled(targetPackage, false)
                            repository.grantAccess(targetPackage, null)
                        } else {
                            val grantDuration = durationMs ?: config.reinterventionMs
                            repository.grantAccess(targetPackage, grantDuration)
                        }
                        repository.recordAttempt(targetPackage, AttemptOutcome.CONTINUED, emergencyTime)
                    }
                    if (durationMs != null) {
                        cancelReintervention(targetPackage)
                    } else if (!disableTarget) {
                        scheduleReintervention(targetPackage, config, 1)
                    }
                },
                onClose = {
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    cancelReintervention(targetPackage)
                    scope.launch(Dispatchers.IO) {
                        repository.recordAttempt(targetPackage, AttemptOutcome.ABANDONED, Instant.now())
                        repository.revokeAccess(targetPackage)
                    }
                    repository.recordExit(targetPackage, Instant.now())
                    currentForegroundPackage = null
                },
                onContinue = {
                    val continueTime = Instant.now()
                    scope.launch(Dispatchers.IO) {
                        repository.grantAccess(targetPackage, config.reinterventionMs)
                        repository.recordAttempt(targetPackage, AttemptOutcome.CONTINUED, continueTime)
                    }
                    scheduleReintervention(targetPackage, reinterventionConfig, cycle + 1)
                }
            )
        }
    }

    override fun onInterrupt() {
        cancelReintervention()
        overlayController.dismiss()
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(screenOffReceiver)
        } catch (e: Exception) {
            // Ignore if not registered
        }
        cancelReintervention()
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
