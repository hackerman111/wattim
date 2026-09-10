package io.ronesec.android.ui.intervention

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import io.ronesec.android.WattimApplication
import io.ronesec.android.platform.overlay.OverlayMode
import io.ronesec.domain.model.SessionId
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/**
 * Full-screen Activity hosting the intervention UI.
 *
 * When opened, it displaces the active target application (e.g. TikTok, Instagram)
 * from the Android foreground, triggering onPause() in the target activity so that
 * media players pause automatically without global volume hacks or audio focus races.
 *
 * Excluded from recents and runs in its own task so that upon finishAndRemoveTask(),
 * Android immediately resumes the underlying target application.
 */
class InterventionActivity : ComponentActivity() {

    private var composeView: androidx.compose.ui.platform.ComposeView? = null

    companion object {
        const val EXTRA_SESSION_ID = "extra_session_id"
        const val EXTRA_CYCLE = "extra_cycle"

        private var currentActivity: WeakReference<InterventionActivity>? = null

        fun start(context: Context, sessionId: SessionId, cycle: Int) {
            val intent = Intent(context, InterventionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(EXTRA_SESSION_ID, sessionId.toString())
                putExtra(EXTRA_CYCLE, cycle)
            }
            context.startActivity(intent)
        }

        fun dismiss() {
            currentActivity?.get()?.finishIntervention()
        }

        internal fun getCurrentActivity(): InterventionActivity? = currentActivity?.get()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentActivity = WeakReference(this)
        applyZeroTransitions()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        val isComposeTestRule = Thread.currentThread().stackTrace.any { element ->
            element.className.contains("AndroidComposeTestRule") ||
            element.className.contains("AndroidComposeUiTestEnvironment")
        }
        if (isComposeTestRule) {
            return
        }

        val app = applicationContext as? WattimApplication
        val presenter = app?.activePresenter?.value
        if (presenter == null) {
            finishIntervention()
            return
        }

        lifecycleScope.launch {
            presenter.uiState.collect { uiState ->
                if (uiState.mode is OverlayMode.None) {
                    finishIntervention()
                }
            }
        }

        onBackPressedDispatcher.addCallback(this) {
            val activePres = (applicationContext as? WattimApplication)?.activePresenter?.value ?: presenter
            activePres.onBackExit()
        }

        val view = androidx.compose.ui.platform.ComposeView(this).apply {
            setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnLifecycleDestroyed(this@InterventionActivity))
            setContent {
                val uiState by presenter.uiState.collectAsState()
                LaunchedEffect(uiState.mode) {
                    if (uiState.mode is OverlayMode.None) {
                        finishIntervention()
                    }
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    OverlayRootContent(presenter)
                }
            }
        }
        composeView = view
        setContentView(view)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyZeroTransitions()
    }

    fun finishIntervention() {
        if (!isFinishing) {
            finishAndRemoveTask()
            applyZeroTransitions()
        }
    }

    override fun finish() {
        super.finish()
        applyZeroTransitions()
    }

    override fun onDestroy() {
        composeView?.disposeComposition()
        composeView = null
        if (currentActivity?.get() == this) {
            currentActivity = null
        }
        super.onDestroy()
    }

    private fun applyZeroTransitions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }
}
