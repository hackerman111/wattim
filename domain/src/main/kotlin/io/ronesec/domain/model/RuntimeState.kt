package io.ronesec.domain.model

/**
 * Coherent, committed policy snapshot produced transactionally by PolicyStore.
 * Completely immutable and free of platform dependencies.
 */
data class RuntimePolicySnapshot(
    val revision: Long,
    val targets: Map<String, TargetConfig>,
    val activeGrants: Map<String, TimedGrant>,
    val activeBlockSessions: List<CompiledBlockSession>,
    val activeSchedules: List<CompiledSchedule>,
    val globalPause: GlobalPause,
    val recentEntryTimestamps: Map<String, List<Long>> = emptyMap()
) {
    companion object {
        val EMPTY = RuntimePolicySnapshot(
            revision = 0L,
            targets = emptyMap(),
            activeGrants = emptyMap(),
            activeBlockSessions = emptyList(),
            activeSchedules = emptyList(),
            globalPause = GlobalPause.None,
            recentEntryTimestamps = emptyMap()
        )
    }
}

/**
 * Full in-memory runtime state evaluated by RuleEngine and InterventionCoordinator.
 * Combines durable snapshot with ephemeral session permits and exits.
 */
data class RuntimeState(
    val snapshot: RuntimePolicySnapshot,
    val sessionPermits: Map<String, SessionPermit> = emptyMap(),
    val lastExitElapsedMs: Map<String, Long> = emptyMap(),
    val uncommittedHistoryDeltas: Map<String, List<Long>> = emptyMap()
) {
    fun getEffectiveHistory(packageName: String): List<Long> {
        val committed = snapshot.recentEntryTimestamps[packageName] ?: emptyList()
        val deltas = uncommittedHistoryDeltas[packageName] ?: emptyList()
        return committed + deltas
    }
}
