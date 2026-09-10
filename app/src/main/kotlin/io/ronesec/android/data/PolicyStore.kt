package io.ronesec.android.data

import androidx.room.withTransaction
import io.ronesec.android.ui.designsystem.ThemeId
import io.ronesec.android.data.entity.AppSettingsEntity
import io.ronesec.android.data.entity.BlockScheduleEntity
import io.ronesec.android.data.entity.BlockSessionEntity
import io.ronesec.android.data.entity.BlockSessionTargetCrossRef
import io.ronesec.android.data.entity.OpenAttemptEntity
import io.ronesec.android.data.entity.PolicyRevisionEntity
import io.ronesec.android.data.entity.ScheduleOverrideEntity
import io.ronesec.android.data.entity.ScheduleTargetCrossRef
import io.ronesec.domain.model.AttemptOutcome
import io.ronesec.domain.model.AttemptRecord
import io.ronesec.domain.model.CompiledSchedule
import io.ronesec.domain.model.GlobalPause
import io.ronesec.domain.model.MonotonicClock
import io.ronesec.domain.model.RuntimePolicySnapshot
import io.ronesec.domain.model.TargetConfig
import io.ronesec.domain.model.TimedGrant
import io.ronesec.domain.model.WallClock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID

class PolicyStore(
    private val database: WattimDatabase,
    private val wallClock: WallClock,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val writeMutex = Mutex()

    private val _snapshotFlow = MutableStateFlow(RuntimePolicySnapshot.EMPTY)
    val snapshotFlow: StateFlow<RuntimePolicySnapshot> = _snapshotFlow.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _presentationSettings = MutableStateFlow(PresentationSettings())
    val presentationSettings: StateFlow<PresentationSettings> = _presentationSettings.asStateFlow()

    val currentSnapshot: RuntimePolicySnapshot
        get() = _snapshotFlow.value

    init {
        scope.launch(ioDispatcher) {
            writeMutex.withLock {
                initStorage()
                val snapshot = readTransactionalSnapshot()
                _snapshotFlow.value = snapshot
                _isLoading.value = false
            }
        }
    }

    suspend fun awaitReady(): RuntimePolicySnapshot {
        if (!_isLoading.value) return _snapshotFlow.value
        return withContext(ioDispatcher) {
            writeMutex.withLock {
                if (_isLoading.value) {
                    initStorage()
                    val snapshot = readTransactionalSnapshot()
                    _snapshotFlow.value = snapshot
                    _isLoading.value = false
                }
                _snapshotFlow.value
            }
        }
    }

    private suspend fun initStorage() {
        if (database.policyRevisionDao().getRevision() == null) {
            database.policyRevisionDao().setRevision(PolicyRevisionEntity(id = 1, revision = 1L))
        }
        if (database.appSettingsDao().getSettings() == null) {
            database.appSettingsDao().setSettings(AppSettingsEntity(id = 1))
        }
        database.openAttemptDao().finalizeAllUnresolvedAttempts(wallClock.now().toEpochMilli())
    }

    private suspend fun readTransactionalSnapshot(): RuntimePolicySnapshot {
        return database.withTransaction {
            val nowMs = wallClock.now().toEpochMilli()
            val revision = database.policyRevisionDao().getRevision() ?: 1L
            val targets = database.targetAppDao().getAllTargets()
            val grants = database.accessGrantDao().getActiveGrants(nowMs)
            val sessions = database.blockSessionDao().getActiveSessions(nowMs)
            val sessionTargets = database.blockSessionDao().getAllSessionTargets()
            val schedules = database.blockScheduleDao().getAllSchedules()
            val scheduleTargets = database.blockScheduleDao().getAllScheduleTargets()
            val scheduleOverrides = database.blockScheduleDao().getAllScheduleOverrides()
            val settings = database.appSettingsDao().getSettings()
            val recentEntries = database.openAttemptDao().getAllRecentEntryTimestamps(nowMs - 24 * 3600 * 1000L)

            val presentation = PresentationSettings(
                themeId = ThemeId.fromId(settings?.themeId),
                language = settings?.language ?: "AUTO",
                showOverlayStats = settings?.showOverlayStats ?: true,
                savedSessionMinutes = settings?.savedSessionMinutes ?: 7,
                customEmergencyMinutes = settings?.customEmergencyMinutes
            )
            _presentationSettings.value = presentation

            PolicyCompiler.compileSnapshot(
                revision = revision,
                targets = targets,
                grants = grants,
                sessions = sessions,
                sessionTargets = sessionTargets,
                schedules = schedules,
                scheduleTargets = scheduleTargets,
                scheduleOverrides = scheduleOverrides,
                settings = settings,
                recentEntries = recentEntries
            )
        }
    }

    private suspend fun mutateAndPublish(action: suspend () -> Unit): Result<Unit> {
        return try {
            writeMutex.withLock {
                withContext(ioDispatcher) {
                    database.withTransaction {
                        action()
                        database.policyRevisionDao().incrementRevision()
                    }
                    val newSnapshot = readTransactionalSnapshot()
                    _snapshotFlow.value = newSnapshot
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveTarget(target: TargetConfig): Result<Unit> = mutateAndPublish {
        val existing = database.targetAppDao().getTarget(target.packageName)
        if (existing != null && existing.rowVersion != target.rowVersion) {
            throw IllegalStateException("Stale edit: expected rowVersion ${target.rowVersion}, but found ${existing.rowVersion}")
        }
        val nextVersion = (existing?.rowVersion ?: 0L) + 1L
        val entity = PolicyCompiler.toTargetEntity(target.copy(rowVersion = nextVersion))
        database.targetAppDao().insertOrUpdate(entity)
    }

    suspend fun toggleTarget(packageName: String, enabled: Boolean): Result<Unit> = mutateAndPublish {
        database.targetAppDao().updateEnabled(packageName, enabled)
    }

    suspend fun removeTarget(packageName: String): Result<Unit> = mutateAndPublish {
        database.targetAppDao().deleteTarget(packageName)
        database.accessGrantDao().deleteForPackage(packageName)
        // Clean empty sessions/schedules
        val sessions = database.blockSessionDao().getActiveSessions(wallClock.now().toEpochMilli())
        for (session in sessions) {
            val remainingTargets = database.blockSessionDao().getTargetPackagesForSession(session.id)
            if (remainingTargets.isEmpty()) {
                database.blockSessionDao().deactivateSession(session.id)
            }
        }
    }

    suspend fun setGlobalPause(pause: GlobalPause): Result<Unit> = mutateAndPublish {
        when (pause) {
            is GlobalPause.None -> database.appSettingsDao().updatePause("NONE", null)
            is GlobalPause.Until -> database.appSettingsDao().updatePause("UNTIL", pause.until.toEpochMilli())
            is GlobalPause.Indefinite -> database.appSettingsDao().updatePause("INDEFINITE", null)
        }
    }

    suspend fun resumeGlobalPause(): Result<Unit> = mutateAndPublish {
        database.appSettingsDao().updatePause("NONE", null)
    }

    suspend fun recordAttempt(record: AttemptRecord): Result<Unit> {
        return try {
            withContext(ioDispatcher) {
                val entity = OpenAttemptEntity(
                    attemptId = record.attemptId,
                    sessionId = "${record.sessionId.processNonce}-${record.sessionId.serviceGeneration}-${record.sessionId.entryCounter}",
                    cycle = record.cycle,
                    packageName = record.packageName,
                    displayNameAtAttempt = record.displayNameAtAttempt,
                    generation = record.generation,
                    kind = record.kind.name,
                    timestamp = record.timestamp.toEpochMilli(),
                    outcome = record.outcome?.name,
                    resolvedAt = record.resolvedAt?.toEpochMilli()
                )
                database.openAttemptDao().insertIdempotent(entity)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun finalizeAttempt(
        attemptId: String,
        outcome: AttemptOutcome,
        resolvedAt: Instant
    ): Result<Unit> {
        return try {
            withContext(ioDispatcher) {
                database.openAttemptDao().finalizeAttempt(
                    attemptId = attemptId,
                    outcome = outcome.name,
                    resolvedAt = resolvedAt.toEpochMilli()
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun finalizeDeadGenerationAttempts(currentGeneration: Long): Result<Unit> {
        return try {
            withContext(ioDispatcher) {
                database.openAttemptDao().finalizeInterruptedAttempts(
                    currentGeneration = currentGeneration,
                    resolvedAt = wallClock.now().toEpochMilli()
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun grantAccess(grant: TimedGrant): Result<Unit> = mutateAndPublish {
        database.accessGrantDao().insertOrUpdate(PolicyCompiler.toAccessGrantEntity(grant))
    }

    suspend fun revokeGrant(packageName: String, grantId: String): Result<Unit> = mutateAndPublish {
        database.accessGrantDao().deleteGrant(packageName, grantId)
    }

    suspend fun createBlockSession(
        name: String,
        startTime: Instant,
        endTime: Instant,
        targetPackages: Set<String>
    ): Result<String> {
        val sessionId = UUID.randomUUID().toString()
        val result = mutateAndPublish {
            database.blockSessionDao().insertSession(
                BlockSessionEntity(
                    id = sessionId,
                    name = name,
                    startTime = startTime.toEpochMilli(),
                    endTime = endTime.toEpochMilli(),
                    active = true
                )
            )
            val crossRefs = targetPackages.map { BlockSessionTargetCrossRef(sessionId, it) }
            database.blockSessionDao().insertTargets(crossRefs)
        }
        return result.map { sessionId }
    }

    suspend fun stopBlockSession(sessionId: String): Result<Unit> = mutateAndPublish {
        database.blockSessionDao().deactivateSession(sessionId)
    }

    suspend fun saveSchedule(schedule: CompiledSchedule): Result<Long> {
        var assignedId = schedule.id
        val result = mutateAndPublish {
            val entity = BlockScheduleEntity(
                id = schedule.id,
                name = schedule.name,
                weekdayMask = schedule.weekdayMask,
                startMinute = schedule.startMinute,
                endMinute = schedule.endMinute,
                enabled = schedule.enabled,
                type = schedule.type.name,
                rowVersion = 1L
            )
            assignedId = database.blockScheduleDao().insertSchedule(entity)
            database.blockScheduleDao().deleteTargetsForSchedule(assignedId)
            database.blockScheduleDao().deleteOverridesForSchedule(assignedId)

            val targets = schedule.targetPackages.map { ScheduleTargetCrossRef(assignedId, it) }
            database.blockScheduleDao().insertTargets(targets)

            val overrides = schedule.overrides.map { (pkg, ov) ->
                ScheduleOverrideEntity(
                    scheduleId = assignedId,
                    packageName = pkg,
                    durationMs = ov.durationMs,
                    reinterventionMs = ov.reinterventionMs
                )
            }
            if (overrides.isNotEmpty()) {
                database.blockScheduleDao().insertOverrides(overrides)
            }
        }
        return result.map { assignedId }
    }

    suspend fun toggleSchedule(scheduleId: Long, enabled: Boolean): Result<Unit> = mutateAndPublish {
        database.blockScheduleDao().updateEnabled(scheduleId, enabled)
    }

    suspend fun deleteSchedule(scheduleId: Long): Result<Unit> = mutateAndPublish {
        database.blockScheduleDao().deleteSchedule(scheduleId)
    }

    suspend fun updateSettings(update: (AppSettingsEntity) -> AppSettingsEntity): Result<Unit> = mutateAndPublish {
        val current = database.appSettingsDao().getSettings() ?: AppSettingsEntity(id = 1)
        val updated = update(current).copy(id = 1, rowVersion = current.rowVersion + 1L)
        database.appSettingsDao().setSettings(updated)
    }

    suspend fun setTheme(themeId: io.ronesec.android.ui.designsystem.ThemeId): Result<Unit> = updateSettings {
        it.copy(themeId = themeId.name)
    }

    suspend fun setLanguage(language: String): Result<Unit> = updateSettings {
        it.copy(language = language)
    }

    suspend fun setShowOverlayStats(show: Boolean): Result<Unit> = updateSettings {
        it.copy(showOverlayStats = show)
    }

    suspend fun setSavedSessionMinutes(minutes: Int): Result<Unit> = updateSettings {
        it.copy(savedSessionMinutes = minutes)
    }

    suspend fun setCustomEmergencyMinutes(minutes: Int?): Result<Unit> = updateSettings {
        it.copy(customEmergencyMinutes = minutes)
    }
}
