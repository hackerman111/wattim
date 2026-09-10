package io.ronesec.android.protection

import android.content.Context
import android.content.Intent
import io.ronesec.domain.model.SessionId
import io.ronesec.domain.policy.EffectiveInterventionConfig
import java.time.Instant

/**
 * Default Android Home adapter implementing SendToHome via ACTION_MAIN + CATEGORY_HOME.
 */
class AndroidHomePort(private val context: Context) : HomePort {
    override fun sendToHome(): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}

/**
 * Placeholder OverlayPort for S05.
 * Replaced by real Compose OverlayHost in S06.
 */
open class NoOpOverlayPort : OverlayPort {
    override fun showIntervention(sessionId: SessionId, cycle: Int, config: EffectiveInterventionConfig) {}
    override fun showBlock(sessionId: SessionId, cycle: Int, packageName: String, until: Instant?) {}
    override fun updateOverlayComplete(sessionId: SessionId, cycle: Int) {}
    override fun dismissOverlay(sessionId: SessionId?) {}
}

/**
 * Placeholder AudioPort for S05.
 * Replaced by real AudioGuard in S06.
 */
open class NoOpAudioPort : AudioPort {
    override fun acquireAudioLease(sessionId: SessionId, packageName: String) {}
    override fun releaseAudioLease(sessionId: SessionId) {}
}
