package com.qiuzhao.flashcards.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.qiuzhao.flashcards.ui.navigation.AppNavigator
import com.qiuzhao.flashcards.ui.navigation.AppRoute
import kotlinx.coroutines.launch

/**
 * Figma 977:4937 — the one plan-configuration page. Every user manages
 * 选择项目 / 选择卡组 / 每日目标 together: a configured user arrives with the real
 * plan's project, decks and goals pre-filled and saves them through the one
 * atomic PUT /study/plan, while a first-time user starts with nothing picked.
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
    var selectedProjectId by remember { mutableStateOf<String?>(null) }
    var selectedDeckIds by remember { mutableStateOf<Set<String>>(emptySet()) }

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
                // Restore the real current selection; a fresh user picks from scratch.
                selectedProjectId = plan.currentProjectId
                selectedDeckIds = plan.selectedDeckIds.toSet()
            }
            seeded = true
        }
    }
    // Switching project can only keep deck selections that belong to it.
    LaunchedEffect(selectedProjectId, decks) {
        if (selectedProjectId != null) {
            selectedDeckIds = selectedDeckIds
                .filter { id -> decks.any { it.id == id && it.projectId == selectedProjectId } }
                .toSet()
        }
    }

    val configured = plan.configured
    val project = projects.firstOrNull { it.id == selectedProjectId }
    val projectDecks = decks.filter { it.projectId == selectedProjectId }
    val independentDecks = decks.filter { it.projectId == null && it.cardCount > 0 }
    val validGoals = newGoal in 0..200 && reviewGoal in 0..200 && newGoal % 10 == 0 &&
        reviewGoal % 10 == 0 && newGoal + reviewGoal > 0
    val hasLearnableSelection = selectedDeckIds.any { id ->
        projectDecks.any { it.id == id && it.cardCount > 0 }
    }
    val canSave = studyGoalCanSave(
        seeded = seeded,
        saving = plan.saving,
        validGoals = validGoals,
        hasProject = project != null,
        hasLearnableSelection = hasLearnableSelection,
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
                        PlanSectionCard("选择项目", "folder_open") {
                            if (projects.isEmpty()) {
                                PlanSectionHint("先创建项目并导入资料")
                            } else {
                                projects.forEach { item ->
                                    PlanChoiceRow(
                                        title = item.name,
                                        supporting = null,
                                        selected = item.id == selectedProjectId,
                                        onClick = { selectedProjectId = item.id },
                                    )
                                }
                            }
                        }
                        PlanSectionCard("选择卡组", "list_alt_check") {
                            when {
                                project == null -> PlanSectionHint("先选择一个项目")
                                !projectDecks.any { it.cardCount > 0 } ->
                                    PlanSectionHint("先导入资料并生成卡组")
                                else -> projectDecks.forEach { deck ->
                                    PlanChoiceRow(
                                        title = deck.name,
                                        supporting = "${deck.cardCount} 张卡片 · 待巩固 ${deck.dueCount}",
                                        selected = deck.id in selectedDeckIds,
                                        onClick = {
                                            selectedDeckIds = if (deck.id in selectedDeckIds) {
                                                selectedDeckIds - deck.id
                                            } else {
                                                selectedDeckIds + deck.id
                                            }
                                        },
                                    )
                                }
                            }
                            if (project != null && independentDecks.isNotEmpty()) {
                                AppText(
                                    "独立卡组", AppTextRole.Label,
                                    color = AppColors.TextIconDark.copy(alpha = .6f)
                                )
                                independentDecks.forEach { deck ->
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            AppText(deck.name, AppTextRole.CardTitle, color = AppColors.TextIconDark)
                                            AppText(
                                                "${deck.cardCount} 张卡片", AppTextRole.Supporting,
                                                color = AppColors.TextIconDark.copy(alpha = .6f)
                                            )
                                        }
                                        TextButton(onClick = {
                                            viewModel.attachDeckToProject(project.id, deck.id)
                                        }) { Text("归入当前项目") }
                                    }
                                }
                            }
                        }
                        PlanSectionCard("每日目标", "edit_calendar") {
                            GoalRow("每日新学", newGoal) { newGoal = it }
                            GoalRow("每日复习", reviewGoal) { reviewGoal = it }
                            if (!validGoals) {
                                AppText(
                                    "目标须为 0～200 的 10 的倍数，且不能同时为 0",
                                    AppTextRole.Supporting, color = MaterialTheme.colorScheme.error
                                )
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
                        currentProjectId = selectedProjectId.orEmpty(),
                        selectedDeckIds = selectedDeckIds.toList(),
                        dailyNewGoal = newGoal,
                        dailyReviewGoal = reviewGoal,
                    ) {
                        if (configured) nav.popBackStack() else nav.navigate(AppRoute.StudyToday)
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
                .height(60.dp)
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
 * form needs a picked project and at least one learnable deck, plus valid goals.
 */
internal fun studyGoalCanSave(
    seeded: Boolean,
    saving: Boolean,
    validGoals: Boolean,
    hasProject: Boolean,
    hasLearnableSelection: Boolean,
): Boolean = seeded && !saving && validGoals && hasProject && hasLearnableSelection

/** Figma 977:4937 card language: #EEF4FA r36, 20dp padding, 16dp item gap. */
@Composable
private fun PlanSectionCard(title: String, icon: String, content: @Composable ColumnScope.() -> Unit) {
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

@Composable
private fun PlanSectionHint(text: String) {
    AppText(
        text, AppTextRole.Body,
        color = AppColors.TextIconDark.copy(alpha = .6f),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    )
}

/**
 * One selectable row of the first-configuration cards. Selection state follows
 * the shared Figma navigation language: #B0D7FF fill with #003C7A ink for the
 * picked row, white fill with dark ink otherwise.
 */
@Composable
private fun PlanChoiceRow(title: String, supporting: String?, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (selected) AppColors.Blue.primarySecondary else AppColors.Card,
        contentColor = if (selected) AppColors.Blue.ink else AppColors.TextIconDark,
        shape = RoundedCornerShape(AppButtonShapeRadius.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selected) {
                MaterialSymbol("check_circle", null, tint = AppColors.Blue.ink, size = fixedSp(20f), filled = true)
            }
            Column(Modifier.weight(1f)) {
                AppText(title, AppTextRole.CardTitle)
                supporting?.let {
                    AppText(it, AppTextRole.Supporting, color = AppColors.TextIconDark.copy(alpha = .6f))
                }
            }
        }
    }
}

/** Figma 977:4937's goal row: a white label pill beside the 72dp value wheel. */
@Composable
private fun GoalRow(label: String, value: Int, onValueChange: (Int) -> Unit) {
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
 * Three-digit values (100+) would overflow the 64dp slot at tier sizes, so the
 * font shrinks proportionally while keeping the tier hierarchy.
 */
internal fun wheelFontScale(value: Int): Float = if (value >= 100) 0.68f else 1f

/**
 * The 72dp horizontal wheel: values snap to the centre slot; the centred value
 * renders 48sp black, its neighbours 40sp at 75% and the rest 24sp at 45%,
 * matching Figma's Metric/Large / Medium / Small tiers.
 */
@Composable
private fun GoalValueWheel(value: Int, onValueChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // The wheel only reports settle changes after the initial value scroll has
    // positioned the track, so the first composition can't emit a stray 0.
    var wheelReady by remember { mutableStateOf(false) }
    BoxWithConstraints(modifier.clip(RoundedCornerShape(AppButtonShapeRadius.dp)).background(AppColors.Card)) {
        val slotWidth = 64.dp
        val density = LocalDensity.current
        val slotWidthPx = with(density) { slotWidth.toPx() }
        LaunchedEffect(value, constraints.maxWidth) {
            listState.centreWheelOn(goalWheelValues.indexOf(value).coerceAtLeast(0))
            wheelReady = true
        }
        LazyRow(
            state = listState,
            flingBehavior = rememberSnapFlingBehavior(lazyListState = listState),
            contentPadding = PaddingValues(horizontal = (maxWidth - slotWidth) / 2),
            modifier = Modifier.fillMaxSize()
        ) {
            items(goalWheelValues.size) { index ->
                val itemValue = goalWheelValues[index]
                val distanceSlots = wheelDistanceSlots(listState, slotWidthPx, index)
                Box(
                    Modifier.width(slotWidth).fillMaxHeight().clickable {
                        scope.launch { listState.animateScrollToItem(index) }
                    },
                    contentAlignment = Alignment.Center
                ) {
                    val spec = when {
                        distanceSlots == 0 -> WheelTier(48f, 0f, 1f, AppFonts.GoogleSansFlexBold)
                        distanceSlots <= 1 -> WheelTier(40f, -0.6f, .75f, AppFonts.GoogleSansFlexBold)
                        else -> WheelTier(24f, 0.6f, .45f, AppFonts.GoogleSansFlexExtraBold)
                    }
                    val fontScale = wheelFontScale(itemValue)
                    Text(
                        itemValue.toString(),
                        color = AppColors.TextIconDark.copy(alpha = spec.alpha),
                        fontFamily = spec.font,
                        fontWeight = FontWeight.Normal,
                        fontSize = fixedSp(spec.size * fontScale),
                        lineHeight = fixedSp(spec.size * fontScale),
                        letterSpacing = fixedSp(spec.letterSpacing),
                        style = figmaCardTextStyle(),
                        maxLines = 1
                    )
                }
            }
        }
        // A scroll settle is the moment the centred value becomes the form value.
        LaunchedEffect(listState.isScrollInProgress) {
            if (!listState.isScrollInProgress && wheelReady) {
                val centred = centredWheelIndex(listState, slotWidthPx)
                val resolved = goalWheelValues.getOrNull(centred) ?: return@LaunchedEffect
                if (resolved != value) onValueChange(resolved)
            }
        }
    }
}

private data class WheelTier(val size: Float, val letterSpacing: Float, val alpha: Float, val font: androidx.compose.ui.text.font.FontFamily)

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

private fun centredWheelIndex(listState: LazyListState, slotWidthPx: Float): Int {
    val layoutInfo = listState.layoutInfo
    val visible = layoutInfo.visibleItemsInfo
    if (visible.isEmpty()) return listState.firstVisibleItemIndex
    val centre = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f
    return visible.minBy { distanceFromCentre(it.offset + it.size / 2f, centre) }.index
}

private fun wheelDistanceSlots(listState: LazyListState, slotWidthPx: Float, index: Int): Int {
    val layoutInfo = listState.layoutInfo
    val item = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return 3
    val centre = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f
    val distance = distanceFromCentre(item.offset + item.size / 2f, centre)
    return (distance / slotWidthPx).toInt()
}

private fun distanceFromCentre(itemCentre: Float, viewportCentre: Float): Float = kotlin.math.abs(itemCentre - viewportCentre)
