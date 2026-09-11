package com.qiuzhao.flashcards.ui

import android.app.Activity
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
import com.qiuzhao.flashcards.domain.v25.V25Difficulty
import com.qiuzhao.flashcards.R
import com.qiuzhao.flashcards.ui.motion.AppMotion
import com.qiuzhao.flashcards.ui.navigation.AppNavigator
import com.qiuzhao.flashcards.ui.navigation.AppRoute
import com.qiuzhao.flashcards.ui.navigation.rememberAppNavigationState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How long an AGAIN card stays out of the session before its relearn visit. Matches the server FSRS relearning step (main/services/scheduling/scheduler.py). */
private const val RELEARN_DELAY_MS = 10 * 60 * 1000L

/** One slot in the review session queue. [relearn] entries are AGAIN cards returning for their same-day second pass. */
internal data class StudyQueueEntry(val cardId: String, val relearn: Boolean = false)

/** A card scheduled to come back after the FSRS relearning step, as (cardId, dueAtEpochMs). */
private data class ScheduledRequeue(val cardId: String, val dueAtMs: Long)

/** Position / total / completed shown by the review session's top bar. */
internal data class TodaySessionCounter(val position: Int, val total: Int, val completed: Int)

/**
 * Today's core plan re-derives its queue on every visit and only holds the cards still
 * remaining for the day, so its session-local numbers are offset by [baseCompleted]
 * (already completed earlier today) to keep "position/total" a day-wide progress.
 * Deck reviews and the backlog overflow have no day-wide baseline and count from 1.
 */
internal fun todaySessionCounter(
    baseCompleted: Int,
    queue: List<StudyQueueEntry>,
    entry: StudyQueueEntry,
    latestRatings: Map<String, Rating>,
): TodaySessionCounter {
    val sessionTotal = queue.count { !it.relearn }
    val sessionPosition = queue.indexOfFirst { !it.relearn && it.cardId == entry.cardId } + 1
    val sessionCompleted = queue.filter { !it.relearn }
        .map { it.cardId }
        .distinct()
        .count { cardId -> latestRatings[cardId] != null && latestRatings[cardId] != Rating.AGAIN }
    return TodaySessionCounter(
        position = baseCompleted + sessionPosition,
        total = baseCompleted + sessionTotal,
        completed = baseCompleted + sessionCompleted,
    )
}


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
    // The session queue never shrinks: rated cards stay browsable via the
    // previous/next arrows (the x/total counter keeps the initial total), and
    // AGAIN cards return as relearn entries after the server FSRS relearning
    // step, so a miss costs the learner an extra pass within this session.
    val studyKey = if (todayMode) "today" else deckId
    var sessionQueue by remember(studyKey, reviewMode) { mutableStateOf<List<StudyQueueEntry>?>(null) }
    var currentIndex by remember(studyKey, reviewMode) { mutableIntStateOf(0) }
    var latestRatings by remember(studyKey, reviewMode) { mutableStateOf<Map<String, Rating>>(emptyMap()) }
    var scheduledRequeues by remember(studyKey, reviewMode) { mutableStateOf<List<ScheduledRequeue>>(emptyList()) }
    var sessionFinished by remember(studyKey, reviewMode) { mutableStateOf(false) }
    var rememberedCount by remember(studyKey, reviewMode) { mutableIntStateOf(0) }
    var forgottenCount by remember(studyKey, reviewMode) { mutableIntStateOf(0) }
    var loadingStudy by remember(studyKey, reviewMode) { mutableStateOf(false) }
    // Today's queue holds only the cards still remaining, so the counter continues the
    // day-wide count from a snapshot taken when the session was built. Backlog overflow
    // (beyond the core target) counts on its own.
    var baseCompleted by remember(studyKey, reviewMode) { mutableIntStateOf(0) }
    var backlogSession by remember(studyKey, reviewMode) { mutableStateOf(false) }
    LaunchedEffect(studyKey, reviewMode, todayMode) {
        sessionQueue = null
        currentIndex = 0
        latestRatings = emptyMap()
        scheduledRequeues = emptyList()
        sessionFinished = false
        rememberedCount = 0
        forgottenCount = 0
        loadingStudy = true
        baseCompleted = 0
        backlogSession = false
        try {
            val load = if (todayMode) viewModel.startTodayStudy() else viewModel.startStudy(deckId, reviewMode)
            load.join()
        } finally {
            loadingStudy = false
        }
    }

    LaunchedEffect(cards) {
        if (!loadingStudy && sessionQueue == null && cards.isNotEmpty()) {
            baseCompleted = if (todayMode && !backlogSession) todayPlan.completedCount else 0
            sessionQueue = cards.map { StudyQueueEntry(it.id) }
        }
    }

    // 学习时长（设备本地实测，Anki 口径）：前台计时，今日模式的时段归属当前卡片的卡组，
    // 切卡即切换归属；会话结束页不再计时。自由刷题不走本屏，与统计口径一致不计时。
    val studyTimer = remember(studyKey, reviewMode) { StudyTimeAccumulator() }
    fun timingDeckId(): String? = if (todayMode) {
        val entry = sessionQueue?.getOrNull(currentIndex)
        entry?.let { queued -> cards.firstOrNull { it.id == queued.cardId }?.deckId }
    } else {
        deckId
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, studyKey, reviewMode) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME ->
                    if (!sessionFinished) timingDeckId()?.let { studyTimer.resume(it, System.currentTimeMillis()) }
                Lifecycle.Event.ON_PAUSE ->
                    viewModel.recordStudySeconds(studyTimer.pause(System.currentTimeMillis()))
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            viewModel.recordStudySeconds(studyTimer.pause(System.currentTimeMillis()))
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    LaunchedEffect(currentIndex, sessionQueue, sessionFinished) {
        if (sessionFinished) {
            viewModel.recordStudySeconds(studyTimer.pause(System.currentTimeMillis()))
        } else {
            timingDeckId()?.let { studyTimer.resume(it, System.currentTimeMillis()) }
        }
    }

    fun insertDueRequeues(nowMs: Long) {
        val queue = sessionQueue ?: return
        val due = scheduledRequeues.filter { it.dueAtMs <= nowMs }
        if (due.isEmpty()) return
        scheduledRequeues = scheduledRequeues.filter { it.dueAtMs > nowMs }
        val at = (currentIndex + 1).coerceIn(0, queue.size)
        sessionQueue = queue.subList(0, at) +
            due.sortedBy { it.dueAtMs }.map { StudyQueueEntry(it.cardId, relearn = true) } +
            queue.subList(at, queue.size)
    }

    fun handleRated(entryIndex: Int, cardId: String, rating: Rating) {
        val queue = sessionQueue ?: return
        latestRatings = latestRatings + (cardId to rating)
        if (rating == Rating.AGAIN) forgottenCount++ else rememberedCount++
        if (rating == Rating.AGAIN) {
            // Schedule a same-session return unless one is already waiting
            // further ahead in the queue (re-rating behind an existing entry
            // must not duplicate it).
            val relearnAhead = queue.withIndex().any { (index, entry) ->
                index > entryIndex && entry.relearn && entry.cardId == cardId
            }
            if (!relearnAhead) {
                scheduledRequeues = (
                    scheduledRequeues.filterNot { it.cardId == cardId } +
                        ScheduledRequeue(cardId, System.currentTimeMillis() + RELEARN_DELAY_MS)
                    ).sortedBy { it.dueAtMs }
            }
        } else {
            // A non-AGAIN rating settles the card for today: drop any pending
            // return visit, both scheduled and already queued ahead.
            scheduledRequeues = scheduledRequeues.filterNot { it.cardId == cardId }
            sessionQueue = queue.withIndex()
                .filterNot { (index, entry) -> index > entryIndex && entry.relearn && entry.cardId == cardId }
                .map { it.value }
        }
        insertDueRequeues(System.currentTimeMillis())
        val grownQueue = sessionQueue.orEmpty()
        if (entryIndex + 1 > grownQueue.lastIndex) {
            // The plan is exhausted: bring the pending relearn entries back
            // right away instead of parking the learner on a wait screen —
            // the FSRS step only matters while there are other cards left.
            if (scheduledRequeues.isNotEmpty()) {
                val entries = scheduledRequeues
                    .sortedBy { it.dueAtMs }
                    .map { StudyQueueEntry(it.cardId, relearn = true) }
                scheduledRequeues = emptyList()
                sessionQueue = grownQueue + entries
                currentIndex = grownQueue.size
                return
            }
            sessionFinished = true
            return
        }
        currentIndex = entryIndex + 1
    }

    val queue = sessionQueue
    val cardsById = cards.associateBy { it.id }
    if (reviewMode && !queue.isNullOrEmpty()) {
        if (sessionFinished) {
            CompleteStudy(
                modifier = Modifier.fillMaxSize(),
                nav = nav,
                hasBacklog = todayMode && todayPlan.backlogCount > 0,
                onContinueBacklog = {
                    sessionQueue = null
                    currentIndex = 0
                    latestRatings = emptyMap()
                    scheduledRequeues = emptyList()
                    sessionFinished = false
                    rememberedCount = 0
                    forgottenCount = 0
                    loadingStudy = true
                    backlogSession = true
                    viewModel.startTodayBacklogStudy { succeeded ->
                        loadingStudy = false
                        if (!succeeded) sessionQueue = emptyList()
                    }
                },
            )
            return
        }
        val safeIndex = currentIndex.coerceIn(0, queue.lastIndex)
        val entry = queue[safeIndex]
        val card = cardsById[entry.cardId]
        if (card != null) {
            val counter = todaySessionCounter(baseCompleted, queue, entry, latestRatings)
            var showAnswer by remember(entry.cardId, entry.relearn) { mutableStateOf(false) }
            ReviewStudy(
                card = card,
                isRelearnVisit = entry.relearn,
                position = counter.position,
                total = counter.total,
                progress = if (counter.total == 0) 0f else counter.completed.toFloat() / counter.total,
                theme = theme,
                showAnswer = showAnswer,
                canGoPrevious = safeIndex > 0,
                canGoNext = safeIndex < queue.lastIndex,
                rememberedCount = rememberedCount,
                forgottenCount = forgottenCount,
                submitting = reviewSubmitting,
                selectedRating = latestRatings[card.id],
                modifier = Modifier.fillMaxSize(),
                onBack = nav::popBackStack,
                onEdit = viewModel::updateCard,
                onToggleAnswer = { showAnswer = !showAnswer },
                onPrevious = {
                    insertDueRequeues(System.currentTimeMillis())
                    currentIndex = (safeIndex - 1).coerceAtLeast(0)
                },
                onNext = {
                    insertDueRequeues(System.currentTimeMillis())
                    val grownQueue = sessionQueue.orEmpty()
                    currentIndex = (safeIndex + 1).coerceAtMost(grownQueue.lastIndex)
                },
                onRate = { rating ->
                    val ratedIndex = safeIndex
                    // The rating lands in the outbox first; the session-level
                    // bookkeeping (relearn schedule, advance) happens in the
                    // completion callback, and a failure keeps the card on
                    // screen for a retry that replays the same event identifiers.
                    viewModel.rate(card.id, rating) {
                        handleRated(ratedIndex, card.id, rating)
                    }
                }
            )
            return
        }
    }
    if (!reviewMode && cards.isNotEmpty()) {
        FreeStudy(cards = cards, theme = theme, onBack = nav::popBackStack, onUpdateCard = viewModel::updateCard)
        return
    }
    Scaffold(topBar = { AppBar(if (reviewMode) "记忆巩固" else "自由刷题", nav::popBackStack) }) { padding ->
        when {
            reviewMode && sessionQueue?.isEmpty() == true -> CompleteStudy(
                modifier = Modifier.padding(padding),
                nav = nav,
                hasBacklog = todayMode && todayPlan.backlogCount > 0,
                onContinueBacklog = {
                    sessionQueue = null
                    currentIndex = 0
                    latestRatings = emptyMap()
                    scheduledRequeues = emptyList()
                    sessionFinished = false
                    rememberedCount = 0
                    forgottenCount = 0
                    loadingStudy = true
                    backlogSession = true
                    viewModel.startTodayBacklogStudy { succeeded ->
                        loadingStudy = false
                        if (!succeeded) sessionQueue = emptyList()
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
            Button(onClick = { if (todayMode) nav.navigate(AppRoute.StudyGoal) else nav.popBackStack() }) {
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
    isRelearnVisit: Boolean,
    position: Int,
    total: Int,
    progress: Float,
    theme: DeckTheme,
    showAnswer: Boolean,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    rememberedCount: Int,
    forgottenCount: Int,
    submitting: Boolean,
    selectedRating: Rating?,
    modifier: Modifier,
    onBack: () -> Unit,
    onEdit: (FlashcardEntity) -> Unit,
    onToggleAnswer: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRate: (Rating) -> Unit
) {
    var editingCard by remember(card.id) { mutableStateOf<FlashcardEntity?>(null) }
    val designScale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(0.75f, 1f)
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ScreenTopInformationBar(
            title = "记忆巩固",
            subtitle = if (isRelearnVisit) "$position/$total · 复练" else "$position/$total",
            onBack = onBack,
            backContainer = theme.cardPanel, titleColor = theme.text,
            modifier = Modifier.zIndex(1f)
        )
        LinearProgressIndicator(
            progress = { progress },
            color = theme.primary, trackColor = theme.secondary,
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = (16 * designScale).dp)
                .padding(top = (88 * designScale).dp).height((4 * designScale).dp)
        )
        Column(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = (16 * designScale).dp)
                .padding(top = (132 * designScale).dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Figma 41:1853 / 44:2464: the big card is a fixed 370x524 frame.
            Box(Modifier.fillMaxWidth().height((524 * designScale).dp)) {
                ReviewFlipCard(
                    card = card,
                    showAnswer = showAnswer,
                    relearn = isRelearnVisit,
                    onClick = onToggleAnswer,
                    modifier = Modifier.fillMaxSize(),
                    designScale = designScale,
                    theme = theme
                )
            }
            Spacer(Modifier.height((12 * designScale).dp))
            // The four rating buttons are always on screen: tapping the card
            // only reveals the answer, ratings never depend on flipping first.
            // The session's latest rating for this card stays outlined, so coming
            // back through the previous/next arrows still shows how it was graded.
            ReviewRatingControls(
                enabled = !submitting,
                selected = selectedRating,
                onRate = onRate,
            )
            Spacer(Modifier.height((8 * designScale).dp))
            ReviewQuestionControls(theme, canGoPrevious, canGoNext, rememberedCount, forgottenCount, onPrevious, onNext)
        }
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
private fun ReviewRatingControls(enabled: Boolean, selected: Rating?, onRate: (Rating) -> Unit) {
    val scale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(.75f, 1f)
    Row(Modifier.fillMaxWidth().height((56 * scale).dp), horizontalArrangement = Arrangement.spacedBy((8 * scale).dp), verticalAlignment = Alignment.CenterVertically) {
        ReviewRatingButton(
            label = "没想起来",
            color = Color(0xFFF4D1CE),
            contentColor = AppColors.Warning,
            enabled = enabled,
            selected = selected == Rating.AGAIN,
            modifier = Modifier.weight(1f).fillMaxHeight(),
            scale = scale,
            onClick = { onRate(Rating.AGAIN) },
        )
        ReviewRatingButton(
            label = "勉强想起",
            color = AppColors.Orange.surface,
            contentColor = AppColors.Orange.ink,
            enabled = enabled,
            selected = selected == Rating.HARD,
            modifier = Modifier.weight(1f).fillMaxHeight(),
            scale = scale,
            onClick = { onRate(Rating.HARD) },
        )
        ReviewRatingButton(
            label = "正常想起",
            color = AppColors.Green.background,
            contentColor = AppColors.Green.primaryStrong,
            enabled = enabled,
            selected = selected == Rating.GOOD,
            modifier = Modifier.weight(1f).fillMaxHeight(),
            scale = scale,
            onClick = { onRate(Rating.GOOD) },
        )
        ReviewRatingButton(
            label = "轻松想起",
            color = AppColors.Blue.background,
            contentColor = AppColors.Blue.primaryStrong,
            enabled = enabled,
            selected = selected == Rating.EASY,
            modifier = Modifier.weight(1f).fillMaxHeight(),
            scale = scale,
            onClick = { onRate(Rating.EASY) },
        )
    }
}

@Composable
private fun ReviewRatingButton(
    label: String,
    color: Color,
    contentColor: Color,
    enabled: Boolean,
    selected: Boolean,
    modifier: Modifier,
    scale: Float,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        color = color,
        contentColor = contentColor,
        // A selected card keeps a dark outline in the button's own ink colour so a
        // revisit via the previous/next arrows still shows how it was graded.
        border = if (selected) androidx.compose.foundation.BorderStroke((3 * scale).dp, contentColor) else null,
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
            MaterialSymbol(symbol, if (symbol == "arrow_back") "上一张卡片" else "下一张卡片", tint = LocalContentColor.current, size = fixedSp(24 * scale), filled = true)
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

/**
 * The card-type pill follows the card's server-owned difficulty tier. BASIC keeps the
 * card-type semantic blue from Figma 41:1853, 755:4354 and 44:2464 even when the
 * surrounding learning screen inherits a non-blue project theme; cards without a
 * tier (manual/import) render no pill.
 */
private fun cardDifficultyTagStyle(difficulty: V25Difficulty?): CardListTagStyle? = when (difficulty) {
    V25Difficulty.BASIC -> CardListTagStyle(
        label = "基础记忆",
        container = AppColors.Blue.primary,
        content = AppColors.Blue.ink
    )
    V25Difficulty.UNDERSTANDING -> CardListTagStyle(
        label = "理解分析",
        container = AppColors.Green.primarySecondary,
        content = AppColors.Green.ink
    )
    V25Difficulty.DEEP_QUESTION -> CardListTagStyle(
        label = "综合应用",
        container = AppColors.Pink.primarySecondary,
        content = AppColors.Pink.ink
    )
    null -> null
}

/** Relearn visits carry a warning-red tag so a returning AGAIN card reads at a glance. */
private fun relearnCardTagStyle() = CardListTagStyle(
    label = "复练",
    container = Color(0xFFF4D1CE),
    content = AppColors.Warning
)

/**
 * The review card flips in 3D between the question and the answer. Tapping the
 * card only reveals the answer — the four always-visible rating buttons below
 * the card are the only way to grade a card.
 */
@Composable
private fun ReviewFlipCard(
    card: FlashcardEntity,
    showAnswer: Boolean,
    relearn: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    designScale: Float,
    theme: DeckTheme,
) {
    val rotation by animateFloatAsState(
        targetValue = if (showAnswer) 180f else 0f,
        animationSpec = AppMotion.emphasisSpring(),
        label = "review card flip"
    )
    val frontAlpha = if (rotation <= 90f) 1f else 0f
    val backAlpha = if (rotation > 90f) 1f else 0f
    val faceShape = RoundedCornerShape((AppShapeRadius * designScale).dp)
    val tag = if (relearn) relearnCardTagStyle() else cardDifficultyTagStyle(card.targetDifficulty)
    Box(
        modifier = modifier
            .clip(faceShape)
            // Use the Material ripple that homepage cards use. Clipping first keeps
            // the native press state within the same 32dp container shape.
            .clickable(onClick = onClick)
    ) {
        ReviewCardFace(
            title = "问题", content = card.front, symbol = "book_5", visible = frontAlpha,
            tag = tag, rotation = rotation, shape = faceShape, designScale = designScale, backFace = false, theme = theme
        )
        ReviewCardFace(
            title = "答案", content = card.back, symbol = "wb_incandescent", visible = backAlpha,
            tag = tag, rotation = rotation, shape = faceShape, designScale = designScale, backFace = true,
            theme = theme, scrollKey = card.id
        )
    }
}

@Composable
private fun ReviewCardFace(
    title: String,
    content: String,
    symbol: String,
    tag: CardListTagStyle?,
    visible: Float,
    rotation: Float,
    shape: RoundedCornerShape,
    designScale: Float,
    backFace: Boolean,
    theme: DeckTheme,
    questionInk: Boolean = true,
    scrollKey: Any = Unit,
) {
    // Figma 203:2594 big flip card: question face = ink, answer face = surface
    // (one step deeper than the Background page).
    val questionColor = if (questionInk) theme.strongText else theme.cardPanel
    val faceColor = if (backFace) theme.cardPanel else questionColor
    val faceContent = if (backFace) theme.strongText else if (questionInk) AppColors.TextIconLight else theme.strongText
    // The answer face scrolls when the content outgrows the fixed card body;
    // the scroll position resets whenever the face is (re)entered for a new
    // card via [scrollKey]. The question face keeps the centred Figma layout.
    val scrollState = remember(scrollKey) { ScrollState(0) }
    val contentFits = !backFace || scrollState.maxValue <= 0
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
                tag?.let { style ->
                    Surface(color = style.container, shape = RoundedCornerShape(999.dp)) {
                        Text(
                            style.label,
                            modifier = Modifier.padding(horizontal = (16 * designScale).dp, vertical = (8 * designScale).dp),
                            color = style.content,
                            fontFamily = AppFonts.MiSansBold,
                            fontWeight = FontWeight.Normal,
                            fontSize = fixedSp(16 * designScale),
                            lineHeight = fixedSp(21 * designScale),
                            maxLines = 1
                        )
                    }
                }
            }
            // Center while the content fits; once it overflows, anchor to the
            // top so scrolling can reach the whole answer (a centred scroll
            // container would clip the first lines above its scroll origin).
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f).then(
                    if (backFace) Modifier.verticalScroll(scrollState) else Modifier
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = if (contentFits) Arrangement.Center else Arrangement.Top
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
                modifier = Modifier.fillMaxWidth().padding(horizontal = (16 * designScale).dp).height((68 * designScale).dp),
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
            tag = cardDifficultyTagStyle(card.targetDifficulty),
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
            tag = cardDifficultyTagStyle(card.targetDifficulty),
            visible = if (rotation > 90f) 1f else 0f,
            rotation = rotation,
            shape = shape,
            designScale = designScale,
            backFace = true,
            theme = theme,
            scrollKey = card.id
        )
    }
}
