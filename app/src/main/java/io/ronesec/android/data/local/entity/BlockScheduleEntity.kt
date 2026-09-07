package io.ronesec.android.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import io.ronesec.android.domain.model.BlockSchedule
import io.ronesec.android.domain.model.ScheduleAppOverride
import io.ronesec.android.domain.model.ScheduleType
import java.time.DayOfWeek
import java.time.LocalTime

@Entity(tableName = "block_schedules")
data class BlockScheduleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val daysOfWeek: String, // e.g. "MONDAY,TUESDAY,WEDNESDAY"
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val packages: String, // Comma-separated package names
    val enabled: Boolean,
    val scheduleType: String = "HARD_BLOCK",
    val appOverridesJson: String? = null
) {
    fun toDomain(): BlockSchedule = BlockSchedule(
        id = id,
        name = name,
        days = if (daysOfWeek.isBlank()) emptySet() else daysOfWeek.split(",").map { DayOfWeek.valueOf(it) }.toSet(),
        start = LocalTime.of(startHour, startMinute),
        end = LocalTime.of(endHour, endMinute),
        packages = if (packages.isBlank()) emptySet() else packages.split(",").toSet(),
        enabled = enabled,
        scheduleType = try {
            ScheduleType.valueOf(scheduleType)
        } catch (e: Exception) {
            ScheduleType.HARD_BLOCK
        },
        appOverrides = parseAppOverrides(appOverridesJson)
    )

    companion object {
        fun fromDomain(domain: BlockSchedule): BlockScheduleEntity = BlockScheduleEntity(
            id = domain.id,
            name = domain.name,
            daysOfWeek = domain.days.joinToString(",") { it.name },
            startHour = domain.start.hour,
            startMinute = domain.start.minute,
            endHour = domain.end.hour,
            endMinute = domain.end.minute,
            packages = domain.packages.joinToString(","),
            enabled = domain.enabled,
            scheduleType = domain.scheduleType.name,
            appOverridesJson = serializeAppOverrides(domain.appOverrides)
        )

        fun parseAppOverrides(json: String?): Map<String, ScheduleAppOverride> {
            if (json.isNullOrBlank()) return emptyMap()
            val result = mutableMapOf<String, ScheduleAppOverride>()
            val entryRegex = Regex("\"([^\"]+)\"\\s*:\\s*\\{([^}]*)\\}")
            val durationRegex = Regex("\"durationMs\"\\s*:\\s*(\\d+)")
            val reinterventionRegex = Regex("\"reinterventionMs\"\\s*:\\s*(\\d+)")

            entryRegex.findAll(json).forEach { match ->
                val pkg = match.groupValues[1]
                val content = match.groupValues[2]
                val duration = durationRegex.find(content)?.groupValues?.get(1)?.toLongOrNull()
                val reintervention = reinterventionRegex.find(content)?.groupValues?.get(1)?.toLongOrNull()
                result[pkg] = ScheduleAppOverride(durationMs = duration, reinterventionMs = reintervention)
            }
            return result
        }

        fun serializeAppOverrides(overrides: Map<String, ScheduleAppOverride>): String? {
            if (overrides.isEmpty()) return null
            return buildString {
                append("{")
                var firstEntry = true
                for ((pkg, override) in overrides) {
                    if (!firstEntry) append(",")
                    firstEntry = false
                    append("\"").append(pkg).append("\":{")
                    val fields = mutableListOf<String>()
                    if (override.durationMs != null) {
                        fields.add("\"durationMs\":${override.durationMs}")
                    }
                    if (override.reinterventionMs != null) {
                        fields.add("\"reinterventionMs\":${override.reinterventionMs}")
                    }
                    append(fields.joinToString(","))
                    append("}")
                }
                append("}")
            }
        }
    }
}
