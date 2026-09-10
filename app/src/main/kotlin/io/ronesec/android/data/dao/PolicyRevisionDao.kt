package io.ronesec.android.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.ronesec.android.data.entity.PolicyRevisionEntity

@Dao
interface PolicyRevisionDao {
    @Query("SELECT revision FROM policy_revision WHERE id = 1")
    suspend fun getRevision(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setRevision(entity: PolicyRevisionEntity)

    @Query("UPDATE policy_revision SET revision = revision + 1 WHERE id = 1")
    suspend fun incrementRevision(): Int
}
