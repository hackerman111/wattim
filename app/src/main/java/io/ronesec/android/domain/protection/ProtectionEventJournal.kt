package io.ronesec.android.domain.protection

import java.time.Instant
import java.util.ArrayDeque

data class JournalEntry(
    val timestamp: Instant = Instant.now(),
    val sessionId: Long? = null,
    val eventType: String,
    val packageName: String? = null,
    val windowId: Int? = null,
    val stateBefore: String,
    val decision: String? = null,
    val stateAfter: String,
    val effects: List<String> = emptyList(),
    val durationMs: Long = 0L
) {
    fun toJson(): String {
        val effectStr = effects.joinToString(",", prefix = "[", postfix = "]") { "\"$it\"" }
        return buildString {
            append("{")
            append("\"timestamp\":${timestamp.toEpochMilli()},")
            append("\"sessionId\":${sessionId ?: "null"},")
            append("\"eventType\":\"$eventType\",")
            append("\"packageName\":${packageName?.let { "\"$it\"" } ?: "null"},")
            append("\"windowId\":${windowId ?: "null"},")
            append("\"stateBefore\":\"$stateBefore\",")
            append("\"decision\":${decision?.let { "\"$it\"" } ?: "null"},")
            append("\"stateAfter\":\"$stateAfter\",")
            append("\"effects\":$effectStr,")
            append("\"durationMs\":$durationMs")
            append("}")
        }
    }
}

class ProtectionEventJournal(
    private val capacity: Int = 256
) {
    private val lock = Any()
    private val buffer = ArrayDeque<JournalEntry>(capacity)

    fun record(entry: JournalEntry) = synchronized(lock) {
        if (buffer.size >= capacity) {
            buffer.removeFirst()
        }
        buffer.addLast(entry)
    }

    fun record(
        sessionId: SessionId?,
        eventType: String,
        packageName: String?,
        stateBefore: ProtectionState,
        stateAfter: ProtectionState,
        effects: List<ProtectionEffect> = emptyList(),
        decision: String? = null,
        durationMs: Long = 0L,
        windowId: Int? = null
    ) {
        record(
            JournalEntry(
                sessionId = sessionId?.value,
                eventType = eventType,
                packageName = packageName,
                windowId = windowId,
                stateBefore = stateBefore::class.simpleName ?: stateBefore.toString(),
                decision = decision,
                stateAfter = stateAfter::class.simpleName ?: stateAfter.toString(),
                effects = effects.map { it::class.simpleName ?: it.toString() },
                durationMs = durationMs
            )
        )
    }

    fun getEntries(): List<JournalEntry> = synchronized(lock) {
        buffer.toList()
    }

    fun clear() = synchronized(lock) {
        buffer.clear()
    }

    fun exportJson(): String = synchronized(lock) {
        buffer.joinToString(separator = ",\n", prefix = "[\n", postfix = "\n]") { "  ${it.toJson()}" }
    }
}
