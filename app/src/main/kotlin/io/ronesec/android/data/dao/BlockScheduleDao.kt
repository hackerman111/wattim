package io.ronesec.android.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.ronesec.android.data.entity.BlockScheduleEntity
import io.ronesec.android.data.entity.ScheduleOverrideEntity
import io.ronesec.android.data.entity.ScheduleTargetCrossRef

@Dao
interface BlockScheduleDao {
    @Query("SELECT * FROM block_schedules WHERE enabled = 1")
    suspend fun getActiveSchedules(): List<BlockScheduleEntity>

    @Query("SELECT * FROM block_schedules ORDER BY id ASC")
    suspend fun getAllSchedules(): List<BlockScheduleEntity>

    @Query("SELECT * FROM block_schedules WHERE id = :id")
    suspend fun getSchedule(id: Long): BlockScheduleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedule(schedule: BlockScheduleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTargets(crossRefs: List<ScheduleTargetCrossRef>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOverrides(overrides: List<ScheduleOverrideEntity>)

    @Query("DELETE FROM schedule_targets WHERE scheduleId = :scheduleId")
    suspend fun deleteTargetsForSchedule(scheduleId: Long)

    @Query("DELETE FROM schedule_overrides WHERE scheduleId = :scheduleId")
    suspend fun deleteOverridesForSchedule(scheduleId: Long)

    @Query("UPDATE block_schedules SET enabled = :enabled, rowVersion = rowVersion + 1 WHERE id = :scheduleId")
    suspend fun updateEnabled(scheduleId: Long, enabled: Boolean): Int

    @Query("DELETE FROM block_schedules WHERE id = :scheduleId")
    suspend fun deleteSchedule(scheduleId: Long): Int

    @Query("SELECT * FROM schedule_targets WHERE scheduleId = :scheduleId")
    suspend fun getTargetsForSchedule(scheduleId: Long): List<ScheduleTargetCrossRef>

    @Query("SELECT * FROM schedule_targets")
    suspend fun getAllScheduleTargets(): List<ScheduleTargetCrossRef>

    @Query("SELECT * FROM schedule_overrides WHERE scheduleId = :scheduleId")
    suspend fun getOverridesForSchedule(scheduleId: Long): List<ScheduleOverrideEntity>

    @Query("SELECT * FROM schedule_overrides")
    suspend fun getAllScheduleOverrides(): List<ScheduleOverrideEntity>
}
