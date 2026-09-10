package io.ronesec.android.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.ronesec.android.data.entity.BlockSessionEntity
import io.ronesec.android.data.entity.BlockSessionTargetCrossRef

@Dao
interface BlockSessionDao {
    @Query("SELECT * FROM block_sessions WHERE active = 1 AND endTime > :nowEpochMs")
    suspend fun getActiveSessions(nowEpochMs: Long): List<BlockSessionEntity>

    @Query("SELECT * FROM block_sessions WHERE id = :id")
    suspend fun getSession(id: String): BlockSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: BlockSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTargets(crossRefs: List<BlockSessionTargetCrossRef>)

    @Query("SELECT packageName FROM block_session_targets WHERE blockSessionId = :sessionId")
    suspend fun getTargetPackagesForSession(sessionId: String): List<String>

    @Query("SELECT * FROM block_session_targets")
    suspend fun getAllSessionTargets(): List<BlockSessionTargetCrossRef>

    @Query("UPDATE block_sessions SET active = 0, rowVersion = rowVersion + 1 WHERE id = :sessionId AND active = 1")
    suspend fun deactivateSession(sessionId: String): Int
}
