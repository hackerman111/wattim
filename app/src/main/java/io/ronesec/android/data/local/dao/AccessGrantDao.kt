package io.ronesec.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.ronesec.android.data.local.entity.AccessGrantEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccessGrantDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entity: AccessGrantEntity)

    @Query("SELECT * FROM access_grants WHERE packageName = :packageName LIMIT 1")
    suspend fun getByPackage(packageName: String): AccessGrantEntity?

    @Query("SELECT * FROM access_grants")
    suspend fun getAll(): List<AccessGrantEntity>

    @Query("SELECT * FROM access_grants")
    fun getAllFlow(): Flow<List<AccessGrantEntity>>

    @Query("DELETE FROM access_grants WHERE packageName = :packageName")
    suspend fun deleteByPackage(packageName: String)

    @Query("DELETE FROM access_grants WHERE expiresAt IS NOT NULL AND expiresAt < :nowEpochMs")
    suspend fun deleteExpired(nowEpochMs: Long)

    @Query("DELETE FROM access_grants")
    suspend fun clearAll()
}
