package io.ronesec.android.domain.engine

import io.ronesec.android.domain.model.AccessGrant
import io.ronesec.android.domain.model.AllowReason
import io.ronesec.android.domain.model.BlockSchedule
import io.ronesec.android.domain.model.BlockSession
import io.ronesec.android.domain.model.Decision
import io.ronesec.android.domain.model.InterventionConfig
import io.ronesec.android.domain.model.ScheduleAppOverride
import io.ronesec.android.domain.model.ScheduleType
import io.ronesec.android.domain.model.TargetApp
import io.ronesec.android.domain.protection.SessionId
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

class SharedRuleEngineSpecTest {

    private val ruleEngine = RuleEngine()

    private fun findSpecDir(): File {
        val candidates = listOf(
            File("spec/rule-engine"),
            File("../spec/rule-engine"),
            File("../../spec/rule-engine")
        )
        return candidates.firstOrNull { it.exists() && it.isDirectory }
            ?: error("spec/rule-engine directory not found in candidates: $candidates")
    }

    @Test
    fun executeAllSharedRuleEngineFixtures() {
        val specDir = findSpecDir()
        val fixtureNames = listOf(
            "allow.json",
            "grants.json",
            "schedules.json",
            "overnight.json",
            "hard-block.json",
            "backoff.json",
            "priority.json"
        )

        var totalPassed = 0
        for (fixtureName in fixtureNames) {
            val file = File(specDir, fixtureName)
            assertTrue("Fixture file must exist: ${file.path}", file.exists())
            val root = JSONObject(file.readText())
            val cases = root.getJSONArray("cases")

            for (i in 0 until cases.length()) {
                val caseObj = cases.getJSONObject(i)
                val id = caseObj.getString("id")
                val target = caseObj.getString("target")
                val nowMs = caseObj.getLong("now")
                val now = Instant.ofEpochMilli(nowMs)
                val zoneId = if (caseObj.has("zoneId")) ZoneId.of(caseObj.getString("zoneId")) else ZoneId.systemDefault()
                val recentAttemptsCount = caseObj.optInt("recentAttemptsCount", 0)
                val sessionId = if (caseObj.has("sessionId")) SessionId(caseObj.getLong("sessionId")) else null

                val stateObj = caseObj.getJSONObject("state")
                val globalPauseUntil = stateObj.optLong("globalPauseUntil", 0L).takeIf { it > 0L }

                val targetsMap = mutableMapOf<String, TargetApp>()
                val targetsArray = stateObj.optJSONArray("targets")
                if (targetsArray != null) {
                    for (t in 0 until targetsArray.length()) {
                        val tObj = targetsArray.getJSONObject(t)
                        val pkg = tObj.optString("packageName", tObj.optString("domain"))
                        val enabled = tObj.optBoolean("enabled", true)
                        val durationMs = tObj.optLong("durationMs", (tObj.optInt("durationSeconds", 10) * 1000).toLong())
                        val exponentialGrowth = tObj.optBoolean("exponentialGrowthEnabled", true)
                        val growthPercent = tObj.optInt("growthPercent", 20)

                        targetsMap[pkg] = TargetApp(
                            packageName = pkg,
                            displayName = pkg,
                            enabled = enabled,
                            intervention = InterventionConfig(
                                durationMs = durationMs,
                                exponentialGrowthEnabled = exponentialGrowth,
                                growthPercent = growthPercent
                            )
                        )
                    }
                }

                val activeSessions = mutableListOf<BlockSession>()
                val sessionsArray = stateObj.optJSONArray("activeBlockSessions")
                if (sessionsArray != null) {
                    for (s in 0 until sessionsArray.length()) {
                        val sObj = sessionsArray.getJSONObject(s)
                        val sId = sObj.optLong("id", 1L)
                        val sName = sObj.optString("name", "Session")
                        val active = sObj.optBoolean("active", true)
                        val startTime = Instant.ofEpochMilli(sObj.getLong("startTime"))
                        val endTime = Instant.ofEpochMilli(sObj.getLong("endTime"))
                        val pkgs = mutableSetOf<String>()
                        val pkgsArray = sObj.optJSONArray("packages")
                        if (pkgsArray != null) {
                            for (p in 0 until pkgsArray.length()) pkgs.add(pkgsArray.getString(p))
                        }
                        activeSessions.add(
                            BlockSession(
                                id = sId,
                                name = sName,
                                startTime = startTime,
                                endTime = endTime,
                                packages = pkgs,
                                active = active
                            )
                        )
                    }
                }

                val schedules = mutableListOf<BlockSchedule>()
                val schedulesArray = stateObj.optJSONArray("schedules")
                if (schedulesArray != null) {
                    for (s in 0 until schedulesArray.length()) {
                        val sObj = schedulesArray.getJSONObject(s)
                        val sId = sObj.optLong("id", 1L)
                        val sName = sObj.optString("name", "Schedule")
                        val enabled = sObj.optBoolean("enabled", true)
                        val typeStr = sObj.optString("scheduleType", "INTERVENTION")
                        val type = ScheduleType.valueOf(typeStr)
                        val start = LocalTime.parse(sObj.getString("start"))
                        val end = LocalTime.parse(sObj.getString("end"))
                        val days = mutableSetOf<DayOfWeek>()
                        val daysArray = sObj.optJSONArray("days")
                        if (daysArray != null) {
                            for (d in 0 until daysArray.length()) {
                                days.add(DayOfWeek.valueOf(daysArray.getString(d).uppercase()))
                            }
                        }
                        val pkgs = mutableSetOf<String>()
                        val pkgsArray = sObj.optJSONArray("packages")
                        if (pkgsArray != null) {
                            for (p in 0 until pkgsArray.length()) pkgs.add(pkgsArray.getString(p))
                        }
                        val appOverrides = mutableMapOf<String, ScheduleAppOverride>()
                        if (sObj.has("customDurationMs")) {
                            for (pkg in pkgs) {
                                appOverrides[pkg] = ScheduleAppOverride(durationMs = sObj.getLong("customDurationMs"))
                            }
                        }
                        schedules.add(
                            BlockSchedule(
                                id = sId,
                                name = sName,
                                enabled = enabled,
                                scheduleType = type,
                                days = days,
                                start = start,
                                end = end,
                                packages = pkgs,
                                appOverrides = appOverrides
                            )
                        )
                    }
                }

                val grantsMap = mutableMapOf<String, AccessGrant>()
                val grantsArray = stateObj.optJSONArray("grants")
                if (grantsArray != null) {
                    for (g in 0 until grantsArray.length()) {
                        val gObj = grantsArray.getJSONObject(g)
                        val gTarget = gObj.optString("target", gObj.optString("packageName"))
                        val expiresAt = Instant.ofEpochMilli(gObj.getLong("expiresAt"))
                        grantsMap[gTarget] = AccessGrant(
                            packageName = gTarget,
                            createdAt = now,
                            expiresAt = expiresAt
                        )
                    }
                }

                val sessionPermitsMap = mutableMapOf<String, Long>()
                val permitsObj = stateObj.optJSONObject("sessionPermits")
                if (permitsObj != null) {
                    for (key in permitsObj.keys()) {
                        sessionPermitsMap[key] = permitsObj.getLong(key)
                    }
                }

                val runtimeState = RuntimeState(
                    targets = targetsMap,
                    activeBlockSessions = activeSessions,
                    blockSchedules = schedules,
                    activeGrants = grantsMap,
                    activeSessionPermits = sessionPermitsMap,
                    protectionPausedUntil = globalPauseUntil
                )

                val decision = ruleEngine.evaluate(
                    packageName = target,
                    now = now,
                    state = runtimeState,
                    zoneId = zoneId,
                    recentAttemptsCount = recentAttemptsCount,
                    sessionId = sessionId
                )

                val expectedObj = caseObj.getJSONObject("expected")
                val expectedAction = expectedObj.getString("action")

                when (expectedAction) {
                    "ALLOW" -> {
                        assertTrue("[$fixtureName::$id] Expected Decision.Allow, got $decision", decision is Decision.Allow)
                        val allowDecision = decision as Decision.Allow
                        if (expectedObj.has("reason")) {
                            val expectedReason = AllowReason.valueOf(expectedObj.getString("reason"))
                            assertEquals("[$fixtureName::$id] Reason mismatch", expectedReason, allowDecision.reason)
                        }
                    }
                    "BLOCK" -> {
                        assertTrue("[$fixtureName::$id] Expected Decision.Block, got $decision", decision is Decision.Block)
                        val blockDecision = decision as Decision.Block
                        if (expectedObj.has("until")) {
                            val expectedUntil = Instant.ofEpochMilli(expectedObj.getLong("until"))
                            assertEquals("[$fixtureName::$id] Until mismatch", expectedUntil, blockDecision.until)
                        }
                    }
                    "INTERVENTION" -> {
                        assertTrue("[$fixtureName::$id] Expected Decision.Intervention, got $decision", decision is Decision.Intervention)
                        val interventionDecision = decision as Decision.Intervention
                        if (expectedObj.has("durationMs")) {
                            val expectedDurationMs = expectedObj.getLong("durationMs")
                            assertEquals("[$fixtureName::$id] DurationMs mismatch", expectedDurationMs, interventionDecision.config.durationMs)
                        }
                    }
                    else -> error("Unknown expected action: $expectedAction")
                }
                totalPassed++
            }
        }
        println("SharedRuleEngineSpecTest successfully executed and passed $totalPassed test cases!")
    }
}
