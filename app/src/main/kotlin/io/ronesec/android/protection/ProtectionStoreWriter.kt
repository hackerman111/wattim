package io.ronesec.android.protection

import io.ronesec.android.data.PolicyStore
import io.ronesec.domain.protection.ProtectionEffect
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Process-owned FIFO for durable effects emitted by the protection reducer.
 * A later outcome can therefore never overtake creation of its attempt row.
 */
class ProtectionStoreWriter(
    private val policyStore: PolicyStore,
    scope: CoroutineScope,
    dispatcher: CoroutineDispatcher
) {
    private sealed interface Command {
        data class Persist(val effect: ProtectionEffect) : Command
        data class Barrier(val completion: CompletableDeferred<Unit>) : Command
    }

    private val commands = Channel<Command>(capacity = Channel.UNLIMITED)

    init {
        scope.launch(dispatcher) {
            for (command in commands) {
                when (command) {
                    is Command.Persist -> persist(command.effect)
                    is Command.Barrier -> command.completion.complete(Unit)
                }
            }
        }
    }

    fun enqueue(effect: ProtectionEffect) {
        check(commands.trySend(Command.Persist(effect)).isSuccess) {
            "Protection store writer is unavailable"
        }
    }

    internal suspend fun awaitIdle() {
        val completion = CompletableDeferred<Unit>()
        commands.send(Command.Barrier(completion))
        completion.await()
    }

    private suspend fun persist(effect: ProtectionEffect) {
        when (effect) {
            is ProtectionEffect.RecordAttempt -> policyStore.recordAttempt(effect.record)
            is ProtectionEffect.CommitAttemptOutcome -> policyStore.finalizeAttempt(
                effect.attemptId,
                effect.outcome,
                effect.resolvedAt
            )
            is ProtectionEffect.CommitAccessGrant -> policyStore.grantAccess(effect.grant)
            is ProtectionEffect.RevokeGrant -> policyStore.revokeGrant(effect.packageName, effect.grantId)
            is ProtectionEffect.DisableTarget -> policyStore.toggleTarget(effect.packageName, enabled = false)
            else -> error("Unsupported store effect: ${effect::class.simpleName}")
        }
    }
}
