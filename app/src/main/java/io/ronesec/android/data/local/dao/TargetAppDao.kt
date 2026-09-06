package io.ronesec.android.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.ronesec.android.data.local.entity.TargetAppEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TargetAppDao {
    @Query("SELECT * FROM target_apps ORDER BY displayName ASC")
    fun getAllFlow(): Flow<List<TargetAppEntity>>

    @Query("SELECT * FROM target_apps")
    suspend fun getAll(): List<TargetAppEntity>

    @Query("SELECT * FROM target_apps WHERE packageName = :packageName LIMIT 1")
    suspend fun getByPackage(packageName: String): TargetAppEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entity: TargetAppEntity)

    @Update
    suspend fun update(entity: TargetAppEntity)

    @Delete
    suspend fun delete(entity: TargetAppEntity)

    @Query("DELETE FROM target_apps WHERE packageName = :packageName")
    suspend fun deleteByPackage(packageName: String)
}
