package io.ronesec.android.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import io.ronesec.android.domain.model.BlockSession
import java.time.Instant

@Entity(tableName = "block_sessions")
data class BlockSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val startTime: Long,
    val endTime: Long,
    val active: Boolean,
    val packages: String // Comma-separated package names
) {
    fun toDomain(): BlockSession = BlockSession(
        id = id,
        name = name,
        startTime = Instant.ofEpochMilli(startTime),
        endTime = Instant.ofEpochMilli(endTime),
        active = active,
        packages = if (packages.isBlank()) emptySet() else packages.split(",").toSet()
    )

    companion object {
        fun fromDomain(domain: BlockSession): BlockSessionEntity = BlockSessionEntity(
            id = domain.id,
            name = domain.name,
            startTime = domain.startTime.toEpochMilli(),
            endTime = domain.endTime.toEpochMilli(),
            active = domain.active,
            packages = domain.packages.joinToString(",")
        )
    }
}
