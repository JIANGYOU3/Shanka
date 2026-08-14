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
internal fun LibraryScreen(decks: List<DeckSummary>, viewModel: AppViewModel, searchQuery: String, nav: ScreenNavigator) {
    val designScale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(0.75f, 1f)
    val sideInset = 16 * designScale
    var pendingDeletedDecks by remember { mutableStateOf<Set<String>>(emptySet()) }
    var deleteFailed by remember { mutableStateOf(false) }
    val visibleDecks = decks.filter { it.id !in pendingDeletedDecks && it.name.contains(searchQuery, ignoreCase = true) }
    var deckPendingDeletion by remember { mutableStateOf<DeckSummary?>(null) }

    LaunchedEffect(deleteFailed) {
        if (deleteFailed) {
            delay(1_800)
            deleteFailed = false
        }
    }
    LaunchedEffect(decks) {
        pendingDeletedDecks = pendingDeletedDecks.intersect(decks.map { it.id }.toSet())
    }

    Box(Modifier.fillMaxSize().statusBarsPadding()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(start = sideInset.dp, top = (88 * designScale).dp, end = sideInset.dp),
                verticalArrangement = Arrangement.spacedBy((16 * designScale).dp)
            ) {
                StudyAddDeckButton(designScale) { nav.navigate(AppRoute.Import) }
                Box(
                    // This viewport fills all space down to the navigation safe area. Only
                    // its own deck flow scrolls; the fixed header and add button do not.
                    modifier = Modifier.weight(1f).fillMaxWidth()
                        .clip(RoundedCornerShape(AppShapeRadius.dp))
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        // The navigation bar overlays this clipped viewport. This tail lets
                        // the last card travel fully above the fade/navigation area.
                        contentPadding = PaddingValues(bottom = (180 * designScale).dp),
                        verticalArrangement = Arrangement.spacedBy((16 * designScale).dp)
                    ) {
                        items(visibleDecks, key = { it.id }) { deck ->
                            val progress by viewModel.deckProgress(deck.id).collectAsState(
                                initial = DeckProgress(deck.cardCount, deck.dueCount, masteredCards = 0, reviewCount = 0)
                            )
                            val visual = studyDeckVisual(deck, decks.indexOfFirst { it.id == deck.id })
                            StudyDeckCard(
                                deck = deck,
                                progress = progress,
                                visual = visual,
                                designScale = designScale,
                                onClick = { nav.navigate(AppRoute.Deck(deck.id)) },
                                onDelete = { deckPendingDeletion = deck }
                            )
                        }
                    }
                }
            }
            BottomContentFade(designScale, Modifier.align(Alignment.BottomCenter))
            DeleteFailureHint(
                visible = deleteFailed,
                modifier = Modifier.align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = (88 * designScale).dp)
            )
    }
    deckPendingDeletion?.let { deck ->
        AlertDialog(
            onDismissRequest = { deckPendingDeletion = null },
            title = { Text("删除卡片组", fontFamily = AppFonts.MiSansSemibold, fontWeight = FontWeight.Normal) },
            text = {
                MixedLanguageText(
                    text = "“${displayDeckTitle(deck)}”及其中的卡片将从本机删除。",
                    color = MaterialTheme.colorScheme.onSurface,
                    chineseFont = AppFonts.MiSansMedium,
                    latinFont = AppFonts.GoogleSansFlex,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingDeletedDecks = pendingDeletedDecks + deck.id
                    viewModel.deleteDeck(deck.id, onFailure = {
                        pendingDeletedDecks = pendingDeletedDecks - deck.id
                        deleteFailed = true
                    })
                    deckPendingDeletion = null
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deckPendingDeletion = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun StudyAddDeckButton(designScale: Float, onClick: () -> Unit) {
    Surface(
        onClick = onClick, color = Color(0xFF489FFF), contentColor = Color(0xFFEBF5FF),
        shape = RoundedCornerShape((24 * designScale).dp),
        modifier = Modifier.width((163 * designScale).dp).height((60 * designScale).dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = (24 * designScale).dp),
            horizontalArrangement = Arrangement.spacedBy((8 * designScale).dp), verticalAlignment = Alignment.CenterVertically
        ) {
            MaterialSymbol("note_stack_add", "添加卡片组", tint = LocalContentColor.current, size = fixedSp(24 * designScale), filled = true)
            Text("添加卡片组", fontFamily = AppFonts.MiSansBold, fontWeight = FontWeight.Normal, fontSize = fixedSp(16 * designScale), letterSpacing = fixedSp(.6f * designScale))
        }
    }
}

@Composable
private fun StudyDeckCard(deck: DeckSummary, progress: DeckProgress, visual: StudyDeckVisual, designScale: Float, onClick: () -> Unit, onDelete: () -> Unit) {
    val masteryRatio = if (progress.cardCount == 0) 0f else progress.masteredCards.toFloat() / progress.cardCount
    val progressPercent = (masteryRatio * 100).roundToInt()
    val deleteWidth = (112 * designScale).dp
    // Figma 143:3526 defines a shared 16dp overlap between the swiped card and
    // its action panel. Keeping the action anchored in the clipped viewport
    // makes the same geometry work throughout the swipe.
    val deleteOverlap = (16 * designScale).dp
    val revealWidth = deleteWidth - deleteOverlap
    val revealWidthPx = with(LocalDensity.current) { revealWidth.toPx() }
    var dragOffset by remember(deck.id) { mutableFloatStateOf(0f) }
    val animatedOffset by animateFloatAsState(dragOffset, label = "${deck.id} delete swipe")
    val dragState = rememberDraggableState { delta ->
        dragOffset = (dragOffset + delta).coerceIn(-revealWidthPx, 0f)
    }
    val containerShape = RoundedCornerShape(AppShapeRadius.dp)
    Box(
        // The updated 287:8214 text stack is allowed its full line box; the
        // no-action card keeps the resulting 206dp total height.
        modifier = Modifier.fillMaxWidth().height((206 * designScale).dp).clip(containerShape)
    ) {
        Surface(
            onClick = onDelete,
            shape = containerShape,
            color = Color(0xFFBD3F3F),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(deleteWidth)
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(start = (24 * designScale).dp, end = (16 * designScale).dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                MaterialSymbol("delete", "删除卡组", tint = Color(0xFFFFEDED), size = fixedSp(24 * designScale), filled = true)
                Spacer(Modifier.height((4 * designScale).dp))
                Text("删除卡组", color = Color(0xFFFFEDED), fontFamily = AppFonts.MiSansBold, fontWeight = FontWeight.Normal, fontSize = fixedSp(16 * designScale), lineHeight = fixedSp(20 * designScale))
            }
        }
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(AppShapeRadius.dp),
        colors = CardDefaults.cardColors(containerColor = visual.background),
        modifier = Modifier.fillMaxWidth().height((206 * designScale).dp)
            .offset { IntOffset(animatedOffset.roundToInt(), 0) }
            .draggable(
                state = dragState,
                orientation = Orientation.Horizontal,
                onDragStopped = { dragOffset = if (dragOffset < -revealWidthPx / 2f) -revealWidthPx else 0f }
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding((24 * designScale).dp),
            verticalArrangement = Arrangement.spacedBy((16 * designScale).dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy((12 * designScale).dp), verticalAlignment = Alignment.Top) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy((8 * designScale).dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(shape = RoundedCornerShape((16 * designScale).dp), color = visual.iconBackground, modifier = Modifier.size((56 * designScale).dp)) {
                        Box(contentAlignment = Alignment.Center) { MaterialSymbol(visual.icon, null, tint = visual.iconTint, size = fixedSp(24 * designScale), filled = true) }
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy((4 * designScale).dp)) {
                        MixedLanguageText(displayDeckTitle(deck), modifier = Modifier.fillMaxWidth(), color = visual.titleColor, chineseFont = AppFonts.MiSansBold, latinFont = AppFonts.GoogleSansFlexBold, fontSize = fixedSp(20 * designScale), lineHeight = fixedSp(24 * designScale), maxLines = 1, overflow = TextOverflow.Ellipsis, includeFontPadding = false)
                        Row(horizontalArrangement = Arrangement.spacedBy((4 * designScale).dp), verticalAlignment = Alignment.CenterVertically) {
                            MaterialSymbol("brightness_alert", null, tint = Color(0xFFD23535), size = fixedSp(18 * designScale), filled = true)
                            Text("高优先级", color = Color(0xFFD23535), fontFamily = AppFonts.MiSansSemibold, fontWeight = FontWeight.Normal, fontSize = fixedSp(16 * designScale), lineHeight = fixedSp(20 * designScale), style = figmaCardTextStyle())
                        }
                    }
                }
                ReviewCountBadge(
                    count = deck.cardCount,
                    background = visual.panel,
                    contentColor = visual.badgeText,
                    compactScale = designScale
                )
            }
            Surface(shape = RoundedCornerShape((20 * designScale).dp), color = visual.panel, modifier = Modifier.fillMaxWidth().weight(1f)) {
                Column(
                    modifier = Modifier.fillMaxSize().padding((12 * designScale).dp),
                    verticalArrangement = Arrangement.spacedBy((8 * designScale).dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("进度", color = visual.progressLabel, fontFamily = AppFonts.MiSansSemibold, fontWeight = FontWeight.Normal, fontSize = fixedSp(16 * designScale), lineHeight = fixedSp(20 * designScale), style = figmaCardTextStyle())
                        Text("${progressPercent}%", color = visual.progress, fontFamily = AppFonts.GoogleSansFlexBold, fontWeight = FontWeight.Normal, fontSize = fixedSp(24 * designScale), lineHeight = fixedSp(28 * designScale), style = figmaCardTextStyle())
                    }
                    Box(Modifier.fillMaxWidth().height((20 * designScale).dp).clip(RoundedCornerShape(999.dp)).background(Color.White.copy(alpha = .5f))) {
                        if (masteryRatio > 0f) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth(masteryRatio).background(visual.progressFill))
                        }
                    }
                }
            }
        }
    }
    }
}
