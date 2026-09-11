package io.ronesec.domain.breathing

object AttentionCheckSchedule {
    fun generate(
        durationMs: Long,
        count: Int,
        randomProvider: (Long, Long) -> Long = { min, max -> if (max > min) (min..max).random() else min }
    ): List<Long> {
        if (count <= 0 || durationMs <= 0) return emptyList()
        val minOffset = (durationMs * 0.15).toLong().coerceAtLeast(100L)
        val maxOffset = (durationMs * 0.85).toLong().coerceAtMost(durationMs - 100L)
        if (maxOffset <= minOffset) return emptyList()

        val segmentSize = (maxOffset - minOffset) / count
        if (segmentSize <= 0) return emptyList()

        return (0 until count).map { i ->
            val segStart = minOffset + i * segmentSize
            val segEnd = segStart + segmentSize
            randomProvider(segStart, segEnd)
        }.sorted()
    }
}
