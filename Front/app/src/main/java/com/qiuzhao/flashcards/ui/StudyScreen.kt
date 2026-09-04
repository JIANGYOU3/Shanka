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
internal fun StudyScreen(
    viewModel: AppViewModel,
    nav: ScreenNavigator,
    deckId: String,
    reviewMode: Boolean,
    todayMode: Boolean = false,
) {
    val cards by viewModel.studyCards.collectAsState()
    val decks by viewModel.decks.collectAsState()
    val projects by viewModel.projects.collectAsState()
    val todayPlan by viewModel.todayPlan.collectAsState()
    val reviewSubmitting by viewModel.reviewSubmitting.collectAsState()
    // The flip/free-practice theme follows the owning project (per the user's
    // colour semantics), falling back to the deck's stored family.
    val themeDeckId = if (todayMode) cards.firstOrNull()?.deckId else deckId
    val theme = decks.firstOrNull { it.id == themeDeckId }?.let { deck -> deckTheme(deck, projects) } ?: DeckThemes.first()
    // Keep a local queue for this session. A card disappears from it immediately
    // after it is rated, so the previous/next controls can never reopen a card
    // that has already been swiped away.
    val studyKey = if (todayMode) "today" else deckId
    var remainingCardIds by remember(studyKey, reviewMode) { mutableStateOf<List<String>?>(null) }
    var currentIndex by remember(studyKey, reviewMode) { mutableIntStateOf(0) }
    var rememberedCount by remember(studyKey, reviewMode) { mutableIntStateOf(0) }
    var forgottenCount by remember(studyKey, reviewMode) { mutableIntStateOf(0) }
    var loadingStudy by remember(studyKey, reviewMode) { mutableStateOf(false) }
    LaunchedEffect(studyKey, reviewMode, todayMode) {
        remainingCardIds = null
        currentIndex = 0
        rememberedCount = 0
        forgottenCount = 0
        loadingStudy = true
        try {
            val load = if (todayMode) viewModel.startTodayStudy() else viewModel.startStudy(deckId, reviewMode)
            load.join()
        } finally {
            loadingStudy = false
        }
    }

    LaunchedEffect(cards) {
        if (!loadingStudy && remainingCardIds == null && cards.isNotEmpty()) {
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
            theme = theme,
            canGoPrevious = safeIndex > 0,
            canGoNext = safeIndex < remainingCards.lastIndex,
            rememberedCount = rememberedCount,
            forgottenCount = forgottenCount,
            submitting = reviewSubmitting,
            modifier = Modifier.fillMaxSize(),
            onBack = nav::popBackStack,
            onEdit = viewModel::updateCard,
            onPrevious = { currentIndex = (safeIndex - 1).coerceAtLeast(0) },
            onNext = { currentIndex = (safeIndex + 1).coerceAtMost(remainingCards.lastIndex) },
            onRate = { rating ->
                // The card leaves the queue only after the server committed the event; a failure
                // keeps the card on screen and the retry replays the same event identifiers.
                viewModel.rate(card.id, rating) {
                    // HARD still means the learner recalled the answer (just with effort); only
                    // AGAIN represents a miss in the compact two-counter footer.
                    if (rating == Rating.AGAIN) forgottenCount++ else rememberedCount++
                    val updatedIds = initialCardIds.orEmpty().filterNot { it == card.id }
                    remainingCardIds = updatedIds
                    currentIndex = safeIndex.coerceAtMost((updatedIds.size - 1).coerceAtLeast(0))
                }
            }
        )
        return
    }
    if (!reviewMode && cards.isNotEmpty()) {
        FreeStudy(cards = cards, theme = theme, onBack = nav::popBackStack, onUpdateCard = viewModel::updateCard)
        return
    }
    Scaffold(topBar = { AppBar(if (reviewMode) "记忆巩固" else "自由刷题", nav::popBackStack) }) { padding ->
        when {
            reviewMode && remainingCardIds?.isEmpty() == true -> CompleteStudy(
                modifier = Modifier.padding(padding),
                nav = nav,
                hasBacklog = todayMode && todayPlan.backlogCount > 0,
                onContinueBacklog = {
                    remainingCardIds = null
                    currentIndex = 0
                    rememberedCount = 0
                    forgottenCount = 0
                    loadingStudy = true
                    viewModel.startTodayBacklogStudy { succeeded ->
                        loadingStudy = false
                        if (!succeeded) remainingCardIds = emptyList()
                    }
                },
            )
            cards.isEmpty() -> EmptyStudy(Modifier.padding(padding), reviewMode, nav, todayMode)
            else -> Unit
        }
    }
}

@Composable
private fun EmptyStudy(modifier: Modifier, reviewMode: Boolean, nav: ScreenNavigator, todayMode: Boolean = false) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MaterialSymbol("star", null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary, size = 44.sp)
            AppText(if (todayMode) "今天没有可学习的卡片" else if (reviewMode) "没有到期卡片" else "这个卡组还是空的", AppTextRole.PageTitle)
            AppText(if (todayMode) "先设置学习计划，或导入资料并生成卡组。" else if (reviewMode) "休息一下，或者自由刷题巩固印象。" else "先导入几张问答卡吧。", AppTextRole.Body, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { if (todayMode) nav.navigate(AppRoute.StudyPlan) else nav.popBackStack() }) {
                AppText(if (todayMode) "设置学习计划" else "返回", AppTextRole.Label)
            }
        }
    }
}

@Composable
private fun CompleteStudy(
    modifier: Modifier,
    nav: ScreenNavigator,
    hasBacklog: Boolean = false,
    onContinueBacklog: () -> Unit = {},
) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            MaterialSymbol("star", null, modifier = Modifier.size(52.dp), tint = MaterialTheme.colorScheme.primary, size = 52.sp)
            AppText("本轮完成", AppTextRole.PageTitle)
            AppText("做得好，下一次巩固会按你的记忆情况自动安排。", AppTextRole.Body, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = if (hasBacklog) onContinueBacklog else nav::popBackStack) {
                AppText(if (hasBacklog) "继续巩固积压" else "回到卡组", AppTextRole.Label)
            }
        }
    }
}

@Composable
private fun ReviewStudy(
    card: FlashcardEntity,
    position: Int,
    total: Int,
    theme: DeckTheme,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    rememberedCount: Int,
    forgottenCount: Int,
    submitting: Boolean,
    modifier: Modifier,
    onBack: () -> Unit,
    onEdit: (FlashcardEntity) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRate: (Rating) -> Unit
) {
    var flipped by remember(card.id) { mutableStateOf(false) }
    var editingCard by remember(card.id) { mutableStateOf<FlashcardEntity?>(null) }
    val designScale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(0.75f, 1f)
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ScreenTopInformationBar(
            title = "记忆巩固", subtitle = "$position/$total", onBack = onBack,
            backContainer = theme.cardPanel, titleColor = theme.text,
            modifier = Modifier.zIndex(1f)
        )
        LinearProgressIndicator(
            progress = { position.toFloat() / total },
            color = theme.primary, trackColor = theme.secondary,
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = (16 * designScale).dp)
                .padding(top = (88 * designScale).dp).height((4 * designScale).dp)
        )
        Column(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = (16 * designScale).dp)
                .padding(top = (132 * designScale).dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Figma 41:1853 / 44:2464: the big flip card is a fixed 370x524 frame.
            Box(Modifier.fillMaxWidth().height((524 * designScale).dp)) {
                FigmaReviewCard(
                    card = card,
                    flipped = flipped,
                    onFlip = { flipped = !flipped },
                    onRate = onRate,
                    canRate = !submitting,
                    modifier = Modifier.fillMaxSize(),
                    designScale = designScale,
                    theme = theme
                )
            }
            Spacer(Modifier.height((16 * designScale).dp))
            if (flipped) ReviewAnswerControls(
                enabled = !submitting,
                onHard = { onRate(Rating.HARD) },
                onGood = { onRate(Rating.GOOD) },
            )
            else ReviewQuestionControls(theme, canGoPrevious, canGoNext, rememberedCount, forgottenCount, onPrevious, onNext)
        }
        if (flipped) ReviewSwipeHint(
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = (756 * designScale).dp),
            scale = designScale,
            enabled = !submitting,
            onRate = onRate,
        ) else Text(
            "点击卡片查看答案",
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = (769 * designScale).dp),
            color = PageForegroundColor(), fontFamily = AppFonts.MiSansMedium, fontWeight = FontWeight.Normal,
            fontSize = fixedSp(18 * designScale), lineHeight = fixedSp(24 * designScale), textAlign = TextAlign.Center
        )
    }
    editingCard?.let { editableCard ->
        CardEditDialog(
            card = editableCard,
            onSave = {
                onEdit(it)
                editingCard = null
            },
            onDismiss = { editingCard = null }
        )
    }
}

@Composable
private fun ReviewQuestionControls(
    theme: DeckTheme,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    rememberedCount: Int,
    forgottenCount: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    val scale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(.75f, 1f)
    Row(Modifier.fillMaxWidth().height((72 * scale).dp), horizontalArrangement = Arrangement.spacedBy((16 * scale).dp), verticalAlignment = Alignment.CenterVertically) {
        ReviewNavigationButton("arrow_back", canGoPrevious, Modifier.width((93 * scale).dp).fillMaxHeight(), scale, theme, onPrevious)
        Row(horizontalArrangement = Arrangement.spacedBy((8 * scale).dp), verticalAlignment = Alignment.CenterVertically) {
            ReviewCountBadge("check", rememberedCount, AppColors.Green.background, AppColors.Green.primaryStrong, Modifier.width((72 * scale).dp).fillMaxHeight(), scale)
            ReviewCountBadge("close", forgottenCount, Color(0xFFF4D1CE), AppColors.Warning, Modifier.width((72 * scale).dp).fillMaxHeight(), scale)
        }
        ReviewNavigationButton("arrow_forward", canGoNext, Modifier.width((93 * scale).dp).fillMaxHeight(), scale, theme, onNext)
    }
}

@Composable
private fun ReviewAnswerControls(enabled: Boolean, onHard: () -> Unit, onGood: () -> Unit) {
    val scale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(.75f, 1f)
    Row(Modifier.fillMaxWidth().height((72 * scale).dp), horizontalArrangement = Arrangement.spacedBy((16 * scale).dp), verticalAlignment = Alignment.CenterVertically) {
        ReviewRatingButton(
            label = "勉强想起",
            color = AppColors.Orange.surface,
            contentColor = AppColors.Orange.ink,
            enabled = enabled,
            modifier = Modifier.weight(1f).fillMaxHeight(),
            scale = scale,
            onClick = onHard,
        )
        ReviewRatingButton(
            label = "正常想起",
            color = AppColors.Green.background,
            contentColor = AppColors.Green.primaryStrong,
            enabled = enabled,
            modifier = Modifier.weight(1f).fillMaxHeight(),
            scale = scale,
            onClick = onGood,
        )
    }
}

@Composable
private fun ReviewRatingButton(
    label: String,
    color: Color,
    contentColor: Color,
    enabled: Boolean,
    modifier: Modifier,
    scale: Float,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        color = color,
        contentColor = contentColor,
        shape = RoundedCornerShape((32 * scale).dp),
        modifier = modifier,
    ) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontFamily = AppFonts.MiSansBold, fontWeight = FontWeight.Normal, fontSize = fixedSp(16 * scale), lineHeight = fixedSp(21 * scale), letterSpacing = fixedSp(.6f * scale), maxLines = 1)
        }
    }
}

@Composable
private fun ReviewNavigationButton(symbol: String, enabled: Boolean, modifier: Modifier, scale: Float, theme: DeckTheme, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        color = theme.cardPanel,
        contentColor = theme.strongText,
        shape = RoundedCornerShape((32 * scale).dp),
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
        shape = RoundedCornerShape((32 * scale).dp),
        modifier = modifier.fillMaxHeight()
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy((8 * scale).dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize(),) {
            Spacer(Modifier.weight(1f))
            MaterialSymbol(symbol, null, tint = LocalContentColor.current, size = fixedSp(24 * scale), filled = true)
            Text("$count", fontFamily = AppFonts.GoogleSansFlexExtraBold, fontWeight = FontWeight.Normal, fontSize = fixedSp(16 * scale), lineHeight = fixedSp(20 * scale), letterSpacing = fixedSp(.4f * scale))
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun ReviewSwipeHint(
    modifier: Modifier,
    scale: Float,
    enabled: Boolean,
    onRate: (Rating) -> Unit,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ReviewSwipeHintGroup("swipe_left", "轻松想起", scale, enabled) { onRate(Rating.EASY) }
        ReviewSwipeHintGroup("swipe_right", "没想起来", scale, enabled) { onRate(Rating.AGAIN) }
    }
}

@Composable
private fun ReviewSwipeHintGroup(symbol: String, text: String, scale: Float, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy((8 * scale).dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MaterialSymbol(symbol, null, tint = PageForegroundColor(), size = fixedSp(24 * scale), filled = true)
        Text(text, color = PageForegroundColor(), fontFamily = AppFonts.MiSansMedium, fontWeight = FontWeight.Normal, fontSize = fixedSp(18 * scale), lineHeight = fixedSp(24 * scale))
    }
}

/**
 * Figma 41:1853, 755:4354 and 44:2464 use the card-type semantic blue even
 * when the surrounding learning screen inherits a non-blue project theme.
 */
private fun reviewCardTagStyle() = CardListTagStyle(
    label = "基础记忆",
    container = AppColors.Blue.primary,
    content = AppColors.Blue.ink
)

@Composable
private fun FigmaReviewCard(
    card: FlashcardEntity,
    flipped: Boolean,
    onFlip: () -> Unit,
    onRate: (Rating) -> Unit,
    canRate: Boolean,
    modifier: Modifier,
    designScale: Float,
    theme: DeckTheme
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
    // A swipe that triggered a rating leaves the card shifted; snap it back while the
    // submission is in flight so a failure shows the card centred and ready to retry.
    LaunchedEffect(canRate) {
        if (!canRate) offsetX = 0f
    }
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
                enabled = flipped && canRate,
                onDragStopped = {
                    when {
                        // Left swipe = EASY; right swipe = AGAIN.
                        offsetX > 140f -> onRate(Rating.AGAIN)
                        offsetX < -140f -> onRate(Rating.EASY)
                        else -> scope.launch { offsetX = 0f }
                    }
                }
            )
    ) {
        ReviewCardFace(
            title = "问题", content = card.front, symbol = "book_5", visible = frontAlpha,
            tag = reviewCardTagStyle(), rotation = rotation, shape = faceShape, designScale = designScale, backFace = false, theme = theme
        )
        ReviewCardFace(
            title = "答案", content = card.back, symbol = "wb_incandescent", visible = backAlpha,
            tag = reviewCardTagStyle(), rotation = rotation, shape = faceShape, designScale = designScale, backFace = true, theme = theme
        )
    }
}

@Composable
private fun ReviewCardFace(
    title: String,
    content: String,
    symbol: String,
    tag: CardListTagStyle,
    visible: Float,
    rotation: Float,
    shape: RoundedCornerShape,
    designScale: Float,
    backFace: Boolean,
    theme: DeckTheme,
    questionInk: Boolean = true
) {
    // Figma 203:2594 big flip card: question face = ink, answer face = surface
    // (one step deeper than the Background page).
    val questionColor = if (questionInk) theme.strongText else theme.cardPanel
    val faceColor = if (backFace) theme.cardPanel else questionColor
    val faceContent = if (backFace) theme.strongText else if (questionInk) AppColors.TextIconLight else theme.strongText
    Box(
        // The layer must wrap both the gradient and its text. Keeping it before
        // background prevents the invisible reverse face from painting over the
        // visible face during the 3D transition.
        modifier = Modifier.fillMaxSize().clip(shape).graphicsLayer {
            rotationY = if (backFace) rotation - 180f else rotation
            transformOrigin = TransformOrigin.Center
            cameraDistance = 20f * density
            alpha = visible
        }.background(faceColor)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding((24 * designScale).dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Surface(color = tag.container, shape = RoundedCornerShape(999.dp)) {
                    Text(
                        tag.label,
                        modifier = Modifier.padding(horizontal = (16 * designScale).dp, vertical = (8 * designScale).dp),
                        color = tag.content,
                        fontFamily = AppFonts.MiSansBold,
                        fontWeight = FontWeight.Normal,
                        fontSize = fixedSp(16 * designScale),
                        lineHeight = fixedSp(21 * designScale),
                        maxLines = 1
                    )
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                MaterialSymbol(symbol, null, tint = faceContent, size = fixedSp(44 * designScale), filled = true)
                Spacer(Modifier.height((16 * designScale).dp))
                AppText(title, AppTextRole.PageTitle, color = faceContent, designScale = designScale, textAlign = TextAlign.Center)
                Spacer(Modifier.height((8 * designScale).dp))
                MixedLanguageText(
                    text = content,
                    color = faceContent,
                    chineseFont = AppFonts.MiSansMedium,
                    latinFont = AppFonts.GoogleSansFlex,
                    fontSize = fixedSp(20 * designScale),
                    lineHeight = fixedSp(27 * designScale),
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Clip
                )
            }
            // Figma 41:1853 / 44:2464 reserve a symmetric 37dp lower spacer
            // under the centred prompt/answer group.
            Spacer(Modifier.height((37 * designScale).dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FreeStudy(cards: List<FlashcardEntity>, theme: DeckTheme, onBack: () -> Unit, onUpdateCard: (FlashcardEntity) -> Unit) {
    var displayedCards by remember(cards) { mutableStateOf(cards) }
    var editingCard by remember { mutableStateOf<FlashcardEntity?>(null) }
    val pager = rememberPagerState(pageCount = { displayedCards.size })
    val scope = rememberCoroutineScope()
    val designScale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(0.75f, 1f)
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ScreenTopInformationBar(
            title = "自由刷题", subtitle = "${pager.currentPage + 1}/${displayedCards.size}", onBack = onBack,
            backContainer = theme.cardPanel, titleColor = theme.text,
            modifier = Modifier.zIndex(1f)
        )
        LinearProgressIndicator(
            progress = { (pager.currentPage + 1).toFloat() / displayedCards.size },
            color = theme.primary,
            trackColor = theme.secondary,
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
                FreeStudyCard(displayedCards[page], flipped, { flipped = !flipped }, designScale, theme, Modifier.fillMaxSize())
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = (16 * designScale).dp).height((60 * designScale).dp),
                horizontalArrangement = Arrangement.spacedBy((15 * designScale).dp)
            ) {
                Surface(
                    onClick = { editingCard = displayedCards.getOrNull(pager.currentPage) },
                    color = theme.cardPanel,
                    contentColor = theme.strongText,
                    shape = RoundedCornerShape((24 * designScale).dp),
                    modifier = Modifier.weight(1f).fillMaxHeight()
                ) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        MaterialSymbol("edit", null, tint = theme.strongText, size = fixedSp(24 * designScale), filled = true)
                        Spacer(Modifier.width((8 * designScale).dp))
                        AppText("编辑该卡", AppTextRole.Label, designScale = designScale)
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
                    color = theme.primary,
                    contentColor = AppColors.TextIconLight,
                    shape = RoundedCornerShape((24 * designScale).dp),
                    modifier = Modifier.weight(1f).fillMaxHeight()
                ) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        MaterialSymbol("shuffle", null, tint = LocalContentColor.current, size = fixedSp(24 * designScale), filled = true)
                        Spacer(Modifier.width((8 * designScale).dp))
                        AppText("打乱顺序", AppTextRole.Label, designScale = designScale)
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
private fun FreeStudyCard(card: FlashcardEntity, flipped: Boolean, onFlip: () -> Unit, designScale: Float, theme: DeckTheme, modifier: Modifier) {
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
            tag = cardListTagStyle(card.position),
            visible = if (rotation <= 90f) 1f else 0f,
            rotation = rotation,
            shape = shape,
            designScale = designScale,
            backFace = false,
            theme = theme,
            questionInk = false
        )
        ReviewCardFace(
            title = "答案",
            content = listOfNotNull(card.back, card.code?.takeIf { it.isNotBlank() }).joinToString("\n\n"),
            symbol = "wb_incandescent",
            tag = cardListTagStyle(card.position),
            visible = if (rotation > 90f) 1f else 0f,
            rotation = rotation,
            shape = shape,
            designScale = designScale,
            backFace = true,
            theme = theme
        )
    }
}
