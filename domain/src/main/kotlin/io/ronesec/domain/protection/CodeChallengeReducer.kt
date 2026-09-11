package io.ronesec.domain.protection

import io.ronesec.domain.model.SessionId

/** Session code transitions stay separate from policy/grant evaluation. */
internal object CodeChallengeReducer {
    private const val CODE_LIFETIME_MS = 300_000L

    fun intercept(state: ProtectionState, event: ProtectionEvent, context: ReducerContext): ReducerResult? {
        val active = state as? ProtectionState.Intervening ?: return null
        fun unchanged() = ReducerResult(active, emptyList(), context.runtimeState)
        fun matches(id: SessionId, cycle: Int) = active.session.sessionId == id && active.session.cycle == cycle
        val expired = active.codes.unlockExpiresElapsedMs?.let { context.nowElapsedMs >= it } == true
        if (expired) {
            val cleared = active.copy(codes = active.codes.copy(unlockCode = null, unlockExpiresElapsedMs = null, error = true))
            // Expiry consumes input, so an expired submission can never begin breathing.
            if (event is ProtectionEvent.SubmitUnlockCode || event is ProtectionEvent.GenerateUnlockCode ||
                event is ProtectionEvent.TemporalBoundaryReached) return update(cleared, context)
            val next = ProtectionReducer.reduce(cleared, event, context)
            return next.copy(effects = listOf(challenge(cleared)) + next.effects)
        }
        when (event) {
            is ProtectionEvent.GenerateUnlockCode -> {
                if (!matches(event.sessionId, event.cycle) || active.substate !is InterveningSubstate.CodeGate ||
                    active.codes.unlockCode != null || active.codes.travel != CodeTravel.NONE) return unchanged()
                val length = active.session.effectiveConfig?.unlockCodeLength ?: 4
                val deadline = context.nowElapsedMs + CODE_LIFETIME_MS
                val requestRevision = active.codes.unlockRequestRevision + 1L
                val next = active.copy(codes = active.codes.copy(
                    unlockCode = context.codePort.generate(length), unlockExpiresElapsedMs = deadline,
                    unlockRequestRevision = requestRevision, travel = CodeTravel.TO_WATTIM, error = false
                ))
                return update(next, context, listOf(
                    ProtectionEffect.ScheduleTemporalBoundary(CODE_LIFETIME_MS, deadline),
                    ProtectionEffect.OpenWattim(active.session.sessionId, active.session.cycle, requestRevision)
                ))
            }
            is ProtectionEvent.CodePanelShown -> {
                if (!matches(event.sessionId, event.cycle) || active.codes.travel != CodeTravel.TO_WATTIM ||
                    active.codes.unlockCode == null ||
                    active.codes.unlockRequestRevision != event.requestRevision) return unchanged()
                val next = active.copy(codes = active.codes.copy(travel = CodeTravel.IN_WATTIM))
                return update(next, context, listOf(
                    ProtectionEffect.DismissOverlay(active.session.sessionId),
                    ProtectionEffect.ReleaseAudioLease(active.session.sessionId)
                ))
            }
            is ProtectionEvent.CodeTripFailed -> {
                if (!matches(event.sessionId, event.cycle) || active.codes.travel == CodeTravel.NONE ||
                    active.codes.unlockRequestRevision != event.requestRevision) return unchanged()
                val next = active.copy(codes = active.codes.copy(unlockCode = null, unlockExpiresElapsedMs = null, travel = CodeTravel.NONE))
                return update(next, context, remount(next))
            }
            is ProtectionEvent.SubmitUnlockCode -> {
                if (!matches(event.sessionId, event.cycle) || active.substate !is InterveningSubstate.CodeGate ||
                    active.codes.travel != CodeTravel.NONE) return unchanged()
                val expected = active.codes.unlockCode
                if (expected == null || !context.codePort.matches(expected, event.code)) {
                    return update(active.copy(codes = active.codes.copy(error = true)), context)
                }
                return startBreathing(active.copy(codes = active.codes.copy(unlockCode = null, unlockExpiresElapsedMs = null, error = false)), context)
            }
            is ProtectionEvent.OverlayAttached -> {
                if (matches(event.sessionId, event.cycle) && active.substate is InterveningSubstate.AwaitingAttachment) {
                    return if (active.session.effectiveConfig?.twoStageUnlock == true) {
                        update(active.copy(substate = InterveningSubstate.CodeGate), context)
                    } else startBreathing(active, context)
                }
            }
            is ProtectionEvent.ForegroundCandidate -> {
                if (active.codes.travel != CodeTravel.NONE) {
                    if (event.packageName == active.session.packageName) {
                        val next = active.copy(codes = active.codes.copy(travel = CodeTravel.NONE))
                        return update(next, context, remount(next))
                    }
                    if (event.packageName == context.wattimPackageName) {
                        if (active.codes.travel == CodeTravel.TO_WATTIM) {
                            val next = active.copy(codes = active.codes.copy(travel = CodeTravel.IN_WATTIM))
                            return update(next, context, listOf(
                                ProtectionEffect.DismissOverlay(active.session.sessionId),
                                ProtectionEffect.ReleaseAudioLease(active.session.sessionId)
                            ))
                        }
                        return unchanged()
                    }
                    if (event.isLauncher) {
                        val travel = if (active.codes.travel == CodeTravel.TO_WATTIM) CodeTravel.TO_WATTIM else CodeTravel.RETURNING
                        return update(active.copy(codes = active.codes.copy(travel = travel)), context)
                    }
                }
            }
            is ProtectionEvent.SubmitAttentionCheckCode -> {
                if (!matches(event.sessionId, event.cycle) || active.substate !is InterveningSubstate.AttentionCheck) return unchanged()
                val expected = active.substate.code
                if (!context.codePort.matches(expected, event.code)) {
                    return update(active.copy(substate = active.substate.copy(hasError = true)), context)
                }
                val pausedProgress = active.substate.pausedElapsedProgressMs
                val newStartElapsedMs = context.nowElapsedMs - pausedProgress
                val remaining = active.substate.remainingCheckOffsetsMs
                val duration = active.substate.durationMs
                return if (remaining.isNotEmpty()) {
                    val nextOffset = remaining.first()
                    val nextRemaining = remaining.drop(1)
                    val nextBoundary = newStartElapsedMs + nextOffset
                    val delay = (nextBoundary - context.nowElapsedMs).coerceAtLeast(0L)
                    val nextSubstate = InterveningSubstate.Breathing(
                        startElapsedMs = newStartElapsedMs,
                        durationMs = duration,
                        deadlineElapsedMs = nextBoundary,
                        remainingCheckOffsetsMs = nextRemaining
                    )
                    update(active.copy(substate = nextSubstate), context,
                        listOf(ProtectionEffect.ScheduleTemporalBoundary(delay, nextBoundary)))
                } else {
                    val endBoundary = newStartElapsedMs + duration
                    val delay = (endBoundary - context.nowElapsedMs).coerceAtLeast(0L)
                    val nextSubstate = InterveningSubstate.Breathing(
                        startElapsedMs = newStartElapsedMs,
                        durationMs = duration,
                        deadlineElapsedMs = endBoundary,
                        remainingCheckOffsetsMs = emptyList()
                    )
                    update(active.copy(substate = nextSubstate), context,
                        listOf(ProtectionEffect.ScheduleTemporalBoundary(delay, endBoundary)))
                }
            }
            is ProtectionEvent.TemporalBoundaryReached -> {
                if (active.substate is InterveningSubstate.AttentionCheck) {
                    if (context.nowElapsedMs >= active.substate.deadlineElapsedMs) {
                        return startBreathing(active, context)
                    }
                }
                if (active.substate is InterveningSubstate.Breathing) {
                    if (context.nowElapsedMs >= active.substate.deadlineElapsedMs &&
                        active.substate.deadlineElapsedMs < active.substate.startElapsedMs + active.substate.durationMs) {
                        val pausedProgress = (context.nowElapsedMs - active.substate.startElapsedMs).coerceAtLeast(0L)
                        val timeoutMs = active.session.effectiveConfig?.attentionCheckTimeoutMs ?: 5_000L
                        val codeLength = active.session.effectiveConfig?.attentionCheckCodeLength ?: 4
                        val code = context.codePort.generate(codeLength)
                        val deadline = context.nowElapsedMs + timeoutMs
                        val attentionSubstate = InterveningSubstate.AttentionCheck(
                            pausedElapsedProgressMs = pausedProgress,
                            durationMs = active.substate.durationMs,
                            code = code,
                            deadlineElapsedMs = deadline,
                            timeoutMs = timeoutMs,
                            remainingCheckOffsetsMs = active.substate.remainingCheckOffsetsMs,
                            hasError = false
                        )
                        return update(active.copy(substate = attentionSubstate), context,
                            listOf(ProtectionEffect.ScheduleTemporalBoundary(timeoutMs, deadline)))
                    }
                }
            }
            is ProtectionEvent.ActionEmergencyOnce -> return checkEmergency(active, event.sessionId, event.cycle, event.code, context)
            is ProtectionEvent.ActionEmergencyTimed -> return checkEmergency(active, event.sessionId, event.cycle, event.code, context)
            is ProtectionEvent.ActionEmergencyForever -> return checkEmergency(active, event.sessionId, event.cycle, event.code, context)
            is ProtectionEvent.ActionExit -> if (event.sessionId != null && event.sessionId != active.session.sessionId) return unchanged()
            is ProtectionEvent.ActionCancel -> if (event.sessionId != null && event.sessionId != active.session.sessionId) return unchanged()
            else -> Unit
        }
        return null
    }

    fun finish(previous: ProtectionState, result: ReducerResult, context: ReducerContext): ReducerResult {
        var active = result.newState as? ProtectionState.Intervening ?: return result
        val old = previous as? ProtectionState.Intervening
        val sameSession = old?.session?.sessionId == active.session.sessionId && old.session.cycle == active.session.cycle
        val originalConfig = old?.session?.effectiveConfig
        val updatedConfig = active.session.effectiveConfig
        if (sameSession && originalConfig != null && updatedConfig != null) {
            // A challenge keeps its contract for its lifetime; edits apply on the next entry.
            active = active.copy(session = active.session.copy(effectiveConfig = updatedConfig.copy(
                twoStageUnlock = originalConfig.twoStageUnlock,
                unlockCodeLength = originalConfig.unlockCodeLength,
                requireEmergencyCode = originalConfig.requireEmergencyCode,
                attentionChecksEnabled = originalConfig.attentionChecksEnabled,
                attentionCheckCount = originalConfig.attentionCheckCount,
                attentionCheckCodeLength = originalConfig.attentionCheckCodeLength,
                attentionCheckTimeoutMs = originalConfig.attentionCheckTimeoutMs
            )))
        }
        var codes = if (sameSession) old!!.codes else active.codes
        if (active.session.effectiveConfig?.requireEmergencyCode == true && codes.emergencyCode == null) {
            codes = codes.copy(emergencyCode = context.codePort.generate(10))
        }
        if (active.session.effectiveConfig?.requireEmergencyCode != true) codes = codes.copy(emergencyCode = null, emergencyError = false)
        val next = active.copy(codes = codes)
        val changed = old?.codeChallengeUi() != next.codeChallengeUi() || !sameSession ||
            result.effects.any { it is ProtectionEffect.ShowIntervention }
        return result.copy(newState = next, effects = if (changed) result.effects + challenge(next) else result.effects)
    }

    private fun checkEmergency(active: ProtectionState.Intervening, id: SessionId, cycle: Int, entered: String?, context: ReducerContext): ReducerResult? {
        if (active.session.sessionId != id || active.session.cycle != cycle) return ReducerResult(active, emptyList(), context.runtimeState)
        if (active.codes.travel != CodeTravel.NONE || active.substate is InterveningSubstate.AwaitingAttachment) {
            return ReducerResult(active, emptyList(), context.runtimeState)
        }
        if (active.session.effectiveConfig?.requireEmergencyCode != true) return null
        val expected = active.codes.emergencyCode
        return if (expected != null && entered != null && context.codePort.matches(expected, entered)) null
        else update(active.copy(codes = active.codes.copy(emergencyError = true)), context)
    }

    private fun startBreathing(active: ProtectionState.Intervening, context: ReducerContext): ReducerResult {
        val config = active.session.effectiveConfig
        val duration = config?.durationMs ?: 8_000L
        val checksEnabled = config?.attentionChecksEnabled == true && config.attentionCheckCount > 0
        val offsets = if (checksEnabled) {
            io.ronesec.domain.breathing.AttentionCheckSchedule.generate(duration, config.attentionCheckCount)
        } else emptyList()

        return if (offsets.isNotEmpty()) {
            val firstOffset = offsets.first()
            val remaining = offsets.drop(1)
            val deadline = context.nowElapsedMs + firstOffset
            update(active.copy(substate = InterveningSubstate.Breathing(
                startElapsedMs = context.nowElapsedMs,
                durationMs = duration,
                deadlineElapsedMs = deadline,
                remainingCheckOffsetsMs = remaining
            )), context, listOf(ProtectionEffect.ScheduleTemporalBoundary(firstOffset, deadline)))
        } else {
            val deadline = context.nowElapsedMs + duration
            update(active.copy(substate = InterveningSubstate.Breathing(
                startElapsedMs = context.nowElapsedMs,
                durationMs = duration,
                deadlineElapsedMs = deadline,
                remainingCheckOffsetsMs = emptyList()
            )), context, listOf(ProtectionEffect.ScheduleTemporalBoundary(duration, deadline)))
        }
    }

    private fun remount(active: ProtectionState.Intervening): List<ProtectionEffect> = listOf(
        ProtectionEffect.ShowIntervention(active.session.sessionId, active.session.cycle, requireNotNull(active.session.effectiveConfig)),
        ProtectionEffect.AcquireAudioLease(active.session.sessionId, active.session.packageName)
    )

    private fun challenge(active: ProtectionState.Intervening) = ProtectionEffect.UpdateCodeChallenge(
        active.session.sessionId, active.session.cycle, active.codeChallengeUi()
    )

    private fun update(active: ProtectionState.Intervening, context: ReducerContext, effects: List<ProtectionEffect> = emptyList()) =
        ReducerResult(active, effects + challenge(active), context.runtimeState)
}
