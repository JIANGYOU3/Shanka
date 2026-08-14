package com.qiuzhao.flashcards.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.unit.sp
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
        AppTextRole.CardTitle to FigmaTextSpec(20f, 27f, 0f, 630),
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
        AppTextRole.CardTitle to FigmaTextSpec(20f, 20f, 0f, 700),
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

    fun spec(role: AppTextRole, language: AppTextLanguage): FigmaTextSpec =
        if (language == AppTextLanguage.Chinese) chinese.getValue(role) else latin.getValue(role)

    fun lineHeight(role: AppTextRole): Float = maxOf(
        chinese.getValue(role).lineHeight, latin.getValue(role).lineHeight
    )

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

val AppShapeRadius = 32

private val LightColors = lightColorScheme(
    primary = Color(0xFF489FFF), onPrimary = Color.White,
    primaryContainer = Color(0xFFE5F1FF), onPrimaryContainer = Color(0xFF374B61),
    secondary = Color(0xFF4A545F), onSecondary = Color.White,
    surface = Color.White, surfaceVariant = Color(0xFFF0F8FF), background = Color.White,
    onSurface = Color(0xFF000D1C), onSurfaceVariant = Color(0xFF8E8E93),
    error = Color(0xFFFF3B30)
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF90C5FF), onPrimary = Color(0xFF00345F),
    primaryContainer = Color(0xFF1A3B59), onPrimaryContainer = Color(0xFFD9ECFF),
    secondary = Color(0xFFC5D1DE), surface = Color(0xFF151E27),
    surfaceVariant = Color(0xFF1D2B38), background = Color(0xFF0E151C),
    onSurface = Color(0xFFE9F1FA), onSurfaceVariant = Color(0xFFB8C7D6),
    outline = Color(0xFF3D5062)
)

private val AppTypography = Typography(
    displayLarge = androidx.compose.ui.text.TextStyle(fontFamily = AppFonts.GoogleSansFlexBold, fontSize = 48.sp, lineHeight = 48.sp),
    headlineSmall = androidx.compose.ui.text.TextStyle(fontFamily = AppFonts.MiSansSemibold, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = androidx.compose.ui.text.TextStyle(fontFamily = AppFonts.MiSansBold, fontSize = 20.sp, lineHeight = 27.sp),
    titleMedium = androidx.compose.ui.text.TextStyle(fontFamily = AppFonts.MiSansSemibold, fontSize = 16.sp, lineHeight = 21.sp),
    bodyLarge = androidx.compose.ui.text.TextStyle(fontFamily = AppFonts.MiSansMedium, fontSize = 20.sp, lineHeight = 27.sp),
    bodyMedium = androidx.compose.ui.text.TextStyle(fontFamily = AppFonts.MiSansMedium, fontSize = 18.sp, lineHeight = 24.sp),
    labelLarge = androidx.compose.ui.text.TextStyle(fontFamily = AppFonts.MiSansBold, fontSize = 16.sp, lineHeight = 21.sp, letterSpacing = .6.sp)
)

@Composable
fun AutumnFlashcardsTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, typography = AppTypography, content = content)
}
