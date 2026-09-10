package io.ronesec.android.ui.stats

import java.util.Locale

/**
 * Humanized duration formatter for days/hours/minutes as required by F69 and §2.8.5.
 * Truthful zero/empty formatting and localized unit suffixes.
 */
object DurationFormatter {

    fun format(totalMinutes: Long, isRussian: Boolean = true): String {
        val minSuffix = if (isRussian) "мин" else "min"
        val hourSuffix = if (isRussian) "ч" else "h"
        val daySuffix = if (isRussian) "д" else "d"

        if (totalMinutes <= 0L) {
            return "0 $minSuffix"
        }

        val days = totalMinutes / (24 * 60)
        val hours = (totalMinutes % (24 * 60)) / 60
        val minutes = totalMinutes % 60

        val parts = mutableListOf<String>()
        if (days > 0) parts.add("$days $daySuffix")
        if (hours > 0) parts.add("$hours $hourSuffix")
        if (minutes > 0 || parts.isEmpty()) parts.add("$minutes $minSuffix")

        return parts.joinToString(" ")
    }

    fun format(totalMinutes: Long, locale: Locale): String {
        val isRussian = locale.language.startsWith("ru", ignoreCase = true)
        return format(totalMinutes, isRussian)
    }
}
