package io.ronesec.android.ui.stats

import io.ronesec.android.ui.locale.WattimLocale
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class DurationFormatterTest {

    @Test
    fun `format duration in Russian`() {
        assertEquals("0 мин", DurationFormatter.format(0L, isRussian = true))
        assertEquals("0 мин", DurationFormatter.format(-10L, isRussian = true))
        assertEquals("35 мин", DurationFormatter.format(35L, isRussian = true))
        assertEquals("1 ч", DurationFormatter.format(60L, isRussian = true))
        assertEquals("1 ч 15 мин", DurationFormatter.format(75L, isRussian = true))
        assertEquals("1 д", DurationFormatter.format(1440L, isRussian = true))
        assertEquals("2 д 4 ч 10 мин", DurationFormatter.format(2880L + 250L, isRussian = true))
    }

    @Test
    fun `format duration in English`() {
        assertEquals("0 min", DurationFormatter.format(0L, isRussian = false))
        assertEquals("0 min", DurationFormatter.format(-5L, isRussian = false))
        assertEquals("35 min", DurationFormatter.format(35L, isRussian = false))
        assertEquals("1 h", DurationFormatter.format(60L, isRussian = false))
        assertEquals("1 h 15 min", DurationFormatter.format(75L, isRussian = false))
        assertEquals("1 d", DurationFormatter.format(1440L, isRussian = false))
        assertEquals("2 d 4 h 10 min", DurationFormatter.format(2880L + 250L, isRussian = false))
    }

    @Test
    fun `format duration by Locale`() {
        assertEquals("1 ч 15 мин", DurationFormatter.format(75L, Locale("ru", "RU")))
        assertEquals("1 h 15 min", DurationFormatter.format(75L, Locale.US))
        assertEquals("1 h 15 min", DurationFormatter.format(75L, Locale.GERMANY))
    }

    @Test
    fun `WattimLocale resolves correctly with English fallback`() {
        // Explicit selections
        assertEquals("en", WattimLocale.resolveLocale("ENGLISH", Locale("ru")).language)
        assertEquals("ru", WattimLocale.resolveLocale("РУССКИЙ", Locale.US).language)

        // AUTO with Russian system locale
        assertEquals("ru", WattimLocale.resolveLocale("AUTO", Locale("ru", "RU")).language)

        // AUTO with non-Russian system locale falls back to English
        assertEquals("en", WattimLocale.resolveLocale("AUTO", Locale.US).language)
        assertEquals("en", WattimLocale.resolveLocale("AUTO", Locale.FRENCH).language)
        assertEquals("en", WattimLocale.resolveLocale("AUTO", Locale.GERMAN).language)
    }
}
