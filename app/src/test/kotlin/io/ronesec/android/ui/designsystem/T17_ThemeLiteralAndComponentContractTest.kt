package io.ronesec.android.ui.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class T17_ThemeLiteralAndComponentContractTest {

    data class SpecExpectedColors(
        val background: Long,
        val surface: Long,
        val surfaceElevated: Long,
        val textPrimary: Long,
        val textSecondary: Long,
        val border: Long,
        val accent: Long,
        val error: Long
    )

    // Independent spec fixture directly transcribed from specification §2.7.2 / §8.4
    private val independentSpecColors = mapOf(
        ThemeId.CYBER_TERMINAL to SpecExpectedColors(
            background = 0xFF090B0DL,
            surface = 0xFF101316L,
            surfaceElevated = 0xFF161A1EL,
            textPrimary = 0xFFE6E8E9L,
            textSecondary = 0xFF737A80L,
            border = 0xFF252A2EL,
            accent = 0xFF00E5FFL,
            error = 0xFFFF5252L
        ),
        ThemeId.NORD to SpecExpectedColors(
            background = 0xFF2E3440L,
            surface = 0xFF3B4252L,
            surfaceElevated = 0xFF434C5EL,
            textPrimary = 0xFFECEFF4L,
            textSecondary = 0xFFD8DEE9L,
            border = 0xFF4C566AL,
            accent = 0xFF88C0D0L,
            error = 0xFFBF616AL
        ),
        ThemeId.CATPPUCCIN to SpecExpectedColors(
            background = 0xFF1E1E2EL,
            surface = 0xFF252538L,
            surfaceElevated = 0xFF313244L,
            textPrimary = 0xFFCDD6F4L,
            textSecondary = 0xFFA6ADC8L,
            border = 0xFF45475AL,
            accent = 0xFFCBA6F7L,
            error = 0xFFF38BA8L
        ),
        ThemeId.DRACULA to SpecExpectedColors(
            background = 0xFF282A36L,
            surface = 0xFF343746L,
            surfaceElevated = 0xFF44475AL,
            textPrimary = 0xFFF8F8F2L,
            textSecondary = 0xFF6272A4L,
            border = 0xFF4D5368L,
            accent = 0xFFBD93F9L,
            error = 0xFFFF5555L
        ),
        ThemeId.GRUVBOX to SpecExpectedColors(
            background = 0xFF282828L,
            surface = 0xFF32302FL,
            surfaceElevated = 0xFF3C3836L,
            textPrimary = 0xFFEBDBB2L,
            textSecondary = 0xFFA89984L,
            border = 0xFF504945L,
            accent = 0xFFFABD2FL,
            error = 0xFFFB4934L
        ),
        ThemeId.TOKYO_NIGHT to SpecExpectedColors(
            background = 0xFF1A1B26L,
            surface = 0xFF24283BL,
            surfaceElevated = 0xFF2F354FL,
            textPrimary = 0xFFC0CAF5L,
            textSecondary = 0xFF7982A9L,
            border = 0xFF414868L,
            accent = 0xFF7AA2F7L,
            error = 0xFFF7768EL
        )
    )

    @Test
    fun `all 48 exact theme colors match independent specification fixture`() {
        assertEquals(6, ThemeId.entries.size)
        assertEquals(ThemeId.NORD, ThemeId.DEFAULT)

        for ((themeId, expected) in independentSpecColors) {
            val colors = ThemeRegistry.getColors(themeId)
            assertEquals("Theme $themeId background mismatch", Color(expected.background), colors.background)
            assertEquals("Theme $themeId surface mismatch", Color(expected.surface), colors.surface)
            assertEquals("Theme $themeId surfaceElevated mismatch", Color(expected.surfaceElevated), colors.surfaceElevated)
            assertEquals("Theme $themeId textPrimary mismatch", Color(expected.textPrimary), colors.textPrimary)
            assertEquals("Theme $themeId textSecondary mismatch", Color(expected.textSecondary), colors.textSecondary)
            assertEquals("Theme $themeId border mismatch", Color(expected.border), colors.border)
            assertEquals("Theme $themeId accent mismatch", Color(expected.accent), colors.accent)
            assertEquals("Theme $themeId error mismatch", Color(expected.error), colors.error)
        }
    }

    @Test
    fun `emergency scrim is black at 75 percent alpha`() {
        val scrim = WattimColors.EmergencyScrim
        assertEquals(0f, scrim.red, 0.001f)
        assertEquals(0f, scrim.green, 0.001f)
        assertEquals(0f, scrim.blue, 0.001f)
        assertEquals(0.75f, scrim.alpha, 0.001f)
    }

    @Test
    fun `unknown theme id safely resolves to Nord default`() {
        assertEquals(ThemeId.NORD, ThemeId.fromId("NON_EXISTENT_THEME"))
        assertEquals(ThemeId.NORD, ThemeId.fromId(null))
        assertEquals(ThemeId.NORD, ThemeId.fromId(""))
        assertEquals(ThemeId.CYBER_TERMINAL, ThemeId.fromId("CYBER_TERMINAL"))
        assertEquals(ThemeId.CYBER_TERMINAL, ThemeId.fromId("cyber_terminal"))
    }

    @Test
    fun `typography exact monospace scale and roles match spec`() {
        val typo = WattimTypography()

        // Monospace check everywhere
        assertEquals(FontFamily.Monospace, typo.displayLarge.fontFamily)
        assertEquals(FontFamily.Monospace, typo.titleLarge.fontFamily)
        assertEquals(FontFamily.Monospace, typo.titleMedium.fontFamily)
        assertEquals(FontFamily.Monospace, typo.bodyLarge.fontFamily)
        assertEquals(FontFamily.Monospace, typo.bodyMedium.fontFamily)
        assertEquals(FontFamily.Monospace, typo.labelSmall.fontFamily)
        assertEquals(FontFamily.Monospace, typo.button.fontFamily)
        assertEquals(FontFamily.Monospace, typo.badge.fontFamily)
        assertEquals(FontFamily.Monospace, typo.phrase.fontFamily)

        // Exact sizes, weights, and letterSpacing
        assertEquals(32.sp, typo.displayLarge.fontSize)
        assertEquals(FontWeight.Bold, typo.displayLarge.fontWeight)
        assertEquals(0.05.sp, typo.displayLarge.letterSpacing)

        assertEquals(20.sp, typo.titleLarge.fontSize)
        assertEquals(FontWeight.Bold, typo.titleLarge.fontWeight)
        assertEquals(0.05.sp, typo.titleLarge.letterSpacing)

        assertEquals(14.sp, typo.titleMedium.fontSize)
        assertEquals(FontWeight.SemiBold, typo.titleMedium.fontWeight)
        assertEquals(0.15.sp, typo.titleMedium.letterSpacing)

        assertEquals(16.sp, typo.bodyLarge.fontSize)
        assertEquals(FontWeight.Normal, typo.bodyLarge.fontWeight)
        assertEquals(0.02.sp, typo.bodyLarge.letterSpacing)

        assertEquals(14.sp, typo.bodyMedium.fontSize)
        assertEquals(FontWeight.Normal, typo.bodyMedium.fontWeight)
        assertEquals(0.02.sp, typo.bodyMedium.letterSpacing)

        assertEquals(12.sp, typo.labelSmall.fontSize)
        assertEquals(FontWeight.Normal, typo.labelSmall.fontWeight)
        assertEquals(0.1.sp, typo.labelSmall.letterSpacing)

        // Supplementary roles
        assertEquals(13.sp, typo.button.fontSize)
        assertEquals(FontWeight.Bold, typo.button.fontWeight)

        assertEquals(11.sp, typo.badge.fontSize)
        assertEquals(FontWeight.SemiBold, typo.badge.fontWeight)

        assertEquals(22.sp, typo.phrase.fontSize)
        assertEquals(FontWeight.Normal, typo.phrase.fontWeight)
    }

    @Test
    fun `dimensions scale and shapes match spec`() {
        val dim = WattimDimensions()
        assertEquals(4.dp, dim.space4)
        assertEquals(8.dp, dim.space8)
        assertEquals(12.dp, dim.space12)
        assertEquals(16.dp, dim.space16)
        assertEquals(24.dp, dim.space24)
        assertEquals(32.dp, dim.space32)

        assertEquals(4.dp, dim.cardRadius)
        assertEquals(4.dp, dim.buttonRadius)
        assertEquals(4.dp, dim.inputRadius)
        assertEquals(3.dp, dim.badgeRadius)

        assertEquals(1.dp, dim.borderWidth)
        assertEquals(16.dp, dim.cardPadding)
        assertEquals(16.dp, dim.buttonPaddingHorizontal)
        assertEquals(10.dp, dim.buttonPaddingVertical)
        assertEquals(6.dp, dim.badgePaddingHorizontal)
        assertEquals(2.dp, dim.badgePaddingVertical)

        assertEquals(48.dp, dim.minTouchTarget)
    }

    @Test
    fun `static check - no color hex literals outside ThemeRegistry and independent fixture`() {
        val baseDir = File(System.getProperty("user.dir") ?: ".")
        val appDir = if (File(baseDir, "src/main/kotlin").exists()) baseDir else File(baseDir, "app")
        val mainKotlinDir = File(appDir, "src/main/kotlin")
        assertTrue("app/src/main/kotlin should exist", mainKotlinDir.exists())

        val hexPattern = Regex("""0x[0-9a-fA-F]{6,8}|#[0-9a-fA-F]{6,8}""")
        val violations = mutableListOf<String>()

        mainKotlinDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.name != "ThemeRegistry.kt" }
            .forEach { file ->
                file.readLines().forEachIndexed { lineNum, line ->
                    val trimmed = line.trim()
                    // Allow comment lines and non-color hex codes like bitmasks (e.g. 0x01)
                    if (!trimmed.startsWith("//") && !trimmed.startsWith("/*") && !trimmed.startsWith("*")) {
                        if (hexPattern.containsMatchIn(line) && !line.contains("Color.Black.copy") && !line.contains("Color(0x")) {
                            violations.add("${file.name}:${lineNum + 1}: $trimmed")
                        } else if (line.contains("Color(0x")) {
                            violations.add("${file.name}:${lineNum + 1}: $trimmed")
                        }
                    }
                }
            }

        assertTrue("Found prohibited color hex literals outside ThemeRegistry:\n${violations.joinToString("\n")}", violations.isEmpty())
    }

    @Test
    fun `localization string resources are 100 percent complete and synchronized between en and ru`() {
        val baseDir = File(System.getProperty("user.dir") ?: ".")
        val appDir = if (File(baseDir, "src/main/res").exists()) baseDir else File(baseDir, "app")
        val enStringsFile = File(appDir, "src/main/res/values/strings.xml")
        val ruStringsFile = File(appDir, "src/main/res/values-ru/strings.xml")

        assertTrue("en strings.xml exists", enStringsFile.exists())
        assertTrue("ru strings.xml exists", ruStringsFile.exists())

        fun parseStringKeys(file: File): Set<String> {
            val db = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            val doc = db.parse(file)
            val nodes = doc.getElementsByTagName("string")
            val keys = mutableSetOf<String>()
            for (i in 0 until nodes.length) {
                val node = nodes.item(i)
                val name = node.attributes?.getNamedItem("name")?.nodeValue
                if (name != null) {
                    keys.add(name)
                }
            }
            return keys
        }

        val enKeys = parseStringKeys(enStringsFile)
        val ruKeys = parseStringKeys(ruStringsFile)

        val missingInRu = enKeys - ruKeys
        val missingInEn = ruKeys - enKeys

        assertTrue("Keys present in EN but missing in RU: $missingInRu", missingInRu.isEmpty())
        assertTrue("Keys present in RU but missing in EN: $missingInEn", missingInEn.isEmpty())
        assertTrue("At least 30 synchronized string keys expected", enKeys.size >= 30)
    }
}
