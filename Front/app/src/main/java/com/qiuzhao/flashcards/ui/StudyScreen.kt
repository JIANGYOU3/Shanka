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


@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun StudyScreen(viewModel: AppViewModel, nav: ScreenNavigator, deckId: String, reviewMode: Boolean) {
    val cards by viewModel.studyCards.collectAsState()
    // Keep a local queue for this session. A card disappears from it immediately
    // after it is rated, so the previous/next controls can never reopen a card
    // that has already been swiped away.
    var remainingCardIds by remember(deckId, reviewMode) { mutableStateOf<List<String>?>(null) }
    var currentIndex by remember(deckId, reviewMode) { mutableIntStateOf(0) }
    var rememberedCount by remember(deckId, reviewMode) { mutableIntStateOf(0) }
    var forgottenCount by remember(deckId, reviewMode) { mutableIntStateOf(0) }
    LaunchedEffect(deckId, reviewMode) { viewModel.startStudy(deckId, reviewMode) }

    LaunchedEffect(cards) {
        if (remainingCardIds == null && cards.isNotEmpty()) {
            remainingCardIds = cards.map { it.id }
        }
    }

    val initialCardIds = remainingCardIds
    val cardsById = cards.associateBy { it.id }
    val remainingCards = initialCardIds.orEmpty().mapNotNull(cardsById::get)
    if (reviewMode && remainingCards.isNotEmpty()) {
        val safeIndex = currentIndex.coerceIn(0, remainingCards.lastIndex)
        val card = remainingCards[safeIndex]
        ReviewStudy(
            card = card,
            position = initialCardIds.orEmpty().indexOf(card.id) + 1,
            total = initialCardIds.orEmpty().size,
            canGoPrevious = safeIndex > 0,
            canGoNext = safeIndex < remainingCards.lastIndex,
            rememberedCount = rememberedCount,
            forgottenCount = forgottenCount,
            modifier = Modifier.fillMaxSize(),
            onBack = nav::popBackStack,
            onPrevious = { currentIndex = (safeIndex - 1).coerceAtLeast(0) },
            onNext = { currentIndex = (safeIndex + 1).coerceAtMost(remainingCards.lastIndex) },
            onRate = { rating ->
                viewModel.rate(card.id, rating)
                if (rating == Rating.GOOD) rememberedCount++ else forgottenCount++
                val updatedIds = initialCardIds.orEmpty().filterNot { it == card.id }
                remainingCardIds = updatedIds
                currentIndex = safeIndex.coerceAtMost((updatedIds.size - 1).coerceAtLeast(0))
            }
        )
        return
    }
    if (!reviewMode && cards.isNotEmpty()) {
        FreeStudy(cards = cards, onBack = nav::popBackStack, onUpdateCard = viewModel::updateCard)
        return
    }
    Scaffold(topBar = { AppBar(if (reviewMode) "间隔复习" else "自由刷题", nav::popBackStack) }) { padding ->
        when {
            cards.isEmpty() -> EmptyStudy(Modifier.padding(padding), reviewMode, nav)
            reviewMode && remainingCardIds?.isEmpty() == true -> CompleteStudy(Modifier.padding(padding), nav)
            else -> Unit
        }
    }
}

@Composable
private fun EmptyStudy(modifier: Modifier, reviewMode: Boolean, nav: ScreenNavigator) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MaterialSymbol("star", null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary, size = 44.sp)
            Text(if (reviewMode) "没有到期卡片" else "这个卡组还是空的", style = MaterialTheme.typography.headlineSmall, fontFamily = AppFonts.MiSansBold, fontWeight = FontWeight.Normal)
            Text(if (reviewMode) "休息一下，或者自由刷题巩固印象。" else "先导入几张问答卡吧。", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = nav::popBackStack) { Text("返回") }
        }
    }
}

@Composable
private fun CompleteStudy(modifier: Modifier, nav: ScreenNavigator) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            MaterialSymbol("star", null, modifier = Modifier.size(52.dp), tint = MaterialTheme.colorScheme.primary, size = 52.sp)
            Text("本轮完成", style = MaterialTheme.typography.headlineSmall, fontFamily = AppFonts.MiSansBold, fontWeight = FontWeight.Normal)
            Text("做得好，下一次复习会按你的记忆情况自动安排。", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = nav::popBackStack) { Text("回到卡组") }
        }
    }
}

@Composable
private fun ReviewStudy(
    card: FlashcardEntity,
    position: Int,
    total: Int,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    rememberedCount: Int,
    forgottenCount: Int,
    modifier: Modifier,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRate: (Rating) -> Unit
) {
    var flipped by remember(card.id) { mutableStateOf(false) }
    val designScale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(0.75f, 1f)
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ScreenTopInformationBar(
            title = "间隔复习", subtitle = "$position/$total", onBack = onBack,
            modifier = Modifier.zIndex(1f)
        )
        LinearProgressIndicator(
            progress = { position.toFloat() / total },
            color = AppColors.Blue.primary, trackColor = AppColors.Blue.primarySecondary,
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = (16 * designScale).dp)
                .padding(top = (88 * designScale).dp).height((4 * designScale).dp)
        )
        Column(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = (16 * designScale).dp)
                .padding(top = (132 * designScale).dp).height((600 * designScale).dp),
            verticalArrangement = Arrangement.spacedBy((16 * designScale).dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            FigmaReviewCard(
                card = card,
                flipped = flipped,
                onFlip = { flipped = !flipped },
                onRate = onRate,
                modifier = Modifier.fillMaxWidth().weight(1f),
                designScale = designScale
            )
            if (flipped) ReviewAnswerControls(canGoPrevious, canGoNext, onPrevious, onNext) { onRate(Rating.HARD) }
            else ReviewQuestionControls(canGoPrevious, canGoNext, rememberedCount, forgottenCount, onPrevious, onNext)
        }
        if (flipped) ReviewSwipeHint(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = (756 * designScale).dp), designScale) else Text(
            "点击卡片查看答案",
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = (769 * designScale).dp),
            color = PageForegroundColor(), fontFamily = AppFonts.MiSansMedium, fontWeight = FontWeight.Normal,
            fontSize = fixedSp(20 * designScale), lineHeight = fixedSp(28 * designScale), textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ReviewQuestionControls(
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    rememberedCount: Int,
    forgottenCount: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    val scale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(.75f, 1f)
    Row(Modifier.fillMaxWidth().height((60 * scale).dp), horizontalArrangement = Arrangement.spacedBy((15 * scale).dp)) {
        ReviewNavigationButton("arrow_back", canGoPrevious, Modifier.weight(1f), scale, onPrevious)
        ReviewCountBadge("check", rememberedCount, AppColors.Green.background, AppColors.Green.primaryStrong, Modifier.weight(1f), scale)
        ReviewCountBadge("close", forgottenCount, AppColors.Pink.background, AppColors.Warning, Modifier.weight(1f), scale)
        ReviewNavigationButton("arrow_forward", canGoNext, Modifier.weight(1f), scale, onNext)
    }
}

@Composable
private fun ReviewAnswerControls(canGoPrevious: Boolean, canGoNext: Boolean, onPrevious: () -> Unit, onNext: () -> Unit, onHard: () -> Unit) {
    val scale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(.75f, 1f)
    Row(Modifier.fillMaxWidth().height((60 * scale).dp), horizontalArrangement = Arrangement.spacedBy((15 * scale).dp), verticalAlignment = Alignment.CenterVertically) {
        ReviewNavigationButton("arrow_back", enabled = canGoPrevious, Modifier.weight(1f), scale, onPrevious)
        Surface(
            onClick = onHard,
            color = AppColors.Blue.background,
            contentColor = AppColors.Blue.ink,
            border = androidx.compose.foundation.BorderStroke((2 * scale).dp, AppColors.Blue.ink),
            shape = RoundedCornerShape((24 * scale).dp),
            modifier = Modifier.height((59 * scale).dp)
        ) {
            Row(Modifier.padding(horizontal = (24 * scale).dp), horizontalArrangement = Arrangement.spacedBy((8 * scale).dp), verticalAlignment = Alignment.CenterVertically) {
                MaterialSymbol("comedy_mask", null, tint = LocalContentColor.current, size = fixedSp(24 * scale), filled = true)
                Text("印象模糊，明天再刷", fontFamily = AppFonts.MiSansBold, fontWeight = FontWeight.Normal, fontSize = fixedSp(16 * scale), lineHeight = fixedSp(16 * scale), letterSpacing = fixedSp(.6f * scale))
            }
        }
        ReviewNavigationButton("arrow_forward", enabled = canGoNext, Modifier.weight(1f), scale, onNext)
    }
}

@Composable
private fun ReviewNavigationButton(symbol: String, enabled: Boolean, modifier: Modifier, scale: Float, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        color = AppColors.Blue.primary,
        contentColor = AppColors.TextIconLight,
        shape = RoundedCornerShape((24 * scale).dp),
        modifier = modifier.fillMaxHeight()
    ) {
        Box(contentAlignment = Alignment.Center) {
            MaterialSymbol(symbol, if (symbol == "arrow_back") "上一张未完成卡片" else "下一张未完成卡片", tint = LocalContentColor.current, size = fixedSp(24 * scale), filled = true)
        }
    }
}

@Composable
private fun ReviewCountBadge(symbol: String, count: Int, color: Color, contentColor: Color, modifier: Modifier, scale: Float) {
    Surface(
        color = color,
        contentColor = contentColor,
        border = androidx.compose.foundation.BorderStroke((2 * scale).dp, contentColor),
        shape = RoundedCornerShape((24 * scale).dp),
        modifier = modifier.fillMaxHeight()
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy((8 * scale).dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize(),) {
            Spacer(Modifier.weight(1f))
            MaterialSymbol(symbol, null, tint = LocalContentColor.current, size = fixedSp(24 * scale), filled = true)
            Text("$count", fontFamily = AppFonts.GoogleSansFlexBold, fontWeight = FontWeight.Normal, fontSize = fixedSp(16 * scale), lineHeight = fixedSp(16 * scale), letterSpacing = fixedSp(.6f * scale))
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun ReviewSwipeHint(modifier: Modifier, scale: Float) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy((4 * scale).dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MaterialSymbol("swipe_left", null, tint = PageForegroundColor(), size = fixedSp(24 * scale), filled = true)
        Text("左滑是记得，", color = PageForegroundColor(), fontFamily = AppFonts.MiSansMedium, fontWeight = FontWeight.Normal, fontSize = fixedSp(20 * scale), lineHeight = fixedSp(28 * scale))
        MaterialSymbol("swipe_right", null, tint = PageForegroundColor(), size = fixedSp(24 * scale), filled = true)
        Text("右滑是不记得", color = PageForegroundColor(), fontFamily = AppFonts.MiSansMedium, fontWeight = FontWeight.Normal, fontSize = fixedSp(20 * scale), lineHeight = fixedSp(28 * scale))
    }
}

@Composable
private fun FigmaReviewCard(
    card: FlashcardEntity,
    flipped: Boolean,
    onFlip: () -> Unit,
    onRate: (Rating) -> Unit,
    modifier: Modifier,
    designScale: Float
) {
    var offsetX by remember(card.id) { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val draggable = rememberDraggableState { offsetX += it }
    val rotation by animateFloatAsState(
        targetValue = if (flipped) 180f else 0f,
        animationSpec = AppMotion.emphasisSpring(),
        label = "figma review flip"
    )
    val frontAlpha = if (rotation <= 90f) 1f else 0f
    val backAlpha = if (rotation > 90f) 1f else 0f
    val faceShape = RoundedCornerShape((AppShapeRadius * designScale).dp)
    Box(
        modifier = modifier
            .offset { IntOffset(offsetX.roundToInt(), 0) }
            .graphicsLayer(rotationZ = offsetX / 55f)
            .clip(faceShape)
            // Use the Material ripple that homepage cards use. Clipping first keeps
            // the native press state within the same 32dp container shape.
            .clickable(onClick = onFlip)
            .draggable(
                state = draggable,
                orientation = Orientation.Horizontal,
                enabled = flipped,
                onDragStopped = {
                    when {
                        // Figma 44:2464: left = remembered; right = forgot.
                        offsetX > 140f -> onRate(Rating.AGAIN)
                        offsetX < -140f -> onRate(Rating.GOOD)
                        else -> scope.launch { offsetX = 0f }
                    }
                }
            )
    ) {
        ReviewCardFace(
            title = "问题", content = card.front, symbol = "book_5", visible = frontAlpha,
            rotation = rotation, shape = faceShape, designScale = designScale, backFace = false
        )
        ReviewCardFace(
            title = "答案", content = card.back, symbol = "wb_incandescent", visible = backAlpha,
            rotation = rotation, shape = faceShape, designScale = designScale, backFace = true
        )
    }
}

@Composable
private fun ReviewCardFace(
    title: String,
    content: String,
    symbol: String,
    visible: Float,
    rotation: Float,
    shape: RoundedCornerShape,
    designScale: Float,
    backFace: Boolean
) {
    val faceGradient = if (backFace) {
        Brush.verticalGradient(listOf(AppColors.Blue.primarySecondary, AppColors.Blue.primary))
    } else Brush.verticalGradient(listOf(AppColors.Blue.background, AppColors.Blue.primarySecondary))
    Box(
        // The layer must wrap both the gradient and its text. Keeping it before
        // background prevents the invisible reverse face from painting over the
        // visible face during the 3D transition.
        modifier = Modifier.fillMaxSize().clip(shape).graphicsLayer {
            rotationY = if (backFace) rotation - 180f else rotation
            transformOrigin = TransformOrigin.Center
            cameraDistance = 20f * density
            alpha = visible
        }.background(faceGradient)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding((24 * designScale).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            MaterialSymbol(symbol, null, tint = AppColors.Blue.ink, size = fixedSp(44 * designScale), filled = true)
            Spacer(Modifier.height((16 * designScale).dp))
            // Figma 44:2446 / 44:2452 / 48:4553: heading is the project's MiSans
            // Semibold token (520) and body is its Medium token (380). Use fixed faces rather
            // than a requested system weight so every phone renders identically.
            Text(title, color = AppColors.Blue.ink, fontFamily = AppFonts.MiSansSemibold, fontWeight = FontWeight.Normal, fontSize = fixedSp(24 * designScale), lineHeight = fixedSp(32 * designScale), textAlign = TextAlign.Center)
            Spacer(Modifier.height((8 * designScale).dp))
            MixedLanguageText(
                text = content,
                color = AppColors.Blue.ink,
                chineseFont = AppFonts.MiSansMedium,
                latinFont = AppFonts.GoogleSansFlex,
                fontSize = fixedSp(24 * designScale),
                lineHeight = fixedSp(32 * designScale),
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Clip
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FreeStudy(cards: List<FlashcardEntity>, onBack: () -> Unit, onUpdateCard: (FlashcardEntity) -> Unit) {
    var displayedCards by remember(cards) { mutableStateOf(cards) }
    var editingCard by remember { mutableStateOf<FlashcardEntity?>(null) }
    val pager = rememberPagerState(pageCount = { displayedCards.size })
    val scope = rememberCoroutineScope()
    val designScale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(0.75f, 1f)
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ScreenTopInformationBar(
            title = "自由刷题", subtitle = "${pager.currentPage + 1}/${displayedCards.size}", onBack = onBack,
            modifier = Modifier.zIndex(1f)
        )
        LinearProgressIndicator(
            progress = { (pager.currentPage + 1).toFloat() / displayedCards.size },
            color = AppColors.Blue.primary,
            trackColor = AppColors.Blue.primarySecondary,
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = (16 * designScale).dp)
                .padding(top = (88 * designScale).dp).height((4 * designScale).dp)
        )
        Column(
            modifier = Modifier.fillMaxWidth().statusBarsPadding()
                .padding(top = (132 * designScale).dp).height((600 * designScale).dp),
            verticalArrangement = Arrangement.spacedBy((16 * designScale).dp)
        ) {
            HorizontalPager(
                state = pager,
                pageSize = PageSize.Fixed((346 * designScale).dp),
                // Keep the pager viewport edge-to-edge. The first card starts at 16dp,
                // while the next one can peek through the physical screen edge instead
                // of being clipped a second time by an inset parent.
                contentPadding = PaddingValues(start = (16 * designScale).dp, end = (16 * designScale).dp),
                pageSpacing = (18 * designScale).dp,
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) { page ->
                var flipped by remember(displayedCards[page].id) { mutableStateOf(false) }
                FreeStudyCard(displayedCards[page], flipped, { flipped = !flipped }, designScale, Modifier.fillMaxSize())
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = (16 * designScale).dp).height((60 * designScale).dp),
                horizontalArrangement = Arrangement.spacedBy((15 * designScale).dp)
            ) {
                Surface(
                    onClick = { editingCard = displayedCards.getOrNull(pager.currentPage) },
                    color = AppColors.Blue.surface,
                    contentColor = AppColors.Blue.ink,
                    shape = RoundedCornerShape((24 * designScale).dp),
                    modifier = Modifier.weight(1f).fillMaxHeight()
                ) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        MaterialSymbol("edit", null, tint = LocalContentColor.current, size = fixedSp(24 * designScale), filled = true)
                        Spacer(Modifier.width((8 * designScale).dp))
                        Text("编辑该卡", fontFamily = AppFonts.MiSansBold, fontWeight = FontWeight.Normal, fontSize = fixedSp(16 * designScale), lineHeight = fixedSp(16 * designScale), letterSpacing = fixedSp(.6f * designScale))
                    }
                }
                Surface(
                    onClick = {
                        val shuffledCards = displayedCards.shuffled()
                        // A random shuffle can occasionally preserve the same order. In that
                        // case rotate once so this action always gives the user visible feedback.
                        displayedCards = if (shuffledCards == displayedCards && displayedCards.size > 1) {
                            displayedCards.drop(1) + displayedCards.first()
                        } else {
                            shuffledCards
                        }
                        scope.launch { pager.scrollToPage(0) }
                    },
                    color = AppColors.Blue.primary,
                    contentColor = AppColors.TextIconLight,
                    shape = RoundedCornerShape((24 * designScale).dp),
                    modifier = Modifier.weight(1f).fillMaxHeight()
                ) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        MaterialSymbol("shuffle", null, tint = LocalContentColor.current, size = fixedSp(24 * designScale), filled = true)
                        Spacer(Modifier.width((8 * designScale).dp))
                        Text("打乱顺序", fontFamily = AppFonts.MiSansBold, fontWeight = FontWeight.Normal, fontSize = fixedSp(16 * designScale), lineHeight = fixedSp(16 * designScale), letterSpacing = fixedSp(.6f * designScale))
                    }
                }
            }
        }
        Text(
            text = "点击卡片查看答案",
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = (756 * designScale).dp),
            color = PageForegroundColor(),
            fontFamily = AppFonts.MiSansMedium,
            fontWeight = FontWeight.Normal,
            fontSize = fixedSp(20 * designScale),
            lineHeight = fixedSp(28 * designScale),
            textAlign = TextAlign.Center
        )
    }
    editingCard?.let { card ->
        CardEditDialog(
            card = card,
            onSave = { updated ->
                displayedCards = displayedCards.map { if (it.id == updated.id) updated else it }
                onUpdateCard(updated)
                editingCard = null
            },
            onDismiss = { editingCard = null }
        )
    }
}

@Composable
private fun FreeStudyCard(card: FlashcardEntity, flipped: Boolean, onFlip: () -> Unit, designScale: Float, modifier: Modifier) {
    val rotation by animateFloatAsState(
        targetValue = if (flipped) 180f else 0f,
        animationSpec = AppMotion.emphasisSpring(),
        label = "free study flip"
    )
    val shape = RoundedCornerShape((AppShapeRadius * designScale).dp)
    Box(modifier = modifier.clip(shape).clickable(onClick = onFlip)) {
        ReviewCardFace(
            title = "问题",
            content = card.front,
            symbol = "book_5",
            visible = if (rotation <= 90f) 1f else 0f,
            rotation = rotation,
            shape = shape,
            designScale = designScale,
            backFace = false
        )
        ReviewCardFace(
            title = "答案",
            content = listOfNotNull(card.back, card.code?.takeIf { it.isNotBlank() }).joinToString("\n\n"),
            symbol = "wb_incandescent",
            visible = if (rotation > 90f) 1f else 0f,
            rotation = rotation,
            shape = shape,
            designScale = designScale,
            backFace = true
        )
    }
}
