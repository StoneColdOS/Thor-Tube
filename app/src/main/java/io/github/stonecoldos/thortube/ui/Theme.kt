package io.github.stonecoldos.thortube.ui

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** The themes in Setup. MATCH follows the Thor's own dark mode setting. */
enum class ThemeMode(val label: String) {
    DARK("Dark"), GREY("Grey"), OLED("OLED Black"), LIGHT("Light"), MATCH("Match the Thor"),
}

/** Every color the app uses, per theme. */
data class Palette(
    val bg: Color,
    val rail: Color,
    val railLine: Color,
    val card: Color,
    val text: Color,
    val dim: Color,
    val accent: Color,
    /** Background of the selected rail item. */
    val railOn: Color,
    /** The selected tab chip, and the text on it. */
    val chipOn: Color,
    val chipOnText: Color,
    val isLight: Boolean,
)

private val Accent = Color(0xFF40C4FF)

val DarkPalette = Palette(
    bg = Color(0xFF0E0F12), rail = Color(0xFF18191F), railLine = Color(0x00000000), card = Color(0xFF20232A),
    text = Color(0xFFF0F2F6), dim = Color(0xFF969CAA), accent = Accent, railOn = Color(0xFF283C50),
    chipOn = Color(0xFFF0F2F6), chipOnText = Color(0xFF0E0F12), isLight = false,
)

val GreyPalette = Palette(
    bg = Color(0xFF36393F), rail = Color(0xFF2C2F34), railLine = Color(0x00000000), card = Color(0xFF444850),
    text = Color(0xFFEEF0F4), dim = Color(0xFFAAB0BC), accent = Color(0xFF60CEFF), railOn = Color(0xFF465A6E),
    chipOn = Color(0xFFEEF0F4), chipOnText = Color(0xFF2C2F34), isLight = false,
)

/** True black, so an OLED screen switches those pixels off. */
val OledPalette = Palette(
    bg = Color.Black, rail = Color.Black, railLine = Color(0xFF1E1E22), card = Color(0xFF121214),
    text = Color(0xFFECEEF2), dim = Color(0xFF828894), accent = Accent, railOn = Color(0xFF102230),
    chipOn = Color(0xFFECEEF2), chipOnText = Color.Black, isLight = false,
)

val LightPalette = Palette(
    bg = Color(0xFFF6F7FA), rail = Color(0xFFE8EBF1), railLine = Color(0x00000000), card = Color(0xFFE1E5EC),
    text = Color(0xFF14161C), dim = Color(0xFF606674), accent = Color(0xFF0084D6), railOn = Color(0xFFCEE4F6),
    chipOn = Color(0xFF14161C), chipOnText = Color(0xFFF6F7FA), isLight = true,
)

fun paletteFor(mode: ThemeMode, systemDark: Boolean): Palette = when (mode) {
    ThemeMode.DARK -> DarkPalette
    ThemeMode.GREY -> GreyPalette
    ThemeMode.OLED -> OledPalette
    ThemeMode.LIGHT -> LightPalette
    ThemeMode.MATCH -> if (systemDark) DarkPalette else LightPalette
}

val LocalPalette = staticCompositionLocalOf { DarkPalette }

/** The retro picture effect on videos. */
enum class CrtMode(val label: String, val blurb: String) {
    OFF("Off", "Plain, sharp video."),
    NINETIES("90s tube", "A curved tube with heavy scanlines and glowing phosphor stripes."),
    NOUGHTIES("2000s flat", "A flat late-2000s HD tube: faint lines, punchier picture."),
}

/** Saved choices. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var theme: ThemeMode
        get() = runCatching { ThemeMode.valueOf(prefs.getString("theme", null) ?: "") }.getOrDefault(ThemeMode.DARK)
        set(value) = prefs.edit().putString("theme", value.name).apply()

    var crt: CrtMode
        get() = runCatching { CrtMode.valueOf(prefs.getString("crt", null) ?: "") }.getOrDefault(CrtMode.OFF)
        set(value) = prefs.edit().putString("crt", value.name).apply()
}
