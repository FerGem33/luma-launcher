@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package dev.luma.launcher

import androidx.annotation.StringRes
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Horizontal alignment of the home page text (and the drawer's list). */
enum class HomeAlign(val horizontal: Alignment.Horizontal, val text: TextAlign) {
    Start(Alignment.Start, TextAlign.Start),
    Center(Alignment.CenterHorizontally, TextAlign.Center),
    End(Alignment.End, TextAlign.End),
}

enum class Meridiem { None, Inline, Below }
enum class TextCase { Normal, Lower, Upper }

class ClockStyle(
    val use24h: Boolean,
    val size: TextUnit,
    val weight: FontWeight,
    val tracking: TextUnit,
    val meridiem: Meridiem,
    @StringRes val datePatternRes: Int,
    val dateCase: TextCase,
    val dateSize: TextUnit,
    val dateWeight: FontWeight,
    val dateTracking: TextUnit,
    val dateAccent: Boolean,
    val dateAbove: Boolean,
    /** Extra space above the clock, on top of the status bar. */
    val topOffset: Dp = 0.dp,
)

class ListStyle(
    val size: TextUnit,
    val weight: FontWeight,
    val rowPadding: Dp,
    val lowercase: Boolean,
    /** A short accent rule above the pinned list. */
    val rule: Boolean,
)

/**
 * Everything that makes up Luma's look. A theme is a preset of these values; the settings screen picks
 * one. Keeping them together means a new preset is one more entry in [LumaThemes].
 */
class LumaTheme(
    val id: String,
    @StringRes val nameRes: Int,
    @StringRes val taglineRes: Int,
    /** Background colour, or null to show the system wallpaper under a [scrim]. */
    val background: Color?,
    val scrim: Float,
    val fg: Color,
    val fg2: Color,
    /** Highlight colour; null means pressed items simply dim. */
    val accent: Color?,
    /** Cards and fields on top of the background. */
    val surface: Color,
    /** Fields and tiles on top of [surface]. */
    val surfaceHigh: Color,
    /** Backdrop of full-screen overlays (drawer, picker, settings). */
    val overlay: Color,
    /** Opaque background of popup menus. */
    val menu: Color,
    val isLight: Boolean,
    val font: FontFamily,
    val align: HomeAlign,
    val clock: ClockStyle,
    val list: ListStyle,
    /** Colours for the preview thumbnail in the settings screen. */
    val swatch: Brush,
    /** Soft text shadow so text stays readable over a wallpaper. */
    val textShadow: Boolean = false,
)

private val Outfit = FontFamily(
    listOf(200 to FontWeight.ExtraLight, 300 to FontWeight.Light, 400 to FontWeight.Normal, 500 to FontWeight.Medium).map { (w, fw) ->
        Font(R.font.outfit, fw, variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    },
)

private val PlexMono = FontFamily(
    Font(R.font.plexmono_light, FontWeight.Light),
    Font(R.font.plexmono_regular, FontWeight.Normal),
)

/** The Paper family: light or dark notebook pages in monospace, left-aligned, 24-hour clock. */
private fun paper(
    id: String, nameRes: Int, taglineRes: Int,
    bg: Long, fg: Long, fg2: Long, accent: Long, surface: Long, surfaceHigh: Long, menu: Long, isLight: Boolean,
) = LumaTheme(
    id = id, nameRes = nameRes, taglineRes = taglineRes,
    background = Color(bg), scrim = 0f,
    fg = Color(fg), fg2 = Color(fg2), accent = Color(accent),
    surface = Color(surface), surfaceHigh = Color(surfaceHigh),
    overlay = Color(bg), menu = Color(menu),
    isLight = isLight, font = PlexMono, align = HomeAlign.Start,
    clock = ClockStyle(
        use24h = true, size = 58.sp, weight = FontWeight.Light, tracking = (-0.03).em, meridiem = Meridiem.None,
        datePatternRes = R.string.date_pattern_short, dateCase = TextCase.Lower, dateSize = 16.sp, dateWeight = FontWeight.Normal,
        dateTracking = 0.sp, dateAccent = true, dateAbove = false,
    ),
    list = ListStyle(size = 20.sp, weight = FontWeight.Normal, rowPadding = 8.dp, lowercase = true, rule = true),
    swatch = Brush.linearGradient(listOf(Color(bg), Color(bg))),
)

val PaperTheme = paper(
    "paper", R.string.theme_paper, R.string.theme_paper_tagline,
    bg = 0xFFEEEFEA, fg = 0xFF17181A, fg2 = 0xFF6B6E70, accent = 0xFF3F6F52,
    surface = 0xFFE4E5DF, surfaceHigh = 0xFFD8D9D2, menu = 0xFFF8F8F4, isLight = true,
)

/** A warmer, beige page. */
val PaperWarmTheme = paper(
    "paper_warm", R.string.theme_paper_warm, R.string.theme_paper_warm_tagline,
    bg = 0xFFF0E7D6, fg = 0xFF2A241C, fg2 = 0xFF6F6454, accent = 0xFF52663F,
    surface = 0xFFE6DCC8, surfaceHigh = 0xFFDACFB9, menu = 0xFFF7F0E2, isLight = true,
)

/** The same page at night. */
val PaperDarkTheme = paper(
    "paper_dark", R.string.theme_paper_dark, R.string.theme_paper_dark_tagline,
    bg = 0xFF141513, fg = 0xFFE8E8E2, fg2 = 0xFF8F928E, accent = 0xFF8DB89A,
    surface = 0xFF1E201D, surfaceHigh = 0xFF282B27, menu = 0xFF242723, isLight = false,
)

/** True black, left-aligned, hairline type. */
val GraphiteTheme = LumaTheme(
    id = "graphite", nameRes = R.string.theme_graphite, taglineRes = R.string.theme_graphite_tagline,
    background = Color.Black, scrim = 0f,
    fg = Color.White, fg2 = Color(0xFF8C8C8C), accent = null,
    surface = Color(0xFF1C1C1E), surfaceHigh = Color(0xFF2C2C2E),
    overlay = Color(0xFF000000), menu = Color(0xFF2C2C2E),
    isLight = false, font = Outfit, align = HomeAlign.Start,
    clock = ClockStyle(
        use24h = false, size = 64.sp, weight = FontWeight.ExtraLight, tracking = (-0.01).em, meridiem = Meridiem.Inline,
        datePatternRes = R.string.date_pattern_long, dateCase = TextCase.Normal, dateSize = 16.sp, dateWeight = FontWeight.Light,
        dateTracking = 0.sp, dateAccent = false, dateAbove = false,
    ),
    list = ListStyle(size = 30.sp, weight = FontWeight.Light, rowPadding = 10.dp, lowercase = false, rule = false),
    swatch = Brush.linearGradient(listOf(Color.Black, Color.Black)),
)

/**
 * Your own wallpaper (one image, or a collection shown one per day) under a dimming scrim, falling back to
 * the system wallpaper. Meant to become the most configurable theme.
 */
val CustomTheme = LumaTheme(
    id = "custom", nameRes = R.string.theme_custom, taglineRes = R.string.theme_custom_tagline,
    background = null, scrim = 0.35f,
    fg = Color.White, fg2 = Color(0xCCFFFFFF), accent = null,
    surface = Color(0x1AFFFFFF), surfaceHigh = Color(0x2AFFFFFF),
    overlay = Color(0xD9101010), menu = Color(0xFF26262A),
    isLight = false, font = Outfit, align = HomeAlign.Start,
    clock = ClockStyle(
        use24h = true, size = 72.sp, weight = FontWeight.Light, tracking = (-0.02).em, meridiem = Meridiem.None,
        datePatternRes = R.string.date_pattern_long, dateCase = TextCase.Normal, dateSize = 16.sp, dateWeight = FontWeight.Normal,
        dateTracking = 0.sp, dateAccent = false, dateAbove = false,
    ),
    list = ListStyle(size = 28.sp, weight = FontWeight.Normal, rowPadding = 10.dp, lowercase = false, rule = false),
    swatch = Brush.linearGradient(listOf(Color(0xFF3A4A6B), Color(0xFF1F2A44), Color(0xFF6B4F5A))),
    textShadow = true,
)

/** The presets offered in settings, in display order. The first is the default. */
val LumaThemes = listOf(PaperTheme, PaperWarmTheme, PaperDarkTheme, GraphiteTheme, CustomTheme)

fun themeById(id: String?): LumaTheme =
    // "dusk" was the name of the theme that became Custom.
    LumaThemes.firstOrNull { it.id == (if (id == "dusk") "custom" else id) } ?: LumaThemes.first()

val LocalLuma = staticCompositionLocalOf { PaperTheme }
