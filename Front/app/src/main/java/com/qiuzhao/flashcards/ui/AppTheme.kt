package com.qiuzhao.flashcards.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.ExperimentalTextApi
import com.qiuzhao.flashcards.R

/** The only font files used by product copy. Do not create a fallback family. */
@OptIn(ExperimentalTextApi::class)
object AppFonts {
    /** MiSans variable font at the given Figma wght; a single CJK-only face. */
    private fun miSansOnly(weight: Int): FontFamily = FontFamily(
        Font(
            R.font.misans_vf,
            weight = FontWeight.Normal,
            variationSettings = FontVariation.Settings(FontVariation.Setting("wght", weight.toFloat()))
        )
    )

    /** Figma 378:1764 — MiSans VF (Chinese only). */
    val MiSansThin = miSansOnly(150)
    val MiSansExtraLight = miSansOnly(200)
    val MiSansLight = miSansOnly(250)
    val MiSansNormal = miSansOnly(305)
    val MiSansRegular = miSansOnly(330)
    val MiSansMedium = miSansOnly(380)
    val MiSansDemibold = miSansOnly(450)
    val MiSansSemibold = miSansOnly(520)
    val MiSansBold = miSansOnly(630)
    val MiSansHeavy = miSansOnly(700)

    /** Figma 378:1805 — Google Sans Flex (Latin, digits, whitespace and punctuation). */
    private fun googleSansFlexOnly(weight: Int): FontFamily = FontFamily(
        Font(
            R.font.google_sans_flex,
            weight = FontWeight.Normal,
            variationSettings = FontVariation.Settings(
                FontVariation.Setting("GRAD", 0f),
                FontVariation.Setting("ROND", 100f),
                FontVariation.Setting("wdth", 100f),
                FontVariation.Setting("wght", weight.toFloat())
            )
        )
    )
    val GoogleSansFlexThin = googleSansFlexOnly(100)
    val GoogleSansFlexExtraLight = googleSansFlexOnly(200)
    val GoogleSansFlexLight = googleSansFlexOnly(300)
    val GoogleSansFlex = googleSansFlexOnly(400)
    val GoogleSansFlexMedium = googleSansFlexOnly(500)
    val GoogleSansFlexSemibold = googleSansFlexOnly(600)
    val GoogleSansFlexBold = googleSansFlexOnly(700)
    val GoogleSansFlexExtraBold = googleSansFlexOnly(800)
    val GoogleSansFlexBlack = googleSansFlexOnly(900)
    /** Google Material Symbols Rounded: FILL on, Grade Emphasis (GRAD=200). */
    val MaterialSymbolsRoundedEmphasis = FontFamily(
        Font(
            R.font.material_symbols_rounded,
            variationSettings = FontVariation.Settings(
                FontVariation.Setting("FILL", 1f),
                FontVariation.Setting("wght", 400f),
                FontVariation.Setting("GRAD", 200f),
                FontVariation.Setting("opsz", 24f)
            )
        )
    )
    /** Explicit exception for the three main-screen settings controls only. */
    val MaterialSymbolsRoundedOff = FontFamily(
        Font(
            R.font.material_symbols_rounded,
            variationSettings = FontVariation.Settings(
                FontVariation.Setting("FILL", 0f),
                FontVariation.Setting("wght", 400f),
                FontVariation.Setting("GRAD", 200f),
                FontVariation.Setting("opsz", 24f)
            )
        )
    )
    val MaterialSymbolsRounded = MaterialSymbolsRoundedEmphasis
    val MaterialSymbolsRoundedFilled = MaterialSymbolsRoundedEmphasis
}

/**
 * Source-of-truth Figma roles. `MetricMedium` intentionally corrects the
 * presentational Figma spelling `MetricMeduim`; its numeric values are unchanged.
 */
internal enum class AppTextRole {
    PageTitle, AuthHeroTitle, SectionTitle, CardTitle, CardSubtitle, Body, Supporting, Label,
    MetricXSmall, MetricSmall, MetricMedium, MetricLarge
}

internal enum class AppTextLanguage { Chinese, Latin }

internal data class FigmaTextSpec(
    val size: Float,
    val lineHeight: Float,
    val letterSpacing: Float,
    val weight: Int
)

internal object AppTypographyTokens {
    /** Figma 378:1764 weights, retained as data for auditing and tests. */
    val miSansWeights = listOf(150, 200, 250, 305, 330, 380, 450, 520, 630, 700)
    /** Figma 378:1805 weights, with neutral GRAD=0, ROND=100 and wdth=100. */
    val googleSansFlexWeights = (1..9).map { it * 100 }

    private val chinese = mapOf(
        AppTextRole.PageTitle to FigmaTextSpec(24f, 32f, 0f, 520),
        // Figma 427:4182: first-launch login headline has an explicit 32/32 override.
        AppTextRole.AuthHeroTitle to FigmaTextSpec(32f, 32f, 0f, 520),
        AppTextRole.SectionTitle to FigmaTextSpec(20f, 27f, 0f, 630),
        AppTextRole.CardTitle to FigmaTextSpec(18f, 24f, 0f, 630),
        AppTextRole.CardSubtitle to FigmaTextSpec(16f, 21f, 0f, 520),
        AppTextRole.Body to FigmaTextSpec(20f, 27f, 0f, 380),
        AppTextRole.Supporting to FigmaTextSpec(18f, 24f, 0f, 380),
        AppTextRole.Label to FigmaTextSpec(16f, 21f, .6f, 630),
        AppTextRole.MetricXSmall to FigmaTextSpec(20f, 28f, 0f, 630),
        AppTextRole.MetricSmall to FigmaTextSpec(24f, 24f, .6f, 630),
        AppTextRole.MetricMedium to FigmaTextSpec(40f, 40f, -.6f, 630),
        AppTextRole.MetricLarge to FigmaTextSpec(48f, 48f, 0f, 630)
    )
    private val latin = mapOf(
        AppTextRole.PageTitle to FigmaTextSpec(24f, 30f, 0f, 700),
        AppTextRole.AuthHeroTitle to FigmaTextSpec(32f, 32f, 0f, 700),
        AppTextRole.SectionTitle to FigmaTextSpec(20f, 20f, 0f, 700),
        AppTextRole.CardTitle to FigmaTextSpec(18f, 18f, 0f, 700),
        AppTextRole.CardSubtitle to FigmaTextSpec(16f, 16f, 0f, 600),
        // Figma 379:2014 latest variables: the former 24/30 English body token
        // is now 20/27, matching the compact body control without synthetic scaling.
        AppTextRole.Body to FigmaTextSpec(20f, 27f, 0f, 400),
        AppTextRole.Supporting to FigmaTextSpec(18f, 24f, 0f, 500),
        // English control/label tracking was refined from .6px to .4px.
        AppTextRole.Label to FigmaTextSpec(16f, 20f, .4f, 800),
        AppTextRole.MetricXSmall to FigmaTextSpec(20f, 28f, 0f, 700),
        AppTextRole.MetricSmall to FigmaTextSpec(24f, 24f, .6f, 800),
        AppTextRole.MetricMedium to FigmaTextSpec(40f, 40f, -.6f, 700),
        AppTextRole.MetricLarge to FigmaTextSpec(48f, 48f, 0f, 700)
    )

    /** Figma 378:1775. Root navigation labels are currently Chinese-only product copy. */
    private val selectedNavigationBarLabel = FigmaTextSpec(14f, 18f, .6f, 630)
    private val unselectedNavigationBarLabel = FigmaTextSpec(14f, 18f, .6f, 520)

    fun spec(role: AppTextRole, language: AppTextLanguage): FigmaTextSpec =
        if (language == AppTextLanguage.Chinese) chinese.getValue(role) else latin.getValue(role)

    fun lineHeight(role: AppTextRole): Float = maxOf(
        chinese.getValue(role).lineHeight, latin.getValue(role).lineHeight
    )

    fun navigationBarLabelSpec(selected: Boolean): FigmaTextSpec =
        if (selected) selectedNavigationBarLabel else unselectedNavigationBarLabel

    fun fontFamily(language: AppTextLanguage, weight: Int): FontFamily = when (language) {
        AppTextLanguage.Chinese -> when (weight) {
            150 -> AppFonts.MiSansThin; 200 -> AppFonts.MiSansExtraLight; 250 -> AppFonts.MiSansLight
            305 -> AppFonts.MiSansNormal; 330 -> AppFonts.MiSansRegular; 380 -> AppFonts.MiSansMedium
            450 -> AppFonts.MiSansDemibold; 520 -> AppFonts.MiSansSemibold; 630 -> AppFonts.MiSansBold
            700 -> AppFonts.MiSansHeavy; else -> error("Unknown MiSans weight: $weight")
        }
        AppTextLanguage.Latin -> when (weight) {
            100 -> AppFonts.GoogleSansFlexThin; 200 -> AppFonts.GoogleSansFlexExtraLight; 300 -> AppFonts.GoogleSansFlexLight
            400 -> AppFonts.GoogleSansFlex; 500 -> AppFonts.GoogleSansFlexMedium; 600 -> AppFonts.GoogleSansFlexSemibold
            700 -> AppFonts.GoogleSansFlexBold; 800 -> AppFonts.GoogleSansFlexExtraBold; 900 -> AppFonts.GoogleSansFlexBlack
            else -> error("Unknown Google Sans Flex weight: $weight")
        }
    }
}

/** Figma shared geometry: main cards, root switchers and navigation containers. */
val AppShapeRadius = 36

/** Figma shared geometry: card internals, control containers and all buttons. */
val AppNestedShapeRadius = 24
val AppButtonShapeRadius = 24

/** Fixed clipping boundary for every full-page scrollable content viewport. */
val AppScrollableContentClipRadius = 24

/** One six-step Figma colour family. */
internal data class AppColorFamily(
    val background: Color,
    val surface: Color,
    val primarySecondary: Color,
    val primary: Color,
    val primaryStrong: Color,
    val ink: Color
)

/** Latest Figma Variables. Blue is the application brand family. */
internal object AppColors {
    /** Figma “深色文本/icon”: #000000 at 80%. */
    val TextIconDark = Color(0xCC000000)

    /** Figma “浅色文本/icon”: #FFFFFF at 90%. */
    val TextIconLight = Color(0xE6FFFFFF)

    /** Neutral elevated-card surface. */
    val Card = Color.White

    /** Figma Base / Background. Used by the three root pages. */
    val BaseBackground = Color.White

    /** Figma Navigation Bar / Primary. */
    val NavigationBar = Color(0xFF425161)

    val Blue = AppColorFamily(
        background = Color(0xFFEEF4FA), surface = Color(0xFFCCE6FF), primarySecondary = Color(0xFFB0D7FF),
        primary = Color(0xFF389DFF), primaryStrong = Color(0xFF0063C4), ink = Color(0xFF003C7A)
    )
    val Purple = AppColorFamily(
        background = Color(0xFFF3F3FF), surface = Color(0xFFE4E4FF), primarySecondary = Color(0xFFC8C8FF),
        primary = Color(0xFF716FDD), primaryStrong = Color(0xFF3836B7), ink = Color(0xFF38387A)
    )
    val Green = AppColorFamily(
        background = Color(0xFFEAF4E5), surface = Color(0xFFD6EEC9), primarySecondary = Color(0xFFB6DCA6),
        primary = Color(0xFF65AA56), primaryStrong = Color(0xFF278B00), ink = Color(0xFF1F5225)
    )
    val Pink = AppColorFamily(
        background = Color(0xFFF9EFF3), surface = Color(0xFFF8DBE3), primarySecondary = Color(0xFFFFBFD3),
        primary = Color(0xFFF16692), primaryStrong = Color(0xFFAA0047), ink = Color(0xFF730022)
    )
    val Orange = AppColorFamily(
        background = Color(0xFFFAF2EB), surface = Color(0xFFFBE7C8), primarySecondary = Color(0xFFF4DDBA),
        primary = Color(0xFFE48A4A), primaryStrong = Color(0xFFD15700), ink = Color(0xFF642A00)
    )

    /** Warning / Primary. */
    val Warning = Color(0xFFBD3F3F)

    /** Warning / Primary-Strong. */
    val WarningStrong = Color(0xFFD23535)

    /** Figma 654:3950 — warning-secondary is used for semantic warning surfaces. */
    val WarningSecondary = Color(0xFFE87F77)

    /** Figma 654:3950 — readable warning copy and icon tint. */
    val WarningInk = Color(0xFF670700)

    // Figma 604:2732 review-status semantic colours.
    val ReviewKnown = Color(0xFF579B00)
    val ReviewRecognised = Color(0xFFAFCD82)
    val ReviewUncertain = Color(0xFFFFC000)
    val ReviewUnfamiliar = Color(0xFFFF3D00)
    val ReviewUnseen = Color(0xFFDDDDDD)
    val StudyTime = Color(0xFFB7AC4A)
    val StudyTimeInk = Color(0xFF484100)
    val StudyMastered = Color(0xFF2C913C)
    val StudyMasteredInk = Color(0xFF00590D)
}

internal val LightColors = lightColorScheme(
    primary = AppColors.Blue.primary,
    onPrimary = AppColors.TextIconLight,
    primaryContainer = AppColors.Blue.primarySecondary,
    onPrimaryContainer = AppColors.Blue.ink,
    secondary = AppColors.Blue.primarySecondary,
    onSecondary = AppColors.Blue.ink,
    surface = AppColors.Card,
    surfaceVariant = AppColors.Blue.surface,
    background = AppColors.BaseBackground,
    onSurface = AppColors.TextIconDark,
    onSurfaceVariant = AppColors.Blue.ink,
    outline = AppColors.Blue.primarySecondary,
    error = AppColors.Warning,
    onError = AppColors.TextIconLight
)

@Composable
fun AutumnFlashcardsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightColors, content = content)
}
