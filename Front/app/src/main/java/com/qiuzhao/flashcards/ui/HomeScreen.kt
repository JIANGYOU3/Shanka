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
import com.qiuzhao.flashcards.data.remote.Dashboard
import com.qiuzhao.flashcards.data.ImportParser
import com.qiuzhao.flashcards.data.remote.Rating
import com.qiuzhao.flashcards.data.remote.ProjectSummary
import com.qiuzhao.flashcards.R
import com.qiuzhao.flashcards.ui.motion.AppMotion
import com.qiuzhao.flashcards.ui.navigation.AppNavigator
import com.qiuzhao.flashcards.ui.navigation.AppRoute
import com.qiuzhao.flashcards.ui.navigation.rememberAppNavigationState
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay


@Composable
internal fun HomeScreen(decks: List<DeckSummary>, projects: List<ProjectSummary>, dueCount: Int, nav: ScreenNavigator) {
    val activeDeck = decks.firstOrNull { it.dueCount > 0 } ?: decks.firstOrNull()
    // One Figma design canvas: 402dp wide. On a narrower phone, every visual value
    // uses this one scale rather than responding independently to display/font settings.
    val compactScale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(0.75f, 1f)
    val sideInset = 16 * compactScale
    // The persistent root shell owns the navigation. Home owns only its scrollable body.
    Box(Modifier.fillMaxSize().statusBarsPadding()) {
            // This is the fixed, rounded viewport from Figma node 19:611. The list may
            // scroll inside it, but nothing can paint into the fixed settings/header area.
            // The app content area already starts beneath the device status inset.
            // Header starts at 16dp and is 56dp tall. The 88dp viewport inset keeps
            // Figma's explicit 16dp gap between it and the first content card.
            Box(Modifier.fillMaxSize().padding(start = sideInset.dp, top = (88 * compactScale).dp, end = sideInset.dp)) {
                // The positioning box establishes the viewport bounds. Only its inner
                // child is clipped, so the crop begins below the fixed settings layer.
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(AppShapeRadius.dp))) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        // Leave a scroll tail so the two quick cards can move fully
                        // above the floating navigation instead of becoming trapped by it.
                        contentPadding = PaddingValues(bottom = (180 * compactScale).dp),
                        verticalArrangement = Arrangement.spacedBy((12 * compactScale).dp)
                    ) {
                    item { DailyGoalCard(compactScale) }
                    item {
                        // Node 19:620 has a 12dp title-to-card-group gap; only the
                        // two cards *inside* the group retain the 16dp spacing.
                        Column(verticalArrangement = Arrangement.spacedBy((12 * compactScale).dp)) {
                            Text(
                                "用户名，快来学习", modifier = Modifier.padding(horizontal = (8 * compactScale).dp), color = PageForegroundColor(), fontFamily = AppFonts.MiSansBold,
                                fontWeight = FontWeight.Normal, fontSize = fixedSp(20 * compactScale), lineHeight = fixedSp(28 * compactScale)
                            )
                            Column(verticalArrangement = Arrangement.spacedBy((16 * compactScale).dp)) {
                                ContinueLearningCard(
                                    deck = activeDeck, projects = projects,
                                    compactScale = compactScale,
                                    onOpenDeck = { activeDeck?.let { nav.navigate(AppRoute.Deck(it.id)) } },
                                    onContinue = { activeDeck?.let { nav.navigate(AppRoute.Study(it.id, true)) } }
                                )
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy((16 * compactScale).dp)) {
                                    QuickLearningCard(
                                        modifier = Modifier.weight(1f), background = AppColors.Pink.background,
                                        button = AppColors.Pink.primary, textColor = AppColors.Pink.ink,
                                        iconBackground = AppColors.Pink.primarySecondary,
                                        icon = "brightness_alert", iconTint = AppColors.Pink.ink,
                                        label = "昨日错题",
                                        compactScale = compactScale, onClick = { activeDeck?.let { nav.navigate(AppRoute.Study(it.id, true)) } }
                                    )
                                    QuickLearningCard(
                                        modifier = Modifier.weight(1f), background = AppColors.Orange.background,
                                        button = AppColors.Orange.primary, textColor = AppColors.Orange.ink,
                                        iconBackground = AppColors.Orange.primarySecondary,
                                        icon = "star_shine", iconTint = AppColors.Orange.ink,
                                        label = "随机复习",
                                        compactScale = compactScale, onClick = { activeDeck?.let { nav.navigate(AppRoute.Study(it.id, false)) } }
                                    )
                                }
                            }
                        }
                    }
                    }
                }
            }
            BottomContentFade(compactScale, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun DailyGoalCard(compactScale: Float) {
    Card(
        shape = RoundedCornerShape(AppShapeRadius.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.Blue.primary),
        modifier = Modifier.fillMaxWidth().height((196 * compactScale).dp)
    ) {
        Column(
            Modifier.fillMaxSize().padding((24 * compactScale).dp),
            verticalArrangement = Arrangement.spacedBy((24 * compactScale).dp)
        ) {
            Row(Modifier.fillMaxWidth().height((32 * compactScale).dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(
                    modifier = Modifier.width((115 * compactScale).dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MaterialSymbol("local_fire_department", null, tint = AppColors.TextIconLight, size = fixedSp(28 * compactScale), filled = true)
                    Spacer(Modifier.width((8 * compactScale).dp))
                    Text("今日目标", color = AppColors.TextIconLight, fontFamily = AppFonts.MiSansBold, fontWeight = FontWeight.Normal, fontSize = fixedSp(20 * compactScale), lineHeight = fixedSp(28 * compactScale), letterSpacing = fixedSp(-.5f * compactScale))
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = AppColors.TextIconLight,
                    modifier = Modifier.width((134 * compactScale).dp).height((32 * compactScale).dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        MixedLanguageText(
                            text = "连续天数：12",
                            modifier = Modifier.fillMaxWidth(),
                            color = AppColors.Blue.ink,
                            chineseFont = AppFonts.MiSansBold,
                            latinFont = AppFonts.GoogleSansFlexBold,
                            fontSize = fixedSp(16 * compactScale),
                            lineHeight = fixedSp(16 * compactScale),
                            letterSpacing = fixedSp(.6f * compactScale),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth().height((48 * compactScale).dp), verticalAlignment = Alignment.Bottom) {
                Text("12", modifier = Modifier.alignByBaseline(), fontFamily = AppFonts.GoogleSansFlexBold, fontSize = fixedSp(48 * compactScale), lineHeight = fixedSp(48 * compactScale), fontWeight = FontWeight.Normal, color = AppColors.TextIconLight, letterSpacing = fixedSp(-2.4f * compactScale))
                Text("/ 50", modifier = Modifier.padding(start = (4 * compactScale).dp).alignByBaseline(), fontFamily = AppFonts.GoogleSansFlexBold, fontSize = fixedSp(20 * compactScale), lineHeight = fixedSp(28 * compactScale), fontWeight = FontWeight.Normal, color = AppColors.TextIconLight.copy(alpha = .75f))
                Text("卡片已复习", modifier = Modifier.padding(start = (4 * compactScale).dp).alignByBaseline(), color = AppColors.TextIconLight.copy(alpha = .75f), fontFamily = AppFonts.MiSansSemibold, fontWeight = FontWeight.Normal, fontSize = fixedSp(20 * compactScale), lineHeight = fixedSp(28 * compactScale))
                Spacer(Modifier.weight(1f))
                Text("24%", modifier = Modifier.alignByBaseline(), fontFamily = AppFonts.GoogleSansFlexBold, fontSize = fixedSp(47 * compactScale), lineHeight = fixedSp(36 * compactScale), fontWeight = FontWeight.Normal, color = AppColors.TextIconLight)
            }
            Row(Modifier.fillMaxWidth().height((20 * compactScale).dp), horizontalArrangement = Arrangement.spacedBy((5 * compactScale).dp)) {
                Box(Modifier.width((97 * compactScale).dp).fillMaxSize().clip(RoundedCornerShape(999.dp)).background(AppColors.Card))
                Box(Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(999.dp)).background(AppColors.Card.copy(alpha = .5f)))
            }
        }
    }
}

@Composable
private fun ContinueLearningCard(
    deck: DeckSummary?,
    projects: List<ProjectSummary>,
    compactScale: Float,
    onOpenDeck: () -> Unit,
    onContinue: () -> Unit
) {
    val fallbackDeck = deck ?: DeckSummary("", "计算机网络", 2, "builtin", "violet", 20, 14)
    val theme = deckTheme(fallbackDeck, projects)
    val cardCount = deck?.cardCount ?: 20
    val dueCount = deck?.dueCount ?: 14
    val progress = if (cardCount == 0) 0f else ((cardCount - dueCount).coerceAtLeast(0).toFloat() / cardCount).coerceIn(0f, 1f)
    ProjectThemedCard(
        title = displayDeckTitle(fallbackDeck),
        count = cardCount,
        countLabel = "cards",
        progress = progress,
        theme = theme,
        icon = studyDeckIcon(fallbackDeck),
        variant = ProjectThemedCardVariant.TINTED,
        designScale = compactScale,
        onClick = onOpenDeck,
        actionLabel = "继续复习",
        onAction = onContinue
    )
}

@Composable
private fun QuickLearningCard(
    modifier: Modifier,
    background: Color,
    button: Color,
    textColor: Color,
    iconBackground: Color,
    icon: String,
    iconTint: Color,
    label: String,
    compactScale: Float,
    onClick: () -> Unit
) {
    // Figma 287:8015: each quick-review card is 177×172dp, with two
    // equal 56dp tiles above a 52dp text-only action button.
    Card(shape = RoundedCornerShape(AppShapeRadius.dp), colors = CardDefaults.cardColors(containerColor = background), modifier = modifier.height((172 * compactScale).dp)) {
        Column(
            Modifier.fillMaxSize().padding((24 * compactScale).dp),
            verticalArrangement = Arrangement.spacedBy((16 * compactScale).dp)
        ) {
            Row(
                Modifier.fillMaxWidth().height((56 * compactScale).dp),
                horizontalArrangement = Arrangement.spacedBy((16 * compactScale).dp)
            ) {
                Surface(
                    shape = RoundedCornerShape((18 * compactScale).dp),
                    color = iconBackground,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        MaterialSymbol(icon, null, tint = iconTint, size = fixedSp(24 * compactScale), filled = true)
                    }
                }
                Surface(
                    shape = RoundedCornerShape((18 * compactScale).dp),
                    color = iconBackground,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        MaterialSymbol("arrow_forward", null, tint = iconTint, size = fixedSp(20 * compactScale), filled = true)
                    }
                }
            }
            Surface(
                onClick = onClick,
                shape = RoundedCornerShape(AppShapeRadius.dp),
                color = button,
                contentColor = textColor,
                modifier = Modifier.fillMaxWidth().height((52 * compactScale).dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        label,
                        color = textColor,
                        // Figma 287:8015: both Chinese action labels use MiSans VF 630.
                        // Use the card-specific 630 face explicitly so this cannot
                        // regress to Android's semantic Bold mapping.
                        fontFamily = AppFonts.MiSansBold,
                        fontWeight = FontWeight.Normal,
                        fontSize = fixedSp(16 * compactScale),
                        lineHeight = fixedSp(20 * compactScale),
                        style = figmaCardTextStyle()
                    )
                }
            }
        }
    }
}

/** Figma 287:8214 — the reusable English two-line total-card badge. */
@Composable
internal fun ReviewCountBadge(
    count: Int,
    background: Color,
    contentColor: Color,
    compactScale: Float,
    label: String = "cards"
) {
    Surface(
        color = background,
        shape = RoundedCornerShape(999.dp),
        // 287:8214: intrinsic Figma sizing — the 24dp icon and the two-line
        // text stack determine the height; the component itself supplies the
        // specified 12dp vertical padding without an Android-imposed height.
        modifier = Modifier
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = (16 * compactScale).dp,
                vertical = (12 * compactScale).dp
            ),
            horizontalArrangement = Arrangement.spacedBy((8 * compactScale).dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MaterialSymbol(
                "playing_cards",
                null,
                tint = contentColor,
                size = fixedSp(24 * compactScale)
            )
            // 287:8214 latest: the first text row overlaps the second by 2dp
            // (Figma's negative bottom margin), rather than using a positive gap.
            Column(verticalArrangement = Arrangement.spacedBy((-2 * compactScale).dp)) {
                Text(
                    count.toString(),
                    color = contentColor,
                    fontFamily = AppFonts.GoogleSansFlexExtraBold,
                    fontWeight = FontWeight.Normal,
                    fontSize = fixedSp(16 * compactScale),
                    // Figma's wrapper is 16dp, but its paragraph uses the
                    // font's natural line metrics; leaving this unspecified
                    // preserves the visible glyphs instead of Compose-clipping
                    // the second line.
                    lineHeight = TextUnit.Unspecified,
                    letterSpacing = fixedSp(.6f * compactScale),
                    style = figmaCardTextStyle()
                )
                Text(
                    label,
                    color = contentColor,
                    fontFamily = AppFonts.GoogleSansFlexExtraBold,
                    fontWeight = FontWeight.Normal,
                    fontSize = fixedSp(16 * compactScale),
                    lineHeight = TextUnit.Unspecified,
                    letterSpacing = fixedSp(.6f * compactScale),
                    style = figmaCardTextStyle()
                )
            }
        }
    }
}
