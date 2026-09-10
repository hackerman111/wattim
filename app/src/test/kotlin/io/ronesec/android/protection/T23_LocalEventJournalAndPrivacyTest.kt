package io.ronesec.android.protection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T23: Local event journal ring buffer, privacy guarantees, and JSON diagnostic export.
 * F32, Section 2.4, Section 11.2 (T23).
 */
class T23_LocalEventJournalAndPrivacyTest {

    @Test
    fun journal_capsAt256Entries() {
        val journal = ProtectionEventJournal(capacity = 256)

        for (i in 1..300) {
            journal.record(
                timestampMs = 1000L + i,
                eventType = "Event-$i",
                eventSummary = "summary $i",
                stateBefore = "StateBefore",
                stateAfter = "StateAfter",
                effects = listOf("EffectA", "EffectB"),
                sessionId = "session-$i"
            )
        }

        assertEquals(256, journal.size)
        val entries = journal.getEntries()
        assertEquals(256, entries.size)

        // Oldest preserved entry should be 300 - 256 + 1 = 45
        assertEquals("Event-45", entries.first().eventType)
        assertEquals("Event-300", entries.last().eventType)
    }

    @Test
    fun journal_exportsValidJson_withoutScreenOrInputText() {
        val journal = ProtectionEventJournal(capacity = 256)

        journal.record(
            timestampMs = 1725900000000L,
            eventType = "ForegroundCandidate",
            eventSummary = "package=com.example.target, seq=1",
            stateBefore = "Idle",
            stateAfter = "Intervening",
            effects = listOf("ShowIntervention", "AcquireAudioLease"),
            sessionId = "100-1-1"
        )

        val json = journal.exportToJson()

        assertTrue("Must start with [", json.trimStart().startsWith("["))
        assertTrue("Must end with ]", json.trimEnd().endsWith("]"))
        assertTrue("Contains timestamp", json.contains("\"timestamp\": 1725900000000"))
        assertTrue("Contains event", json.contains("\"event\": \"ForegroundCandidate\""))
        assertTrue("Contains sessionId", json.contains("\"sessionId\": \"100-1-1\""))
        assertTrue("Contains effects array", json.contains("\"effects\": [\"ShowIntervention\", \"AcquireAudioLease\"]"))

        // Privacy check: no view hierarchy, input fields, passwords or content text
        assertFalse("No view hierarchy", json.contains("viewId"))
        assertFalse("No input text", json.contains("inputText"))
        assertFalse("No password", json.contains("password"))
    }

    @Test
    fun journal_handlesSpecialCharactersInJson() {
        val journal = ProtectionEventJournal(capacity = 256)

        journal.record(
            timestampMs = 1000L,
            eventType = "Special\"Event\\Test",
            eventSummary = "Line1\nLine2\tTabbed",
            stateBefore = "Before",
            stateAfter = "After",
            effects = listOf("Effect\"Quote\""),
            sessionId = null
        )

        val json = journal.exportToJson()
        assertTrue(json.contains("\"sessionId\": null"))
        assertTrue(json.contains("Special\\\"Event\\\\Test"))
    }
}
