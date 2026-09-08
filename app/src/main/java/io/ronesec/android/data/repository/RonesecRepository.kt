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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

class RonesecRepository private constructor(
    private val database: AppDatabase,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val targetAppDao = database.targetAppDao()
    private val openAttemptDao = database.openAttemptDao()
    private val accessGrantDao = database.accessGrantDao()
    private val blockDao = database.blockDao()
    private val settingsDao = database.settingsDao()

    private val _runtimeState = MutableStateFlow(RuntimeState())
    val runtimeState = _runtimeState.asStateFlow()

    private val _isPolicyReady = MutableStateFlow(false)
    val isPolicyReady = _isPolicyReady.asStateFlow()

    init {
        val readySourcesCount = AtomicInteger(0)
        fun markSourceReady() {
            if (readySourcesCount.incrementAndGet() >= 4) {
                _isPolicyReady.value = true
            }
        }

        scope.launch { accessGrantDao.deleteExpired(System.currentTimeMillis()) }
        // Collect database updates into in-memory hot cache atomically
        scope.launch {
            targetAppDao.getAllFlow().collect { entities ->
                val targets = entities.associate { it.packageName to it.toDomain() }
                _runtimeState.update { it.copy(targets = targets) }
                markSourceReady()
            }
        }

        scope.launch {
            accessGrantDao.getAllFlow().collect { entities ->
                val grants = entities.associate { it.packageName to it.toDomain() }
                _runtimeState.update { it.copy(activeGrants = grants) }
                markSourceReady()
            }
        }

        scope.launch {
            blockDao.getAllSchedulesFlow().collect { entities ->
                val schedules = entities.map { it.toDomain() }
                _runtimeState.update { it.copy(blockSchedules = schedules) }
                markSourceReady()
            }
        }

        scope.launch {
            val now = System.currentTimeMillis()
            blockDao.getActiveSessionsFlow(now).collect { entities ->
                val sessions = entities.map { it.toDomain() }
                _runtimeState.update { it.copy(activeBlockSessions = sessions) }
            }
        }

        scope.launch {
            settingsDao.getFlow("protection_paused_until").collect { pausedUntilStr ->
                val pausedUntil = pausedUntilStr?.toLongOrNull()
                _runtimeState.update { it.copy(protectionPausedUntil = pausedUntil) }
                markSourceReady()
            }
        }
    }

    fun getHotRuntimeState(): RuntimeState = _runtimeState.value

    fun getTargetsFlow(): Flow<List<TargetApp>> {
        return targetAppDao.getAllFlow().map { list -> list.map { it.toDomain() } }
    }

    fun getActiveTargetCountFlow(): Flow<Int> {
        return targetAppDao.getActiveCountFlow().distinctUntilChanged()
    }

    fun getAppStatsSinceFlow(sinceTimestamp: Long): Flow<List<io.ronesec.android.data.local.dao.AppAttemptStat>> {
        return openAttemptDao.getAppStatsSinceFlow(sinceTimestamp)
    }

    suspend fun getTarget(packageName: String): TargetApp? {
        return targetAppDao.getByPackage(packageName)?.toDomain()
    }

    suspend fun saveTarget(target: TargetApp) {
        targetAppDao.insertOrUpdate(TargetAppEntity.fromDomain(target))
    }

    suspend fun updateTargetEnabled(packageName: String, enabled: Boolean) {
        val target = getTarget(packageName) ?: return
        saveTarget(target.copy(enabled = enabled))
        if (!enabled) {
            accessGrantDao.deleteByPackage(packageName)
            clearSessionPermit(packageName)
        }
    }

    suspend fun deleteTarget(packageName: String) {
        targetAppDao.deleteByPackage(packageName)
        accessGrantDao.deleteByPackage(packageName)
        _runtimeState.update { current ->
            current.copy(
                targets = current.targets - packageName,
                activeGrants = current.activeGrants - packageName,
                activeSessionPermits = current.activeSessionPermits - packageName,
                lastExitTimes = current.lastExitTimes - packageName
            )
        }
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

    // Session permit: in-memory RAM permit tied strictly to sessionId
    fun grantSessionPermit(packageName: String, sessionId: Long) {
        _runtimeState.update { current ->
            current.copy(activeSessionPermits = current.activeSessionPermits + (packageName to sessionId))
        }
    }

    fun clearSessionPermit(packageName: String, sessionId: Long? = null) {
        _runtimeState.update { current ->
            val existing = current.activeSessionPermits[packageName]
            if (sessionId == null || existing == sessionId) {
                current.copy(activeSessionPermits = current.activeSessionPermits - packageName)
            } else {
                current
            }
        }
    }

    // Timed permit: persistent with explicit expiration timestamp
    suspend fun grantTimedAccess(packageName: String, durationMs: Long) {
        val now = Instant.now()
        val expiresAt = now.plusMillis(durationMs)
        val grant = AccessGrant(
            packageName = packageName,
            createdAt = now,
            expiresAt = expiresAt
        )
        _runtimeState.update { current ->
            current.copy(activeGrants = current.activeGrants + (packageName to grant))
        }
        accessGrantDao.insertOrUpdate(AccessGrantEntity.fromDomain(grant))
    }

    // Backwards-compatible grantAccess
    suspend fun grantAccess(packageName: String, reinterventionMs: Long?, sessionId: Long? = null) {
        if (sessionId != null) {
            grantSessionPermit(packageName, sessionId)
        }
        if (reinterventionMs != null && reinterventionMs > 0L) {
            grantTimedAccess(packageName, reinterventionMs)
        }
    }

    suspend fun revokeAccess(packageName: String, sessionId: Long? = null) {
        var shouldRevokeDb = false
        _runtimeState.update { current ->
            val existing = current.activeSessionPermits[packageName]
            if (sessionId != null && existing != null && existing != sessionId) {
                current
            } else {
                shouldRevokeDb = true
                current.copy(
                    activeSessionPermits = current.activeSessionPermits - packageName,
                    activeGrants = current.activeGrants - packageName
                )
            }
        }
        if (shouldRevokeDb) {
            accessGrantDao.deleteByPackage(packageName)
        }
    }

    fun recordExit(packageName: String, exitTime: Instant = Instant.now(), sessionId: Long? = null) {
        _runtimeState.update { current ->
            val existing = current.activeSessionPermits[packageName]
            if (sessionId != null && existing != null && existing != sessionId) {
                current
            } else {
                current.copy(
                    lastExitTimes = current.lastExitTimes + (packageName to exitTime),
                    activeSessionPermits = current.activeSessionPermits - packageName
                )
            }
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

    suspend fun pauseProtection(durationMinutes: Int) {
        val targetTime = if (durationMinutes == -1) {
            -1L
        } else {
            System.currentTimeMillis() + durationMinutes * 60_000L
        }
        _runtimeState.update { it.copy(protectionPausedUntil = targetTime) }
        setSetting("protection_paused_until", targetTime.toString())
    }

    suspend fun resumeProtection() {
        _runtimeState.update { it.copy(protectionPausedUntil = null) }
        setSetting("protection_paused_until", "")
    }

    fun getProtectionPausedUntilFlow(): Flow<Long?> {
        return getSettingFlow("protection_paused_until").map { it?.toLongOrNull() }
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
