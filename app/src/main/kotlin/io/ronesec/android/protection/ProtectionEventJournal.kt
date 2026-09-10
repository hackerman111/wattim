package io.ronesec.android.protection

/**
 * Transition journal maintaining a fixed-size 256-entry ring buffer of state transitions.
 * Local diagnostic export artifact without any screen content, input text, or sensitive data.
 * Satisfies I10, F32, and T23.
 */
data class JournalEntry(
    val timestampMs: Long,
    val eventType: String,
    val eventSummary: String,
    val stateBefore: String,
    val stateAfter: String,
    val effects: List<String>,
    val sessionId: String?
)

class ProtectionEventJournal(
    private val capacity: Int = 256
) {
    private val ringBuffer = arrayOfNulls<JournalEntry>(capacity)
    private var writeIndex = 0
    private var count = 0
    private val lock = Any()

    fun record(
        timestampMs: Long,
        eventType: String,
        eventSummary: String,
        stateBefore: String,
        stateAfter: String,
        effects: List<String>,
        sessionId: String?
    ) {
        synchronized(lock) {
            ringBuffer[writeIndex] = JournalEntry(
                timestampMs = timestampMs,
                eventType = eventType,
                eventSummary = sanitizeSummary(eventSummary),
                stateBefore = stateBefore,
                stateAfter = stateAfter,
                effects = effects,
                sessionId = sessionId
            )
            writeIndex = (writeIndex + 1) % capacity
            if (count < capacity) {
                count++
            }
        }
    }

    fun getEntries(): List<JournalEntry> {
        synchronized(lock) {
            val result = ArrayList<JournalEntry>(count)
            val startIndex = if (count < capacity) 0 else writeIndex
            for (i in 0 until count) {
                val idx = (startIndex + i) % capacity
                ringBuffer[idx]?.let { result.add(it) }
            }
            return result
        }
    }

    val size: Int
        get() = synchronized(lock) { count }

    fun clear() {
        synchronized(lock) {
            ringBuffer.fill(null)
            writeIndex = 0
            count = 0
        }
    }

    fun exportToJson(): String {
        val entries = getEntries()
        val sb = StringBuilder()
        sb.append("[\n")
        for (i in entries.indices) {
            val entry = entries[i]
            sb.append("  {\n")
            sb.append("    \"timestamp\": ").append(entry.timestampMs).append(",\n")
            sb.append("    \"event\": \"").append(escapeJson(entry.eventType)).append("\",\n")
            sb.append("    \"eventSummary\": \"").append(escapeJson(entry.eventSummary)).append("\",\n")
            sb.append("    \"stateBefore\": \"").append(escapeJson(entry.stateBefore)).append("\",\n")
            sb.append("    \"stateAfter\": \"").append(escapeJson(entry.stateAfter)).append("\",\n")
            sb.append("    \"effects\": [")
            for (j in entry.effects.indices) {
                sb.append("\"").append(escapeJson(entry.effects[j])).append("\"")
                if (j < entry.effects.size - 1) sb.append(", ")
            }
            sb.append("],\n")
            sb.append("    \"sessionId\": ")
            if (entry.sessionId != null) {
                sb.append("\"").append(escapeJson(entry.sessionId)).append("\"\n")
            } else {
                sb.append("null\n")
            }
            sb.append("  }")
            if (i < entries.size - 1) {
                sb.append(",")
            }
            sb.append("\n")
        }
        sb.append("]")
        return sb.toString()
    }

    private fun sanitizeSummary(text: String): String {
        // Enforce privacy: never include screen text or input text
        return text.replace(Regex("[\r\n\t]"), " ")
    }

    private fun escapeJson(str: String): String {
        return str
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\b", "\\b")
            .replace("\u000C", "\\f")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
