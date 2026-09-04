package com.qiuzhao.flashcards.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qiuzhao.flashcards.data.remote.DeckSummary
import com.qiuzhao.flashcards.data.remote.ProjectSummary
import com.qiuzhao.flashcards.ui.navigation.AppNavigator
import com.qiuzhao.flashcards.ui.navigation.AppRoute

/**
 * The only new page in the learning-loop refresh. It reuses the existing app bar, cards, surfaces
 * and buttons; all choices stay local until one atomic PUT /study/plan succeeds.
 */
@Composable
internal fun StudyPlanScreen(viewModel: AppViewModel, nav: AppNavigator) {
    val projects by viewModel.projects.collectAsState()
    val decks by viewModel.decks.collectAsState()
    val plan by viewModel.studyPlan.collectAsState()
    val uiMessage by viewModel.uiMessage.collectAsState()
    var currentProjectId by remember { mutableStateOf<String?>(null) }
    var selectedDeckIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var dailyNewGoal by remember { mutableIntStateOf(10) }
    var dailyReviewGoal by remember { mutableIntStateOf(40) }
    var seeded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.refreshStudyPlan() }
    // Projects and the plan are loaded independently by refreshAll; include both in the key so a
    // plan response that wins the race cannot leave the form with a null current project.
    LaunchedEffect(plan.loaded, projects) {
        // The two bootstrap requests are independent.  If the plan wins the race, wait for the
        // project list before marking the form seeded; otherwise a valid first project would be
        // left unselected until the user manually taps it.
        if (plan.loaded && !seeded && (plan.currentProjectId != null || projects.isNotEmpty())) {
            currentProjectId = plan.currentProjectId ?: projects.firstOrNull()?.id
            selectedDeckIds = plan.selectedDeckIds.toSet()
            dailyNewGoal = plan.dailyNewGoal
            dailyReviewGoal = plan.dailyReviewGoal
            seeded = true
        }
    }
    LaunchedEffect(currentProjectId, decks) {
        // Do not discard server selections while the deck bootstrap is still in flight.
        if (currentProjectId != null && decks.isNotEmpty()) {
            selectedDeckIds = selectedDeckIds.filter { id -> decks.any { it.id == id && it.projectId == currentProjectId } }.toSet()
        }
    }

    val project = projects.firstOrNull { it.id == currentProjectId }
    val projectDecks = decks.filter { it.projectId == currentProjectId }
    val independentDecks = decks.filter { it.projectId == null && it.cardCount > 0 }
    val hasLearnableDeck = projectDecks.any { it.cardCount > 0 }
    val validGoals = dailyNewGoal in 0..200 && dailyReviewGoal in 0..200 &&
        dailyNewGoal % 10 == 0 && dailyReviewGoal % 10 == 0 && dailyNewGoal + dailyReviewGoal > 0
    val canSave = plan.loaded && project != null && hasLearnableDeck &&
        selectedDeckIds.any { id -> projectDecks.any { it.id == id && it.cardCount > 0 } } && validGoals && !plan.saving

    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        AppBar("学习计划", nav::popBackStack)
        if (!plan.loaded) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard("当前项目") {
                    if (projects.isEmpty()) {
                        EmptyPlanMessage()
                    } else {
                        projects.forEach { item ->
                            ProjectChoice(item, selected = item.id == currentProjectId) { currentProjectId = item.id }
                        }
                    }
                }
            }
            item {
                SectionCard("可学习卡组") {
                    if (project == null || !hasLearnableDeck) {
                        EmptyPlanMessage()
                    } else {
                        projectDecks.forEach { deck ->
                            DeckChoice(deck, selected = deck.id in selectedDeckIds) {
                                selectedDeckIds = if (deck.id in selectedDeckIds) selectedDeckIds - deck.id else selectedDeckIds + deck.id
                            }
                        }
                    }
                    if (project != null && independentDecks.isNotEmpty()) {
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        AppText("独立卡组", AppTextRole.Label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        independentDecks.forEach { deck ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    AppText(deck.name, AppTextRole.Body)
                                    AppText("${deck.cardCount} 张卡片", AppTextRole.Supporting, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                TextButton(onClick = { viewModel.attachDeckToProject(project.id, deck.id) }) { Text("归入当前项目") }
                            }
                        }
                    }
                }
            }
            item {
                SectionCard("每日目标") {
                    QuotaStepper("每日新学", dailyNewGoal, onChange = { dailyNewGoal = it })
                    QuotaStepper("每日巩固", dailyReviewGoal, onChange = { dailyReviewGoal = it })
                    if (!validGoals) {
                        AppText("目标须为 0～200 的 10 的倍数，且不能同时为 0", AppTextRole.Supporting, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            uiMessage?.let { message ->
                item { AppText(message, AppTextRole.Supporting, color = MaterialTheme.colorScheme.error) }
            }
            item {
                Button(
                    onClick = {
                        viewModel.clearUiMessage()
                        viewModel.saveStudyPlan(
                            currentProjectId = currentProjectId.orEmpty(),
                            selectedDeckIds = selectedDeckIds.toList(),
                            dailyNewGoal = dailyNewGoal,
                            dailyReviewGoal = dailyReviewGoal,
                        ) { nav.navigate(AppRoute.StudyToday) }
                    },
                    enabled = canSave,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text(if (plan.configured) "保存并继续学习" else "保存并开始学习")
                }
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppText(title, AppTextRole.SectionTitle)
            content()
        }
    }
}

@Composable
private fun ProjectChoice(project: ProjectSummary, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        AppText(project.name, AppTextRole.Body)
    }
}

@Composable
private fun DeckChoice(deck: DeckSummary, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Checkbox(checked = selected, onCheckedChange = { onClick() })
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            AppText(deck.name, AppTextRole.Body)
            AppText("${deck.cardCount} 张卡片 · 待巩固 ${deck.dueCount}", AppTextRole.Supporting, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun QuotaStepper(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        AppText(label, AppTextRole.Body, modifier = Modifier.weight(1f))
        TextButton(onClick = { onChange((value - 10).coerceAtLeast(0)) }, enabled = value > 0) { Text("−") }
        Text(value.toString(), fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
        TextButton(onClick = { onChange((value + 10).coerceAtMost(200)) }, enabled = value < 200) { Text("+") }
    }
}

@Composable
private fun EmptyPlanMessage() {
    AppText("先导入资料并生成卡组", AppTextRole.Body, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
}
