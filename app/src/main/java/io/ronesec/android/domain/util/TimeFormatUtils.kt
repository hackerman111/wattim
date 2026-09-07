package io.ronesec.android.domain.util

object TimeFormatUtils {

    /**
     * Formats duration in milliseconds to a friendly Russian string, e.g.:
     * - 0L -> "0 сек"
     * - 30_000L -> "30 сек"
     * - 60_000L -> "1 мин"
     * - 90_000L -> "1 мин 30 с"
     */
    fun formatDurationRu(totalMs: Long): String {
        val totalSecs = (totalMs / 1000L).coerceAtLeast(0L)
        val mins = totalSecs / 60L
        val secs = totalSecs % 60L
        return when {
            mins > 0 && secs > 0 -> "$mins мин $secs с"
            mins > 0 -> "$mins мин"
            else -> "$secs сек"
        }
    }

    /**
     * Parses minutes and seconds text inputs safely into milliseconds.
     */
    fun parseDuration(
        minutesStr: String,
        secondsStr: String,
        minMs: Long = 5_000L,
        maxMs: Long = 86_400_000L
    ): Long {
        val m = minutesStr.filter { it.isDigit() }.toLongOrNull() ?: 0L
        val s = secondsStr.filter { it.isDigit() }.toLongOrNull() ?: 0L
        val total = m * 60_000L + s * 1000L
        return if (total <= 0L) minMs else total.coerceIn(minMs, maxMs)
    }

    /**
     * Adjusts the current minutes and seconds inputs by deltaMs (positive or negative),
     * returning a Pair of updated (minutesString, secondsString).
     */
    fun calculateAdjustedTime(
        minutesStr: String,
        secondsStr: String,
        deltaMs: Long,
        minMs: Long = 5_000L,
        maxMs: Long = 86_400_000L
    ): Pair<String, String> {
        val m = minutesStr.filter { it.isDigit() }.toLongOrNull() ?: 0L
        val s = secondsStr.filter { it.isDigit() }.toLongOrNull() ?: 0L
        val currentTotal = m * 60_000L + s * 1000L
        val newTotal = (currentTotal + deltaMs).coerceIn(minMs, maxMs)
        val newM = (newTotal / 60_000L).toString()
        val newS = ((newTotal % 60_000L) / 1000L).toString()
        return Pair(newM, newS)
    }
}
