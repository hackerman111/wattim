package io.ronesec.android.ui.locale

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * Locale resolution and CompositionLocal provider for Wattim (F72).
 * Supports AUTO, ENGLISH, and РУССКИЙ with English fallback when system locale is non-Russian.
 */
object WattimLocale {
    const val AUTO = "AUTO"
    const val ENGLISH = "ENGLISH"
    const val RUSSIAN = "РУССКИЙ"

    val supportedLanguages = listOf(AUTO, ENGLISH, RUSSIAN)

    fun resolveLocale(languageCode: String, systemLocale: Locale = Locale.getDefault()): Locale {
        return when (languageCode.trim().uppercase()) {
            ENGLISH -> Locale.ENGLISH
            RUSSIAN, "RU", "RUSSIAN" -> Locale("ru")
            else -> {
                if (systemLocale.language.startsWith("ru", ignoreCase = true)) {
                    Locale("ru")
                } else {
                    Locale.ENGLISH
                }
            }
        }
    }

    fun isRussian(languageCode: String, systemLocale: Locale = Locale.getDefault()): Boolean {
        return resolveLocale(languageCode, systemLocale).language.startsWith("ru", ignoreCase = true)
    }
}

@Composable
fun ProvideWattimLocale(
    language: String,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val currentConfig = LocalConfiguration.current
    val systemLocale = remember(currentConfig) {
        val locales = currentConfig.locales
        if (!locales.isEmpty) locales[0] else Locale.getDefault()
    }
    val targetLocale = remember(language, systemLocale) {
        WattimLocale.resolveLocale(language, systemLocale)
    }

    val localizedConfig = remember(currentConfig, targetLocale) {
        Configuration(currentConfig).apply {
            setLocale(targetLocale)
            setLayoutDirection(targetLocale)
        }
    }
    val localizedContext = remember(context, localizedConfig) {
        context.createConfigurationContext(localizedConfig)
    }

    CompositionLocalProvider(
        LocalConfiguration provides localizedConfig,
        LocalContext provides localizedContext,
        content = content
    )
}
