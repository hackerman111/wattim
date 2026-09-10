package io.ronesec.android.data.dao

import androidx.room.Dao
import androidx.room.Query

data class PerAppStatRow(
    val packageName: String,
    val displayNameAtAttempt: String,
    val totalOpenings: Int,
    val totalClosed: Int
)

@Dao
interface StatisticsDao {

    @Query(
        """
        SELECT COUNT(*) 
        FROM open_attempts 
        WHERE timestamp >= :startEpochMs 
          AND timestamp < :endEpochMs 
          AND outcome IN ('CONTINUED', 'ABANDONED', 'BLOCKED')
        """
    )
    suspend fun getTodayTotalAttempts(startEpochMs: Long, endEpochMs: Long): Int

    @Query(
        """
        SELECT COUNT(*) 
        FROM open_attempts 
        WHERE timestamp >= :startEpochMs 
          AND timestamp < :endEpochMs 
          AND outcome = 'CONTINUED'
        """
    )
    suspend fun getTodayContinuedCount(startEpochMs: Long, endEpochMs: Long): Int

    @Query(
        """
        SELECT COUNT(*) 
        FROM open_attempts 
        WHERE timestamp >= :startEpochMs 
          AND timestamp < :endEpochMs 
          AND outcome IN ('ABANDONED', 'BLOCKED')
        """
    )
    suspend fun getTodayClosedCount(startEpochMs: Long, endEpochMs: Long): Int

    @Query(
        """
        SELECT COUNT(*) 
        FROM open_attempts 
        WHERE outcome IN ('ABANDONED', 'BLOCKED')
        """
    )
    suspend fun getAllTimeClosedCount(): Int

    @Query(
        """
        SELECT 
            packageName, 
            displayNameAtAttempt, 
            COUNT(*) AS totalOpenings, 
            SUM(CASE WHEN outcome IN ('ABANDONED', 'BLOCKED') THEN 1 ELSE 0 END) AS totalClosed
        FROM open_attempts 
        WHERE timestamp >= :startEpochMs 
          AND timestamp < :endEpochMs 
          AND outcome IN ('CONTINUED', 'ABANDONED', 'BLOCKED')
        GROUP BY packageName 
        ORDER BY totalOpenings DESC, displayNameAtAttempt ASC, packageName ASC
        """
    )
    suspend fun getPerAppStatsToday(startEpochMs: Long, endEpochMs: Long): List<PerAppStatRow>
}
