package io.ronesec.android.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.ronesec.android.data.local.entity.BlockScheduleEntity
import io.ronesec.android.data.local.entity.BlockSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockDao {
    // Sessions
    @Query("SELECT * FROM block_sessions WHERE active = 1 AND endTime > :nowEpochMs ORDER BY endTime DESC")
    fun getActiveSessionsFlow(nowEpochMs: Long): Flow<List<BlockSessionEntity>>

    @Query("SELECT * FROM block_sessions WHERE active = 1 AND endTime > :nowEpochMs")
    suspend fun getActiveSessions(nowEpochMs: Long): List<BlockSessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: BlockSessionEntity): Long

    @Query("UPDATE block_sessions SET active = 0 WHERE id = :sessionId")
    suspend fun deactivateSession(sessionId: Long)

    @Query("UPDATE block_sessions SET active = 0 WHERE endTime <= :nowEpochMs")
    suspend fun deactivateExpiredSessions(nowEpochMs: Long)

    // Schedules
    @Query("SELECT * FROM block_schedules ORDER BY name ASC")
    fun getAllSchedulesFlow(): Flow<List<BlockScheduleEntity>>

    @Query("SELECT * FROM block_schedules WHERE enabled = 1")
    suspend fun getActiveSchedules(): List<BlockScheduleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSchedule(schedule: BlockScheduleEntity): Long

    @Update
    suspend fun updateSchedule(schedule: BlockScheduleEntity)

    @Delete
    suspend fun deleteSchedule(schedule: BlockScheduleEntity)

    @Query("DELETE FROM block_schedules WHERE id = :scheduleId")
    suspend fun deleteScheduleById(scheduleId: Long)
}
