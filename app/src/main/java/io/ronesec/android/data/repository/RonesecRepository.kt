package io.ronesec.android.data.repository

import android.content.Context
import io.ronesec.android.data.local.AppDatabase
import io.ronesec.android.data.local.entity.AccessGrantEntity
import io.ronesec.android.data.local.entity.AppSettingEntity
import io.ronesec.android.data.local.entity.BlockScheduleEntity
import io.ronesec.android.data.local.entity.BlockSessionEntity
import io.ronesec.android.data.local.entity.OpenAttemptEntity
import io.ronesec.android.data.local.entity.TargetAppEntity
import io.ronesec.android.domain.engine.RuntimeState
import io.ronesec.android.domain.model.AccessGrant
import io.ronesec.android.domain.model.AttemptOutcome
import io.ronesec.android.domain.model.BlockSchedule
import io.ronesec.android.domain.model.BlockSession
import io.ronesec.android.domain.model.OpenAttempt
import io.ronesec.android.domain.model.TargetApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Instant

class RonesecRepository private constructor(
    private val database: AppDatabase,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val targetAppDao = database.targetAppDao()
    private val openAttemptDao = database.openAttemptDao()
    private val accessGrantDao = database.accessGrantDao()
    private val blockDao = database.blockDao()
    private val settingsDao = database.settingsDao()

    // In-memory hot-cache state for sub-millisecond RuleEngine evaluation
    private val _runtimeState = MutableStateFlow(RuntimeState())
    val runtimeState = _runtimeState.asStateFlow()

    private val activeGrantsMap = java.util.concurrent.ConcurrentHashMap<String, AccessGrant>()
    private val lastExitMap = java.util.concurrent.ConcurrentHashMap<String, Instant>()

    init {
        scope.launch {
            accessGrantDao.clearAll()
        }

        // Collect database updates into in-memory hot cache
        scope.launch {
            targetAppDao.getAllFlow().collect { entities ->
                val targets = entities.associate { it.packageName to it.toDomain() }
                _runtimeState.value = _runtimeState.value.copy(targets = targets)
            }
        }

        scope.launch {
            accessGrantDao.getAllFlow().collect { entities ->
                val grants = entities.associate { it.packageName to it.toDomain() }
                activeGrantsMap.clear()
                activeGrantsMap.putAll(grants)
                _runtimeState.value = _runtimeState.value.copy(activeGrants = HashMap(activeGrantsMap))
            }
        }

        scope.launch {
            blockDao.getAllSchedulesFlow().collect { entities ->
                val schedules = entities.map { it.toDomain() }
                _runtimeState.value = _runtimeState.value.copy(blockSchedules = schedules)
            }
        }

        scope.launch {
            val now = System.currentTimeMillis()
            blockDao.getActiveSessionsFlow(now).collect { entities ->
                val sessions = entities.map { it.toDomain() }
                _runtimeState.value = _runtimeState.value.copy(activeBlockSessions = sessions)
            }
        }
    }

    fun getHotRuntimeState(): RuntimeState {
        // Merge in-memory active grants and exit timestamps for immediate Grace evaluation
        return _runtimeState.value.copy(
            activeGrants = HashMap(activeGrantsMap),
            lastExitTimes = HashMap(lastExitMap)
        )
    }

    fun getTargetsFlow(): Flow<List<TargetApp>> {
        return targetAppDao.getAllFlow().map { list -> list.map { it.toDomain() } }
    }

    suspend fun getTarget(packageName: String): TargetApp? {
        return targetAppDao.getByPackage(packageName)?.toDomain()
    }

    suspend fun saveTarget(target: TargetApp) {
        targetAppDao.insertOrUpdate(TargetAppEntity.fromDomain(target))
    }

    suspend fun deleteTarget(packageName: String) {
        targetAppDao.deleteByPackage(packageName)
        accessGrantDao.deleteByPackage(packageName)
        lastExitMap.remove(packageName)
    }

    suspend fun recordAttempt(packageName: String, outcome: AttemptOutcome, timestamp: Instant = Instant.now()) {
        openAttemptDao.insert(
            OpenAttemptEntity(
                packageName = packageName,
                timestamp = timestamp.toEpochMilli(),
                outcome = outcome
            )
        )
    }

    fun getRecentAttemptsFlow(sinceTimestamp: Long): Flow<List<OpenAttempt>> {
        return openAttemptDao.getSinceFlow(sinceTimestamp).map { list -> list.map { it.toDomain() } }
    }

    fun getAllAttemptsFlow(): Flow<List<OpenAttempt>> {
        return openAttemptDao.getAllFlow().map { list -> list.map { it.toDomain() } }
    }

    fun getAllAvoidedCountFlow(): Flow<Int> {
        return openAttemptDao.getAllAvoidedCountFlow()
    }

    fun getAvoidedCountSinceFlow(sinceTimestamp: Long): Flow<Int> {
        return openAttemptDao.getAvoidedCountSinceFlow(sinceTimestamp)
    }

    suspend fun getAvoidedCountAllTime(): Int = openAttemptDao.countAllAvoided()

    suspend fun getAvoidedCountSince(sinceTimestamp: Long): Int = openAttemptDao.countAvoidedSince(sinceTimestamp)

    suspend fun getRecentAttemptsCount(packageName: String, periodMinutes: Int): Int {
        val sinceTimestamp = System.currentTimeMillis() - periodMinutes * 60_000L
        return openAttemptDao.countAttemptsByPackageSince(packageName, sinceTimestamp)
    }

    suspend fun grantAccess(packageName: String, reinterventionMs: Long?) {
        val now = Instant.now()
        val expiresAt = reinterventionMs?.let { now.plusMillis(it) }
        val grant = AccessGrant(
            packageName = packageName,
            createdAt = now,
            expiresAt = expiresAt
        )
        activeGrantsMap[packageName] = grant
        _runtimeState.value = _runtimeState.value.copy(activeGrants = HashMap(activeGrantsMap))
        accessGrantDao.insertOrUpdate(AccessGrantEntity.fromDomain(grant))
    }

    suspend fun revokeAccess(packageName: String) {
        activeGrantsMap.remove(packageName)
        _runtimeState.value = _runtimeState.value.copy(activeGrants = HashMap(activeGrantsMap))
        accessGrantDao.deleteByPackage(packageName)
    }

    fun recordExit(packageName: String, exitTime: Instant = Instant.now()) {
        lastExitMap[packageName] = exitTime
        activeGrantsMap.remove(packageName)
        _runtimeState.value = _runtimeState.value.copy(
            activeGrants = HashMap(activeGrantsMap),
            lastExitTimes = HashMap(lastExitMap)
        )
        scope.launch {
            accessGrantDao.deleteByPackage(packageName)
        }
    }

    suspend fun startHardBlock(name: String, durationMinutes: Int, packages: Set<String>): Long {
        val now = Instant.now()
        val endTime = now.plusSeconds(durationMinutes * 60L)
        val session = BlockSession(
            name = name,
            startTime = now,
            endTime = endTime,
            active = true,
            packages = packages
        )
        return blockDao.insertSession(BlockSessionEntity.fromDomain(session))
    }

    suspend fun stopHardBlock(sessionId: Long) {
        blockDao.deactivateSession(sessionId)
    }

    fun getActiveSessionsFlow(nowEpochMs: Long = System.currentTimeMillis()): Flow<List<BlockSession>> {
        return blockDao.getActiveSessionsFlow(nowEpochMs).map { list -> list.map { it.toDomain() } }
    }

    fun getSchedulesFlow(): Flow<List<BlockSchedule>> {
        return blockDao.getAllSchedulesFlow().map { list -> list.map { it.toDomain() } }
    }

    suspend fun saveSchedule(schedule: BlockSchedule): Long {
        return blockDao.insertOrUpdateSchedule(BlockScheduleEntity.fromDomain(schedule))
    }

    suspend fun deleteSchedule(id: Long) {
        blockDao.deleteScheduleById(id)
    }

    fun getSettingFlow(key: String): Flow<String?> {
        return settingsDao.getFlow(key).distinctUntilChanged()
    }

    suspend fun getSetting(key: String): String? {
        return settingsDao.get(key)
    }

    suspend fun setSetting(key: String, value: String) {
        settingsDao.set(AppSettingEntity(key = key, value = value))
    }

    companion object {
        @Volatile
        private var INSTANCE: RonesecRepository? = null

        fun getInstance(context: Context): RonesecRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RonesecRepository(AppDatabase.getInstance(context)).also { INSTANCE = it }
            }
        }
    }
}
