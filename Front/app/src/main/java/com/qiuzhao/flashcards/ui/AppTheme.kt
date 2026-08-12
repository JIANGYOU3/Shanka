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

/**
 * Project-wide Figma typography. Every text family is intentionally bilingual:
 * Google Sans Flex is the first fallback for Latin/digits and MiSans VF supplies
 * CJK glyphs. This prevents Android's system font from entering mixed-language text.
 */
@OptIn(ExperimentalTextApi::class)
object AppFonts {
    /** A CJK-only face for components whose Figma typography must never fallback. */
    private fun miSansOnly(weight: Int): FontFamily = FontFamily(
        Font(
            R.font.misans_vf,
            weight = FontWeight.Normal,
            variationSettings = FontVariation.Settings(FontVariation.Setting("wght", weight.toFloat()))
        )
    )

    private fun bilingual(weight: Int): FontFamily = FontFamily(
        Font(
            R.font.google_sans_flex,
            // Text composables deliberately request Normal so Compose must select
            // this exact face; the variable-font axis below supplies the Figma
            // weight instead of Android falling back to synthetic 400.
            weight = FontWeight.Normal,
            variationSettings = FontVariation.Settings(
                FontVariation.Setting("ROND", 100f),
                FontVariation.Setting("wdth", 100f),
                FontVariation.Setting("wght", weight.toFloat())
            )
        ),
        Font(
            R.font.misans_vf,
            weight = FontWeight.Normal,
            variationSettings = FontVariation.Settings(FontVariation.Setting("wght", weight.toFloat()))
        )
    )

    // Figma 234:5020 — MiSans VF is the sole Chinese typeface for the app.
    val MiSansThin = bilingual(150)
    val MiSansExtraLight = bilingual(200)
    val MiSansLight = bilingual(250)
    val MiSansNormal = bilingual(305)
    val MiSansRegular = bilingual(330)
    val MiSansMedium = bilingual(380)
    val MiSansDemibold = bilingual(450)
    val MiSansSemibold = bilingual(520)
    val MiSansBold = bilingual(630)
    val MiSansHeavy = bilingual(700)

    // Figma card component: use the CJK source directly. MixedLanguageText
    // supplies Google Sans Flex only for its Latin/digit spans.
    val MiSansCardSemibold = miSansOnly(520)
    val MiSansCardBold = miSansOnly(630)

    // Compatibility names point to the exact Figma tokens; new UI should prefer
    // the named token above rather than Android's conventional 400/600/700 mapping.
    val MiSans = MiSansRegular
    val MiSans450 = MiSansDemibold
    val MiSans630 = MiSansBold
    val MiSansNavigation = MiSansSemibold

    /** Shared top-information rule: CJK Semibold 520, Latin/digits Bold 700. */
    val MiSansTopInformation = FontFamily(
        Font(
            R.font.google_sans_flex,
            weight = FontWeight.Normal,
            variationSettings = FontVariation.Settings(
                FontVariation.Setting("ROND", 100f),
                FontVariation.Setting("wdth", 100f),
                FontVariation.Setting("wght", 700f)
            )
        ),
        Font(
            R.font.misans_vf,
            weight = FontWeight.Normal,
            variationSettings = FontVariation.Settings(FontVariation.Setting("wght", 520f))
        )
    )

    val GoogleSansFlex = FontFamily(
        Font(
            R.font.google_sans_flex,
            variationSettings = FontVariation.Settings(
                FontVariation.Setting("ROND", 100f),
                FontVariation.Setting("wdth", 100f)
            )
        )
    )
    val GoogleSansFlexSemibold = FontFamily(
        Font(
            R.font.google_sans_flex,
            weight = FontWeight.Normal,
            variationSettings = FontVariation.Settings(
                FontVariation.Setting("ROND", 100f),
                FontVariation.Setting("wdth", 100f),
                FontVariation.Setting("wght", 520f)
            )
        )
    )
    val GoogleSansFlexExtraBold = FontFamily(
        Font(
            R.font.google_sans_flex,
            weight = FontWeight.Normal,
            variationSettings = FontVariation.Settings(
                FontVariation.Setting("ROND", 100f),
                FontVariation.Setting("wdth", 100f),
                FontVariation.Setting("wght", 800f)
            )
        )
    )
    val GoogleSansFlexBold = FontFamily(
        Font(
            R.font.google_sans_flex,
            weight = FontWeight.Normal,
            variationSettings = FontVariation.Settings(
                // Figma card text is neutral grade; icon emphasis is configured separately.
                FontVariation.Setting("GRAD", 0f),
                FontVariation.Setting("ROND", 100f),
                FontVariation.Setting("wdth", 100f),
                FontVariation.Setting("wght", 700f)
            )
        )
    )
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
    /** Explicit exception for the shared main-screen settings control: Rounded FILL off. */
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
    displayLarge = androidx.compose.ui.text.TextStyle(fontFamily = AppFonts.GoogleSansFlexBold, fontWeight = FontWeight.Normal, fontSize = 48.sp),
    headlineMedium = androidx.compose.ui.text.TextStyle(fontFamily = AppFonts.MiSansBold, fontWeight = FontWeight.Normal, fontSize = 28.sp),
    headlineSmall = androidx.compose.ui.text.TextStyle(fontFamily = AppFonts.MiSansBold, fontWeight = FontWeight.Normal, fontSize = 24.sp),
    titleLarge = androidx.compose.ui.text.TextStyle(fontFamily = AppFonts.MiSansSemibold, fontWeight = FontWeight.Normal, fontSize = 20.sp),
    titleMedium = androidx.compose.ui.text.TextStyle(fontFamily = AppFonts.MiSansSemibold, fontWeight = FontWeight.Normal, fontSize = 16.sp),
    bodyLarge = androidx.compose.ui.text.TextStyle(fontFamily = AppFonts.MiSansRegular, fontWeight = FontWeight.Normal, fontSize = 16.sp),
    bodyMedium = androidx.compose.ui.text.TextStyle(fontFamily = AppFonts.MiSansRegular, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    labelLarge = androidx.compose.ui.text.TextStyle(fontFamily = AppFonts.MiSansSemibold, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    labelMedium = androidx.compose.ui.text.TextStyle(fontFamily = AppFonts.MiSansMedium, fontWeight = FontWeight.Normal, fontSize = 12.sp)
)

@Composable
fun AutumnFlashcardsTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, typography = AppTypography, content = content)
}
