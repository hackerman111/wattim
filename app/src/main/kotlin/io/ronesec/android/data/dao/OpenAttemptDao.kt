package io.ronesec.android.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.ronesec.android.data.entity.OpenAttemptEntity

data class PackageTimestamp(
    val packageName: String,
    val timestamp: Long
)

@Dao
interface OpenAttemptDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIdempotent(attempt: OpenAttemptEntity): Long

    @Query("UPDATE open_attempts SET outcome = :outcome, resolvedAt = :resolvedAt WHERE attemptId = :attemptId AND outcome IS NULL")
    suspend fun finalizeAttempt(attemptId: String, outcome: String, resolvedAt: Long): Int

    @Query("UPDATE open_attempts SET outcome = 'INTERRUPTED', resolvedAt = :resolvedAt WHERE outcome IS NULL AND generation != :currentGeneration")
    suspend fun finalizeInterruptedAttempts(currentGeneration: Long, resolvedAt: Long): Int

    @Query("UPDATE open_attempts SET outcome = 'INTERRUPTED', resolvedAt = :resolvedAt WHERE outcome IS NULL")
    suspend fun finalizeAllUnresolvedAttempts(resolvedAt: Long): Int

    @Query("SELECT * FROM open_attempts WHERE packageName = :packageName AND kind = 'ENTRY' AND outcome = 'CONTINUED' AND timestamp > :sinceTimestamp ORDER BY timestamp ASC")
    suspend fun getRecentEntryAttempts(packageName: String, sinceTimestamp: Long): List<OpenAttemptEntity>

    @Query("SELECT packageName, timestamp FROM open_attempts WHERE kind = 'ENTRY' AND outcome = 'CONTINUED' AND timestamp > :sinceTimestamp ORDER BY timestamp ASC")
    suspend fun getAllRecentEntryTimestamps(sinceTimestamp: Long): List<PackageTimestamp>

    @Query("SELECT * FROM open_attempts WHERE attemptId = :attemptId")
    suspend fun getAttempt(attemptId: String): OpenAttemptEntity?
}
