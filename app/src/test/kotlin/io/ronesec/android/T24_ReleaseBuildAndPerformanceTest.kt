package io.ronesec.android

import io.ronesec.android.platform.accessibility.EventIngress
import io.ronesec.android.platform.time.TemporalBoundaryScheduler
import io.ronesec.android.protection.ProtectionEventJournal
import io.ronesec.domain.breathing.BreathingPhase
import io.ronesec.domain.breathing.BreathingTimeline
import io.ronesec.domain.protection.ProtectionEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.system.measureNanoTime

/**
 * T24: Release build invariants, R8/ProGuard configuration, offline privacy,
 * performance benchmarks, and battery safety constraints.
 *
 * Covers:
 * - F79: Release/install, R8/resource shrink, offline APK, targetSdk/distribution review.
 * - F80: Efficient monitoring and rendering, timeline math performance, zero polling, bounded ingress.
 * - F02, I10: Offline isolation and backup exclusions.
 * - Section 11.2 (T24), Section 12 (S10), Section 14 (DoD).
 */
class T24_ReleaseBuildAndPerformanceTest {

    @Test
    fun releaseBuildConfigurationAndProguardRules_F79() {
        val proguardFile = findFile("proguard-rules.pro")
        assertTrue("proguard-rules.pro must exist", proguardFile.exists())

        val proguardContent = proguardFile.readText()
        // Room preservation
        assertTrue("ProGuard must keep RoomDatabase", proguardContent.contains("androidx.room.RoomDatabase"))
        assertTrue("ProGuard must keep Room @Dao", proguardContent.contains("@androidx.room.Dao"))
        assertTrue("ProGuard must keep Room @Entity", proguardContent.contains("@androidx.room.Entity"))
        assertTrue("ProGuard must keep Room entity package", proguardContent.contains("io.ronesec.android.data.entity.**"))
        assertTrue("ProGuard must keep Room DAO package", proguardContent.contains("io.ronesec.android.data.dao.**"))

        // Domain models preservation
        assertTrue("ProGuard must keep domain models", proguardContent.contains("io.ronesec.domain.model.**"))
        assertTrue("ProGuard must keep domain protection", proguardContent.contains("io.ronesec.domain.protection.**"))

        // Android Platform entrypoints
        assertTrue("ProGuard must keep WattimApplication", proguardContent.contains("io.ronesec.android.WattimApplication"))
        assertTrue("ProGuard must keep MainActivity", proguardContent.contains("io.ronesec.android.ui.MainActivity"))
        assertTrue("ProGuard must keep InterventionActivity", proguardContent.contains("io.ronesec.android.ui.intervention.InterventionActivity"))
        assertTrue("ProGuard must keep AppMonitorService", proguardContent.contains("io.ronesec.android.platform.accessibility.AppMonitorService"))
        assertTrue("ProGuard must keep FocusForegroundService", proguardContent.contains("io.ronesec.android.platform.system.FocusForegroundService"))
        assertTrue("ProGuard must keep BootReceiver", proguardContent.contains("io.ronesec.android.platform.system.BootReceiver"))

        // Compose
        assertTrue("ProGuard must keep Composable members", proguardContent.contains("@androidx.compose.runtime.Composable"))

        // Gradle build configuration
        val buildGradleFile = findFile("build.gradle.kts")
        assertTrue("build.gradle.kts must exist", buildGradleFile.exists())
        val buildContent = buildGradleFile.readText()

        assertTrue("Must enable isMinifyEnabled for release", buildContent.contains("isMinifyEnabled = true"))
        assertTrue("Must enable isShrinkResources for release", buildContent.contains("isShrinkResources = true"))
        assertTrue("Must reference proguard-rules.pro", buildContent.contains("proguard-rules.pro"))
        assertTrue("Must use the passwordless release store", buildContent.contains("wattim-release-passwordless.p12"))
        assertFalse("Must not read a keystore password", buildContent.contains("KEYSTORE_PASSWORD"))
        assertFalse("Must not read a private-key password", buildContent.contains("KEY_PASSWORD"))
        assertFalse("Must not embed the debug keystore password", buildContent.contains("storePassword = \"android\""))
    }

    @Test
    fun offlinePrivacyAndDataExtractionRules_F02_F79_I10() {
        val dataExtractionFile = findFile("src/main/res/xml/data_extraction_rules.xml")
        assertTrue("data_extraction_rules.xml must exist", dataExtractionFile.exists())
        val dataExtractionContent = dataExtractionFile.readText()
        assertTrue("Must exclude cloud-backup", dataExtractionContent.contains("<cloud-backup>"))
        assertTrue("Must exclude device-transfer", dataExtractionContent.contains("<device-transfer>"))
        assertTrue("Must exclude all paths with path=\".\"", dataExtractionContent.contains("path=\".\""))

        val backupRulesFile = findFile("src/main/res/xml/backup_rules.xml")
        assertTrue("backup_rules.xml must exist", backupRulesFile.exists())
        val backupContent = backupRulesFile.readText()
        assertTrue("Must exclude root domain", backupContent.contains("domain=\"root\""))
        assertTrue("Must exclude file domain", backupContent.contains("domain=\"file\""))
        assertTrue("Must exclude database domain", backupContent.contains("domain=\"database\""))
        assertTrue("Must exclude sharedpref domain", backupContent.contains("domain=\"sharedpref\""))

        val manifestFile = findFile("src/main/AndroidManifest.xml")
        assertTrue("AndroidManifest.xml must exist", manifestFile.exists())
        val manifestContent = manifestFile.readText()

        assertFalse("Must NEVER declare INTERNET permission (F02, I10)", manifestContent.contains("android.permission.INTERNET"))
        assertFalse("Must NEVER declare ACCESS_NETWORK_STATE permission", manifestContent.contains("android.permission.ACCESS_NETWORK_STATE"))
        assertTrue("Manifest must disable allowBackup", manifestContent.contains("android:allowBackup=\"false\""))
        assertTrue("Manifest must declare dataExtractionRules", manifestContent.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\""))
        assertTrue("Manifest must declare fullBackupContent", manifestContent.contains("android:fullBackupContent=\"@xml/backup_rules\""))
    }

    @Test
    fun nativeAbiFiltersAndPlatformLimits_F01_F79() {
        val buildGradleFile = findFile("build.gradle.kts")
        val buildContent = buildGradleFile.readText()

        assertTrue("minSdk must be 29 (F01)", buildContent.contains("minSdk = 29"))
        assertTrue("Must declare arm64-v8a ABI", buildContent.contains("\"arm64-v8a\""))
        assertTrue("Must declare armeabi-v7a ABI", buildContent.contains("\"armeabi-v7a\""))
        assertTrue("Must declare x86_64 ABI", buildContent.contains("\"x86_64\""))
    }

    @Test
    fun breathingTimelineExecutionPerformance_F80_T14() {
        val iterations = 5_000
        val startMs = 1_000_000L
        val durationMs = 8_000L

        // Warmup
        for (i in 0 until 500) {
            BreathingTimeline.calculate(startMs, startMs + i * 16L, durationMs)
        }

        // Measure timeline calculation time
        val elapsedNanos = measureNanoTime {
            for (i in 0 until iterations) {
                val nowMs = startMs + (i % 8000L)
                val progress = BreathingTimeline.calculate(startMs, nowMs, durationMs)
                assertNotNull(progress)
            }
        }

        val averageMicros = (elapsedNanos / iterations.toDouble()) / 1_000.0
        assertTrue(
            "BreathingTimeline.calculate must take < 50 microseconds per call on average (measured ${averageMicros}µs)",
            averageMicros < 50.0
        )

        // Mathematical invariants
        val atStart = BreathingTimeline.calculate(startMs, startMs, durationMs)
        assertEquals(0.0, atStart.t, 0.0001)
        assertEquals(0.0, atStart.p, 0.0001)
        assertEquals(BreathingPhase.INHALE, atStart.phase)
        assertFalse(atStart.isComplete)

        val atMid = BreathingTimeline.calculate(startMs, startMs + 4_000L, durationMs)
        assertEquals(0.5, atMid.t, 0.0001)
        assertEquals(1.0, atMid.p, 0.0001)
        assertEquals(BreathingPhase.EXHALE, atMid.phase)
        assertFalse(atMid.isComplete)

        val atEnd = BreathingTimeline.calculate(startMs, startMs + 8_000L, durationMs)
        assertEquals(1.0, atEnd.t, 0.0001)
        assertEquals(0.0, atEnd.p, 0.0001)
        assertEquals(BreathingPhase.COMPLETE, atEnd.phase)
        assertTrue(atEnd.isComplete)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun temporalBoundarySchedulerIsEventDrivenWithoutPolling_F80_T05() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        var boundaryFiredCount = 0
        var lastTokenFired: Long? = null

        val scheduler = TemporalBoundaryScheduler(
            scope = testScope,
            dispatcher = testDispatcher,
            onBoundaryReached = { token ->
                boundaryFiredCount++
                lastTokenFired = token
            }
        )

        // Schedule boundary in 5000ms
        scheduler.schedule(5_000L, 101L)
        testScheduler.advanceTimeBy(100)
        assertEquals("Boundary should not fire early", 0, boundaryFiredCount)

        // Advance before boundary
        testScheduler.advanceTimeBy(4_800)
        assertEquals("Boundary should still not have fired at 4900ms", 0, boundaryFiredCount)

        // Advance past boundary
        testScheduler.advanceTimeBy(200)
        assertEquals("Boundary must fire exactly when delay expires without polling", 1, boundaryFiredCount)
        assertEquals(101L, lastTokenFired)

        // Cancel test
        scheduler.schedule(10_000L, 102L)
        testScheduler.advanceTimeBy(1_000)
        scheduler.cancel()
        testScheduler.advanceTimeBy(20_000)
        assertEquals("Canceled boundary must not fire", 1, boundaryFiredCount)
    }

    @Test
    fun eventIngressQueueBoundsAndOverloadProtection_F80_T09() {
        val ingress = EventIngress(foregroundCapacity = 16)
        ingress.setGeneration(1L)

        // Non-coalescible alternating sequence: com.app.a, com.app.b, com.app.a...
        // 1000 events must not cause unbounded memory growth
        for (i in 0 until 1_000) {
            val pkg = if (i % 2 == 0) "com.pkg.alpha" else "com.pkg.beta"
            ingress.sendForegroundCandidate(
                ProtectionEvent.ForegroundCandidate(
                    packageName = pkg,
                    sourceUptimeMs = i * 10L,
                    eventSequence = i.toLong()
                )
            )
        }

        val highWaterMark = ingress.currentHighWaterMark
        assertTrue("High water mark must never exceed capacity (16)", highWaterMark <= 16)
        assertTrue("Overflow resync must have been triggered on burst overload", ingress.currentOverflowResyncCount > 0)
    }

    @Test
    fun eventJournalRingBufferCapacityAndPrivacySanitization_F80_T23() {
        val journal = ProtectionEventJournal(capacity = 256)

        // Record 500 transitions
        for (i in 0 until 500) {
            journal.record(
                timestampMs = 1000L + i,
                eventType = "TEST_EVENT",
                eventSummary = "Event index $i with possible secret input",
                stateBefore = "READY",
                stateAfter = "INTERVENING",
                effects = listOf("SHOW_OVERLAY"),
                sessionId = "session-$i"
            )
        }

        val entries = journal.getEntries()
        assertEquals("Journal ring buffer must be capped at exactly 256 entries (F32)", 256, entries.size)

        // Check earliest remaining entry is entry 244 (500 - 256)
        assertEquals("session-244", entries.first().sessionId)
        assertEquals("session-499", entries.last().sessionId)

        // Export JSON verification
        val jsonString = journal.exportToJson()
        assertNotNull(jsonString)
        val jsonArray = JSONArray(jsonString)
        assertEquals(256, jsonArray.length())

        // Verify JSON contains metadata only, no raw screen text or unauthorized tokens
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            assertTrue(obj.has("timestamp"))
            assertTrue(obj.has("event"))
            assertTrue(obj.has("stateBefore"))
            assertTrue(obj.has("stateAfter"))
            assertTrue(obj.has("effects"))
            assertFalse(obj.has("screenContent"))
            assertFalse(obj.has("userInput"))
        }
    }

    private fun findFile(relativePath: String): File {
        val direct = File(relativePath)
        if (direct.exists()) return direct
        val fromApp = File("app", relativePath)
        if (fromApp.exists()) return fromApp
        val fromRoot = File("../app", relativePath)
        if (fromRoot.exists()) return fromRoot
        return direct
    }
}
