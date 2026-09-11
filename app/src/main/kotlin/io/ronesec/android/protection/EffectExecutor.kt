package io.ronesec.android.protection

import io.ronesec.android.platform.accessibility.SubscriptionController
import io.ronesec.android.platform.time.TemporalBoundaryScheduler
import io.ronesec.domain.model.SessionId
import io.ronesec.domain.policy.EffectiveInterventionConfig
import io.ronesec.domain.protection.ProtectionEffect
import io.ronesec.domain.protection.CodeChallengeUi
import java.time.Instant

interface OverlayPort {
    fun updateCodeChallenge(sessionId: SessionId, cycle: Int, snapshot: CodeChallengeUi) {}
    fun showIntervention(sessionId: SessionId, cycle: Int, config: EffectiveInterventionConfig)
    fun showBlock(sessionId: SessionId, cycle: Int, packageName: String, until: Instant?)
    fun updateOverlayComplete(sessionId: SessionId, cycle: Int)
    fun dismissOverlay(sessionId: SessionId?)
}

interface AudioPort {
    fun acquireAudioLease(sessionId: SessionId, packageName: String)
    fun releaseAudioLease(sessionId: SessionId)
}

interface HomePort {
    fun sendToHome(): Boolean
}

fun interface CodesNavigationPort {
    fun openWattim(sessionId: SessionId, cycle: Int)
}

interface ForegroundResyncPort {
    fun requestResync(generation: Long, requestSequence: Long)
}

fun interface ProtectionStatusPort {
    fun setOperational(operational: Boolean)
}

/**
 * Dispatches typed effects produced by ProtectionReducer.
 * - Immediate effects: Overlay, Audio, Home, Scheduler, Subscription, Resync.
 * - Asynchronous persistence: Attempts and Grants are dispatched to PolicyStore without blocking the coordinator.
 * Satisfies I1, I2, I4, F26, Section 3.1.
 */
class EffectExecutor(
    private val overlayPort: OverlayPort,
    private val audioPort: AudioPort,
    private val homePort: HomePort,
    private val resyncPort: ForegroundResyncPort,
    private val protectionStatusPort: ProtectionStatusPort,
    private val storeWriter: ProtectionStoreWriter,
    private val scheduler: TemporalBoundaryScheduler,
    private val subscriptionController: SubscriptionController,
    private val codesNavigationPort: CodesNavigationPort = CodesNavigationPort { _, _ -> }
) {
    fun execute(effects: List<ProtectionEffect>) {
        for (effect in effects) {
            when (effect) {
                is ProtectionEffect.UpdateCodeChallenge -> overlayPort.updateCodeChallenge(effect.sessionId, effect.cycle, effect.snapshot)
                is ProtectionEffect.OpenWattim -> codesNavigationPort.openWattim(effect.sessionId, effect.cycle)
                is ProtectionEffect.ShowIntervention -> {
                    overlayPort.showIntervention(effect.sessionId, effect.cycle, effect.config)
                }

                is ProtectionEffect.ShowBlock -> {
                    overlayPort.showBlock(
                        effect.sessionId,
                        effect.cycle,
                        effect.packageName,
                        effect.until
                    )
                }

                is ProtectionEffect.UpdateOverlayComplete -> {
                    overlayPort.updateOverlayComplete(effect.sessionId, effect.cycle)
                }

                is ProtectionEffect.DismissOverlay -> {
                    overlayPort.dismissOverlay(effect.sessionId)
                }

                is ProtectionEffect.AcquireAudioLease -> {
                    audioPort.acquireAudioLease(effect.sessionId, effect.packageName)
                }

                is ProtectionEffect.ReleaseAudioLease -> {
                    audioPort.releaseAudioLease(effect.sessionId)
                }

                is ProtectionEffect.SendToHome -> {
                    homePort.sendToHome()
                }

                is ProtectionEffect.SetProtectionOperational -> {
                    protectionStatusPort.setOperational(effect.operational)
                }

                is ProtectionEffect.ScheduleTemporalBoundary -> {
                    scheduler.schedule(effect.delayMs, effect.boundaryToken)
                }

                is ProtectionEffect.UpdateServiceSubscription -> {
                    subscriptionController.applyConfiguration(effect.targetPackages, effect.trackAll)
                }

                is ProtectionEffect.ResyncRequired -> {
                    resyncPort.requestResync(effect.generation, effect.requestSequence)
                }

                is ProtectionEffect.RecordAttempt -> {
                    storeWriter.enqueue(effect)
                }

                is ProtectionEffect.CommitAttemptOutcome -> {
                    storeWriter.enqueue(effect)
                }

                is ProtectionEffect.CommitAccessGrant -> {
                    storeWriter.enqueue(effect)
                }

                is ProtectionEffect.RevokeGrant -> {
                    storeWriter.enqueue(effect)
                }

                is ProtectionEffect.DisableTarget -> {
                    storeWriter.enqueue(effect)
                }
            }
        }
    }
}
