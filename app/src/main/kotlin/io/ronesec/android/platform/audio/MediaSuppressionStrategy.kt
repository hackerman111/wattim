package io.ronesec.android.platform.audio

/**
 * Strategy interface governing audio suppression and fallback behavior.
 * Preserves §2.6 and Section 6 of IMPLEMENTATION_PLAN.md: focus is always primary;
 * bounded media-PAUSE burst is a reliability fallback for non-cooperating media players.
 */
interface MediaSuppressionStrategy {
    val executeFallbackBurst: Boolean
    val burstDelaysMs: List<Long>
        get() = listOf(0L, 150L, 250L, 400L, 400L)
}

/**
 * Default strategy executing bounded PAUSE key events at cumulative offsets:
 * 0ms, 150ms, 400ms, 800ms, 1200ms.
 */
object BoundedPauseFallbackStrategy : MediaSuppressionStrategy {
    override val executeFallbackBurst: Boolean = true
}

/**
 * Strategy requesting audio focus only, without dispatching global media key events.
 */
object FocusOnlyStrategy : MediaSuppressionStrategy {
    override val executeFallbackBurst: Boolean = false
}
