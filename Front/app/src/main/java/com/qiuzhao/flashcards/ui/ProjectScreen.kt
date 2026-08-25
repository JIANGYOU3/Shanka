package com.qiuzhao.flashcards.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qiuzhao.flashcards.data.remote.DeckSummary
import com.qiuzhao.flashcards.data.remote.LEGACY_UNASSIGNED_PROJECT_ID
import com.qiuzhao.flashcards.data.remote.ProjectSummary
import com.qiuzhao.flashcards.ui.navigation.AppRoute

/** Figma 494:1447 project root. Project data is derived from the contract layer. */
@Composable
internal fun ProjectScreen(
    projects: List<ProjectSummary>,
    decks: List<DeckSummary>,
    searchQuery: String,
    nav: ScreenNavigator
) {
    val scale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(.75f, 1f)
    val visibleProjects = projects.filter { it.name.contains(searchQuery.trim(), ignoreCase = true) }
    Box(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(start = (16 * scale).dp, top = (88 * scale).dp, end = (16 * scale).dp),
            verticalArrangement = Arrangement.spacedBy((16 * scale).dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy((16 * scale).dp)) {
                ProjectRootAction(
                    modifier = Modifier.weight(1f), icon = "create_new_folder", label = "添加项目",
                    background = AppColors.Blue.primary, content = AppColors.TextIconLight
                ) { nav.navigate(AppRoute.ProjectCreate) }
                ProjectRootAction(
                    modifier = Modifier.weight(1f), icon = "edit_document", label = "资料管理",
                    background = AppColors.Blue.primarySecondary, content = AppColors.Blue.ink
                ) { nav.navigate(AppRoute.MaterialManagement) }
            }
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy((16 * scale).dp),
                contentPadding = PaddingValues(bottom = (180 * scale).dp)
            ) {
                items(visibleProjects, key = { it.id }) { project ->
                    ProjectSummaryCard(project, decks.filter { (it.projectId ?: LEGACY_UNASSIGNED_PROJECT_ID) == project.id }, scale) {
                        // Project detail is introduced as the next project work item.
                        nav.navigate(AppRoute.ProjectDetail(project.id))
                    }
                }
            }
        }
        BottomContentFade(scale, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun ProjectRootAction(
    modifier: Modifier, icon: String, label: String, background: Color, content: Color, onClick: () -> Unit
) = Surface(
    onClick = onClick, color = background, contentColor = content,
    shape = RoundedCornerShape(24.dp), modifier = modifier.height(60.dp)
) {
    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
        MaterialSymbol(icon, label, tint = LocalContentColor.current, size = fixedSp(24f), filled = true)
        Spacer(Modifier.width(8.dp))
        AppText(label, AppTextRole.Label, color = LocalContentColor.current)
    }
}

@Composable
private fun ProjectSummaryCard(project: ProjectSummary, decks: List<DeckSummary>, scale: Float, onClick: () -> Unit) {
    val theme = deckTheme(project)
    val totalCards = decks.sumOf { it.cardCount }
    val masteredCards = decks.sumOf { it.masteredCards }
    val ratio = if (totalCards == 0) 0f else masteredCards.toFloat() / totalCards
    ProjectThemedCard(
        title = project.name,
        count = project.deckCount,
        countLabel = "group",
        progress = ratio,
        theme = theme,
        icon = "heap_snapshot_multiple",
        variant = ProjectThemedCardVariant.TINTED,
        designScale = scale,
        onClick = onClick
    )
}

/** Figma 588:1922. Geometry here deliberately mirrors the supplied creation screen. */
@Composable
internal fun ProjectCreateScreen(viewModel: AppViewModel, nav: ScreenNavigator) {
    val scale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(.75f, 1f)
    val testMode by viewModel.frontendTestMode.collectAsState()
    var name by rememberSaveable { mutableStateOf("") }
    var selectedTheme by rememberSaveable { mutableStateOf("azure") }
    var message by remember { mutableStateOf<String?>(null) }
    Box(Modifier.fillMaxSize()) {
        ScreenTopInformationBar(title = "添加项目", subtitle = null, onBack = nav::goBack)
        // The header is an independent layer in Figma 588:1922. The body must
        // never paint through it while scrolling, even during a fling.
        Box(
            Modifier.fillMaxSize().statusBarsPadding()
                .padding(top = (88 * scale).dp, start = (16 * scale).dp, end = (16 * scale).dp)
                .clipToBounds()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy((16 * scale).dp),
                contentPadding = PaddingValues(bottom = (112 * scale).dp)
            ) {
            item {
                Surface(color = AppColors.Blue.background, shape = RoundedCornerShape((32 * scale).dp), modifier = Modifier.fillMaxWidth().height((72 * scale).dp)) {
                    Box(Modifier.padding(horizontal = (24 * scale).dp), contentAlignment = Alignment.CenterStart) {
                        AppText("可编辑主题色、名称，以及添加文件", AppTextRole.CardSubtitle, color = AppColors.TextIconDark, designScale = scale)
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy((12 * scale).dp)) {
                    AppText("项目主题色", AppTextRole.SectionTitle, color = AppColors.TextIconDark, designScale = scale)
                    Surface(color = AppColors.Blue.background, shape = RoundedCornerShape((32 * scale).dp), modifier = Modifier.fillMaxWidth().height((88 * scale).dp)) {
                        Row(Modifier.fillMaxSize().padding(horizontal = (12 * scale).dp), horizontalArrangement = Arrangement.spacedBy((8 * scale).dp), verticalAlignment = Alignment.CenterVertically) {
                            DeckThemes.forEach { theme ->
                                val selected = selectedTheme == theme.key
                                val choiceWidth by animateDpAsState(if (selected) (120 * scale).dp else (48 * scale).dp, tween(durationMillis = 500, easing = FastOutSlowInEasing), label = "${theme.key} project color width")
                                val choiceHeight by animateDpAsState(if (selected) (64 * scale).dp else (48 * scale).dp, tween(durationMillis = 500, easing = FastOutSlowInEasing), label = "${theme.key} project color height")
                                val cornerRadius by animateDpAsState(if (selected) (24 * scale).dp else 999.dp, tween(durationMillis = 500, easing = FastOutSlowInEasing), label = "${theme.key} project color corner")
                                Surface(
                                    onClick = { selectedTheme = theme.key }, color = theme.primary,
                                    shape = RoundedCornerShape(cornerRadius),
                                    modifier = Modifier.width(choiceWidth).height(choiceHeight)
                                ) {
                                    if (selected) Box(contentAlignment = Alignment.Center) {
                                        MaterialSymbol("check", "已选择${theme.label}", tint = theme.onPrimary, size = fixedSp(24 * scale), filled = true)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy((12 * scale).dp)) {
                    AppText("项目名称", AppTextRole.SectionTitle, color = AppColors.TextIconDark, designScale = scale)
                    Surface(color = AppColors.Blue.background, shape = RoundedCornerShape((32 * scale).dp), modifier = Modifier.fillMaxWidth().height((74 * scale).dp)) {
                    androidx.compose.foundation.text.BasicTextField(
                        value = name, onValueChange = { name = it }, singleLine = true,
                        textStyle = appInputTextStyle(AppTextRole.CardTitle, scale, PageForegroundColor()),
                        visualTransformation = rememberBilingualInputTransformation(AppTextRole.CardTitle, scale),
                        modifier = Modifier.fillMaxSize().padding(horizontal = (24 * scale).dp),
                        decorationBox = { input -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                            if (name.isBlank()) AppText("例如：世界现代设计史", AppTextRole.CardTitle, color = AppColors.TextIconDark.copy(alpha = .55f), designScale = scale)
                            input()
                        } }
                    ) }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy((12 * scale).dp)) {
                    AppText("添加学习资料", AppTextRole.SectionTitle, color = AppColors.TextIconDark, designScale = scale)
                    LearningMaterialsSection(scale)
                }
            }
                message?.let { item { AppText(it, AppTextRole.CardSubtitle, color = AppColors.WarningStrong, designScale = scale) } }
            }
        }
        BottomContentFade(scale, Modifier.align(Alignment.BottomCenter))
        Surface(
            onClick = {
                viewModel.createFrontendTestProject(name, selectedTheme) { error ->
                    message = error
                    if (error == null) nav.goBack()
                }
            },
            color = if (testMode) AppColors.Blue.primary else AppColors.Blue.primarySecondary, contentColor = AppColors.TextIconLight,
            shape = RoundedCornerShape((24 * scale).dp),
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = (16 * scale).dp, vertical = (16 * scale).dp).fillMaxWidth().height((60 * scale).dp).zIndex(1f)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxSize()) {
                MaterialSymbol("list_alt_check", null, tint = LocalContentColor.current, size = fixedSp(24 * scale), filled = true)
                Spacer(Modifier.width((8 * scale).dp))
                AppText("完成设置", AppTextRole.Label, color = LocalContentColor.current, designScale = scale)
            }
        }
    }
}

/** Figma 595:2321: file and text material blocks shown inside project setup. */
@Composable
private fun LearningMaterialsSection(scale: Float) = Column(verticalArrangement = Arrangement.spacedBy((12 * scale).dp)) {
    MaterialImportGroup(
        actionTitle = "选择文件", actionSubtitle = "PDF/ .txt/ .md格式", actionIcon = "picture_as_pdf",
        helpText = "已添加的文件，右滑卡片可删除", sampleFiles = listOf("文件名称" to "pdf", "文件名称" to "md"), scale = scale
    )
    MaterialImportGroup(
        actionTitle = "输入文本", actionSubtitle = "粘贴文本或手动输入", actionIcon = "file_copy",
        helpText = "右滑卡片可进行编辑与删除", sampleFiles = emptyList(), scale = scale
    )
}

@Composable
private fun MaterialImportGroup(
    actionTitle: String, actionSubtitle: String, actionIcon: String, helpText: String,
    sampleFiles: List<Pair<String, String>>, scale: Float
) = Surface(color = AppColors.Blue.background, shape = RoundedCornerShape((32 * scale).dp), modifier = Modifier.fillMaxWidth()) {
    Column(Modifier.padding((12 * scale).dp), verticalArrangement = Arrangement.spacedBy((16 * scale).dp)) {
        Surface(color = AppColors.Blue.primary, contentColor = AppColors.TextIconLight, shape = RoundedCornerShape((32 * scale).dp), modifier = Modifier.fillMaxWidth().height((80 * scale).dp)) {
            Row(Modifier.fillMaxSize().padding((12 * scale).dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = AppColors.Blue.background, shape = RoundedCornerShape(999.dp), modifier = Modifier.size((56 * scale).dp)) { Box(contentAlignment = Alignment.Center) { MaterialSymbol(actionIcon, null, tint = AppColors.Blue.ink, size = fixedSp(24 * scale), filled = true) } }
                Spacer(Modifier.width((16 * scale).dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy((4 * scale).dp)) {
                    AppText(actionTitle, AppTextRole.CardTitle, color = LocalContentColor.current, designScale = scale)
                    AppText(actionSubtitle, AppTextRole.CardSubtitle, color = AppColors.TextIconLight.copy(alpha = .75f), designScale = scale)
                }
                MaterialSymbol("arrow_forward", actionTitle, tint = LocalContentColor.current, size = fixedSp(24 * scale), filled = true)
            }
        }
        Surface(color = AppColors.Card, shape = RoundedCornerShape((32 * scale).dp), modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.padding((24 * scale).dp), contentAlignment = Alignment.CenterStart) { AppText(helpText, AppTextRole.Supporting, color = AppColors.TextIconDark, designScale = scale) }
        }
        if (sampleFiles.isNotEmpty()) sampleFiles.forEach { (name, format) -> MaterialFileSample(name, format, scale) } else {
            TextMaterialSample("文本资料（标题）", scale)
            TextMaterialSample("文本资料（标题）", scale)
        }
    }
}

@Composable
private fun MaterialFileSample(name: String, format: String, scale: Float) = Surface(
    color = AppColors.Blue.surface, shape = RoundedCornerShape((32 * scale).dp), modifier = Modifier.fillMaxWidth().height((104 * scale).dp)
) {
    Row(Modifier.fillMaxSize().padding((24 * scale).dp), horizontalArrangement = Arrangement.spacedBy((16 * scale).dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(color = AppColors.Blue.primary, shape = RoundedCornerShape((16 * scale).dp), modifier = Modifier.size((56 * scale).dp)) { Box(contentAlignment = Alignment.Center) { MaterialSymbol("picture_as_pdf", null, tint = AppColors.TextIconLight, size = fixedSp(24 * scale), filled = true) } }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy((4 * scale).dp)) { AppText(name, AppTextRole.CardTitle, color = AppColors.TextIconDark, designScale = scale); AppText("xxxx 页", AppTextRole.CardSubtitle, color = AppColors.TextIconDark.copy(alpha = .625f), designScale = scale) }
        Surface(color = AppColors.Blue.primary, shape = RoundedCornerShape((20 * scale).dp)) { Box(Modifier.padding(horizontal = (16 * scale).dp, vertical = (8 * scale).dp), contentAlignment = Alignment.Center) { AppText(format, AppTextRole.CardSubtitle, color = AppColors.TextIconLight, designScale = scale) } }
    }
}

@Composable
private fun TextMaterialSample(title: String, scale: Float) = Surface(color = AppColors.Card, shape = RoundedCornerShape((32 * scale).dp), modifier = Modifier.fillMaxWidth()) {
    Column(Modifier.padding((12 * scale).dp), verticalArrangement = Arrangement.spacedBy((10 * scale).dp)) {
        Surface(color = AppColors.Blue.surface, shape = RoundedCornerShape((32 * scale).dp), modifier = Modifier.fillMaxWidth()) { AppText(title, AppTextRole.Body, modifier = Modifier.padding((24 * scale).dp), color = AppColors.TextIconDark, designScale = scale) }
        Surface(color = AppColors.Blue.background, shape = RoundedCornerShape((24 * scale).dp), modifier = Modifier.fillMaxWidth()) { AppText("此处最多显示两行可以吗。此处最多显示两行。超出省略号", AppTextRole.Body, modifier = Modifier.padding((24 * scale).dp), color = AppColors.TextIconDark.copy(alpha = .625f), designScale = scale, maxLines = 2, overflow = TextOverflow.Ellipsis) }
    }
}
