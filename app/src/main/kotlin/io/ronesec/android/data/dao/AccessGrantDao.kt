package io.ronesec.android.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.ronesec.android.data.entity.AccessGrantEntity

@Dao
interface AccessGrantDao {
    @Query("SELECT * FROM access_grants WHERE expiresAt > :nowEpochMs")
    suspend fun getActiveGrants(nowEpochMs: Long): List<AccessGrantEntity>

    @Query("SELECT * FROM access_grants WHERE packageName = :packageName AND expiresAt > :nowEpochMs")
    suspend fun getGrant(packageName: String, nowEpochMs: Long): AccessGrantEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(grant: AccessGrantEntity)

    @Query("DELETE FROM access_grants WHERE packageName = :packageName AND grantId = :grantId")
    suspend fun deleteGrant(packageName: String, grantId: String): Int

    @Query("DELETE FROM access_grants WHERE packageName = :packageName")
    suspend fun deleteForPackage(packageName: String): Int

    @Query("DELETE FROM access_grants WHERE expiresAt <= :nowEpochMs")
    suspend fun deleteExpired(nowEpochMs: Long): Int
}
