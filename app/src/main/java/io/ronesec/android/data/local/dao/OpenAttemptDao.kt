package io.ronesec.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import io.ronesec.android.data.local.entity.OpenAttemptEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OpenAttemptDao {
    @Insert
    suspend fun insert(entity: OpenAttemptEntity): Long

    @Query("SELECT * FROM open_attempts ORDER BY timestamp DESC")
    fun getAllFlow(): Flow<List<OpenAttemptEntity>>

    @Query("SELECT * FROM open_attempts WHERE timestamp >= :sinceTimestamp ORDER BY timestamp DESC")
    fun getSinceFlow(sinceTimestamp: Long): Flow<List<OpenAttemptEntity>>

    @Query("SELECT * FROM open_attempts WHERE packageName = :packageName ORDER BY timestamp DESC")
    fun getByPackageFlow(packageName: String): Flow<List<OpenAttemptEntity>>

    @Query("SELECT COUNT(*) FROM open_attempts WHERE timestamp >= :sinceTimestamp")
    suspend fun countSince(sinceTimestamp: Long): Int

    @Query("SELECT COUNT(*) FROM open_attempts WHERE timestamp >= :sinceTimestamp AND outcome = 'CONTINUED'")
    suspend fun countContinuedSince(sinceTimestamp: Long): Int

    @Query("SELECT COUNT(*) FROM open_attempts WHERE timestamp >= :sinceTimestamp AND outcome = 'ABANDONED'")
    suspend fun countAbandonedSince(sinceTimestamp: Long): Int
}
