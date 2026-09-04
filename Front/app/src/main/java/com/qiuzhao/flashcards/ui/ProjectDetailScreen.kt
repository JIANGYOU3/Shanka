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
import com.qiuzhao.flashcards.ui.navigation.AppRoute

/** Figma 540:3778: a project owns a statistics and deck-management view. */
@Composable
internal fun ProjectDetailScreen(
    project: ProjectSummary,
    decks: List<DeckSummary>,
    nav: ScreenNavigator,
    onDeleteDeck: (String, (Boolean) -> Unit) -> Unit,
    onDeleteProject: (retainDecks: Boolean, onResult: (Boolean) -> Unit) -> Unit,
    tasks: List<V25ObservedTask> = emptyList(),
    progress: V25ProgressSummary? = null,
    /** Server status EMPTY: the project has no materials yet and shows the add-material guide. */
    isEmptyProject: Boolean = false,
    onAddPdfMaterial: () -> Unit = {},
    onAddTextMaterial: () -> Unit = {},
) {
    val scale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(.75f, 1f)
    val theme = deckTheme(project)
    var section by rememberSaveable { mutableStateOf(ProjectDetailSection.STATISTICS) }
    var showProjectDeletionConfirmation by rememberSaveable(project.id) { mutableStateOf(false) }
    var projectDeletionInFlight by rememberSaveable(project.id) { mutableStateOf(false) }
    var deckPendingDeletion by rememberSaveable { mutableStateOf<String?>(null) }
    var deckDeletionInFlight by rememberSaveable { mutableStateOf(false) }
    // The coloured project canvas uses the family Background token; every
    // project-owned deck card then lifts to that family's Surface token.
    Box(Modifier.fillMaxSize().background(theme.background)) {
        ScreenTopInformationBar(
            title = project.name, subtitle = null, onBack = nav::goBack,
            backContainer = theme.cardPanel,
            onTrailingAction = { nav.navigate(AppRoute.ProjectEdit(project.id)) },
            trailingActionSymbol = "edit", trailingActionDescription = "编辑项目",
            trailingActionContainer = theme.cardPanel,
            onSecondaryTrailingAction = { showProjectDeletionConfirmation = true },
            secondaryTrailingActionDescription = "删除项目"
        )
        if (isEmptyProject) {
            ProjectEmptyContent(
                scale = scale,
                theme = theme,
                onAddPdfMaterial = onAddPdfMaterial,
                onAddTextMaterial = onAddTextMaterial,
                onRequestDeletion = { showProjectDeletionConfirmation = true },
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(start = (16 * scale).dp, top = (88 * scale).dp, end = (16 * scale).dp),
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize().statusBarsPadding().padding(start = (16 * scale).dp, top = (88 * scale).dp, end = (16 * scale).dp),
                verticalArrangement = Arrangement.spacedBy((16 * scale).dp)
            ) {
                ProjectSectionSwitcher(section, { section = it }, theme = deckTheme(project))
                when (section) {
                    ProjectDetailSection.STATISTICS -> ProjectStatisticsContent(
                        decks,
                        tasks,
                        progress,
                        theme,
                        scale,
                        Modifier.weight(1f),
                    )
                    ProjectDetailSection.DECKS -> ProjectDecksContent(
                        project,
                        decks,
                        scale,
                        nav,
                        onRequestDeleteDeck = {
                            deckPendingDeletion = it
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (section == ProjectDetailSection.DECKS) {
                BottomContentFade(scale, Modifier.align(Alignment.BottomCenter), color = theme.background)
                ProjectDeckActions(
                    theme = deckTheme(project),
                    scale = scale,
                    onAddDeck = { nav.navigate(AppRoute.DeckGeneration(project.id)) },
                    modifier = Modifier.align(Alignment.BottomCenter).zIndex(1f)
                )
            }
        }
    }
    if (showProjectDeletionConfirmation) {
        ProjectDeletionDialog(
            projectName = project.name,
            theme = theme,
            deleting = projectDeletionInFlight,
            onConfirm = { retainDecks ->
                if (projectDeletionInFlight) return@ProjectDeletionDialog
                projectDeletionInFlight = true
                onDeleteProject(retainDecks) { succeeded ->
                    projectDeletionInFlight = false
                    if (succeeded) {
                        showProjectDeletionConfirmation = false
                        nav.returnToTopLevel()
                    }
                }
            },
            onDismiss = { if (!projectDeletionInFlight) showProjectDeletionConfirmation = false }
        )
    }
    deckPendingDeletion?.let { deckId ->
        val deck = decks.firstOrNull { it.id == deckId }
        if (deck != null) {
            CompactDeckDeletionDialog(
                deleting = deckDeletionInFlight,
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
 * The EMPTY-project guide (contract V25-D-29): a just-created project owns no materials, so the
 * page offers the two add-material entries and the destructive project exit. Generation and
 * chapter features stay hidden — there is nothing to generate from yet.
 */
@Composable
private fun ProjectEmptyContent(
    scale: Float,
    theme: DeckTheme,
    onAddPdfMaterial: () -> Unit,
    onAddTextMaterial: () -> Unit,
    onRequestDeletion: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy((16 * scale).dp),
        contentPadding = PaddingValues(bottom = (NaturalScrollTail * scale).dp),
    ) {
        item {
            Surface(color = theme.cardPanel, shape = RoundedCornerShape((24 * scale).dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding((24 * scale).dp), verticalArrangement = Arrangement.spacedBy((8 * scale).dp)) {
                    AppText("这是一个空项目", AppTextRole.SectionTitle, color = theme.text, designScale = scale)
                    AppText(
                        "先添加学习资料（PDF 或粘贴文本），添加完成后即可开始制卡。",
                        AppTextRole.CardSubtitle,
                        color = theme.text.copy(alpha = .65f),
                        designScale = scale,
                    )
                }
            }
        }
        item {
            ProjectMaterialActionCard(
                icon = "picture_as_pdf",
                title = "添加 PDF 资料",
                subtitle = "上传 PDF 文件，服务端解析章节",
                theme = theme,
                scale = scale,
                onClick = onAddPdfMaterial,
            )
        }
        item {
            ProjectMaterialActionCard(
                icon = "file_copy",
                title = "粘贴文本资料",
                subtitle = "粘贴文本，30000 字以内，即时就绪",
                theme = theme,
                scale = scale,
                onClick = onAddTextMaterial,
            )
        }
        item {
            ProjectDeletionEntry(scale = scale, onRequestDeletion = onRequestDeletion)
        }
    }
}

@Composable
private fun CompactDeckDeletionDialog(
    deleting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!deleting) onDismiss() },
        title = { Text("删除卡组？", fontFamily = AppFonts.MiSansSemibold) },
        text = { Text("卡组、卡片和学习记录将一并删除。") },
        confirmButton = {
            TextButton(enabled = !deleting, onClick = onConfirm) {
                Text(if (deleting) "正在删除" else "删除", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(enabled = !deleting, onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ProjectStatisticsContent(
    decks: List<DeckSummary>,
    tasks: List<V25ObservedTask>,
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
        item { ProjectTaskStatusCard(tasks, theme, scale) }
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
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy((16 * scale).dp)) {
                StatisticsMetricCard(
                    // The existing metric slot now shows the server-derived due count; no local
                    // estimate or additional card is introduced.
                    value = honestCount(progress?.dueCount),
                    kind = StatisticsMetricKind.DueCards,
                    surface = StatisticsMetricSurface.White,
                    designScale = scale,
                    modifier = Modifier.weight(1f)
                )
                StatisticsMetricCard(
                    value = honestCount(progress?.masteredCount),
                    kind = StatisticsMetricKind.MasteredCards,
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

@Composable
private fun ProjectTaskStatusCard(
    tasks: List<V25ObservedTask>,
    theme: DeckTheme,
    scale: Float,
) {
    val active = tasks.count {
        it.status == V25TaskStatus.DRAFT ||
            it.status == V25TaskStatus.SAMPLE_GENERATING ||
            it.status == V25TaskStatus.AWAITING_SAMPLE_CONFIRMATION ||
            it.status == V25TaskStatus.GENERATING
    }
    Surface(
        color = theme.cardPanel,
        contentColor = theme.text,
        shape = RoundedCornerShape((AppNestedShapeRadius * scale).dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding((20 * scale).dp),
            verticalArrangement = Arrangement.spacedBy((12 * scale).dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy((8 * scale).dp), verticalAlignment = Alignment.CenterVertically) {
                    MaterialSymbol("sync", null, tint = theme.primary, size = fixedSp(20 * scale), filled = true)
                    AppText("制卡任务", AppTextRole.CardTitle, color = theme.text, designScale = scale)
                }
                AppText(
                    if (active == 0) "无进行中任务" else "$active 个进行中",
                    AppTextRole.CardSubtitle,
                    color = if (active == 0) theme.text.copy(alpha = .65f) else theme.primary,
                    designScale = scale,
                )
            }
            if (tasks.isEmpty()) {
                AppText("暂无任务。进入项目后，任务状态会在这里持续更新。", AppTextRole.CardSubtitle, color = theme.text.copy(alpha = .7f), designScale = scale)
            } else {
                tasks.take(5).forEach { task ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy((2 * scale).dp)) {
                            AppText("任务 ${task.taskId.take(8)}", AppTextRole.CardSubtitle, color = theme.text, designScale = scale, maxLines = 1)
                            AppText(taskStatusLabel(task.status), AppTextRole.CardSubtitle, color = theme.text.copy(alpha = .65f), designScale = scale, maxLines = 1)
                        }
                        Surface(
                            color = taskStatusColor(task.status, theme),
                            shape = RoundedCornerShape(999.dp),
                        ) {
                            AppText(task.status.name, AppTextRole.Label, color = theme.text, designScale = scale, modifier = Modifier.padding(horizontal = (8 * scale).dp, vertical = (4 * scale).dp), maxLines = 1)
                        }
                    }
                }
                if (tasks.size > 5) {
                    AppText("还有 ${tasks.size - 5} 个历史任务", AppTextRole.CardSubtitle, color = theme.text.copy(alpha = .65f), designScale = scale)
                }
            }
        }
    }
}

private fun taskStatusLabel(status: V25TaskStatus): String = when (status) {
    V25TaskStatus.DRAFT -> "草稿，等待生成"
    V25TaskStatus.SAMPLE_GENERATING -> "正在生成样卡"
    V25TaskStatus.AWAITING_SAMPLE_CONFIRMATION -> "等待确认样卡"
    V25TaskStatus.GENERATING -> "正式生成中"
    V25TaskStatus.COMPLETED -> "已完成"
    V25TaskStatus.FAILED -> "生成失败"
    V25TaskStatus.ABANDONED -> "已放弃"
}

private fun taskStatusColor(status: V25TaskStatus, theme: DeckTheme): Color = when (status) {
    V25TaskStatus.COMPLETED -> Color(0xFFD8F3E2)
    V25TaskStatus.FAILED -> Color(0xFFFFE0DD)
    V25TaskStatus.ABANDONED -> Color(0xFFE8E3EF)
    else -> theme.primary.copy(alpha = .16f)
}

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
            entry("未开始", AppColors.ReviewUnseen, progress?.notStartedCount ?: 0),
            entry("初学中", AppColors.ReviewRecognised, progress?.learningCount ?: 0),
            entry("需重学", AppColors.ReviewUncertain, progress?.relearningCount ?: 0),
            entry("巩固中", AppColors.ReviewUnfamiliar, progress?.consolidatingCount ?: 0),
            entry("已掌握", AppColors.ReviewKnown, progress?.masteredCount ?: 0),
        ),
        designScale = scale,
    )
}

/** Figma 540:4661, the lower pair of project-only summary cards. */
@Composable
private fun ProjectStreakMetrics(scale: Float) = Row(
    Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy((16 * scale).dp)
) {
    // No per-project streak or app-open source exists; keep the slots and show dashes.
    StatisticsMetricCard(
        value = "—",
        kind = StatisticsMetricKind.LongestStreak,
        surface = StatisticsMetricSurface.White,
        designScale = scale,
        modifier = Modifier.weight(1f)
    )
    StatisticsMetricCard(
        value = "—",
        kind = StatisticsMetricKind.OpenCount,
        surface = StatisticsMetricSurface.White,
        designScale = scale,
        modifier = Modifier.weight(1f)
    )
}

@Composable
private fun ProjectDecksContent(
    project: ProjectSummary,
    decks: List<DeckSummary>,
    scale: Float,
    nav: ScreenNavigator,
    onRequestDeleteDeck: (String) -> Unit,
    modifier: Modifier,
) = LazyColumn(
    modifier = modifier.fillMaxWidth().clip(RoundedCornerShape((AppScrollableContentClipRadius * scale).dp)), contentPadding = PaddingValues(bottom = (fixedBottomControlScrollTail(bottomOffset = 16) * scale).dp), verticalArrangement = Arrangement.spacedBy((16 * scale).dp)
) {
    itemsIndexed(decks, key = { _, deck -> deck.id }) { _, deck ->
        val theme = deckTheme(project)
        val progress = deck.masteryRatio ?: if (deck.cardCount == 0) 0f else deck.masteredCards.toFloat() / deck.cardCount
        ProjectSwipeAuto(
            actions = listOf(ProjectSwipeAction("delete", "删除", AppColors.Warning, AppColors.TextIconLight, { onRequestDeleteDeck(deck.id) })),
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
    }
}

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
    modifier = modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = (16 * scale).dp, vertical = (16 * scale).dp).height((60 * scale).dp)
) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            MaterialSymbol("note_stack_add", null, tint = LocalContentColor.current, size = fixedSp(24 * scale), filled = true)
            Spacer(Modifier.width((8 * scale).dp)); AppText("添加卡片组", AppTextRole.Label, color = LocalContentColor.current, designScale = scale, maxLines = 1)
        }
}
