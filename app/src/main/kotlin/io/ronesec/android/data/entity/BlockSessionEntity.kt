package io.ronesec.android.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "block_sessions",
    indices = [
        Index(value = ["active", "endTime"])
    ]
)
data class BlockSessionEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val startTime: Long,
    val endTime: Long,
    val active: Boolean,
    val rowVersion: Long = 1L
)

@Entity(
    tableName = "block_session_targets",
    primaryKeys = ["blockSessionId", "packageName"],
    foreignKeys = [
        ForeignKey(
            entity = BlockSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["blockSessionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TargetAppEntity::class,
            parentColumns = ["packageName"],
            childColumns = ["packageName"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["packageName"])
    ]
)
data class BlockSessionTargetCrossRef(
    val blockSessionId: String,
    val packageName: String
)
