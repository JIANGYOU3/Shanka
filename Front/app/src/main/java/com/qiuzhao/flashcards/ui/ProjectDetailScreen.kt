package com.qiuzhao.flashcards.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.qiuzhao.flashcards.data.remote.DeckSummary
import com.qiuzhao.flashcards.data.remote.ProjectSummary
import com.qiuzhao.flashcards.domain.v25.V25ObservedTask
import com.qiuzhao.flashcards.domain.v25.V25ProgressSummary
import com.qiuzhao.flashcards.domain.v25.V25TaskStatus
import com.qiuzhao.flashcards.domain.v25.isTerminal
import com.qiuzhao.flashcards.ui.navigation.AppRoute

/**
 * Figma 540:3778 (statistics) / 1114:6906 (empty deck management): a project
 * owns a statistics and deck-management view behind the section switcher.
 */
@Composable
internal fun ProjectDetailScreen(
    project: ProjectSummary,
    decks: List<DeckSummary>,
    nav: ScreenNavigator,
    onDeleteDeck: (String, (Boolean) -> Unit) -> Unit,
    progress: V25ProgressSummary? = null,
    /** Server status EMPTY: the project has no materials yet and shows the add-deck guide. */
    isEmptyProject: Boolean = false,
    /** Light task projections joined against decks locally for the 卡组四态 (交接文档 4.3). */
    tasks: List<V25ObservedTask> = emptyList(),
    /** 卡组二「点击重试」：owner 页面负责 retry + 导航到样卡等待页。 */
    onRetryDeckTask: (String) -> Unit = {},
) {
    val scale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(.75f, 1f)
    val theme = deckTheme(project)
    // Figma 1114:6906: an empty project opens on 卡组管理, whose pane carries the notice.
    var section by rememberSaveable(project.id) {
        mutableStateOf(if (isEmptyProject) ProjectDetailSection.DECKS else ProjectDetailSection.STATISTICS)
    }
    var deckPendingDeletion by rememberSaveable { mutableStateOf<String?>(null) }
    var deckDeletionInFlight by rememberSaveable { mutableStateOf(false) }
    // The coloured project canvas uses the family Background token; every
    // project-owned deck card then lifts to that family's Surface token.
    Box(Modifier.fillMaxSize().background(theme.background)) {
        ScreenTopInformationBar(
            title = project.name, subtitle = null, onBack = nav::goBack,
            backContainer = theme.secondary,
            onTrailingAction = { nav.navigate(AppRoute.ProjectEdit(project.id)) },
            trailingActionSymbol = "edit", trailingActionDescription = "编辑项目",
            trailingActionContainer = theme.secondary
        )
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().padding(start = (16 * scale).dp, top = (88 * scale).dp, end = (16 * scale).dp),
            verticalArrangement = Arrangement.spacedBy((16 * scale).dp)
        ) {
            ProjectSectionSwitcher(section, { section = it }, theme = theme)
            when (section) {
                ProjectDetailSection.STATISTICS -> ProjectStatisticsContent(
                    progress,
                    theme,
                    scale,
                    Modifier.weight(1f),
                )
                ProjectDetailSection.DECKS -> if (isEmptyProject) {
                    ProjectEmptyDecksNotice(
                        theme = theme,
                        scale = scale,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    ProjectDecksContent(
                        project,
                        decks,
                        tasks,
                        scale,
                        nav,
                        onRequestDeleteDeck = {
                            deckPendingDeletion = it
                        },
                        onRetryDeckTask = onRetryDeckTask,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        if (section == ProjectDetailSection.DECKS) {
            BottomContentFade(scale, Modifier.align(Alignment.BottomCenter), color = theme.background)
            ProjectDeckActions(
                theme = theme,
                scale = scale,
                onAddDeck = { nav.navigate(AppRoute.DeckGeneration(project.id)) },
                modifier = Modifier.align(Alignment.BottomCenter).zIndex(1f)
            )
        }
    }
    deckPendingDeletion?.let { deckId ->
        val deck = decks.firstOrNull { it.id == deckId }
        if (deck != null) {
            // Figma 1130:8079 卡组版确认弹窗（交接文档 决策②）。
            CuteConfirmDialog(
                title = "是否删除卡组？",
                busy = deckDeletionInFlight,
                onConfirm = {
                    if (!deckDeletionInFlight) {
                        deckDeletionInFlight = true
                        onDeleteDeck(deckId) { succeeded ->
                            deckDeletionInFlight = false
                            if (succeeded) deckPendingDeletion = null
                        }
                    }
                },
                onDismiss = { if (!deckDeletionInFlight) deckPendingDeletion = null },
            )
        }
    }
}

/**
 * Figma 1114:6906: an EMPTY project's 卡组管理 pane is a single notice — the
 * 添加卡片组 action below is the way in, and the generation page owns material
 * import for a project that has no materials yet.
 */
@Composable
private fun ProjectEmptyDecksNotice(
    theme: DeckTheme,
    scale: Float,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape((AppScrollableContentClipRadius * scale).dp)),
        contentPadding = PaddingValues(bottom = (NaturalScrollTail * scale).dp),
    ) {
        item {
            // Figma 1114:7171 导入说明: family Surface, 24dp radius and inset,
            // Supporting copy at the 80% neutral ink.
            Surface(
                color = theme.cardPanel,
                shape = RoundedCornerShape((AppNestedShapeRadius * scale).dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                AppText(
                    "暂未添加任何学习资料。请先添加学习资料文件与文本等。",
                    AppTextRole.Supporting,
                    modifier = Modifier.fillMaxWidth().padding((24 * scale).dp),
                    color = AppColors.TextIconDark,
                    designScale = scale,
                )
            }
        }
    }
}

@Composable
private fun ProjectStatisticsContent(
    progress: V25ProgressSummary?,
    theme: DeckTheme,
    scale: Float,
    modifier: Modifier,
) {
    var showToday by rememberSaveable { mutableStateOf(true) }
    // The project endpoint is the source of truth.  Until it returns, every metric stays an
    // honest dash instead of being recomputed from the visible deck list.
    val learnedCards = progress?.let { (it.cardCount - it.notStartedCount).coerceAtLeast(0) }
    LazyColumn(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape((AppScrollableContentClipRadius * scale).dp)),
        // Statistics has no fixed bottom action bar. A 32dp tail places the
        // final cards just above the system navigation area, as in Figma.
        contentPadding = PaddingValues(bottom = (NaturalScrollTail * scale).dp),
        verticalArrangement = Arrangement.spacedBy((16 * scale).dp)
    ) {
        item {
            LearningDataProgressCard(
                // The overview tab is backed by the server-derived lifecycle aggregate. The
                // today tab has no project-scoped daily endpoint in this screen, so it remains
                // an honest dash without changing the card geometry.
                reviewedCards = if (showToday) null else learnedCards,
                totalCards = if (showToday) null else progress?.cardCount?.takeIf { it > 0 },
                progressPercent = if (showToday || progress == null || progress.cardCount == 0) null
                    else (learnedCards!! * 100 / progress.cardCount),
                todaySelected = showToday,
                onTodaySelected = { showToday = it },
                theme = theme,
                designScale = scale
            )
        }
        // Figma 540:3778 order: 已掌握卡片 / 学习时长, then the review-progress
        // chart, then 打开次数 / 单次最大连胜. Learning time and app-open counts
        // have no per-project source, so those slots stay honest dashes.
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy((16 * scale).dp)) {
                StatisticsMetricCard(
                    value = honestCount(progress?.masteredCount),
                    kind = StatisticsMetricKind.MasteredCards,
                    surface = StatisticsMetricSurface.White,
                    designScale = scale,
                    modifier = Modifier.weight(1f)
                )
                StatisticsMetricCard(
                    value = "—",
                    kind = StatisticsMetricKind.LearningTime,
                    surface = StatisticsMetricSurface.White,
                    designScale = scale,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item { ProjectProgressDistribution(scale, progress) }
        item { ProjectStreakMetrics(scale) }
    }
}

/**
 * Figma 540:3778 复习进度: five fixed buckets in mastery order, each keeping
 * its design colour. The server lifecycle counts map by meaning: 熟识=mastered,
 * 认识=consolidating, 模糊=relearning, 陌生=learning, 没学=not started.
 */
@Composable
private fun ProjectProgressDistribution(scale: Float, progress: V25ProgressSummary?) {
    val total = progress?.cardCount ?: 0
    fun entry(label: String, color: Color, count: Int): ReviewProgressEntry {
        val percentage = if (total > 0) (count * 100f / total).toInt() else null
        val height = if (total > 0) (count * 116f / total).toInt() else 0
        return ReviewProgressEntry(label, color, percentage, height)
    }
    ReviewProgressCard(
        entries = listOf(
            entry("熟识", AppColors.ReviewKnown, progress?.masteredCount ?: 0),
            entry("认识", AppColors.ReviewRecognised, progress?.consolidatingCount ?: 0),
            entry("模糊", AppColors.ReviewUncertain, progress?.relearningCount ?: 0),
            entry("陌生", AppColors.ReviewUnfamiliar, progress?.learningCount ?: 0),
            entry("没学", AppColors.ReviewUnseen, progress?.notStartedCount ?: 0),
        ),
        designScale = scale,
        title = "复习进度",
    )
}

/** Figma 540:3778, the lower pair of project-only summary cards. */
@Composable
private fun ProjectStreakMetrics(scale: Float) = Row(
    Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy((16 * scale).dp)
) {
    // No per-project streak or app-open source exists; keep the slots and show dashes.
    StatisticsMetricCard(
        value = "—",
        kind = StatisticsMetricKind.OpenCount,
        surface = StatisticsMetricSurface.White,
        designScale = scale,
        modifier = Modifier.weight(1f)
    )
    StatisticsMetricCard(
        value = "—",
        kind = StatisticsMetricKind.LongestStreak,
        surface = StatisticsMetricSurface.White,
        designScale = scale,
        modifier = Modifier.weight(1f)
    )
}

@Composable
private fun ProjectDecksContent(
    project: ProjectSummary,
    decks: List<DeckSummary>,
    tasks: List<V25ObservedTask>,
    scale: Float,
    nav: ScreenNavigator,
    onRequestDeleteDeck: (String) -> Unit,
    onRetryDeckTask: (String) -> Unit,
    modifier: Modifier,
) = LazyColumn(
    modifier = modifier.fillMaxWidth().clip(RoundedCornerShape((AppScrollableContentClipRadius * scale).dp)), contentPadding = PaddingValues(bottom = (fixedBottomControlScrollTail(bottomOffset = 16) * scale).dp), verticalArrangement = Arrangement.spacedBy((16 * scale).dp)
) {
    itemsIndexed(decks, key = { _, deck -> deck.id }) { _, deck ->
        val theme = deckTheme(project)
        // 卡组四态（Figma 1130:7288 / 交接文档 4.3）：deck↔最新任务推导。
        val latestTask = tasks.filter { it.deckId == deck.id }.maxByOrNull { it.updatedAt }
        val state = when {
            latestTask == null -> DeckTaskState.NORMAL
            latestTask.status == V25TaskStatus.AWAITING_CONFIRMATION -> DeckTaskState.AWAITING_CONFIRMATION
            latestTask.status == V25TaskStatus.FAILED -> DeckTaskState.FAILED
            !latestTask.status.isTerminal -> DeckTaskState.GENERATING
            else -> DeckTaskState.NORMAL
        }
        val progress = deck.masteryRatio ?: if (deck.cardCount == 0) 0f else deck.masteredCards.toFloat() / deck.cardCount
        when (state) {
            DeckTaskState.NORMAL -> ProjectSwipeAuto(
                actions = listOf(
                    ProjectSwipeAction("edit", "编辑卡片", AppColors.Card, AppColors.TextIconDark) {
                        nav.navigate(AppRoute.EditCardList(deck.id))
                    },
                    ProjectSwipeAction("delete", "删除卡组", AppColors.Warning, AppColors.TextIconLight) {
                        onRequestDeleteDeck(deck.id)
                    },
                ),
                scale = scale
            ) {
                ProjectThemedCard(
                    title = displayDeckTitle(deck),
                    count = deck.cardCount,
                    countLabel = "cards",
                    progress = progress,
                    theme = theme,
                    icon = "heap_snapshot_multiple",
                    variant = ProjectThemedCardVariant.THEME_BACKGROUND,
                    designScale = scale,
                    onClick = { nav.navigate(AppRoute.Deck(deck.id)) }
                )
            }
            DeckTaskState.GENERATING -> ProjectSwipeAuto(
                actions = listOf(
                    ProjectSwipeAction("delete", "删除卡组", AppColors.Warning, AppColors.TextIconLight) {
                        onRequestDeleteDeck(deck.id)
                    },
                ),
                scale = scale
            ) {
                GeneratingDeckCard(displayDeckTitle(deck), theme, scale)
            }
            DeckTaskState.FAILED -> ProjectSwipeAuto(
                actions = listOf(
                    ProjectSwipeAction("delete", "删除卡组", AppColors.Warning, AppColors.TextIconLight) {
                        onRequestDeleteDeck(deck.id)
                    },
                ),
                scale = scale
            ) {
                FailedDeckCard(
                    title = displayDeckTitle(deck),
                    reason = failureReasonText(latestTask?.errorCode),
                    theme = theme,
                    scale = scale,
                    onRetry = { latestTask?.taskId?.let(onRetryDeckTask) }
                )
            }
            DeckTaskState.AWAITING_CONFIRMATION -> ProjectSwipeAuto(
                actions = listOf(
                    ProjectSwipeAction("delete", "删除卡组", AppColors.Warning, AppColors.TextIconLight) {
                        onRequestDeleteDeck(deck.id)
                    },
                ),
                scale = scale
            ) {
                AwaitConfirmationDeckCard(
                    title = displayDeckTitle(deck),
                    theme = theme,
                    scale = scale,
                    onView = {
                        latestTask?.let { task ->
                            nav.navigate(AppRoute.SmartCardReview(task.taskId, project.id, theme.key))
                        }
                    }
                )
            }
        }
    }
}

/** 卡组四态（交接文档 4.3）。 */
private enum class DeckTaskState { NORMAL, GENERATING, FAILED, AWAITING_CONFIRMATION }

/** Figma 494:1447 / 540:3778 deck-management fixed actions. */
@Composable
private fun ProjectDeckActions(
    theme: DeckTheme,
    scale: Float,
    onAddDeck: () -> Unit,
    modifier: Modifier = Modifier
) = Surface(
    onClick = onAddDeck,
    color = theme.primary,
    contentColor = theme.onPrimary,
    shape = RoundedCornerShape((24 * scale).dp),
    modifier = modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = (16 * scale).dp, vertical = (16 * scale).dp).height((68 * scale).dp)
) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            MaterialSymbol("note_stack_add", null, tint = LocalContentColor.current, size = fixedSp(24 * scale), filled = true)
            Spacer(Modifier.width((8 * scale).dp)); AppText("添加卡片组", AppTextRole.Label, color = LocalContentColor.current, designScale = scale, maxLines = 1)
        }
}

/**
 * Figma 1130:7288 卡组一（正在生成）: family Surface card, 60dp progress_activity
 * tile on family Primary-Secondary, subtitle 正在生成. Not clickable — generation
 * is server-owned and the generating screen owns the live view.
 */
@Composable
internal fun GeneratingDeckCard(title: String, theme: DeckTheme, scale: Float) = Surface(
    color = theme.cardPanel,
    shape = RoundedCornerShape((AppShapeRadius * scale).dp),
    modifier = Modifier.fillMaxWidth()
) {
    Row(
        Modifier.fillMaxWidth().padding((20 * scale).dp),
        horizontalArrangement = Arrangement.spacedBy((12 * scale).dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(color = theme.secondary, shape = RoundedCornerShape((24 * scale).dp), modifier = Modifier.size((60 * scale).dp)) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    color = theme.primary,
                    trackColor = Color.Transparent,
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                    strokeWidth = (3 * scale).dp,
                    modifier = Modifier.size((28 * scale).dp)
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy((8 * scale).dp)) {
            AppText(title, AppTextRole.CardTitle, color = theme.text, designScale = scale, maxLines = 1, overflow = TextOverflow.Ellipsis)
            AppText("正在生成", AppTextRole.CardSubtitle, color = theme.text, designScale = scale, maxLines = 1)
        }
    }
}

/**
 * Figma 1130:7478 卡组二（生成失败）: #E87F77 card, #BD3F3F error tile, white ink,
 * reason on one line, and the 点击重试 replay button (交接文档 4.3).
 */
@Composable
internal fun FailedDeckCard(
    title: String,
    reason: String,
    theme: DeckTheme,
    scale: Float,
    onRetry: () -> Unit,
) = Surface(
    color = AppColors.WarningSecondary,
    shape = RoundedCornerShape((AppShapeRadius * scale).dp),
    modifier = Modifier.fillMaxWidth()
) {
    Column(Modifier.fillMaxWidth().padding((20 * scale).dp), verticalArrangement = Arrangement.spacedBy((16 * scale).dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy((12 * scale).dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(color = AppColors.Warning, shape = RoundedCornerShape((24 * scale).dp), modifier = Modifier.size((60 * scale).dp)) {
                Box(contentAlignment = Alignment.Center) {
                    MaterialSymbol("error", null, tint = AppColors.TextIconLight, size = fixedSp(24 * scale), filled = true)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy((8 * scale).dp)) {
                AppText(title, AppTextRole.CardTitle, color = AppColors.TextIconLight, designScale = scale, maxLines = 1, overflow = TextOverflow.Ellipsis)
                AppText("生成失败：$reason", AppTextRole.CardSubtitle, color = AppColors.TextIconLight, designScale = scale, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Surface(
            onClick = onRetry,
            color = AppColors.Warning,
            contentColor = AppColors.TextIconLight,
            shape = RoundedCornerShape((AppButtonShapeRadius * scale).dp),
            modifier = Modifier.fillMaxWidth().height((60 * scale).dp)
        ) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                MaterialSymbol("replay", null, tint = LocalContentColor.current, size = fixedSp(24 * scale), filled = true)
                Spacer(Modifier.width((8 * scale).dp))
                AppText("点击重试", AppTextRole.Label, color = LocalContentColor.current, designScale = scale, maxLines = 1)
            }
        }
    }
}

/**
 * Figma 1130:8005 卡组三（待确认）: family Surface card with a Primary tile and the
 * 点击查看 feature_search button into the read-only review screen; no count badge
 * because the deck's cards are still STAGED (交接文档 4.3).
 */
@Composable
internal fun AwaitConfirmationDeckCard(
    title: String,
    theme: DeckTheme,
    scale: Float,
    onView: () -> Unit,
) = Surface(
    color = theme.cardPanel,
    shape = RoundedCornerShape((AppShapeRadius * scale).dp),
    modifier = Modifier.fillMaxWidth()
) {
    Column(Modifier.fillMaxWidth().padding((20 * scale).dp), verticalArrangement = Arrangement.spacedBy((16 * scale).dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy((12 * scale).dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(color = theme.primary, shape = RoundedCornerShape((24 * scale).dp), modifier = Modifier.size((60 * scale).dp)) {
                Box(contentAlignment = Alignment.Center) {
                    MaterialSymbol("history_edu", null, tint = theme.onPrimary, size = fixedSp(24 * scale), filled = true)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy((8 * scale).dp)) {
                AppText(title, AppTextRole.CardTitle, color = theme.text, designScale = scale, maxLines = 1, overflow = TextOverflow.Ellipsis)
                AppText("生成完成，待确认", AppTextRole.CardSubtitle, color = theme.text, designScale = scale, maxLines = 1)
            }
        }
        Surface(
            onClick = onView,
            color = theme.primary,
            contentColor = theme.onPrimary,
            shape = RoundedCornerShape((AppButtonShapeRadius * scale).dp),
            modifier = Modifier.fillMaxWidth().height((60 * scale).dp)
        ) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                MaterialSymbol("feature_search", null, tint = LocalContentColor.current, size = fixedSp(24 * scale), filled = true)
                Spacer(Modifier.width((8 * scale).dp))
                AppText("点击查看", AppTextRole.Label, color = LocalContentColor.current, designScale = scale, maxLines = 1)
            }
        }
    }
}
