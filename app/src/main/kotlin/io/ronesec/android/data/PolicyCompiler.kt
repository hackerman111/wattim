package io.ronesec.android.data

import io.ronesec.android.data.dao.PackageTimestamp
import io.ronesec.android.data.entity.AccessGrantEntity
import io.ronesec.android.data.entity.AppSettingsEntity
import io.ronesec.android.data.entity.BlockScheduleEntity
import io.ronesec.android.data.entity.BlockSessionEntity
import io.ronesec.android.data.entity.BlockSessionTargetCrossRef
import io.ronesec.android.data.entity.ScheduleOverrideEntity
import io.ronesec.android.data.entity.ScheduleTargetCrossRef
import io.ronesec.android.data.entity.TargetAppEntity
import io.ronesec.domain.model.AnimationMode
import io.ronesec.domain.model.BackoffConfig
import io.ronesec.domain.model.CompiledBlockSession
import io.ronesec.domain.model.CompiledSchedule
import io.ronesec.domain.model.GlobalPause
import io.ronesec.domain.model.GrantOrigin
import io.ronesec.domain.model.RuntimePolicySnapshot
import io.ronesec.domain.model.ScheduleOverride
import io.ronesec.domain.model.ScheduleType
import io.ronesec.domain.model.TargetConfig
import io.ronesec.domain.model.TimedGrant
import java.time.Instant

object PolicyCompiler {

    fun toTargetConfig(entity: TargetAppEntity): TargetConfig {
        val anim = try {
            AnimationMode.valueOf(entity.animation)
        } catch (_: Exception) {
            AnimationMode.FILL
        }
        return TargetConfig(
            packageName = entity.packageName,
            displayName = entity.displayName,
            enabled = entity.enabled,
            phrase = entity.phrase,
            animation = anim,
            durationMs = entity.durationMs,
            reinterventionMs = entity.reinterventionMs,
            quickReturnGraceMs = entity.quickReturnGraceMs,
            growthConfig = BackoffConfig(
                enabled = entity.growthEnabled,
                percent = entity.growthPercent,
                windowMs = entity.growthWindowMs
            ),
            rowVersion = entity.rowVersion,
            twoStageUnlock = entity.twoStageUnlock,
            unlockCodeLength = entity.unlockCodeLength,
            requireEmergencyCode = entity.requireEmergencyCode,
            randomDurationEnabled = entity.randomDurationEnabled,
            randomMaxDurationMs = entity.randomMaxDurationMs
        )
    }

    fun toTargetEntity(config: TargetConfig): TargetAppEntity {
        return TargetAppEntity(
            packageName = config.packageName,
            displayName = config.displayName,
            enabled = config.enabled,
            phrase = config.phrase,
            animation = config.animation.name,
            durationMs = config.durationMs,
            reinterventionMs = config.reinterventionMs,
            quickReturnGraceMs = config.quickReturnGraceMs,
            growthEnabled = config.growthConfig.enabled,
            growthPercent = config.growthConfig.percent,
            growthWindowMs = config.growthConfig.windowMs,
            rowVersion = config.rowVersion,
            twoStageUnlock = config.twoStageUnlock,
            unlockCodeLength = config.unlockCodeLength,
            requireEmergencyCode = config.requireEmergencyCode,
            randomDurationEnabled = config.randomDurationEnabled,
            randomMaxDurationMs = config.randomMaxDurationMs
        )
    }

    fun toTimedGrant(entity: AccessGrantEntity): TimedGrant {
        val origin = try {
            GrantOrigin.valueOf(entity.origin)
        } catch (_: Exception) {
            GrantOrigin.REINTERVENTION
        }
        return TimedGrant(
            packageName = entity.packageName,
            grantId = entity.grantId,
            origin = origin,
            createdAt = Instant.ofEpochMilli(entity.createdAt),
            expiresAt = Instant.ofEpochMilli(entity.expiresAt),
            grantVersion = entity.grantVersion
        )
    }

    fun toAccessGrantEntity(grant: TimedGrant): AccessGrantEntity {
        return AccessGrantEntity(
            packageName = grant.packageName,
            grantId = grant.grantId,
            origin = grant.origin.name,
            createdAt = grant.createdAt.toEpochMilli(),
            expiresAt = grant.expiresAt.toEpochMilli(),
            grantVersion = grant.grantVersion
        )
    }

    fun toCompiledBlockSession(
        entity: BlockSessionEntity,
        targets: Set<String>
    ): CompiledBlockSession {
        return CompiledBlockSession(
            id = entity.id,
            name = entity.name,
            startTime = Instant.ofEpochMilli(entity.startTime),
            endTime = Instant.ofEpochMilli(entity.endTime),
            active = entity.active,
            targetPackages = targets
        )
    }

    fun toCompiledSchedule(
        entity: BlockScheduleEntity,
        targets: Set<String>,
        overrides: List<ScheduleOverrideEntity>
    ): CompiledSchedule {
        val type = try {
            ScheduleType.valueOf(entity.type)
        } catch (_: Exception) {
            ScheduleType.HARD_BLOCK
        }

        val overrideMap = overrides
            .filter { it.scheduleId == entity.id && targets.contains(it.packageName) }
            .associate {
                it.packageName to ScheduleOverride(
                    durationMs = it.durationMs,
                    reinterventionMs = it.reinterventionMs
                )
            }

        return CompiledSchedule(
            id = entity.id,
            name = entity.name,
            weekdayMask = entity.weekdayMask,
            startMinute = entity.startMinute,
            endMinute = entity.endMinute,
            enabled = entity.enabled,
            type = type,
            targetPackages = targets,
            overrides = overrideMap
        )
    }

    fun toGlobalPause(entity: AppSettingsEntity?): GlobalPause {
        if (entity == null) return GlobalPause.None
        return when (entity.pauseKind.uppercase()) {
            "UNTIL" -> {
                val untilMs = entity.pauseUntil
                if (untilMs != null) GlobalPause.Until(Instant.ofEpochMilli(untilMs)) else GlobalPause.None
            }
            "INDEFINITE" -> GlobalPause.Indefinite
            else -> GlobalPause.None
        }
    }

    fun compileSnapshot(
        revision: Long,
        targets: List<TargetAppEntity>,
        grants: List<AccessGrantEntity>,
        sessions: List<BlockSessionEntity>,
        sessionTargets: List<BlockSessionTargetCrossRef>,
        schedules: List<BlockScheduleEntity>,
        scheduleTargets: List<ScheduleTargetCrossRef>,
        scheduleOverrides: List<ScheduleOverrideEntity>,
        settings: AppSettingsEntity?,
        recentEntries: List<PackageTimestamp>
    ): RuntimePolicySnapshot {
        val targetMap = targets.associate { it.packageName to toTargetConfig(it) }
        val grantMap = grants.associate { it.packageName to toTimedGrant(it) }

        val targetsBySessionId = sessionTargets.groupBy({ it.blockSessionId }, { it.packageName })
        val compiledSessions = sessions.map { session ->
            val pkgs = (targetsBySessionId[session.id] ?: emptyList()).toSet()
            toCompiledBlockSession(session, pkgs)
        }

        val targetsByScheduleId = scheduleTargets.groupBy({ it.scheduleId }, { it.packageName })
        val compiledSchedules = schedules.map { schedule ->
            val pkgs = (targetsByScheduleId[schedule.id] ?: emptyList()).toSet()
            toCompiledSchedule(schedule, pkgs, scheduleOverrides)
        }

        val globalPause = toGlobalPause(settings)

        val recentTimestampsMap = recentEntries
            .groupBy({ it.packageName }, { it.timestamp })

        return RuntimePolicySnapshot(
            revision = revision,
            targets = targetMap,
            activeGrants = grantMap,
            activeBlockSessions = compiledSessions,
            activeSchedules = compiledSchedules,
            globalPause = globalPause,
            recentEntryTimestamps = recentTimestampsMap
        )
    }
}
