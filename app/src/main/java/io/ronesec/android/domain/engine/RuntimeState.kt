package io.ronesec.android.domain.engine

import io.ronesec.android.domain.model.AccessGrant
import io.ronesec.android.domain.model.BlockSchedule
import io.ronesec.android.domain.model.BlockSession
import io.ronesec.android.domain.model.TargetApp
import java.time.Instant

data class RuntimeState(
    val targets: Map<String, TargetApp> = emptyMap(),
    val activeGrants: Map<String, AccessGrant> = emptyMap(),
    val lastExitTimes: Map<String, Instant> = emptyMap(),
    val activeBlockSessions: List<BlockSession> = emptyList(),
    val blockSchedules: List<BlockSchedule> = emptyList()
)
