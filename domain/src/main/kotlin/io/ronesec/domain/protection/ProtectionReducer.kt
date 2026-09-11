package io.ronesec.domain.protection

import io.ronesec.domain.model.AttemptKind
import io.ronesec.domain.model.AttemptOutcome
import io.ronesec.domain.model.AttemptRecord
import io.ronesec.domain.model.GrantOrigin
import io.ronesec.domain.model.RuntimePolicySnapshot
import io.ronesec.domain.model.RuntimeState
import io.ronesec.domain.model.SessionId
import io.ronesec.domain.model.SessionPermit
import io.ronesec.domain.model.TimedGrant
import io.ronesec.domain.policy.AllowReason
import io.ronesec.domain.policy.Decision
import io.ronesec.domain.policy.RuleEngine
import io.ronesec.domain.policy.ScheduleResolver
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

data class ReducerContext(
    val nowWall: Instant,
    val nowElapsedMs: Long,
    val zoneId: ZoneId,
    val runtimeState: RuntimeState,
    val nextSessionId: () -> SessionId,
    val nextAttemptId: () -> String = { UUID.randomUUID().toString() },
    val codePort: io.ronesec.domain.codes.SessionCodePort = io.ronesec.domain.codes.SecureSessionCodePort,
    val wattimPackageName: String = "io.ronesec.android"
)

data class ReducerResult(
    val newState: ProtectionState,
    val effects: List<ProtectionEffect>,
    val updatedRuntimeState: RuntimeState
)

object ProtectionReducer {

    fun reduce(
        currentState: ProtectionState,
        event: ProtectionEvent,
        context: ReducerContext
    ): ReducerResult {
        CodeChallengeReducer.intercept(currentState, event, context)?.let { return it }
        return CodeChallengeReducer.finish(currentState, reduceCore(currentState, event, context), context)
    }

    private fun reduceCore(
        currentState: ProtectionState,
        event: ProtectionEvent,
        context: ReducerContext
    ): ReducerResult {
        return when (event) {
            is ProtectionEvent.GenerateUnlockCode, is ProtectionEvent.SubmitUnlockCode,
            is ProtectionEvent.CodeTripFailed, is ProtectionEvent.CodePanelShown ->
                ReducerResult(currentState, emptyList(), context.runtimeState)
            is ProtectionEvent.ServiceDisconnected -> {
                val effects = mutableListOf<ProtectionEffect>()
                when (currentState) {
                    is ProtectionState.Intervening -> {
                        effects += ProtectionEffect.CommitAttemptOutcome(
                            currentState.session.attemptId,
                            AttemptOutcome.INTERRUPTED,
                            context.nowWall
                        )
                        effects += ProtectionEffect.DismissOverlay(currentState.session.sessionId)
                        effects += ProtectionEffect.ReleaseAudioLease(currentState.session.sessionId)
                    }
                    is ProtectionState.Blocked -> {
                        effects += ProtectionEffect.CommitAttemptOutcome(
                            currentState.session.attemptId,
                            AttemptOutcome.INTERRUPTED,
                            context.nowWall
                        )
                        effects += ProtectionEffect.DismissOverlay(null)
                        effects += ProtectionEffect.ReleaseAudioLease(currentState.session.sessionId)
                    }
                    is ProtectionState.Exiting -> {
                        if (currentState.session != null) {
                            effects += ProtectionEffect.DismissOverlay(currentState.session.sessionId)
                            effects += ProtectionEffect.ReleaseAudioLease(currentState.session.sessionId)
                        }
                    }
                    else -> Unit
                }
                ReducerResult(
                    newState = ProtectionState.Unavailable("Service disconnected"),
                    effects = effects + ProtectionEffect.SetProtectionOperational(false),
                    updatedRuntimeState = context.runtimeState.copy(
                        sessionPermits = emptyMap(),
                        lastExitElapsedMs = emptyMap()
                    )
                )
            }

            is ProtectionEvent.ServiceConnected -> {
                ReducerResult(
                    newState = ProtectionState.Loading(),
                    effects = listOf(ProtectionEffect.SetProtectionOperational(false)),
                    updatedRuntimeState = context.runtimeState.copy(
                        sessionPermits = emptyMap(),
                        lastExitElapsedMs = emptyMap()
                    )
                )
            }

            is ProtectionEvent.ScreenOff -> {
                val effects = mutableListOf<ProtectionEffect>()
                when (currentState) {
                    is ProtectionState.Intervening -> {
                        effects += ProtectionEffect.CommitAttemptOutcome(
                            currentState.session.attemptId,
                            AttemptOutcome.INTERRUPTED,
                            context.nowWall
                        )
                        effects += ProtectionEffect.DismissOverlay(currentState.session.sessionId)
                        effects += ProtectionEffect.ReleaseAudioLease(currentState.session.sessionId)
                    }
                    is ProtectionState.Blocked -> {
                        effects += ProtectionEffect.CommitAttemptOutcome(
                            currentState.session.attemptId,
                            AttemptOutcome.INTERRUPTED,
                            context.nowWall
                        )
                        effects += ProtectionEffect.DismissOverlay(null)
                        effects += ProtectionEffect.ReleaseAudioLease(currentState.session.sessionId)
                    }
                    is ProtectionState.Exiting -> {
                        if (currentState.session != null) {
                            effects += ProtectionEffect.DismissOverlay(currentState.session.sessionId)
                            effects += ProtectionEffect.ReleaseAudioLease(currentState.session.sessionId)
                        }
                    }
                    else -> Unit
                }
                ReducerResult(
                    newState = ProtectionState.Suspended(locked = true),
                    effects = effects,
                    updatedRuntimeState = context.runtimeState.copy(
                        sessionPermits = emptyMap(),
                        lastExitElapsedMs = emptyMap()
                    )
                )
            }

            is ProtectionEvent.ScreenOnLocked -> {
                if (currentState is ProtectionState.Suspended) {
                    ReducerResult(currentState, emptyList(), context.runtimeState)
                } else {
                    ReducerResult(ProtectionState.Suspended(locked = true), emptyList(), context.runtimeState)
                }
            }

            is ProtectionEvent.ScreenUnlocked -> {
                if (currentState is ProtectionState.Suspended) {
                    ReducerResult(
                        newState = ProtectionState.Idle,
                        effects = listOf(
                            ProtectionEffect.ResyncRequired(
                                generation = event.generation,
                                requestSequence = 1L
                            )
                        ),
                        updatedRuntimeState = context.runtimeState
                    )
                } else {
                    ReducerResult(currentState, emptyList(), context.runtimeState)
                }
            }

            is ProtectionEvent.CoherentReady -> {
                if (currentState !is ProtectionState.Loading) {
                    return ReducerResult(currentState, emptyList(), context.runtimeState)
                }
                val updatedState = context.runtimeState.copy(snapshot = event.snapshot)
                val readyResult = if (currentState.latestCandidatePackage != null) {
                    // Evaluate candidate that arrived during loading
                    evaluatePackage(currentState.latestCandidatePackage, context.copy(runtimeState = updatedState))
                        .withEffectPrefix(ProtectionEffect.SetProtectionOperational(true))
                } else {
                    ReducerResult(
                        ProtectionState.Idle,
                        listOf(ProtectionEffect.SetProtectionOperational(true)),
                        updatedState
                    )
                }
                readyResult.withPolicySubscription(event.snapshot)
            }

            is ProtectionEvent.PolicyCommitted -> {
                if (event.revision != event.snapshot.revision ||
                    event.revision <= context.runtimeState.snapshot.revision
                ) {
                    return ReducerResult(currentState, emptyList(), context.runtimeState)
                }
                val updatedState = context.runtimeState.copy(snapshot = event.snapshot)
                // Re-evaluate if intervening or blocked
                val policyResult = when (currentState) {
                    is ProtectionState.Intervening -> {
                        val decision = RuleEngine.evaluate(
                            packageName = currentState.session.packageName,
                            nowWall = context.nowWall,
                            nowElapsedMs = context.nowElapsedMs,
                            zoneId = context.zoneId,
                            runtimeState = updatedState,
                            permitSessionId = currentState.session.sessionId
                        )
                        when (decision) {
                            is Decision.Allow -> {
                                ReducerResult(
                                    newState = ProtectionState.Granted(currentState.session, currentState.session.packageName, decision.reason),
                                    effects = listOf(
                                        ProtectionEffect.DismissOverlay(currentState.session.sessionId),
                                        ProtectionEffect.ReleaseAudioLease(currentState.session.sessionId)
                                    ),
                                    updatedRuntimeState = updatedState
                                )
                            }
                            is Decision.Block -> {
                                ReducerResult(
                                    newState = ProtectionState.Blocked(
                                        currentState.session,
                                        decision.until,
                                        AttachmentStatus.AWAITING_ATTACHMENT
                                    ),
                                    effects = listOf(
                                        ProtectionEffect.ShowBlock(
                                            currentState.session.sessionId,
                                            currentState.session.cycle,
                                            currentState.session.packageName,
                                            decision.until
                                        )
                                    ),
                                    updatedRuntimeState = updatedState
                                )
                            }
                            is Decision.Intervention -> {
                                // Preserve timeline start, update effective config
                                ReducerResult(
                                    newState = currentState.copy(
                                        session = currentState.session.copy(effectiveConfig = decision.config)
                                    ),
                                    effects = emptyList(),
                                    updatedRuntimeState = updatedState
                                )
                            }
                        }
                    }
                    is ProtectionState.Blocked -> {
                        reevaluateBlocked(
                            currentState = currentState,
                            context = context.copy(runtimeState = updatedState)
                        )
                    }
                    is ProtectionState.Granted -> {
                        reevaluateGranted(
                            currentState = currentState,
                            context = context.copy(runtimeState = updatedState)
                        )
                    }
                    else -> ReducerResult(currentState, emptyList(), updatedState)
                }
                policyResult.withPolicySubscription(event.snapshot)
            }

            is ProtectionEvent.ForegroundCandidate -> {
                when (currentState) {
                    is ProtectionState.Loading -> {
                        ReducerResult(
                            newState = ProtectionState.Loading(latestCandidatePackage = event.packageName),
                            effects = emptyList(),
                            updatedRuntimeState = context.runtimeState
                        )
                    }
                    is ProtectionState.Unavailable, is ProtectionState.Suspended -> {
                        ReducerResult(currentState, emptyList(), context.runtimeState)
                    }
                    is ProtectionState.Exiting -> {
                        if (event.packageName == currentState.targetPackage) {
                            // Reject stale callback or same package before exit confirmation
                            ReducerResult(currentState, emptyList(), context.runtimeState)
                        } else {
                            // Target changed -> departure confirmed
                            val effects = mutableListOf<ProtectionEffect>()
                            if (currentState.session != null) {
                                effects += ProtectionEffect.DismissOverlay(currentState.session.sessionId)
                                effects += ProtectionEffect.ReleaseAudioLease(currentState.session.sessionId)
                            }
                            effects += ProtectionEffect.UpdateServiceSubscription(
                                targetPackages = enabledTargetPackages(context.runtimeState.snapshot),
                                trackAll = false
                            )
                            val updatedExits = context.runtimeState.lastExitElapsedMs + (currentState.targetPackage to context.nowElapsedMs)
                            val stateAfterExit = context.runtimeState.copy(lastExitElapsedMs = updatedExits)
                            evaluatePackage(event.packageName, context.copy(runtimeState = stateAfterExit), effectsPrefix = effects)
                        }
                    }
                    is ProtectionState.Intervening -> {
                        if (event.packageName == currentState.session.packageName) {
                            // Duplicate foreground event for same intervening package
                            ReducerResult(currentState, emptyList(), context.runtimeState)
                        } else {
                            // Different app opened -> old session abandoned
                            val effects = listOf(
                                ProtectionEffect.CommitAttemptOutcome(currentState.session.attemptId, AttemptOutcome.ABANDONED, context.nowWall),
                                ProtectionEffect.DismissOverlay(currentState.session.sessionId),
                                ProtectionEffect.ReleaseAudioLease(currentState.session.sessionId)
                            )
                            val updatedDeltas = context.runtimeState.uncommittedHistoryDeltas - currentState.session.packageName
                            val contextAfterAbandon = context.copy(runtimeState = context.runtimeState.copy(uncommittedHistoryDeltas = updatedDeltas))
                            evaluatePackage(event.packageName, contextAfterAbandon, effectsPrefix = effects)
                        }
                    }
                    is ProtectionState.Blocked -> {
                        if (event.packageName == currentState.packageName) {
                            ReducerResult(currentState, emptyList(), context.runtimeState)
                        } else {
                            val effects = listOf(
                                ProtectionEffect.DismissOverlay(null),
                                ProtectionEffect.ReleaseAudioLease(currentState.session.sessionId)
                            )
                            evaluatePackage(event.packageName, context, effectsPrefix = effects)
                        }
                    }
                    is ProtectionState.Idle -> {
                        evaluatePackage(event.packageName, context)
                    }
                    is ProtectionState.Granted -> {
                        handleGrantedForegroundChange(currentState, event.packageName, context)
                    }
                    is ProtectionState.Evaluating -> {
                        evaluatePackage(event.packageName, context)
                    }
                }
            }

            is ProtectionEvent.OverlayAttached -> {
                if (currentState is ProtectionState.Intervening &&
                    currentState.session.sessionId == event.sessionId &&
                    currentState.session.cycle == event.cycle &&
                    currentState.substate is InterveningSubstate.AwaitingAttachment
                ) {
                    val durationMs = currentState.session.effectiveConfig?.durationMs ?: 8_000L
                    val deadline = context.nowElapsedMs + durationMs
                    val newState = ProtectionState.Intervening(
                        session = currentState.session,
                        substate = InterveningSubstate.Breathing(
                            startElapsedMs = context.nowElapsedMs,
                            durationMs = durationMs,
                            deadlineElapsedMs = deadline
                        )
                    )
                    ReducerResult(
                        newState = newState,
                        effects = listOf(ProtectionEffect.ScheduleTemporalBoundary(delayMs = durationMs, boundaryToken = deadline)),
                        updatedRuntimeState = context.runtimeState
                    )
                } else if (currentState is ProtectionState.Blocked &&
                    currentState.session.sessionId == event.sessionId &&
                    currentState.session.cycle == event.cycle &&
                    currentState.attachmentStatus == AttachmentStatus.AWAITING_ATTACHMENT
                ) {
                    ReducerResult(
                        newState = currentState.copy(attachmentStatus = AttachmentStatus.ATTACHED),
                        effects = listOf(
                            ProtectionEffect.CommitAttemptOutcome(
                                currentState.session.attemptId,
                                AttemptOutcome.BLOCKED,
                                context.nowWall
                            )
                        ),
                        updatedRuntimeState = context.runtimeState
                    )
                } else {
                    ReducerResult(currentState, emptyList(), context.runtimeState)
                }
            }

            is ProtectionEvent.OverlayAttachFailed -> {
                val failedSession = when (currentState) {
                    is ProtectionState.Intervening -> currentState.session
                    is ProtectionState.Blocked -> currentState.session
                    else -> null
                }
                if (failedSession?.sessionId == event.sessionId && failedSession.cycle == event.cycle) {
                    ReducerResult(
                        newState = ProtectionState.Unavailable(
                            reason = "Overlay attachment failed: ${event.errorType}"
                        ),
                        effects = listOf(
                            ProtectionEffect.CommitAttemptOutcome(
                                failedSession.attemptId,
                                AttemptOutcome.INTERRUPTED,
                                context.nowWall
                            ),
                            ProtectionEffect.DismissOverlay(event.sessionId),
                            ProtectionEffect.ReleaseAudioLease(event.sessionId),
                            ProtectionEffect.UpdateServiceSubscription(
                                targetPackages = enabledTargetPackages(context.runtimeState.snapshot),
                                trackAll = false
                            ),
                            ProtectionEffect.SetProtectionOperational(false)
                        ),
                        updatedRuntimeState = context.runtimeState.copy(
                            sessionPermits = emptyMap(),
                            lastExitElapsedMs = emptyMap()
                        )
                    )
                } else {
                    ReducerResult(currentState, emptyList(), context.runtimeState)
                }
            }

            is ProtectionEvent.BreathingDeadlineReached -> {
                if (currentState is ProtectionState.Intervening &&
                    currentState.session.sessionId == event.sessionId &&
                    currentState.session.cycle == event.cycle &&
                    currentState.substate is InterveningSubstate.Breathing
                ) {
                    if (context.nowElapsedMs >= currentState.substate.deadlineElapsedMs) {
                        val completeSubstate = InterveningSubstate.Complete(
                            startElapsedMs = currentState.substate.startElapsedMs,
                            durationMs = currentState.substate.durationMs
                        )
                        ReducerResult(
                            newState = ProtectionState.Intervening(currentState.session, completeSubstate),
                            effects = listOf(ProtectionEffect.UpdateOverlayComplete(event.sessionId, event.cycle)),
                            updatedRuntimeState = context.runtimeState
                        )
                    } else {
                        ReducerResult(currentState, emptyList(), context.runtimeState)
                    }
                } else {
                    ReducerResult(currentState, emptyList(), context.runtimeState)
                }
            }

            is ProtectionEvent.ActionContinue -> {
                if (currentState is ProtectionState.Intervening &&
                    currentState.session.sessionId == event.sessionId &&
                    currentState.session.cycle == event.cycle &&
                    currentState.substate is InterveningSubstate.Complete
                ) {
                    // Revalidate current policy before committing access
                    val decision = RuleEngine.evaluate(
                        packageName = currentState.session.packageName,
                        nowWall = context.nowWall,
                        nowElapsedMs = context.nowElapsedMs,
                        zoneId = context.zoneId,
                        runtimeState = context.runtimeState,
                        permitSessionId = currentState.session.sessionId
                    )

                    if (decision is Decision.Block) {
                        // Hard block activated while user was breathing
                        ReducerResult(
                            newState = ProtectionState.Blocked(
                                currentState.session,
                                decision.until,
                                AttachmentStatus.AWAITING_ATTACHMENT
                            ),
                            effects = listOf(
                                ProtectionEffect.ShowBlock(
                                    currentState.session.sessionId,
                                    currentState.session.cycle,
                                    currentState.session.packageName,
                                    decision.until
                                )
                            ),
                            updatedRuntimeState = context.runtimeState
                        )
                    } else {
                        val reinterventionMs = currentState.session.effectiveConfig?.reinterventionMs ?: 0L
                        val effects = mutableListOf<ProtectionEffect>()
                        effects += ProtectionEffect.CommitAttemptOutcome(currentState.session.attemptId, AttemptOutcome.CONTINUED, context.nowWall)
                        effects += ProtectionEffect.DismissOverlay(event.sessionId)
                        effects += ProtectionEffect.ReleaseAudioLease(event.sessionId)

                        val newPermit: SessionPermit
                        if (reinterventionMs > 0L) {
                            val expiresElapsed = context.nowElapsedMs + reinterventionMs
                            newPermit = SessionPermit(currentState.session.packageName, currentState.session.sessionId, expiresElapsed)
                            effects += ProtectionEffect.ScheduleTemporalBoundary(reinterventionMs, expiresElapsed)
                        } else {
                            newPermit = SessionPermit(currentState.session.packageName, currentState.session.sessionId, expiresElapsedMs = null)
                        }

                        val updatedPermits = context.runtimeState.sessionPermits + (currentState.session.packageName to newPermit)
                        val updatedRuntime = context.runtimeState.copy(sessionPermits = updatedPermits)

                        ReducerResult(
                            newState = ProtectionState.Granted(currentState.session, currentState.session.packageName, AllowReason.ACTIVE_SESSION_PERMIT),
                            effects = effects,
                            updatedRuntimeState = updatedRuntime
                        )
                    }
                } else {
                    ReducerResult(currentState, emptyList(), context.runtimeState)
                }
            }

            is ProtectionEvent.ActionExit, is ProtectionEvent.ActionCancel -> {
                when (currentState) {
                    is ProtectionState.Intervening -> {
                        val effects = listOf(
                            ProtectionEffect.CommitAttemptOutcome(currentState.session.attemptId, AttemptOutcome.ABANDONED, context.nowWall),
                            ProtectionEffect.SendToHome
                        )
                        val updatedDeltas = context.runtimeState.uncommittedHistoryDeltas - currentState.session.packageName
                        val updatedRuntime = context.runtimeState.copy(uncommittedHistoryDeltas = updatedDeltas)
                        ReducerResult(
                            newState = ProtectionState.Exiting(currentState.session, currentState.session.packageName),
                            effects = effects,
                            updatedRuntimeState = updatedRuntime
                        )
                    }
                    is ProtectionState.Blocked -> {
                        ReducerResult(
                            newState = ProtectionState.Exiting(currentState.session, currentState.packageName),
                            effects = listOf(ProtectionEffect.SendToHome),
                            updatedRuntimeState = context.runtimeState
                        )
                    }
                    else -> ReducerResult(currentState, emptyList(), context.runtimeState)
                }
            }

            is ProtectionEvent.ActionEmergencyOnce -> {
                if (currentState is ProtectionState.Intervening &&
                    currentState.session.sessionId == event.sessionId &&
                    currentState.session.cycle == event.cycle
                ) {
                    val permit = SessionPermit(currentState.session.packageName, currentState.session.sessionId, expiresElapsedMs = null)
                    val effects = listOf(
                        ProtectionEffect.CommitAttemptOutcome(currentState.session.attemptId, AttemptOutcome.CONTINUED, context.nowWall),
                        ProtectionEffect.DismissOverlay(event.sessionId),
                        ProtectionEffect.ReleaseAudioLease(event.sessionId)
                    )
                    val updatedPermits = context.runtimeState.sessionPermits + (currentState.session.packageName to permit)
                    ReducerResult(
                        newState = ProtectionState.Granted(currentState.session, currentState.session.packageName, AllowReason.ACTIVE_SESSION_PERMIT),
                        effects = effects,
                        updatedRuntimeState = context.runtimeState.copy(sessionPermits = updatedPermits)
                    )
                } else {
                    ReducerResult(currentState, emptyList(), context.runtimeState)
                }
            }

            is ProtectionEvent.ActionEmergencyTimed -> {
                if (currentState is ProtectionState.Intervening &&
                    currentState.session.sessionId == event.sessionId &&
                    currentState.session.cycle == event.cycle
                ) {
                    val grant = TimedGrant(
                        packageName = currentState.session.packageName,
                        grantId = UUID.randomUUID().toString(),
                        origin = GrantOrigin.EMERGENCY,
                        createdAt = context.nowWall,
                        expiresAt = context.nowWall.plusMillis(event.durationMs)
                    )
                    val effects = listOf(
                        ProtectionEffect.CommitAttemptOutcome(currentState.session.attemptId, AttemptOutcome.CONTINUED, context.nowWall),
                        ProtectionEffect.CommitAccessGrant(grant),
                        ProtectionEffect.DismissOverlay(event.sessionId),
                        ProtectionEffect.ReleaseAudioLease(event.sessionId),
                        ProtectionEffect.ScheduleTemporalBoundary(event.durationMs, context.nowWall.plusMillis(event.durationMs).toEpochMilli())
                    )
                    ReducerResult(
                        newState = ProtectionState.Granted(currentState.session, currentState.session.packageName, AllowReason.ACTIVE_TIMED_PERMIT),
                        effects = effects,
                        updatedRuntimeState = context.runtimeState
                    )
                } else {
                    ReducerResult(currentState, emptyList(), context.runtimeState)
                }
            }

            is ProtectionEvent.ActionEmergencyForever -> {
                if (currentState is ProtectionState.Intervening &&
                    currentState.session.sessionId == event.sessionId &&
                    currentState.session.cycle == event.cycle
                ) {
                    val effects = listOf(
                        ProtectionEffect.CommitAttemptOutcome(currentState.session.attemptId, AttemptOutcome.CONTINUED, context.nowWall),
                        ProtectionEffect.DisableTarget(currentState.session.packageName),
                        ProtectionEffect.DismissOverlay(event.sessionId),
                        ProtectionEffect.ReleaseAudioLease(event.sessionId)
                    )
                    val updatedPermits = context.runtimeState.sessionPermits - currentState.session.packageName
                    ReducerResult(
                        newState = ProtectionState.Idle,
                        effects = effects,
                        updatedRuntimeState = context.runtimeState.copy(sessionPermits = updatedPermits)
                    )
                } else {
                    ReducerResult(currentState, emptyList(), context.runtimeState)
                }
            }

            is ProtectionEvent.DepartureConfirmed -> {
                if (currentState is ProtectionState.Exiting && currentState.targetPackage == event.packageName) {
                    val effects = mutableListOf<ProtectionEffect>()
                    if (currentState.session != null) {
                        effects += ProtectionEffect.DismissOverlay(currentState.session.sessionId)
                        effects += ProtectionEffect.ReleaseAudioLease(currentState.session.sessionId)
                    } else {
                        effects += ProtectionEffect.DismissOverlay(null)
                    }
                    effects += ProtectionEffect.UpdateServiceSubscription(
                        targetPackages = enabledTargetPackages(context.runtimeState.snapshot),
                        trackAll = false
                    )
                    val updatedExits = context.runtimeState.lastExitElapsedMs + (event.packageName to context.nowElapsedMs)
                    val updatedPermits = context.runtimeState.sessionPermits - event.packageName
                    ReducerResult(
                        newState = ProtectionState.Idle,
                        effects = effects,
                        updatedRuntimeState = context.runtimeState.copy(
                            lastExitElapsedMs = updatedExits,
                            sessionPermits = updatedPermits
                        )
                    )
                } else {
                    ReducerResult(currentState, emptyList(), context.runtimeState)
                }
            }

            is ProtectionEvent.TemporalBoundaryReached -> {
                when (currentState) {
                    is ProtectionState.Granted -> {
                        evaluatePackage(currentState.packageName, context, existingSession = currentState.session)
                    }
                    is ProtectionState.Blocked -> {
                        reevaluateBlocked(currentState, context)
                    }
                    is ProtectionState.Intervening -> {
                        if (currentState.substate is InterveningSubstate.Breathing) {
                            if (context.nowElapsedMs >= currentState.substate.deadlineElapsedMs) {
                                val completeSubstate = InterveningSubstate.Complete(
                                    startElapsedMs = currentState.substate.startElapsedMs,
                                    durationMs = currentState.substate.durationMs
                                )
                                ReducerResult(
                                    newState = ProtectionState.Intervening(currentState.session, completeSubstate),
                                    effects = listOf(ProtectionEffect.UpdateOverlayComplete(currentState.session.sessionId, currentState.session.cycle)),
                                    updatedRuntimeState = context.runtimeState
                                )
                            } else {
                                ReducerResult(currentState, emptyList(), context.runtimeState)
                            }
                        } else {
                            ReducerResult(currentState, emptyList(), context.runtimeState)
                        }
                    }
                    else -> ReducerResult(currentState, emptyList(), context.runtimeState)
                }
            }

            is ProtectionEvent.OverlayDetached -> {
                ReducerResult(currentState, emptyList(), context.runtimeState)
            }

            is ProtectionEvent.ResyncRequested -> {
                ReducerResult(
                    currentState,
                    listOf(ProtectionEffect.ResyncRequired(event.generation, event.requestSequence)),
                    context.runtimeState
                )
            }
        }
    }

    private fun evaluatePackage(
        packageName: String,
        context: ReducerContext,
        effectsPrefix: List<ProtectionEffect> = emptyList(),
        existingSession: ActiveSession? = null
    ): ReducerResult {
        val decision = RuleEngine.evaluate(
            packageName = packageName,
            nowWall = context.nowWall,
            nowElapsedMs = context.nowElapsedMs,
            zoneId = context.zoneId,
            runtimeState = context.runtimeState,
            permitSessionId = existingSession?.sessionId
        )

        val effects = effectsPrefix.toMutableList()

        return when (decision) {
            is Decision.Allow -> {
                val newState = ProtectionState.Granted(
                    session = existingSession,
                    packageName = packageName,
                    reason = decision.reason
                )
                val boundaryDelay = computeNearestBoundaryDelayMs(
                    packageName = packageName,
                    nowWall = context.nowWall,
                    nowElapsedMs = context.nowElapsedMs,
                    zoneId = context.zoneId,
                    runtimeState = context.runtimeState
                )
                if (boundaryDelay != null) {
                    effects += ProtectionEffect.ScheduleTemporalBoundary(
                        delayMs = boundaryDelay,
                        boundaryToken = context.nowElapsedMs + boundaryDelay
                    )
                }
                ReducerResult(newState, effects, context.runtimeState)
            }

            is Decision.Block -> {
                val sessionId = existingSession?.sessionId ?: context.nextSessionId()
                val cycle = (existingSession?.cycle ?: 0) + 1
                val attemptId = context.nextAttemptId()
                val target = context.runtimeState.snapshot.targets[packageName]
                val displayName = target?.displayName ?: packageName
                val attemptRecord = AttemptRecord(
                    attemptId = attemptId,
                    sessionId = sessionId,
                    cycle = cycle,
                    packageName = packageName,
                    displayNameAtAttempt = displayName,
                    generation = sessionId.serviceGeneration,
                    kind = if (existingSession != null) AttemptKind.REINTERVENTION else AttemptKind.ENTRY,
                    timestamp = context.nowWall,
                    outcome = null,
                    resolvedAt = null
                )
                effects += ProtectionEffect.RecordAttempt(attemptRecord)
                val session = ActiveSession(
                    sessionId = sessionId,
                    packageName = packageName,
                    cycle = cycle,
                    attemptId = attemptId
                )
                effects += ProtectionEffect.ShowBlock(sessionId, cycle, packageName, decision.until)
                effects += ProtectionEffect.AcquireAudioLease(sessionId, packageName)
                if (decision.until != null) {
                    val delayMs = java.time.Duration.between(context.nowWall, decision.until).toMillis().coerceAtLeast(0L)
                    effects += ProtectionEffect.ScheduleTemporalBoundary(delayMs, decision.until.toEpochMilli())
                }
                val newState = ProtectionState.Blocked(
                    session = session,
                    until = decision.until,
                    attachmentStatus = AttachmentStatus.AWAITING_ATTACHMENT
                )
                ReducerResult(newState, effects, context.runtimeState)
            }

            is Decision.Intervention -> {
                val sessionId = existingSession?.sessionId ?: context.nextSessionId()
                val cycle = if (existingSession != null) existingSession.cycle + 1 else 1
                val kind = if (existingSession != null) AttemptKind.REINTERVENTION else AttemptKind.ENTRY
                val attemptId = context.nextAttemptId()
                val target = context.runtimeState.snapshot.targets[packageName]
                val displayName = target?.displayName ?: packageName

                val attemptRecord = AttemptRecord(
                    attemptId = attemptId,
                    sessionId = sessionId,
                    cycle = cycle,
                    packageName = packageName,
                    displayNameAtAttempt = displayName,
                    generation = sessionId.serviceGeneration,
                    kind = kind,
                    timestamp = context.nowWall,
                    outcome = null,
                    resolvedAt = null
                )
                effects += ProtectionEffect.RecordAttempt(attemptRecord)

                val session = ActiveSession(
                    sessionId = sessionId,
                    packageName = packageName,
                    cycle = cycle,
                    attemptId = attemptId,
                    effectiveConfig = decision.config
                )

                effects += ProtectionEffect.ShowIntervention(sessionId, cycle, decision.config)
                effects += ProtectionEffect.AcquireAudioLease(sessionId, packageName)
                effects += ProtectionEffect.UpdateServiceSubscription(
                    targetPackages = enabledTargetPackages(context.runtimeState.snapshot),
                    trackAll = true
                )

                // Add uncommitted history delta for backoff immediate reflection
                val existingDeltas = context.runtimeState.uncommittedHistoryDeltas[packageName] ?: emptyList()
                val updatedDeltas = context.runtimeState.uncommittedHistoryDeltas + (packageName to (existingDeltas + context.nowWall.toEpochMilli()))
                val updatedRuntime = context.runtimeState.copy(uncommittedHistoryDeltas = updatedDeltas)

                val newState = ProtectionState.Intervening(
                    session = session,
                    substate = InterveningSubstate.AwaitingAttachment
                )
                ReducerResult(newState, effects, updatedRuntime)
            }
        }
    }

    private fun handleGrantedForegroundChange(
        currentState: ProtectionState.Granted,
        foregroundPackage: String,
        context: ReducerContext
    ): ReducerResult {
        if (foregroundPackage == currentState.packageName) {
            return ReducerResult(currentState, emptyList(), context.runtimeState)
        }

        val departingPackage = currentState.packageName
        val ownsDepartureState = currentState.session?.packageName == departingPackage ||
                departingPackage in context.runtimeState.sessionPermits ||
                departingPackage in context.runtimeState.snapshot.activeGrants
        if (departingPackage !in context.runtimeState.snapshot.targets && !ownsDepartureState) {
            return evaluatePackage(foregroundPackage, context)
        }

        val effects = mutableListOf<ProtectionEffect>()
        val activeGrant = context.runtimeState.snapshot.activeGrants[departingPackage]
        if (activeGrant?.origin == GrantOrigin.REINTERVENTION) {
            effects += ProtectionEffect.RevokeGrant(departingPackage, activeGrant.grantId)
        }
        effects += ProtectionEffect.UpdateServiceSubscription(
            targetPackages = enabledTargetPackages(context.runtimeState.snapshot),
            trackAll = false
        )

        val updatedSnapshot = if (activeGrant?.origin == GrantOrigin.REINTERVENTION) {
            context.runtimeState.snapshot.copy(
                activeGrants = context.runtimeState.snapshot.activeGrants - departingPackage
            )
        } else {
            context.runtimeState.snapshot
        }
        val updatedRuntime = context.runtimeState.copy(
            snapshot = updatedSnapshot,
            sessionPermits = context.runtimeState.sessionPermits - departingPackage,
            lastExitElapsedMs = context.runtimeState.lastExitElapsedMs +
                    (departingPackage to context.nowElapsedMs)
        )
        return evaluatePackage(
            packageName = foregroundPackage,
            context = context.copy(runtimeState = updatedRuntime),
            effectsPrefix = effects
        )
    }

    private fun reevaluateGranted(
        currentState: ProtectionState.Granted,
        context: ReducerContext
    ): ReducerResult {
        val result = evaluatePackage(
            packageName = currentState.packageName,
            context = context,
            existingSession = currentState.session
        )
        val granted = result.newState as? ProtectionState.Granted ?: return result
        if (granted.reason != AllowReason.NOT_TARGET &&
            granted.reason != AllowReason.TARGET_DISABLED
        ) {
            return result
        }
        return result.copy(
            newState = granted.copy(session = null),
            updatedRuntimeState = result.updatedRuntimeState.copy(
                sessionPermits = result.updatedRuntimeState.sessionPermits - currentState.packageName
            )
        )
    }

    private fun reevaluateBlocked(
        currentState: ProtectionState.Blocked,
        context: ReducerContext
    ): ReducerResult {
        return when (val decision = RuleEngine.evaluate(
            packageName = currentState.packageName,
            nowWall = context.nowWall,
            nowElapsedMs = context.nowElapsedMs,
            zoneId = context.zoneId,
            runtimeState = context.runtimeState,
            permitSessionId = currentState.session.sessionId
        )) {
            is Decision.Allow -> ReducerResult(
                newState = ProtectionState.Granted(
                    session = currentState.session,
                    packageName = currentState.packageName,
                    reason = decision.reason
                ),
                effects = buildList {
                    if (currentState.attachmentStatus == AttachmentStatus.AWAITING_ATTACHMENT) {
                        add(
                            ProtectionEffect.CommitAttemptOutcome(
                                currentState.session.attemptId,
                                AttemptOutcome.INTERRUPTED,
                                context.nowWall
                            )
                        )
                    }
                    add(ProtectionEffect.DismissOverlay(currentState.session.sessionId))
                    add(ProtectionEffect.ReleaseAudioLease(currentState.session.sessionId))
                },
                updatedRuntimeState = context.runtimeState
            )

            is Decision.Intervention -> {
                if (currentState.attachmentStatus == AttachmentStatus.AWAITING_ATTACHMENT) {
                    val session = currentState.session.copy(effectiveConfig = decision.config)
                    ReducerResult(
                        newState = ProtectionState.Intervening(
                            session = session,
                            substate = InterveningSubstate.AwaitingAttachment
                        ),
                        effects = listOf(
                            ProtectionEffect.ShowIntervention(
                                session.sessionId,
                                session.cycle,
                                decision.config
                            ),
                            ProtectionEffect.UpdateServiceSubscription(
                                targetPackages = enabledTargetPackages(context.runtimeState.snapshot),
                                trackAll = true
                            )
                        ),
                        updatedRuntimeState = context.runtimeState
                    )
                } else {
                    evaluatePackage(
                        packageName = currentState.packageName,
                        context = context,
                        existingSession = currentState.session
                    )
                }
            }

            is Decision.Block -> {
                val effects = mutableListOf<ProtectionEffect>(
                    ProtectionEffect.ShowBlock(
                        currentState.session.sessionId,
                        currentState.session.cycle,
                        currentState.packageName,
                        decision.until
                    )
                )
                if (decision.until != null) {
                    val delayMs = java.time.Duration.between(context.nowWall, decision.until)
                        .toMillis()
                        .coerceAtLeast(0L)
                    effects += ProtectionEffect.ScheduleTemporalBoundary(
                        delayMs,
                        decision.until.toEpochMilli()
                    )
                }
                ReducerResult(
                    newState = currentState.copy(until = decision.until),
                    effects = effects,
                    updatedRuntimeState = context.runtimeState
                )
            }
        }
    }

    private fun ReducerResult.withEffectPrefix(effect: ProtectionEffect): ReducerResult =
        copy(effects = listOf(effect) + effects)

    private fun ReducerResult.withPolicySubscription(
        snapshot: RuntimePolicySnapshot
    ): ReducerResult {
        val enabledTargets = enabledTargetPackages(snapshot)
        val trackAll = when (val state = newState) {
            is ProtectionState.Evaluating,
            is ProtectionState.Intervening,
            is ProtectionState.Blocked,
            is ProtectionState.Exiting -> true

            is ProtectionState.Granted -> snapshot.targets[state.packageName]?.enabled == true
            is ProtectionState.Loading,
            is ProtectionState.Unavailable,
            is ProtectionState.Idle,
            is ProtectionState.Suspended -> false
        }
        val subscriptionEffect = ProtectionEffect.UpdateServiceSubscription(
            targetPackages = enabledTargets,
            trackAll = trackAll
        )
        return copy(
            effects = effects.filterNot { it is ProtectionEffect.UpdateServiceSubscription } +
                    subscriptionEffect
        )
    }

    private fun enabledTargetPackages(snapshot: RuntimePolicySnapshot): Set<String> =
        snapshot.targets.values
            .asSequence()
            .filter { it.enabled }
            .map { it.packageName }
            .toSet()

    fun computeNearestBoundaryDelayMs(
        packageName: String,
        nowWall: Instant,
        nowElapsedMs: Long,
        zoneId: ZoneId,
        runtimeState: RuntimeState
    ): Long? {
        val candidates = mutableListOf<Long>()

        // 1. Session permit expiry (elapsed time)
        val permit = runtimeState.sessionPermits[packageName]
        if (permit?.expiresElapsedMs != null) {
            val delay = permit.expiresElapsedMs - nowElapsedMs
            if (delay > 0) candidates.add(delay)
        }

        // 2. Timed grant expiry (wall clock)
        val grant = runtimeState.snapshot.activeGrants[packageName]
        if (grant != null && grant.expiresAt.isAfter(nowWall)) {
            val delay = java.time.Duration.between(nowWall, grant.expiresAt).toMillis()
            if (delay > 0) candidates.add(delay)
        }

        // 3. Global pause expiry (wall clock)
        val pause = runtimeState.snapshot.globalPause
        if (pause is io.ronesec.domain.model.GlobalPause.Until && pause.until.isAfter(nowWall)) {
            val delay = java.time.Duration.between(nowWall, pause.until).toMillis()
            if (delay > 0) candidates.add(delay)
        }

        // 4. Quick Return grace expiry
        val lastExit = runtimeState.lastExitElapsedMs[packageName]
        val graceMs = runtimeState.snapshot.targets[packageName]?.quickReturnGraceMs ?: 0L
        if (lastExit != null && graceMs > 0) {
            val remainingGrace = graceMs - (nowElapsedMs - lastExit)
            if (remainingGrace > 0) candidates.add(remainingGrace)
        }

        // 5. Active manual block end
        val manualBlockEnd = ScheduleResolver.resolveActiveManualBlockEnd(
            sessions = runtimeState.snapshot.activeBlockSessions,
            packageName = packageName,
            now = nowWall
        )
        if (manualBlockEnd != null && manualBlockEnd.isAfter(nowWall)) {
            val delay = java.time.Duration.between(nowWall, manualBlockEnd).toMillis()
            if (delay > 0) candidates.add(delay)
        }

        // 6. Active hard block end
        val hardBlock = ScheduleResolver.resolveActiveHardBlock(
            schedules = runtimeState.snapshot.activeSchedules,
            packageName = packageName,
            now = nowWall,
            zoneId = zoneId
        )
        if (hardBlock?.until != null && hardBlock.until.isAfter(nowWall)) {
            val delay = java.time.Duration.between(nowWall, hardBlock.until).toMillis()
            if (delay > 0) candidates.add(delay)
        }

        return candidates.minOrNull()
    }
}
