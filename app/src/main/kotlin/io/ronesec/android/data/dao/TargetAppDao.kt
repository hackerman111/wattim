package io.ronesec.android.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.ronesec.android.data.entity.TargetAppEntity

@Dao
interface TargetAppDao {
    @Query("SELECT * FROM target_apps ORDER BY displayName ASC, packageName ASC")
    suspend fun getAllTargets(): List<TargetAppEntity>

    @Query("SELECT * FROM target_apps WHERE packageName = :packageName")
    suspend fun getTarget(packageName: String): TargetAppEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(target: TargetAppEntity)

    @Query("UPDATE target_apps SET enabled = :enabled, rowVersion = rowVersion + 1 WHERE packageName = :packageName")
    suspend fun updateEnabled(packageName: String, enabled: Boolean): Int

    @Query("DELETE FROM target_apps WHERE packageName = :packageName")
    suspend fun deleteTarget(packageName: String): Int

    @Query("SELECT COUNT(*) FROM target_apps")
    suspend fun count(): Int
}
