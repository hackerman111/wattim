package io.ronesec.android.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import io.ronesec.android.RonesecApplication
import io.ronesec.android.domain.protection.AccessibilitySubscriptionController
import io.ronesec.android.domain.protection.AudioGuard
import io.ronesec.android.domain.protection.ForegroundTracker
import io.ronesec.android.domain.protection.InterventionCoordinator
import io.ronesec.android.domain.protection.ProtectionEffect
import io.ronesec.android.domain.protection.ProtectionEvent
import io.ronesec.android.domain.protection.SessionId
import io.ronesec.android.domain.protection.SystemAudioGuard
import io.ronesec.android.domain.protection.TemporalBoundaryScheduler
import io.ronesec.android.domain.protection.UserProtectionAction
import io.ronesec.android.overlay.OverlayHost
import io.ronesec.android.ui.i18n.AppLanguage
import io.ronesec.android.ui.i18n.resolveAppStrings
import io.ronesec.android.ui.theme.AppTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AppMonitorService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val eventChannel = Channel<ProtectionEvent>(Channel.UNLIMITED)

    private lateinit var audioGuard: AudioGuard
    private lateinit var overlayHost: OverlayHost
    private lateinit var temporalBoundaryScheduler: TemporalBoundaryScheduler
    private lateinit var foregroundTracker: ForegroundTracker
    private lateinit var subscriptionController: AccessibilitySubscriptionController
    private lateinit var coordinator: InterventionCoordinator

    private var currentTheme: AppTheme = AppTheme.NORD
    private var currentLanguage: AppLanguage = AppLanguage.SYSTEM

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                eventChannel.trySend(ProtectionEvent.ScreenOff)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val repository = (application as RonesecApplication).repository

        audioGuard = SystemAudioGuard(this, scope)
        overlayHost = OverlayHost(this)
        temporalBoundaryScheduler = TemporalBoundaryScheduler(scope) { sessionId, pkg, type, timestamp ->
            eventChannel.trySend(ProtectionEvent.TemporalBoundaryReached(sessionId, pkg, type, timestamp))
        }

        coordinator = InterventionCoordinator(
            appLabelResolver = { getAppLabel(it) },
            savedTimeTextResolver = { null },
            onEffect = { executeEffect(it) }
        )

        foregroundTracker = ForegroundTracker(
            ownPackageName = packageName,
            isAdditionalIgnoredPackage = { pkg -> pkg == getCurrentInputMethodPackage() }
        ) { pkg, time ->
            eventChannel.trySend(ProtectionEvent.ForegroundChanged(pkg, time))
        }

        subscriptionController = AccessibilitySubscriptionController(this)

        try {
            ContextCompat.registerReceiver(
                this,
                screenOffReceiver,
                IntentFilter(Intent.ACTION_SCREEN_OFF),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } catch (_: Exception) {
            registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        }

        scope.launch {
            for (event in eventChannel) {
                coordinator.processEvent(event)
            }
        }

        scope.launch {
            repository.runtimeState.collectLatest { state ->
                eventChannel.trySend(ProtectionEvent.PolicySnapshotUpdated(state))
            }
        }

        scope.launch {
            repository.getSettingFlow("app_theme").collectLatest { themeId ->
                currentTheme = AppTheme.fromId(themeId)
            }
        }
        scope.launch {
            repository.getSettingFlow("app_language").collectLatest { langCode ->
                currentLanguage = AppLanguage.fromCode(langCode)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        FocusForegroundService.start(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        foregroundTracker.onAccessibilityEvent(event)
    }

    private fun executeEffect(effect: ProtectionEffect) {
        val repository = (application as RonesecApplication).repository
        val appStrings = resolveAppStrings(currentLanguage)

        when (effect) {
            is ProtectionEffect.ShowInterventionOverlay -> {
                overlayHost.showIntervention(
                    sessionId = effect.sessionId.value,
                    targetAppName = effect.appLabel,
                    config = effect.config,
                    savedTimeText = effect.savedTimeText,
                    theme = currentTheme,
                    appStrings = appStrings,
                    onEmergencyAccess = { durationMs, disableTarget ->
                        eventChannel.trySend(
                            ProtectionEvent.UserAction(
                                effect.sessionId,
                                effect.targetPackage,
                                UserProtectionAction.EmergencyAccess(durationMs, disableTarget)
                            )
                        )
                    },
                    onClose = {
                        eventChannel.trySend(
                            ProtectionEvent.UserAction(
                                effect.sessionId,
                                effect.targetPackage,
                                UserProtectionAction.Close
                            )
                        )
                    },
                    onContinue = {
                        eventChannel.trySend(
                            ProtectionEvent.UserAction(
                                effect.sessionId,
                                effect.targetPackage,
                                UserProtectionAction.Continue
                            )
                        )
                    }
                )
            }

            is ProtectionEffect.ShowBlockOverlay -> {
                overlayHost.showBlock(
                    sessionId = effect.sessionId.value,
                    sessionName = effect.sessionName,
                    until = effect.until,
                    theme = currentTheme,
                    appStrings = appStrings,
                    onClose = {
                        eventChannel.trySend(
                            ProtectionEvent.UserAction(
                                effect.sessionId,
                                effect.targetPackage,
                                UserProtectionAction.Close
                            )
                        )
                    }
                )
            }

            is ProtectionEffect.DismissOverlay -> overlayHost.dismiss(effect.sessionId)
            is ProtectionEffect.AcquireAudio -> audioGuard.acquire(effect.sessionId)
            is ProtectionEffect.ReleaseAudio -> audioGuard.release(effect.sessionId)
            is ProtectionEffect.ScheduleBoundary -> temporalBoundaryScheduler.schedule(
                effect.sessionId,
                effect.targetPackage,
                effect.boundaryType,
                effect.delayMs
            )
            is ProtectionEffect.CancelBoundary -> temporalBoundaryScheduler.cancel(effect.sessionId)
            is ProtectionEffect.PerformGlobalHome -> performGlobalAction(GLOBAL_ACTION_HOME)

            is ProtectionEffect.PersistAttempt -> scope.launch(Dispatchers.IO) {
                repository.recordAttempt(effect.targetPackage, effect.outcome, effect.timestamp)
            }
            is ProtectionEffect.PersistGrant -> scope.launch(Dispatchers.IO) {
                repository.grantAccess(effect.targetPackage, effect.durationMs, effect.sessionId.value)
            }
            is ProtectionEffect.PersistRevoke -> scope.launch(Dispatchers.IO) {
                repository.revokeAccess(effect.targetPackage, effect.sessionId.value)
            }
            is ProtectionEffect.PersistTargetDisabled -> scope.launch(Dispatchers.IO) {
                repository.updateTargetEnabled(effect.targetPackage, false)
                repository.revokeAccess(effect.targetPackage)
            }
            is ProtectionEffect.UpdateAdaptiveSubscription -> subscriptionController.updateSubscription(
                effect.targetPackages,
                effect.isTargetActive
            )
        }
    }

    override fun onInterrupt() {
        eventChannel.trySend(ProtectionEvent.ServiceInterrupted)
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(screenOffReceiver)
        } catch (_: Exception) {}
        eventChannel.close()
        overlayHost.dismiss()
        audioGuard.release(SessionId.NONE)
        scope.cancel()
        super.onDestroy()
    }

    private fun getCurrentInputMethodPackage(): String? {
        val setting = try {
            Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        } catch (_: Exception) {
            null
        }
        return setting?.substringBefore('/')?.takeIf { it.isNotBlank() }
    }

    private fun getAppLabel(pkg: String): String {
        return try {
            val appInfo = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            pkg.substringAfterLast('.')
        }
    }
}
