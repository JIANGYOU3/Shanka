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
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
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
import com.qiuzhao.flashcards.data.remote.ProjectSummary
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


internal data class StudyDeckVisual(
    val background: Color,
    val iconBackground: Color,
    val icon: String,
    val iconTint: Color,
    val titleColor: Color,
    val panel: Color,
    val badgeText: Color,
    val progress: Color,
    val progressFill: Color,
    val progressLabel: Color,
    val progressTrack: Color,
    val action: Color,
)

/** Theme tokens travel with a deck, rather than being inferred from where a screen is opened. */
internal data class DeckTheme(
    val key: String,
    val label: String,
    /** Figma family background; this is the project-page canvas, never plain white. */
    val background: Color,
    val primary: Color,
    val action: Color,
    val progress: Color,
    val progressFill: Color,
    val onPrimary: Color,
    val surface: Color,
    val cardPanel: Color,
    val secondary: Color,
    val strongText: Color,
    val text: Color,
    val mutedText: Color,
    val progressTrack: Color,
    /** Figma 257:6634 gives the purple icon a deliberately softer 24dp corner. */
    val cardIconCornerRadius: Int,
    /** Purple's compact progress panel uses 16dp inset; the other families use 12dp. */
    val cardProgressPanelPadding: Int
)

internal val DeckThemes = listOf(
    deckTheme("azure", "蓝色", AppColors.Blue),
    deckTheme("violet", "紫色", AppColors.Purple),
    deckTheme("mint", "绿色", AppColors.Green),
    deckTheme("coral", "粉色", AppColors.Pink),
    deckTheme("amber", "黄色", AppColors.Orange)
)

/** Decks reference one of the five Figma families; no screen-local swatches. */
private fun deckTheme(key: String, label: String, colors: AppColorFamily) = DeckTheme(
    key = key,
    label = label,
    background = colors.background,
    primary = colors.primary,
    action = colors.primary,
    progress = colors.primaryStrong,
    progressFill = colors.primary,
    onPrimary = AppColors.TextIconLight,
    surface = colors.background,
    cardPanel = colors.surface,
    secondary = colors.primarySecondary,
    strongText = colors.ink,
    text = AppColors.TextIconDark,
    mutedText = colors.ink,
    // Figma 258:6804 / 645:2168: the unfinished segment is the family's
    // Primary-Secondary step; Background belongs to the enclosing panel.
    progressTrack = colors.primarySecondary,
    cardIconCornerRadius = if (key == "violet") 24 else 16,
    cardProgressPanelPadding = if (key == "violet") 16 else 12
)

internal fun deckTheme(deck: DeckSummary): DeckTheme = DeckThemes.firstOrNull { it.key == deck.themeKey } ?: DeckThemes.first()

/**
 * Card-group colour is owned by its project. The deck field remains a legacy
 * fallback so standalone server decks keep their established appearance until
 * the project migration is complete.
 */
internal fun deckTheme(deck: DeckSummary, projects: List<ProjectSummary>): DeckTheme =
    deck.projectId
        ?.let { projectId -> projects.firstOrNull { it.id == projectId } }
        ?.let(::deckTheme)
        ?: deckTheme(deck)

internal fun deckTheme(project: ProjectSummary): DeckTheme =
    DeckThemes.firstOrNull { it.key == project.themeKey } ?: DeckThemes.first()

/**
 * A card group's outer level is derived from the canvas behind it, rather than
 * from its position in a list. This keeps every project-owned deck consistent
 * across the three root pages and a project's coloured detail page.
 */
internal enum class ProjectThemedCardVariant { BASE_PAGE, THEME_BACKGROUND }

internal data class ProjectThemedCardPalette(
    val background: Color,
    val panel: Color,
    val badge: Color,
    val progressTrack: Color
)

/**
 * Figma 184:616 / 494:1447 / 19:621: a card on the neutral white canvas uses
 * the family's Background step; the same card on a family Background canvas
 * uses Surface. Its nested panel always supplies the next visible level.
 */
internal fun projectThemedCardPalette(
    theme: DeckTheme,
    variant: ProjectThemedCardVariant
): ProjectThemedCardPalette = when (variant) {
    ProjectThemedCardVariant.BASE_PAGE -> ProjectThemedCardPalette(
        background = theme.background,
        panel = theme.cardPanel,
        badge = theme.cardPanel,
        // Figma 950:4943: the unfinished segment carries the family's
        // Background token — the same colour as the card itself.
        progressTrack = theme.background
    )
    ProjectThemedCardVariant.THEME_BACKGROUND -> ProjectThemedCardPalette(
        background = theme.cardPanel,
        panel = theme.background,
        badge = theme.background,
        progressTrack = theme.secondary
    )
}

@Composable
internal fun studyDeckVisual(deck: DeckSummary, @Suppress("UNUSED_PARAMETER") index: Int): StudyDeckVisual {
    val theme = deckTheme(deck)
    return StudyDeckVisual(
        background = theme.surface,
        iconBackground = theme.primary,
        icon = studyDeckIcon(deck),
        iconTint = theme.onPrimary,
        titleColor = theme.text,
        // Figma 257:6634: card panels use their own token, while detail-page
        // secondary buttons continue to use Figma 258:7544's secondary token.
        panel = theme.cardPanel,
        badgeText = theme.strongText,
        progress = theme.progress,
        progressFill = theme.progressFill,
        progressLabel = theme.strongText,
        progressTrack = theme.progressTrack,
        action = theme.action
    )
}

/** A small, semantic icon vocabulary keeps bundled and imported decks visually coherent. */
internal fun studyDeckIcon(deck: DeckSummary): String = when (deck.chapter) {
    1 -> "smart_toy"
    2 -> "account_tree"
    3 -> "memory"
    4 -> "extension"
    5 -> "code"
    6 -> "fact_check"
    7 -> "school"
    8 -> "update"
    9 -> "image"
    10 -> "groups"
    else -> "note_stack"
}

/** The short chapter name is real deck metadata, shared by the list and detail title. */
internal fun displayDeckTitle(deck: DeckSummary): String = if (deck.source != "builtin" || !deck.name.startsWith("第 ")) {
    deck.name
} else when (deck.chapter) {
    1 -> "Agent 基础"
    2 -> "上下文工程"
    3 -> "记忆与知识库"
    4 -> "工具与 MCP"
    5 -> "Coding Agent"
    6 -> "Agent 评估"
    7 -> "模型后训练"
    8 -> "持续进化"
    9 -> "多模态与实时"
    10 -> "多 Agent 协作"
    else -> deck.name
}

@Composable
internal fun DeckDetailHeader(title: String, designScale: Float, onBack: () -> Unit, theme: DeckTheme? = null, subtitle: String? = null, modifier: Modifier = Modifier) {
    // Theme decks use their card surface for the back control, per Figma 41:1623;
    // neutral pages use the default secondary-header treatment from 209:2733.
    ScreenTopInformationBar(
        title = title,
        subtitle = subtitle,
        onBack = onBack,
        backContainer = theme?.cardPanel,
        titleColor = theme?.text,
        modifier = modifier
    )
}
