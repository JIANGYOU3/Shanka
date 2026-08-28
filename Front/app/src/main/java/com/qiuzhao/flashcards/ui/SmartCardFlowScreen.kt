package com.qiuzhao.flashcards.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.qiuzhao.flashcards.data.CardDraft
import com.qiuzhao.flashcards.data.remote.ProjectSummary
import com.qiuzhao.flashcards.ui.navigation.AppRoute
import com.qiuzhao.flashcards.ui.motion.AppMotion
import kotlinx.coroutines.delay

/**
 * Figma 849:6541 "正在生成". A full-page generation indicator that reuses the
 * parse-progress card, then creates a frontend-test deck with the sample cards
 * and drops into the existing generated card list so the swipe/edit workflow
 * is exercised end to end.
 */
@Composable
internal fun SmartCardGeneratingScreen(project: ProjectSummary, nav: ScreenNavigator, viewModel: AppViewModel) {
    val scale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(.75f, 1f)
    val theme = deckTheme(project)
    val sampleDrafts = remember {
        listOf(
            CardDraft("此处为问题样板？", "此处为答案样板"),
            CardDraft("为什么要保留可追踪的执行记录？", "它能帮助定位失败、复现问题并评估改进效果。"),
            CardDraft("设计史的关键转折点是什么？", "包豪斯、乌尔姆与后现代主义。"),
            CardDraft("如何判断一个材料适合做什么？", "依据强度、工艺与使用场景综合判断。")
        )
    }
    LaunchedEffect(Unit) {
        delay(2_200)
        viewModel.importDeck(
            "AI 生成卡片组", sampleDrafts,
            { deckId -> nav.replaceTop(AppRoute.CardList(deckId)) },
            themeKey = project.themeKey
        )
    }
    Box(Modifier.fillMaxSize().background(AppColors.BaseBackground)) {
        ScreenTopInformationBar(
            title = "正在生成", subtitle = null, onBack = nav::goBack,
            backContainer = theme.cardPanel, titleColor = theme.text
        )
        Surface(
            color = theme.cardPanel,
            shape = RoundedCornerShape((AppShapeRadius * scale).dp),
            modifier = Modifier.fillMaxWidth().statusBarsPadding()
                .padding(start = (16 * scale).dp, top = (88 * scale).dp, end = (16 * scale).dp)
                .height((209 * scale).dp)
        ) {
            Column(
                Modifier.fillMaxSize().padding((24 * scale).dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                GenerationProgressRing(color = theme.primary, trackColor = theme.secondary, designScale = scale)
                Spacer(Modifier.height((20 * scale).dp))
                AppText("正在生成卡片", AppTextRole.SectionTitle, color = theme.text, designScale = scale, maxLines = 1)
                Spacer(Modifier.height((8 * scale).dp))
                AppText("稍安勿躁", AppTextRole.CardSubtitle, color = theme.text.copy(alpha = .55f), designScale = scale, maxLines = 1)
            }
        }
        BottomContentFade(scale, Modifier.align(Alignment.BottomCenter), color = AppColors.BaseBackground)
        Surface(
            onClick = nav::goBack,
            color = theme.primary, contentColor = theme.onPrimary,
            shape = RoundedCornerShape((24 * scale).dp),
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                .padding(horizontal = (16 * scale).dp, vertical = (16 * scale).dp)
                .fillMaxWidth().height((60 * scale).dp).zIndex(1f)
        ) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                MaterialSymbol("pause_circle", null, tint = LocalContentColor.current, size = fixedSp(24 * scale), filled = true)
                Spacer(Modifier.width((8 * scale).dp))
                AppText("暂停生成", AppTextRole.Label, color = LocalContentColor.current, designScale = scale, maxLines = 1)
            }
        }
    }
}

/**
 * Figma 836:5895 / 839:6220 "智能制卡". After the user picks files and taps
 * "下一步" on generation settings, an AI parse dialog (Figma 856:6605) runs
 * through its three stages and then reveals the parsed chapter list. The page
 * and the dialog both follow the owning project's colour family.
 */
@Composable
internal fun SmartCardChapterScreen(project: ProjectSummary, nav: ScreenNavigator, viewModel: AppViewModel) {
    val scale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(.75f, 1f)
    val theme = deckTheme(project)
    val stages = listOf("已识别文件", "正在整理内容", "正在检查结果")
    var stageIndex by remember { mutableIntStateOf(0) }
    var parsing by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        stages.indices.forEach { index -> stageIndex = index; delay(900) }
        parsing = false
    }
    val chapters = remember {
        listOf(
            SmartChapter("ch1", "第一章 导论", "1-18 页"),
            SmartChapter("ch2", "第二章 设计史", "19-40 页"),
            SmartChapter("ch3", "第三章 流派", "41-66 页"),
            SmartChapter("ch4", "第四章 材料", "67-90 页"),
            SmartChapter("ch5", "第五章 应用", "91-118 页")
        )
    }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }

    Box(Modifier.fillMaxSize().background(AppColors.BaseBackground)) {
        ScreenTopInformationBar(
            title = "智能制卡", subtitle = null, onBack = nav::goBack,
            backContainer = theme.cardPanel, titleColor = theme.text
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding()
                .padding(start = (16 * scale).dp, top = (88 * scale).dp, end = (16 * scale).dp)
                .clip(RoundedCornerShape((AppScrollableContentClipRadius * scale).dp)),
            contentPadding = PaddingValues(bottom = (fixedBottomControlScrollTail(bottomOffset = 16) * scale).dp),
            verticalArrangement = Arrangement.spacedBy((16 * scale).dp)
        ) {
            item {
                SmartChapterIntroCard(theme, scale)
            }
            item {
                AppText("章节", AppTextRole.SectionTitle, modifier = Modifier.padding(start = (8 * scale).dp), color = theme.text, designScale = scale)
            }
            items(chapters, key = { it.id }) { chapter ->
                SmartChapterCard(chapter, selected = chapter.id in selectedIds, theme, scale) {
                    selectedIds = if (it in selectedIds) selectedIds - it else selectedIds + it
                }
            }
        }
        BottomContentFade(scale, Modifier.align(Alignment.BottomCenter), color = AppColors.BaseBackground)
        Surface(
            onClick = { nav.navigate(AppRoute.SmartCardPreview(project.id)) },
            color = theme.primary, contentColor = theme.onPrimary,
            shape = RoundedCornerShape((24 * scale).dp),
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                .padding(horizontal = (16 * scale).dp, vertical = (16 * scale).dp)
                .fillMaxWidth().height((60 * scale).dp).zIndex(1f)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AppText("下一步", AppTextRole.Label, color = LocalContentColor.current, designScale = scale, maxLines = 1)
            }
        }
        if (parsing) {
            SmartParseDialog(stages[stageIndex], theme, scale)
        }
    }
}

private data class SmartChapter(val id: String, val title: String, val pages: String)

/** Figma 839:6220 import note: family Surface pill, 24dp clip. */
@Composable
private fun SmartChapterIntroCard(theme: DeckTheme, scale: Float) = HintBox(
    text = "根据已选文件选择要制作闪卡的章节。",
    parentIsWhite = true,
    theme = theme,
    designScale = scale
)

/**
 * Figma 839:6220 chapter row. Unselected lifts to the family Background with a
 * Primary icon tile; selected turns green (check) following the shared
 * material-card hierarchy.
 */
@Composable
private fun SmartChapterCard(
    chapter: SmartChapter,
    selected: Boolean,
    theme: DeckTheme,
    scale: Float,
    onToggle: (String) -> Unit
) = Surface(
    onClick = { onToggle(chapter.id) },
    color = if (selected) AppColors.Green.background else theme.background,
    shape = RoundedCornerShape((AppShapeRadius * scale).dp),
    modifier = Modifier.fillMaxWidth()
) {
    Row(Modifier.fillMaxSize().padding((16 * scale).dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            color = if (selected) AppColors.Green.primary else theme.primary,
            shape = RoundedCornerShape((24 * scale).dp),
            modifier = Modifier.size((56 * scale).dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                MaterialSymbol(
                    if (selected) "check_circle" else "menu_book", null,
                    tint = if (selected) AppColors.Green.background else theme.background,
                    size = fixedSp(24 * scale), filled = true
                )
            }
        }
        Spacer(Modifier.width((16 * scale).dp))
        Column(Modifier.weight(1f).height((56 * scale).dp), verticalArrangement = Arrangement.SpaceBetween) {
            AppText(chapter.title, AppTextRole.CardTitle, color = theme.text, designScale = scale, maxLines = 1, overflow = TextOverflow.Ellipsis)
            AppText(chapter.pages, AppTextRole.CardSubtitle, color = theme.text.copy(alpha = .5f), designScale = scale)
        }
    }
}

/** Figma 856:6605 themed parse progress dialog (three advancing stages). */
@Composable
private fun SmartParseDialog(stage: String, theme: DeckTheme, scale: Float) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .42f)), contentAlignment = Alignment.Center) {
        Surface(
            color = theme.background,
            shape = RoundedCornerShape((AppShapeRadius * scale).dp),
            modifier = Modifier.width((331 * scale).dp).height((209 * scale).dp)
        ) {
            Column(
                Modifier.fillMaxSize().padding((24 * scale).dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                GenerationProgressRing(color = theme.primary, trackColor = theme.secondary, designScale = scale)
                Spacer(Modifier.height((20 * scale).dp))
                AppText("正在解析文件内容", AppTextRole.SectionTitle, color = theme.text, designScale = scale, maxLines = 1)
                Spacer(Modifier.height((8 * scale).dp))
                AppText(stage, AppTextRole.CardSubtitle, color = theme.text.copy(alpha = .55f), designScale = scale, maxLines = 1)
            }
        }
    }
}

/**
 * Figma 835:5784 "卡片预览". Three generated sample flashcards are shown as
 * flip cards (question front, answer back); the difficulty chip and the two
 * fixed actions reuse the shared CardListActionButton.
 */
@Composable
internal fun SmartCardPreviewScreen(project: ProjectSummary, nav: ScreenNavigator, viewModel: AppViewModel) {
    val scale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(.75f, 1f)
    val theme = deckTheme(project)
    val samples = listOf(
        SmartPreviewSample("此处为问题样板此处为问题样板此处为问题样板此处为问题样板", "此处为答案样板此处为答案样板此处为答案样板此处为答案样板", "基础记忆"),
        SmartPreviewSample("此处为问题样板此处为问题样板此处为问题样板此处为问题样板", "此处为答案样板此处为答案样板此处为答案样板此处为答案样板", "理解分析"),
        SmartPreviewSample("此处为问题样板此处为问题样板此处为问题样板此处为问题样板", "此处为答案样板此处为答案样板此处为答案样板此处为答案样板", "综合应用")
    )
    Box(Modifier.fillMaxSize().background(AppColors.BaseBackground)) {
        ScreenTopInformationBar(
            title = "卡片预览", subtitle = null, onBack = nav::goBack,
            backContainer = theme.cardPanel, titleColor = theme.text
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding()
                .padding(start = (16 * scale).dp, top = (88 * scale).dp, end = (16 * scale).dp)
                .clip(RoundedCornerShape((AppScrollableContentClipRadius * scale).dp)),
            contentPadding = PaddingValues(bottom = (fixedBottomControlScrollTail(bottomOffset = 16) * scale).dp),
            verticalArrangement = Arrangement.spacedBy((16 * scale).dp)
        ) {
            item {
                HintBox(
                    text = "点击卡片可以查看答案。\n卡片左滑可进行编辑与删除。",
                    parentIsWhite = true,
                    theme = theme,
                    designScale = scale
                )
            }
            items(samples) { sample ->
                SmartPreviewFlipCard(sample, theme, scale)
            }
        }
        BottomContentFade(scale, Modifier.align(Alignment.BottomCenter), color = AppColors.BaseBackground)
        Row(
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                .padding(horizontal = (16 * scale).dp, vertical = (16 * scale).dp)
                .fillMaxWidth().height((60 * scale).dp).zIndex(1f),
            horizontalArrangement = Arrangement.spacedBy((12 * scale).dp)
        ) {
            CardListActionButton("返回调整", "cycle", false, Modifier.weight(1f), scale, theme, onClick = nav::goBack)
            CardListActionButton("开始生成", "play_circle", true, Modifier.weight(1f), scale, theme) {
                nav.navigate(AppRoute.SmartCardGenerating(project.id))
            }
        }
    }
}

private data class SmartPreviewSample(val question: String, val answer: String, val difficulty: String)

@Composable
private fun SmartPreviewFlipCard(sample: SmartPreviewSample, theme: DeckTheme, scale: Float) {
    var flipped by remember(sample.question) { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (flipped) 180f else 0f, animationSpec = AppMotion.emphasisSpring(), label = "smart preview flip")
    val shape = RoundedCornerShape((AppShapeRadius * scale).dp)
    val density = LocalDensity.current.density
    Box(
        Modifier.fillMaxWidth().height((208 * scale).dp).clip(shape)
            .clickable(interactionSource = remember(sample.question) { MutableInteractionSource() }, indication = null) { flipped = !flipped }
    ) {
        SmartPreviewFace(sample, theme, answer = false, rotation = rotation, alpha = if (rotation <= 90f) 1f else 0f, shape, density, scale)
        SmartPreviewFace(sample, theme, answer = true, rotation = rotation, alpha = if (rotation > 90f) 1f else 0f, shape, density, scale)
    }
}

@Composable
private fun SmartPreviewFace(
    sample: SmartPreviewSample,
    theme: DeckTheme,
    answer: Boolean,
    rotation: Float,
    alpha: Float,
    shape: RoundedCornerShape,
    density: Float,
    scale: Float
) {
    val badge = smartDifficultyBadge(sample.difficulty, theme)
    Surface(
        color = if (answer) theme.cardPanel else theme.strongText,
        shape = shape,
        modifier = Modifier.fillMaxSize().graphicsLayer {
            rotationY = if (answer) rotation - 180f else rotation
            transformOrigin = TransformOrigin.Center
            cameraDistance = 20f * density
            this.alpha = alpha
        }
    ) {
        Column(Modifier.fillMaxSize().padding((24 * scale).dp), verticalArrangement = Arrangement.spacedBy((16 * scale).dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy((8 * scale).dp), verticalAlignment = Alignment.CenterVertically) {
                    MaterialSymbol(if (answer) "wb_incandescent" else "book_5", null, tint = if (answer) theme.primary else AppColors.TextIconLight, size = fixedSp(24 * scale), filled = true)
                    AppText(if (answer) "答案" else "问题", AppTextRole.SectionTitle, color = if (answer) theme.strongText else AppColors.TextIconLight, designScale = scale)
                }
                Surface(shape = RoundedCornerShape(999.dp), color = badge.background) {
                    AppText(badge.label, AppTextRole.Label, modifier = Modifier.padding(horizontal = (16 * scale).dp, vertical = (8 * scale).dp), color = badge.content, designScale = scale, maxLines = 1)
                }
            }
            AppText(
                if (answer) sample.answer else sample.question,
                AppTextRole.Body,
                color = if (answer) theme.strongText else AppColors.TextIconLight,
                designScale = scale,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private data class SmartDifficultyBadge(val label: String, val background: Color, val content: Color)

private fun smartDifficultyBadge(label: String, theme: DeckTheme): SmartDifficultyBadge = when (label) {
    "理解分析" -> SmartDifficultyBadge(label, AppColors.Green.primarySecondary, AppColors.Green.ink)
    "综合应用" -> SmartDifficultyBadge(label, AppColors.WarningSecondary, AppColors.WarningInk)
    else -> SmartDifficultyBadge(label, theme.secondary, theme.strongText)
}
