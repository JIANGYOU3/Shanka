package com.qiuzhao.flashcards.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.qiuzhao.flashcards.data.remote.DeckSummary
import com.qiuzhao.flashcards.ui.navigation.AppNavigator
import com.qiuzhao.flashcards.ui.navigation.AppRoute
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Figma 977:4937 — the one plan-configuration page. 每日目标 carries the two
 * 72dp wheels; 范围 shows every created project as a checked card whose drawer
 * reveals its decks. A configured user arrives with the real plan's decks and
 * goals pre-filled and saves through the one atomic PUT /study/plan, while a
 * first-time user starts with nothing picked.
 */
@Composable
internal fun StudyGoalScreen(viewModel: AppViewModel, nav: AppNavigator) {
    val plan by viewModel.studyPlan.collectAsState()
    val projects by viewModel.projects.collectAsState()
    val decks by viewModel.decks.collectAsState()
    val uiMessage by viewModel.uiMessage.collectAsState()
    var newGoal by remember { mutableIntStateOf(10) }
    var reviewGoal by remember { mutableIntStateOf(40) }
    var seeded by remember { mutableStateOf(false) }
    // 范围 selection is two sets: a whole-checked project implies all of its
    // decks; the drawer's individually picked decks live beside it. The checked
    // circle derives from either, so picking one deck re-checks its project.
    var wholeProjectIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedDeckIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var expandedProjectIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(Unit) {
        viewModel.refreshStudyPlan()
        viewModel.refreshProjects()
        viewModel.refreshDecks()
    }
    LaunchedEffect(plan.loaded) {
        if (plan.loaded && !seeded) {
            newGoal = plan.dailyNewGoal
            reviewGoal = plan.dailyReviewGoal
            if (plan.configured) {
                // Restore the real current deck picks; a fresh user picks from scratch.
                selectedDeckIds = plan.selectedDeckIds.toSet()
            }
            seeded = true
        }
    }

    // 交接文档 5/1019-5568：尚未就绪的卡组（可见卡为 0：生成中/待确认/失败）不再被
    // 静默隐藏——在范围抽屉里可见但禁选，说明「未设置完成，无法选择」。
    val learnableDecksByProject = projects.associate { project ->
        project.id to decks.filter { it.projectId == project.id && it.cardCount > 0 }
    }
    val pendingDecksByProject = projects.associate { project ->
        project.id to decks.filter { it.projectId == project.id && it.cardCount == 0 }
    }
    val effectiveDeckIds = decks
        .filter { it.cardCount > 0 && (it.projectId in wholeProjectIds || it.id in selectedDeckIds) }
        .map { it.id }
    val primaryProjectId = (
        projects.firstOrNull { it.id in wholeProjectIds }
            ?: projects.firstOrNull { project ->
                decks.any { it.projectId == project.id && it.id in selectedDeckIds }
            }
        )?.id.orEmpty()
    val validGoals = newGoal in 0..200 && reviewGoal in 0..200 && newGoal % 10 == 0 &&
        reviewGoal % 10 == 0 && newGoal + reviewGoal > 0
    val canSave = studyGoalCanSave(
        seeded = seeded,
        saving = plan.saving,
        validGoals = validGoals,
        hasProject = primaryProjectId.isNotEmpty(),
        hasLearnableSelection = effectiveDeckIds.isNotEmpty(),
    )

    Box(Modifier.fillMaxSize().background(AppColors.BaseBackground)) {
        Column(Modifier.fillMaxSize()) {
            // Figma 977:4994: the secondary header keeps its #CCE6FF back control.
            ScreenTopInformationBar(
                title = "设定计划", subtitle = null, onBack = nav::popBackStack,
                backContainer = AppColors.Blue.surface
            )
            Box(Modifier.weight(1f).imePadding()) {
                if (!seeded) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        PlanSectionCard("每日目标", "edit_calendar") {
                            GoalRow("每日新学", newGoal) { newGoal = it }
                            GoalRow("每日复习", reviewGoal) { reviewGoal = it }
                            if (!validGoals) {
                                CardHint(
                                    "目标须为 0～200 的 10 的倍数，且不能同时为 0",
                                    designScale = 1f,
                                    error = true
                                )
                            }
                        }
                        PlanSectionCard("范围", "category_search") {
                            if (projects.isEmpty()) {
                                PlanSectionHint("先创建项目并导入资料")
                            } else {
                                projects.forEach { project ->
                                    val projectDecks = learnableDecksByProject[project.id].orEmpty()
                                    ScopeProjectCard(
                                        projectName = project.name,
                                        decks = projectDecks,
                                        pendingDecks = pendingDecksByProject[project.id].orEmpty(),
                                        checked = project.id in wholeProjectIds ||
                                            projectDecks.any { it.id in selectedDeckIds },
                                        expanded = project.id in expandedProjectIds,
                                        onToggleProject = {
                                            val isChecked = project.id in wholeProjectIds ||
                                                projectDecks.any { it.id in selectedDeckIds }
                                            if (isChecked) {
                                                wholeProjectIds -= project.id
                                                selectedDeckIds -= projectDecks.map { it.id }.toSet()
                                            } else {
                                                // Collapsed or not, checking a project implies
                                                // every deck under it (Figma 范围 default).
                                                wholeProjectIds += project.id
                                            }
                                        },
                                        onToggleExpand = {
                                            if (project.id in expandedProjectIds) {
                                                expandedProjectIds -= project.id
                                            } else {
                                                expandedProjectIds += project.id
                                                // 展开抽屉保留既有选中：整项目勾选迁移为
                                                // 逐卡组勾选（视觉等价、单行可反选），来自
                                                // 现有计划的回填选中原样保留。
                                                val (whole, picked) = drawerOpenSelection(
                                                    projectId = project.id,
                                                    projectDeckIds = projectDecks.map { it.id }.toSet(),
                                                    wholeProjectIds = wholeProjectIds,
                                                    selectedDeckIds = selectedDeckIds,
                                                )
                                                wholeProjectIds = whole
                                                selectedDeckIds = picked
                                            }
                                        },
                                        onToggleDeck = { deck ->
                                            selectedDeckIds = if (deck.id in selectedDeckIds) {
                                                selectedDeckIds - deck.id
                                            } else {
                                                selectedDeckIds + deck.id
                                            }
                                        },
                                        deckChecked = {
                                            it.id in selectedDeckIds || project.id in wholeProjectIds
                                        },
                                    )
                                }
                            }
                        }
                        uiMessage?.let { message ->
                            AppText(message, AppTextRole.Supporting, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
        BottomContentFade(1f, Modifier.align(Alignment.BottomCenter), heightDp = 138)
        Surface(
            onClick = {
                if (canSave) {
                    viewModel.clearUiMessage()
                    // One atomic save of the whole form; a configured user's picks were
                    // seeded from the loaded plan, so an untouched save preserves them.
                    viewModel.saveStudyPlan(
                        currentProjectId = primaryProjectId,
                        selectedDeckIds = effectiveDeckIds,
                        dailyNewGoal = newGoal,
                        dailyReviewGoal = reviewGoal,
                    ) {
                        if (plan.configured) nav.popBackStack() else nav.navigate(AppRoute.StudyToday)
                    }
                }
            },
            enabled = canSave,
            color = if (canSave) AppColors.Blue.primary else AppColors.Blue.primary.copy(alpha = .45f),
            contentColor = AppColors.TextIconLight,
            shape = RoundedCornerShape(AppButtonShapeRadius.dp),
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .zIndex(1f)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                .height(68.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MaterialSymbol("check_circle", null, tint = AppColors.TextIconLight, size = fixedSp(24f), filled = true)
                AppText("完成", AppTextRole.Label, color = AppColors.TextIconLight, textAlign = TextAlign.Center)
            }
        }
    }
}

/**
 * The single save gate, shared by first-time and configured users alike: the
 * form needs a checked project and at least one learnable deck, plus valid goals.
 */
internal fun studyGoalCanSave(
    seeded: Boolean,
    saving: Boolean,
    validGoals: Boolean,
    hasProject: Boolean,
    hasLearnableSelection: Boolean,
): Boolean = seeded && !saving && validGoals && hasProject && hasLearnableSelection

/**
 * 展开卡组抽屉时的选中迁移（[StudyGoalScreen] 范围区）：整项目勾选迁出
 * [wholeProjectIds]、其全部可学卡组逐个进入 [selectedDeckIds]——勾选面不变，
 * 抽屉里的单行从此可单独反选；其余情况（未整选/无可学卡组）原样返回，已
 * 回填的计划选中永不被展开动作清除。返回 (wholeProjectIds', selectedDeckIds')。
 */
internal fun drawerOpenSelection(
    projectId: String,
    projectDeckIds: Set<String>,
    wholeProjectIds: Set<String>,
    selectedDeckIds: Set<String>,
): Pair<Set<String>, Set<String>> {
    if (projectId !in wholeProjectIds || projectDeckIds.isEmpty()) {
        return wholeProjectIds to selectedDeckIds
    }
    return (wholeProjectIds - projectId) to (selectedDeckIds + projectDeckIds)
}

/** Figma 977:4937 card language: #EEF4FA r36, 20dp padding, 16dp item gap. */
@Composable
internal fun PlanSectionCard(title: String, icon: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(AppShapeRadius.dp))
            .background(AppColors.Blue.background)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PlanSectionHeader(title, icon)
        content()
    }
}

/** Section header row inside a Figma-977 card: icon + title, matching 每日目标. */
@Composable
internal fun PlanSectionHeader(title: String, icon: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MaterialSymbol(icon, null, tint = AppColors.TextIconDark, size = fixedSp(24f), filled = true)
        AppText(title, AppTextRole.SectionTitle, color = AppColors.TextIconDark)
    }
}

/** Figma 1050:4956-style empty hint: small gray copy at the card's bottom-left. */
@Composable
private fun PlanSectionHint(text: String) {
    CardHint(text, designScale = 1f)
}

/**
 * Figma 1019:6218 — one 范围 project card: #CCE6FF r24 shell, a checked circle
 * beside the project name, and a 64x35 expand pill whose drawer lists the
 * project's decks. The drawer opens like a drawer: vertical expand/collapse.
 * [pendingDecks] (Figma 1019:5568 卡组3) render visible but disabled: a grey
 * error circle, dimmed copy, and the 「未设置完成，无法选择」 note at the drawer's
 * bottom — tapping them does nothing and they never join the saved plan.
 */
@Composable
internal fun ScopeProjectCard(
    projectName: String,
    decks: List<DeckSummary>,
    checked: Boolean,
    expanded: Boolean,
    onToggleProject: () -> Unit,
    onToggleExpand: () -> Unit,
    onToggleDeck: (DeckSummary) -> Unit,
    deckChecked: (DeckSummary) -> Boolean,
    pendingDecks: List<DeckSummary> = emptyList(),
) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(AppColors.Blue.surface)
            // Figma 1019:6218（用户决策）：整块淡蓝卡片都是点击目标，涟漪覆盖
            // 整卡；展开按钮与抽屉内卡组行的自身点击在内层优先消费。
            .clickable(onClick = onToggleProject)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ScopeCheckCircle(checked = checked, restingColor = AppColors.Blue.background)
                AppText(projectName, AppTextRole.CardTitle, color = AppColors.TextIconDark)
            }
            if (decks.isNotEmpty() || pendingDecks.isNotEmpty()) {
                Surface(
                    onClick = onToggleExpand,
                    color = AppColors.Blue.background,
                    contentColor = AppColors.TextIconDark,
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier.width(64.dp).height(35.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        MaterialSymbol(
                            if (expanded) "arrow_drop_up" else "arrow_drop_down",
                            if (expanded) "收起卡组" else "展开卡组",
                            tint = AppColors.TextIconDark, size = fixedSp(24f), filled = true
                        )
                    }
                }
            }
        }
        if (decks.isNotEmpty() || pendingDecks.isNotEmpty()) {
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(
                    animationSpec = tween(240, easing = FastOutSlowInEasing),
                    expandFrom = Alignment.Top
                ) + fadeIn(tween(240)),
                exit = shrinkVertically(
                    animationSpec = tween(240, easing = FastOutSlowInEasing),
                    shrinkTowards = Alignment.Top
                ) + fadeOut(tween(180))
            ) {
                Column(
                    Modifier.fillMaxWidth()
                        .background(AppColors.Blue.background, RoundedCornerShape(24.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    decks.forEach { deck ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onToggleDeck(deck) },
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ScopeCheckCircle(
                                // A whole-checked project implies every deck under it.
                                checked = deckChecked(deck),
                                restingColor = AppColors.Blue.surface
                            )
                            AppText(deck.name, AppTextRole.CardSubtitle, color = AppColors.TextIconDark)
                        }
                    }
                    pendingDecks.forEach { deck ->
                        // Figma 1049:4926 卡组3: grey error circle + dimmed name; no click target.
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(35.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(Color(0xFFA6A6A6)),
                                contentAlignment = Alignment.Center
                            ) {
                                MaterialSymbol("error", null, tint = AppColors.TextIconLight, size = fixedSp(24f), filled = true)
                            }
                            AppText(deck.name, AppTextRole.CardSubtitle, color = AppColors.TextIconDark.copy(alpha = .5f))
                        }
                    }
                    pendingDecks.forEach { deck ->
                        // Figma 1133:8492: the explanation line sits at the drawer's bottom.
                        AppText(
                            "${deck.name}未设置完成，无法选择",
                            AppTextRole.CardSubtitle,
                            color = AppColors.TextIconDark.copy(alpha = .5f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Drawer-smooth variant of [centreWheelOn]: glides [index] to the centre from
 * wherever the track currently rests. Items scrolled far out are jumped in
 * first, then corrected once their metrics are visible.
 */
private suspend fun androidx.compose.foundation.lazy.LazyListState.animateCentreOn(index: Int) {
    val info = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
    if (info == null) {
        scrollToItem(index)
        androidx.compose.runtime.withFrameNanos { }
        return animateCentreOn(index)
    }
    val viewportCentre = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f
    val delta = info.offset + info.size / 2f - viewportCentre
    if (abs(delta) > 1f) animateScrollBy(-delta, tween(180, easing = FastOutSlowInEasing))
}

/**
 * Figma 1049:4848 — the 35dp round status mark. Checked is the blue circle
 * with a white check; the resting state keeps a dash on the given tint.
 */
@Composable
private fun ScopeCheckCircle(checked: Boolean, restingColor: Color, modifier: Modifier = Modifier) {
    Box(
        modifier.size(35.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (checked) AppColors.Blue.primary else restingColor),
        contentAlignment = Alignment.Center
    ) {
        MaterialSymbol(
            if (checked) "check" else "check_indeterminate_small",
            null,
            tint = if (checked) AppColors.TextIconLight else AppColors.TextIconDark,
            size = fixedSp(24f), filled = true
        )
    }
}

/** Figma 977:5016's goal row: a white label pill beside the 72dp value wheel. */
@Composable
internal fun GoalRow(label: String, value: Int, onValueChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(
            color = AppColors.Card,
            shape = RoundedCornerShape(AppButtonShapeRadius.dp),
            modifier = Modifier.height(72.dp)
        ) {
            Box(Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                AppText(label, AppTextRole.CardTitle, color = AppColors.TextIconDark)
            }
        }
        GoalValueWheel(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f).height(72.dp)
        )
    }
}

private val goalWheelValues: List<Int> = List(21) { it * 10 }

/**
 * The 72dp horizontal wheel (Figma 979:4965): every value renders one uniform
 * Metric/Large size with a fixed 8dp gap — only the ink differs. The centred
 * value is black, its neighbours 75% and the rest 45% (fills 34dc0314 /
 * d0560d9f / 60ac1bcd).
 */
@Composable
internal fun GoalValueWheel(value: Int, onValueChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val latestValue by rememberUpdatedState(value)
    // The wheel only reports settle changes after the initial value scroll has
    // positioned the track, so the first composition can't emit a stray 0.
    var wheelReady by remember { mutableStateOf(false) }
    BoxWithConstraints(modifier.clip(RoundedCornerShape(AppButtonShapeRadius.dp)).background(AppColors.Card)) {
        // One frame of layout, then land the seeded value exactly under the
        // centre hairline before the settle listener opens the value gate.
        LaunchedEffect(constraints.maxWidth) {
            androidx.compose.runtime.withFrameNanos { }
            listState.centreWheelOn(goalWheelValues.indexOf(latestValue).coerceAtLeast(0))
            wheelReady = true
        }
        LazyRow(
            state = listState,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            // Clock-dial physics: every fling damps onto a value centre and can
            // never rest between ticks (variable value widths snap by centre).
            flingBehavior = rememberSnapFlingBehavior(listState, snapPosition = SnapPosition.Center),
            // Wide enough that even the widest (three-digit) value can centre.
            contentPadding = PaddingValues(horizontal = ((maxWidth - 92.dp).coerceAtLeast(0.dp)) / 2),
            modifier = Modifier.fillMaxSize()
        ) {
            items(goalWheelValues.size) { index ->
                val itemValue = goalWheelValues[index]
                val distanceSlots = wheelDistanceSlots(listState, index)
                Box(
                    Modifier.fillMaxHeight().clickable {
                        scope.launch { listState.animateCentreOn(index) }
                    },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        itemValue.toString(),
                        color = Color.Black.copy(
                            alpha = when (distanceSlots) {
                                0 -> 1f
                                1 -> .75f
                                else -> .45f
                            }
                        ),
                        fontFamily = AppFonts.GoogleSansFlexBold,
                        fontWeight = FontWeight.Normal,
                        fontSize = fixedSp(48f),
                        lineHeight = fixedSp(48f),
                        style = figmaCardTextStyle(),
                        maxLines = 1
                    )
                }
            }
        }
        // A scroll settle is the moment the centred value becomes the form
        // value; the snap fling has already parked it under the hairline.
        LaunchedEffect(Unit) {
            snapshotFlow { listState.isScrollInProgress }
                .filter { !it }
                .collect {
                    if (!wheelReady) return@collect
                    val centred = centredWheelIndex(listState)
                    val resolved = goalWheelValues.getOrNull(centred) ?: return@collect
                    if (resolved != latestValue) onValueChange(resolved)
                }
        }
    }
}

/**
 * Lands [index] exactly under the wheel's centre hairline. scrollToItem aligns
 * an item to the viewport edge, so the final correction scroll is measured from
 * the realised layout instead of assuming content-padding semantics.
 */
private suspend fun androidx.compose.foundation.lazy.LazyListState.centreWheelOn(index: Int) {
    scrollToItem(index)
    androidx.compose.runtime.withFrameNanos { }
    val info = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return
    val viewportCentre = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f
    val delta = info.offset + info.size / 2f - viewportCentre
    if (kotlin.math.abs(delta) > 1f) scrollBy(delta)
}

private fun centredWheelIndex(listState: LazyListState): Int {
    val layoutInfo = listState.layoutInfo
    val visible = layoutInfo.visibleItemsInfo
    if (visible.isEmpty()) return listState.firstVisibleItemIndex
    val centre = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f
    return visible.minBy { distanceFromCentre(it.offset + it.size / 2f, centre) }.index
}

/**
 * Tier of [index] measured in neighbour pitches (variable value widths plus the
 * fixed 8dp gap): 0 = centred, 1 = adjacent, 2+ = outer.
 */
private fun wheelDistanceSlots(listState: LazyListState, index: Int): Int {
    val layoutInfo = listState.layoutInfo
    val item = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return 2
    val centre = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f
    val itemCentre = item.offset + item.size / 2f
    val prev = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index - 1 }
    val next = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index + 1 }
    val pitch = when {
        prev != null && next != null ->
            (next.offset + next.size / 2f - (prev.offset + prev.size / 2f)) / 2f
        next != null -> next.offset + next.size / 2f - itemCentre
        prev != null -> itemCentre - (prev.offset + prev.size / 2f)
        else -> item.size.toFloat()
    }.coerceAtLeast(1f)
    // Round, not truncate: the fixed 8dp gap eats into each pitch, so an
    // adjacent value sits below 1.0 pitches away and must still tier as 1.
    return kotlin.math.round(abs(itemCentre - centre) / pitch).toInt()
}

private fun distanceFromCentre(itemCentre: Float, viewportCentre: Float): Float = abs(itemCentre - viewportCentre)
