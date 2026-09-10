package io.ronesec.android.platform.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.InputMethodManager
import io.ronesec.android.WattimApplication
import io.ronesec.android.platform.audio.AndroidAudioDeviceAdapter
import io.ronesec.android.platform.audio.AndroidMediaSessionPauseController
import io.ronesec.android.platform.audio.AudioGuard
import io.ronesec.android.platform.audio.BoundedPauseFallbackStrategy
import io.ronesec.android.platform.overlay.AndroidWindowManagerAdapter
import io.ronesec.android.platform.overlay.OverlayActionDispatcher
import io.ronesec.android.platform.overlay.OverlayHost
import io.ronesec.android.platform.overlay.OverlayHostListener
import io.ronesec.android.platform.overlay.OverlayPresenter
import io.ronesec.android.platform.time.TemporalBoundaryScheduler
import io.ronesec.android.protection.AndroidHomePort
import io.ronesec.android.protection.EffectExecutor
import io.ronesec.android.protection.ForegroundResyncPort
import io.ronesec.android.protection.InterventionCoordinator
import io.ronesec.android.protection.ProtectionEventJournal
import io.ronesec.android.ui.intervention.OverlayRootContent
import io.ronesec.domain.model.SessionId
import io.ronesec.domain.protection.ProtectionEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Android Accessibility service callback adapter.
 * Lifetime of OS Accessibility connection; owns one coordinator and adapter set.
 * Emits Connected/Interrupted/Disconnected, copied foreground, and screen events.
 * Satisfies I1, I4, F24, F25, F29, F30, Section 6.
 */
class AppMonitorService : AccessibilityService(), ForegroundResyncPort {

    companion object {
        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

        private var connectionGenerationCounter = 0L
    }

    private var serviceScope: CoroutineScope? = null
    var coordinator: InterventionCoordinator? = null
        private set

    var overlayHost: OverlayHost? = null
        internal set
    var audioGuard: AudioGuard? = null
        internal set

    private var cachedImePackages: Set<String> = emptySet()
    private var lastImeQueryUptimeMs: Long = 0L

    internal fun queryEnabledImePackages(): Set<String> {
        val now = SystemClock.uptimeMillis()
        if (now - lastImeQueryUptimeMs < 30_000L && cachedImePackages.isNotEmpty()) {
            return cachedImePackages
        }
        return try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            val packages = imm?.enabledInputMethodList?.mapNotNull { it.packageName }?.toSet() ?: emptySet()
            if (packages.isNotEmpty()) {
                cachedImePackages = packages
                lastImeQueryUptimeMs = now
            }
            packages
        } catch (_: Exception) {
            cachedImePackages
        }
    }

    private val foregroundTracker = ForegroundTracker(
        imePackageProvider = { queryEnabledImePackages() }
    )
    private val subscriptionController = SubscriptionController(object : AccessibilityServiceConfigAdapter {
        override fun getServiceInfo(): AccessibilityServiceInfo? = this@AppMonitorService.serviceInfo
        override fun setServiceInfo(info: AccessibilityServiceInfo) {
            this@AppMonitorService.serviceInfo = info
        }
    })

    private var screenReceiver: BroadcastReceiver? = null
    private var currentGeneration = 0L

    public override fun onServiceConnected() {
        super.onServiceConnected()
        // The framework can reconnect this instance without first destroying it.
        tearDownConnection()
        val app = application as? WattimApplication ?: return
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        serviceScope = scope

        currentGeneration = ++connectionGenerationCounter

        val ingress = EventIngress()
        ingress.setGeneration(currentGeneration)

        val scheduler = TemporalBoundaryScheduler(
            scope = scope,
            dispatcher = Dispatchers.Default,
            onBoundaryReached = { token ->
                ingress.sendControlEvent(ProtectionEvent.TemporalBoundaryReached(token))
            }
        )

        val audioAdapter = AndroidAudioDeviceAdapter(this)
        val audio = AudioGuard(
            deviceAdapter = audioAdapter,
            mediaSessionController = AndroidMediaSessionPauseController(this),
            diagnostics = app.audioDiagnostics,
            strategy = BoundedPauseFallbackStrategy,
            scope = scope,
            dispatcher = Dispatchers.Main.immediate
        )
        audioGuard = audio

        val overlayActionDispatcher = object : OverlayActionDispatcher {
            override fun onCodeEvent(event: ProtectionEvent) {
                ingress.sendControlEvent(event)
            }
            override fun onContinue(sessionId: SessionId, cycle: Int) {
                ingress.sendControlEvent(ProtectionEvent.ActionContinue(sessionId, cycle))
            }

            override fun onExit(sessionId: SessionId?) {
                ingress.sendControlEvent(ProtectionEvent.ActionExit(sessionId))
            }

            override fun onCancel(sessionId: SessionId?) {
                ingress.sendControlEvent(ProtectionEvent.ActionCancel(sessionId))
            }

            override fun onBreathingDeadlineReached(sessionId: SessionId, cycle: Int) {
                ingress.sendControlEvent(ProtectionEvent.BreathingDeadlineReached(sessionId, cycle))
            }

            override fun onEmergencyOnce(sessionId: SessionId, cycle: Int) {
                ingress.sendControlEvent(ProtectionEvent.ActionEmergencyOnce(sessionId, cycle))
            }

            override fun onEmergencyTimed(sessionId: SessionId, cycle: Int, durationMs: Long) {
                ingress.sendControlEvent(ProtectionEvent.ActionEmergencyTimed(sessionId, cycle, durationMs))
            }

            override fun onEmergencyForever(sessionId: SessionId, cycle: Int) {
                ingress.sendControlEvent(ProtectionEvent.ActionEmergencyForever(sessionId, cycle))
            }
        }

        val presenter = OverlayPresenter(
            actionDispatcher = overlayActionDispatcher,
            policyStore = app.policyStore,
            statisticsStore = app.statisticsStore,
            scope = scope
        )
        app.setPresenter(presenter)

        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val overlayListener = object : OverlayHostListener {
            override fun onOverlayAttached(sessionId: SessionId, cycle: Int) {
                ingress.sendControlEvent(ProtectionEvent.OverlayAttached(sessionId, cycle))
            }

            override fun onOverlayDetached(sessionId: SessionId) {
                ingress.sendControlEvent(ProtectionEvent.OverlayDetached(sessionId))
            }

            override fun onOverlayAttachFailed(sessionId: SessionId, cycle: Int, error: Throwable) {
                ingress.sendControlEvent(
                    ProtectionEvent.OverlayAttachFailed(
                        sessionId = sessionId,
                        cycle = cycle,
                        errorType = error.javaClass.simpleName.ifBlank { "UnknownError" }
                    )
                )
            }
        }

        val host = OverlayHost(
            context = this,
            windowManagerAdapter = AndroidWindowManagerAdapter(wm),
            presenter = presenter,
            listener = overlayListener,
            contentRenderer = { composeView ->
                composeView.setContent {
                    OverlayRootContent(presenter)
                }
            },
            useActivity = true
        )
        overlayHost = host

        val executor = EffectExecutor(
            overlayPort = host,
            audioPort = audio,
            homePort = AndroidHomePort(this),
            resyncPort = this,
            protectionStatusPort = { operational ->
                app.permissionMonitor.setProtectionOperational(operational)
            },
            storeWriter = app.protectionStoreWriter,
            scheduler = scheduler,
            subscriptionController = subscriptionController,
            codesNavigationPort = io.ronesec.android.protection.AndroidCodesNavigationPort(this) { sessionId, cycle ->
                ingress.sendControlEvent(ProtectionEvent.CodeTripFailed(sessionId, cycle))
            }
        )

        val coord = InterventionCoordinator(
            policyStore = app.policyStore,
            eventIngress = ingress,
            effectExecutor = executor,
            journal = ProtectionEventJournal(),
            wallClock = app.wallClock,
            monotonicClock = app.monotonicClock,
            scope = scope,
            dispatcher = Dispatchers.Main.immediate
        )
        coordinator = coord
        app.setCoordinator(coord)

        coord.onServiceConnected(currentGeneration)
        coord.start()

        // Register screen state receiver
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> coord.onScreenOff()
                    Intent.ACTION_SCREEN_ON -> {
                        val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                        if (km?.isKeyguardLocked == false) {
                            coord.onScreenUnlocked()
                        } else {
                            coord.onScreenOnLocked()
                        }
                    }
                    Intent.ACTION_USER_PRESENT -> coord.onScreenUnlocked()
                }
            }
        }
        screenReceiver = receiver
        registerReceiver(receiver, filter)

        _isConnected.value = true
        app.permissionMonitor.setAccessibilityConnected(true)

        // Trigger initial metadata resync
        requestResync(currentGeneration, 1L)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val coord = coordinator ?: return

        var isImeWindow = false
        var isSubWindow = false
        var isSystemWindow = false

        try {
            val windowList = windows
            val window = windowList?.find { it.id == event.windowId }
            if (window != null) {
                if (window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                    isImeWindow = true
                } else if (window.type == AccessibilityWindowInfo.TYPE_SYSTEM) {
                    isSystemWindow = true
                }
                if (window.parent != null) {
                    isSubWindow = true
                }
            }
        } catch (_: Exception) {
            // Ignore window list access failures gracefully
        }

        val payload = RawAccessibilityPayload(
            eventType = event.eventType,
            packageName = event.packageName?.toString(),
            className = event.className?.toString(),
            uptimeMs = SystemClock.uptimeMillis(),
            isOverlayWindow = false,
            isImeWindow = isImeWindow,
            isSubWindow = isSubWindow,
            isSystemWindow = isSystemWindow
        )

        val candidate = foregroundTracker.normalizeEvent(payload)
        if (candidate is ProtectionEvent.ForegroundCandidate) {
            coord.onForegroundCandidate(candidate)
        }
    }

    override fun requestResync(generation: Long, requestSequence: Long) {
        println("DEBUG: requestResync called with generation=$generation, currentGeneration=$currentGeneration")
        if (generation < currentGeneration) return
        val coord = coordinator ?: return

        serviceScope?.launch(Dispatchers.Main) {
            try {
                val root = rootInActiveWindow
                val pkg = foregroundTracker.extractMetadataPackage(root)
                    ?: foregroundTracker.lastPackage?.takeIf { candidate ->
                        candidate.isNotBlank() &&
                                !foregroundTracker.isIme(candidate) &&
                                !foregroundTracker.isSystemUi(candidate) &&
                                candidate != foregroundTracker.ownPackageName
                    }
                println("DEBUG: requestResync coroutine running: root=$root, pkg=$pkg, lastPackage=${foregroundTracker.lastPackage}")
                if (pkg != null) {
                    coord.onForegroundCandidate(
                        ProtectionEvent.ForegroundCandidate(
                            packageName = pkg,
                            sourceUptimeMs = SystemClock.uptimeMillis(),
                            eventSequence = foregroundTracker.currentSequence + 1
                        )
                    )
                }
            } catch (_: Exception) {
                // Ignore accessibility IPC failures gracefully
            }
        }
    }

    override fun onInterrupt() {
        tearDownConnection()
    }

    override fun onDestroy() {
        tearDownConnection()
        super.onDestroy()
    }

    private fun tearDownConnection() {
        _isConnected.value = false
        val app = application as? WattimApplication
        app?.permissionMonitor?.setAccessibilityConnected(false)
        app?.permissionMonitor?.setProtectionOperational(false)

        screenReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {}
            screenReceiver = null
        }

        overlayHost?.let {
            it.dismissOverlay(null)
            overlayHost = null
        }

        audioGuard?.let {
            it.releaseAll()
            audioGuard = null
        }

        coordinator?.let { coord ->
            coord.stop()
            app?.setCoordinator(null)
            app?.setPresenter(null)
            coordinator = null
        }

        serviceScope?.cancel()
        serviceScope = null
        foregroundTracker.resetWatermark()
        subscriptionController.reset()
    }
}
