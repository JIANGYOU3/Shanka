package com.qiuzhao.flashcards.ui

import android.app.Activity
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.ui.NavDisplay
import com.qiuzhao.flashcards.data.CardDraft
import com.qiuzhao.flashcards.data.remote.DeckProgress
import com.qiuzhao.flashcards.data.remote.DeckSummary
import com.qiuzhao.flashcards.data.remote.FlashcardEntity
import com.qiuzhao.flashcards.data.ImportParser
import com.qiuzhao.flashcards.data.remote.Rating
import com.qiuzhao.flashcards.R
import com.qiuzhao.flashcards.ui.motion.AppMotion
import com.qiuzhao.flashcards.ui.navigation.AppNavigator
import com.qiuzhao.flashcards.ui.navigation.AppRoute
import com.qiuzhao.flashcards.ui.navigation.rememberAppNavigationState
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/** 类型安全导航快捷别名（拆分后由本文件共享，14 个屏幕签名引用）。 */
internal typealias ScreenNavigator = AppNavigator

private val LightHeaderControlBackground = AppColors.Blue.surface
private val LightHeaderControlIcon = AppColors.Blue.ink
private val LightSecondaryHeaderActionBackground = AppColors.Blue.primarySecondary
private val PageTitleColor = AppColors.TextIconDark

@Composable
internal fun PageForegroundColor(): Color = PageTitleColor

@Composable
internal fun HeaderControlBackgroundColor(): Color = LightHeaderControlBackground

@Composable
internal fun HeaderControlIconColor(): Color = LightHeaderControlIcon

/**
 * Figma 209:2733's secondary-page action surface. Root-level controls keep
 * their neutral #EBF0F5 treatment; secondary-page back/edit controls use this
 * blue secondary-emphasis token unless a themed page supplies its own surface.
 */
@Composable
internal fun SecondaryHeaderActionBackgroundColor(theme: DeckTheme? = null): Color = when {
    theme != null -> theme.surface
    else -> LightSecondaryHeaderActionBackground
}

/** Keeps Figma's optical type scale stable even when the phone font-size setting changes. */
@Composable
internal fun fixedSp(value: Float) = with(LocalDensity.current) { value.dp.toSp() }

/**
 * The root navigation occupies 125dp at the device bottom (85dp bar, 16dp
 * outside inset and the system navigation inset). A 148dp scroll tail leaves
 * the Figma 16–24dp visual gap above it when a list reaches its final item.
 */
internal const val RootNavigationScrollTail = 148

/**
 * Bottom spacing for a scrolling page which has no overlaying bottom control.
 * This is the natural visual tail from the final item to the system area.
 */
internal const val NaturalScrollTail = 32

/**
 * Returns the scroll tail needed to clear a control fixed over the page bottom.
 *
 * The fixed control itself is lifted by [bottomOffset] and
 * `navigationBarsPadding()`. The list needs to clear those two areas plus the
 * 16dp Figma breathing room. Keeping that arithmetic here prevents individual
 * screens from accumulating unrelated 140–188dp padding values.
 */
internal fun fixedBottomControlScrollTail(
    controlHeight: Int = 60,
    bottomOffset: Int = 32,
    controlCount: Int = 1,
    gapBetweenControls: Int = 0
): Int = controlHeight * controlCount + gapBetweenControls + bottomOffset + 24 + 16

/** Figma text frames have no Android ascent/descent padding around their line box. */
internal fun figmaCardTextStyle() = TextStyle(
    platformStyle = PlatformTextStyle(includeFontPadding = false)
)

/** Figma 378:1775's Chinese-only root navigation label tokens. */
@Composable
internal fun navigationBarLabelTextStyle(selected: Boolean, designScale: Float): TextStyle {
    val spec = AppTypographyTokens.navigationBarLabelSpec(selected)
    return TextStyle(
        fontFamily = AppTypographyTokens.fontFamily(AppTextLanguage.Chinese, spec.weight),
        fontWeight = FontWeight.Normal,
        fontSize = fixedSp(spec.size * designScale),
        lineHeight = fixedSp(spec.lineHeight * designScale),
        letterSpacing = fixedSp(spec.letterSpacing * designScale),
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    )
}

/** All product copy enters Compose through this script-aware text renderer. */
@Composable
internal fun AppText(
    text: String,
    role: AppTextRole,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    designScale: Float = 1f,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    minLines: Int = 1,
    textDecoration: TextDecoration? = null,
    style: TextStyle = TextStyle.Default
) {
    val cjkSpec = AppTypographyTokens.spec(role, AppTextLanguage.Chinese)
    val latinSpec = AppTypographyTokens.spec(role, AppTextLanguage.Latin)
    val cjkSize = fixedSp(cjkSpec.size * designScale)
    val latinSize = fixedSp(latinSpec.size * designScale)
    val lineHeight = fixedSp(AppTypographyTokens.lineHeight(role) * designScale)
    val cjkLetterSpacing = fixedSp(cjkSpec.letterSpacing * designScale)
    val latinLetterSpacing = fixedSp(latinSpec.letterSpacing * designScale)
    Text(
        text = bilingualAnnotatedString(text, AppTypographyTokens.fontFamily(AppTextLanguage.Latin, latinSpec.weight), latinSize, latinLetterSpacing, textDecoration),
        modifier = modifier,
        color = color,
        fontFamily = AppTypographyTokens.fontFamily(AppTextLanguage.Chinese, cjkSpec.weight),
        fontWeight = FontWeight.Normal,
        fontSize = cjkSize,
        lineHeight = lineHeight,
        letterSpacing = cjkLetterSpacing,
        textAlign = textAlign,
        textDecoration = textDecoration,
        softWrap = softWrap,
        minLines = minLines,
        style = style.merge(figmaCardTextStyle()),
        maxLines = maxLines,
        overflow = overflow
    )
}

/** Text style for editable Compose fields; visual transformation provides Latin spans. */
@Composable
internal fun appInputTextStyle(
    role: AppTextRole = AppTextRole.Body,
    designScale: Float = 1f,
    color: Color = LocalContentColor.current
): TextStyle {
    val spec = AppTypographyTokens.spec(role, AppTextLanguage.Chinese)
    return TextStyle(
        color = color,
        fontFamily = AppTypographyTokens.fontFamily(AppTextLanguage.Chinese, spec.weight),
        fontWeight = FontWeight.Normal,
        fontSize = fixedSp(spec.size * designScale),
        lineHeight = fixedSp(AppTypographyTokens.lineHeight(role) * designScale),
        letterSpacing = fixedSp(spec.letterSpacing * designScale),
        // BasicTextField needs the complete ascent/descent area; MiSans glyphs
        // otherwise risk being clipped against the input's top edge.
        platformStyle = PlatformTextStyle(includeFontPadding = true)
    )
}

@Composable
internal fun rememberBilingualInputTransformation(
    role: AppTextRole = AppTextRole.Body,
    designScale: Float = 1f
): VisualTransformation {
    val latin = AppTypographyTokens.spec(role, AppTextLanguage.Latin)
    val letterSpacing = fixedSp(latin.letterSpacing * designScale)
    val fontSize = fixedSp(latin.size * designScale)
    val family = AppTypographyTokens.fontFamily(AppTextLanguage.Latin, latin.weight)
    return remember(role, designScale, family, fontSize, letterSpacing) {
        BilingualInputTransformation(family, fontSize, letterSpacing)
    }
}

/** Keeps original offsets exactly intact, including selection and IME composing ranges. */
private class BilingualInputTransformation(
    private val latinFont: FontFamily,
    private val latinFontSize: TextUnit,
    private val letterSpacing: TextUnit
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText = TransformedText(
        bilingualAnnotatedString(text.text, latinFont, latinFontSize, letterSpacing),
        OffsetMapping.Identity
    )
}

/** Han is deliberately narrow: all non-Han characters, including punctuation, use Google Sans Flex. */
internal fun isHanCharacter(character: Char): Boolean = character in '\u3400'..'\u4DBF' ||
    character in '\u4E00'..'\u9FFF' || character in '\uF900'..'\uFAFF'

internal fun splitBilingualRuns(text: String): List<Pair<Boolean, String>> {
    if (text.isEmpty()) return emptyList()
    val runs = mutableListOf<Pair<Boolean, String>>()
    var start = 0
    var currentIsHan = isHanCharacter(text[0])
    for (index in 1 until text.length) {
        val isHan = isHanCharacter(text[index])
        if (isHan != currentIsHan) {
            runs += currentIsHan to text.substring(start, index)
            start = index
            currentIsHan = isHan
        }
    }
    runs += currentIsHan to text.substring(start)
    return runs
}

private fun bilingualAnnotatedString(
    text: String,
    latinFont: FontFamily,
    latinFontSize: TextUnit,
    letterSpacing: TextUnit,
    textDecoration: TextDecoration? = null
): AnnotatedString = buildAnnotatedString {
    splitBilingualRuns(text).forEach { (isHan, run) ->
        if (isHan) append(run) else withStyle(
            SpanStyle(fontFamily = latinFont, fontSize = latinFontSize, letterSpacing = letterSpacing, textDecoration = textDecoration)
        ) { append(run) }
    }
}

/**
 * Figma 720:2251: a visual, non-interactive bottom mask for pages with a
 * fixed bottom action or the root navigation. It deliberately stays out of
 * pages without a bottom control so their content reaches the system area
 * without an artificial white fade.
 */
@Composable
internal fun BottomContentFade(designScale: Float, modifier: Modifier = Modifier, color: Color = Color.White) {
    Box(
        modifier.fillMaxWidth()
            .height((163 * designScale).dp)
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    // Figma's `to-[64.825%]` is the gradient end position:
                    // the page background reaches 90% opacity here and remains
                    // solid below. The colour must match the page canvas so a
                    // themed page fades into its own background, not white.
                    .64825f to color.copy(alpha = .9f),
                    1f to color.copy(alpha = .9f)
                )
            )
            .zIndex(.5f)
    )
}

/**
 * A deliberately quiet failure acknowledgement for optimistic delete actions.
 * It is only shown when the item has to be restored, so normal editing and
 * successful deletion do not add any extra copy to the screen.
 */
@Composable
internal fun DeleteFailureHint(visible: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(140)),
        exit = fadeOut(animationSpec = tween(180)),
        modifier = modifier.zIndex(3f)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 2.dp,
            shadowElevation = 2.dp
        ) {
            Text(
                "删除失败",
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = AppFonts.MiSansMedium,
                fontWeight = FontWeight.Normal,
                fontSize = fixedSp(14f)
            )
        }
    }
}

@Composable
internal fun RoundIconButton(symbol: String, description: String, color: Color, onClick: () -> Unit, size: androidx.compose.ui.unit.Dp = 52.dp, tint: Color, filled: Boolean = true) {
    Surface(onClick = onClick, shape = RoundedCornerShape(999.dp), color = color, modifier = Modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) {
            MaterialSymbol(symbol, description, tint = tint, size = fixedSp(size.value * 25.263f / 52f), filled = filled)
        }
    }
}

/**
 * Figma 856:6605 / 849:6541 generation progress ring. Uses the official
 * Material 3 [CircularProgressIndicator] with a rounded cap and a visible
 * track (the Material 3 expressive look); the shared component is fixed at
 * 80 x 80dp. The fully expressive drawStopIndicator/trackStrokeWidth arrive
 * only in material3 1.4.0-alpha, so this stays on the stable ring API.
 */
@Composable
internal fun GenerationProgressRing(
    color: Color,
    trackColor: Color? = null,
    designScale: Float = 1f,
    strokeWidth: Float = 8f,
    modifier: Modifier = Modifier
) {
    CircularProgressIndicator(
        color = color,
        modifier = modifier.size((80 * designScale).dp),
        strokeWidth = (strokeWidth * designScale).dp,
        trackColor = trackColor ?: color.copy(alpha = .2f),
        strokeCap = StrokeCap.Round
    )
}

/**
 * Figma 373:1691 shared hint/notice box. Radius 24dp; the box lifts to the
 * family Surface when its container is white, otherwise it returns to white.
 * Supporting copy, centred, in the 80% neutral ink.
 */
@Composable
internal fun HintBox(
    text: String,
    parentIsWhite: Boolean,
    theme: DeckTheme,
    designScale: Float,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (parentIsWhite) theme.cardPanel else AppColors.Card,
        shape = RoundedCornerShape((AppNestedShapeRadius * designScale).dp),
        modifier = modifier.fillMaxWidth()
    ) {
        AppText(
            text,
            AppTextRole.Supporting,
            modifier = Modifier.fillMaxWidth().padding((24 * designScale).dp),
            color = AppColors.TextIconDark,
            designScale = designScale,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
internal fun MaterialSymbol(
    name: String,
    description: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    size: androidx.compose.ui.unit.TextUnit = 24.sp,
    filled: Boolean = true,
    includeFontPadding: Boolean = true
) {
    val accessibleModifier = if (description == null) modifier.clearAndSetSemantics { }
    else modifier.semantics { contentDescription = description }
    Text(
        text = name, modifier = accessibleModifier,
        // Material Symbols are Rounded + FILL on + Grade Emphasis by default.
        // The shared main-screen settings button is the sole FILL-off exception.
        color = tint,
        fontFamily = if (filled) AppFonts.MaterialSymbolsRounded else AppFonts.MaterialSymbolsRoundedOff,
        fontSize = size, lineHeight = size,
        style = TextStyle(
            fontFeatureSettings = "liga",
            platformStyle = PlatformTextStyle(includeFontPadding = includeFontPadding)
        ),
        maxLines = 1
    )
}

/**
 * MiSans carries the Chinese copy while every non-Chinese run (Latin, numbers and
 * punctuation) uses the given Google Sans Flex face with ROND=100. Both families
 * are single-face (the wght axis lives in variationSettings), so the span only
 * needs the Latin family \u2014 no per-span fontWeight required.
 */
@Composable
internal fun MixedLanguageText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color,
    chineseFont: FontFamily,
    latinFont: FontFamily,
    fontSize: androidx.compose.ui.unit.TextUnit,
    lineHeight: androidx.compose.ui.unit.TextUnit,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    includeFontPadding: Boolean = true,
    fontWeight: FontWeight = FontWeight.Normal,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    softWrap: Boolean = true,
    minLines: Int = 1,
    style: TextStyle = TextStyle.Default
) {
    val styled = remember(text, latinFont, fontSize, letterSpacing, textDecoration) {
        bilingualAnnotatedString(text, latinFont, fontSize, letterSpacing, textDecoration)
    }
    Text(
        text = styled, modifier = modifier, color = color,
        fontFamily = chineseFont, fontWeight = fontWeight,
        fontSize = fontSize, lineHeight = lineHeight, textAlign = textAlign,
        letterSpacing = letterSpacing, textDecoration = textDecoration,
        softWrap = softWrap, minLines = minLines,
        style = if (includeFontPadding) style else style.merge(figmaCardTextStyle()),
        maxLines = maxLines, overflow = overflow
    )
}

/** Figma 307:1419 — shared, text-only explanatory card used across import flows. */
@Composable
internal fun DescriptionInfoCard(text: String, scale: Float) {
    Surface(
        shape = RoundedCornerShape((AppNestedShapeRadius * scale).dp),
        color = AppColors.Purple.background,
        modifier = Modifier.fillMaxWidth().heightIn(min = (102 * scale).dp)
    ) {
        MixedLanguageText(
            text = text,
            modifier = Modifier.padding((24 * scale).dp),
            color = AppColors.TextIconDark,
            chineseFont = AppFonts.MiSansMedium,
            latinFont = AppFonts.GoogleSansFlex,
            fontSize = fixedSp(20 * scale),
            lineHeight = fixedSp(24 * scale),
            includeFontPadding = false
        )
    }
}
