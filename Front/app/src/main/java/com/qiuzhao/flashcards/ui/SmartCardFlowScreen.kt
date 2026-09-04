package com.qiuzhao.flashcards.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.qiuzhao.flashcards.data.remote.ProjectSummary
import com.qiuzhao.flashcards.domain.v25.V25MaterialStatus
import com.qiuzhao.flashcards.domain.v25.V25MaterialType
import com.qiuzhao.flashcards.domain.v25.V25ProjectStatus
import com.qiuzhao.flashcards.domain.v25.V25TaskStatus
import com.qiuzhao.flashcards.ui.navigation.AppRoute
import com.qiuzhao.flashcards.ui.motion.AppMotion

/**
 * Figma 849:6541 "正在生成". A full-page generation indicator that reuses the
 * parse-progress card while the V2.5 generation task runs in the background.
 */
@Composable
internal fun SmartCardGeneratingScreen(project: ProjectSummary, nav: ScreenNavigator, viewModel: AppViewModel) {
    val scale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(.75f, 1f)
    val theme = deckTheme(project)
    val task by viewModel.pdfTask.collectAsState()
    val status = task?.status
    // Navigation only: the observation engine (V25-D-34) polls the task and the projection
    // flow re-emits each status advance — this screen runs no loop of its own.
    LaunchedEffect(task?.taskId, status) {
        if (status == V25TaskStatus.COMPLETED) {
            task?.deckId?.let { nav.replaceTop(AppRoute.CardList(it)) }
        }
    }
    val headline = when (status) {
        V25TaskStatus.FAILED -> "生成失败"
        V25TaskStatus.ABANDONED -> "任务已放弃"
        V25TaskStatus.COMPLETED -> "生成完成"
        else -> "正在生成卡片"
    }
    val detail = when (status) {
        V25TaskStatus.FAILED -> task?.errorCode ?: "服务端未能完成本次生成。"
        V25TaskStatus.ABANDONED -> "本次任务已停止，你可以返回项目重新设置。"
        V25TaskStatus.COMPLETED -> "正在打开已生成的卡片组。"
        else -> "可以安全离开，任务会在后台继续。"
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
                AppText(headline, AppTextRole.SectionTitle, color = theme.text, designScale = scale, maxLines = 1)
                Spacer(Modifier.height((8 * scale).dp))
                AppText(detail, AppTextRole.CardSubtitle, color = theme.text.copy(alpha = .55f), designScale = scale, maxLines = 2)
            }
        }
        BottomContentFade(scale, Modifier.align(Alignment.BottomCenter), color = AppColors.BaseBackground)
        Surface(
            onClick = {
                if (status == V25TaskStatus.FAILED) {
                    viewModel.retryPdfTask { nav.replaceTop(AppRoute.SmartCardPreview(project.id)) }
                } else {
                    nav.goBack()
                }
            },
            color = theme.primary, contentColor = theme.onPrimary,
            shape = RoundedCornerShape((24 * scale).dp),
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                .padding(horizontal = (16 * scale).dp, vertical = (16 * scale).dp)
                .fillMaxWidth().height((60 * scale).dp).zIndex(1f)
        ) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                MaterialSymbol(if (status == V25TaskStatus.FAILED) "refresh" else "arrow_back", null, tint = LocalContentColor.current, size = fixedSp(24 * scale), filled = true)
                Spacer(Modifier.width((8 * scale).dp))
                AppText(if (status == V25TaskStatus.FAILED) "重试生成" else "后台继续", AppTextRole.Label, color = LocalContentColor.current, designScale = scale, maxLines = 1)
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
    val activeProject by viewModel.activePdfProject.collectAsState()
    val generationDraft by viewModel.projectGenerationDraft.collectAsState()
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var sampleRequestInFlight by remember { mutableStateOf(false) }
    var requestError by remember { mutableStateOf<String?>(null) }
    var replacingPdf by remember { mutableStateOf(false) }
    val active = activeProject?.takeIf { it.projectId == project.id }
    // Chapters span every material of the project (contract 3.2a): PDF chapters show their page
    // span, TEXT material chapters are whole-content with no pages.
    val chapters = active?.chapters.orEmpty().map { chapter ->
        SmartChapter(chapter.id, chapter.name, chapter.pageSpanLabel ?: "全文")
    }
    // The wait states derive straight from the Room-backed project flow (V25-D-34): the
    // observation engine keeps it current, so the screen needs no poller and no timeout —
    // leaving and coming back always shows server truth.
    val parsing = active?.status == V25ProjectStatus.PARSING
    val parseFailed = active?.status == V25ProjectStatus.PARSE_FAILED
    val failedMaterial = active?.materials
        ?.firstOrNull { it.type == V25MaterialType.PDF && it.status == V25MaterialStatus.FAILED }
    val blocked = parsing || parseFailed

    LaunchedEffect(project.id) {
        viewModel.openProjectForGeneration(project.id) { }
    }
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        requestError = null
        replacingPdf = true
        viewModel.replaceActiveProjectPdf(uri) { success, message ->
            replacingPdf = false
            if (!success) requestError = message ?: "替换 PDF 失败"
        }
    }
    LaunchedEffect(chapters) {
        if (selectedIds.isEmpty() && chapters.isNotEmpty()) selectedIds = chapters.map { it.id }.toSet()
    }

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
            requestError?.let { error ->
                item {
                    HintBox(
                        text = "无法生成样卡：$error",
                        parentIsWhite = true,
                        theme = theme,
                        designScale = scale,
                    )
                }
            }
            if (parsing) {
                item { SmartParseWaitCard(theme, scale) }
            }
            if (parseFailed) {
                item {
                    SmartParseFailedCard(
                        errorCode = failedMaterial?.errorCode,
                        replacing = replacingPdf,
                        theme = theme,
                        scale = scale,
                        onReplacePdf = { pdfPicker.launch(arrayOf("application/pdf")) },
                    )
                }
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
            onClick = {
                if (blocked || selectedIds.isEmpty() || sampleRequestInFlight || replacingPdf) return@Surface
                sampleRequestInFlight = true
                requestError = null
                viewModel.generatePdfSamples(
                    existingDeckId = null,
                    deckName = generationDraft?.deckName.orEmpty().ifBlank { "${project.name} 卡片组" },
                    chapterIds = selectedIds.toList(),
                    config = generationDraft?.config ?: PdfGenerationConfig(),
                    onReady = {
                        sampleRequestInFlight = false
                        nav.navigate(AppRoute.SmartCardPreview(project.id))
                    },
                    onFailure = { code ->
                        sampleRequestInFlight = false
                        requestError = code ?: "GENERATION_FAILED"
                    },
                )
            },
            color = theme.primary, contentColor = theme.onPrimary,
            shape = RoundedCornerShape((24 * scale).dp),
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                .padding(horizontal = (16 * scale).dp, vertical = (16 * scale).dp)
                .fillMaxWidth().height((60 * scale).dp).zIndex(1f)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AppText(
                    when {
                        parsing -> "正在解析"
                        parseFailed -> "解析失败"
                        replacingPdf -> "正在替换 PDF"
                        sampleRequestInFlight -> "正在生成样卡"
                        else -> "下一步"
                    },
                    AppTextRole.Label,
                    color = LocalContentColor.current,
                    designScale = scale,
                    maxLines = 1,
                )
            }
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

/** In-page parse progress card: the observation engine keeps the projection fresh; leaving is safe. */
@Composable
private fun SmartParseWaitCard(theme: DeckTheme, scale: Float) = Surface(
    color = theme.background,
    shape = RoundedCornerShape((AppShapeRadius * scale).dp),
    modifier = Modifier.fillMaxWidth()
) {
    Column(
        Modifier.fillMaxWidth().padding((24 * scale).dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy((12 * scale).dp)
    ) {
        GenerationProgressRing(color = theme.primary, trackColor = theme.secondary, designScale = scale)
        AppText("正在解析文件内容", AppTextRole.SectionTitle, color = theme.text, designScale = scale, maxLines = 1)
        AppText(
            "正在等待服务端解析结果；可以返回，解析完成后章节会自动出现",
            AppTextRole.CardSubtitle, color = theme.text.copy(alpha = .55f), designScale = scale,
        )
    }
}

/** Terminal parse failure: shows the backend reason and the failed PDF material's in-place replace way out. */
@Composable
private fun SmartParseFailedCard(
    errorCode: String?,
    replacing: Boolean,
    theme: DeckTheme,
    scale: Float,
    onReplacePdf: () -> Unit,
) = Surface(
    color = AppColors.WarningSecondary,
    shape = RoundedCornerShape((AppShapeRadius * scale).dp),
    modifier = Modifier.fillMaxWidth()
) {
    Column(
        Modifier.fillMaxWidth().padding((20 * scale).dp),
        verticalArrangement = Arrangement.spacedBy((12 * scale).dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy((10 * scale).dp), verticalAlignment = Alignment.CenterVertically) {
            MaterialSymbol("error", null, tint = AppColors.WarningInk, size = fixedSp(24 * scale), filled = true)
            AppText("PDF 解析失败", AppTextRole.SectionTitle, color = AppColors.WarningInk, designScale = scale)
        }
        AppText(
            if (errorCode.isNullOrBlank()) "服务无法解析这份 PDF，请重新上传文件替换后重试。"
            else "服务无法解析这份 PDF（$errorCode）。请重新上传文件替换后重试。",
            AppTextRole.CardSubtitle, color = AppColors.WarningInk, designScale = scale,
        )
        Surface(
            onClick = onReplacePdf,
            enabled = !replacing,
            color = AppColors.WarningStrong, contentColor = AppColors.TextIconLight,
            shape = RoundedCornerShape((AppButtonShapeRadius * scale).dp),
            modifier = Modifier.fillMaxWidth().height((48 * scale).dp)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AppText(if (replacing) "正在替换 PDF" else "重新上传 PDF", AppTextRole.Label, color = LocalContentColor.current, designScale = scale)
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
    val remoteSamples by viewModel.pdfSamples.collectAsState()
    var starting by remember { mutableStateOf(false) }
    var startError by remember { mutableStateOf<String?>(null) }
    val samples = remoteSamples.mapIndexed { index, sample ->
        SmartPreviewSample(sample.front, sample.back, "样卡 ${index + 1}")
    }
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
                    text = if (samples.isEmpty()) "服务端尚未返回样卡，请返回重新生成。" else "点击卡片可以查看答案。样卡确认后才会开始正式生成。",
                    parentIsWhite = true,
                    theme = theme,
                    designScale = scale
                )
            }
            startError?.let { error ->
                item {
                    HintBox(
                        text = "无法开始生成：$error",
                        parentIsWhite = true,
                        theme = theme,
                        designScale = scale,
                    )
                }
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
            CardListActionButton(if (starting) "正在开始" else "开始生成", "play_circle", true, Modifier.weight(1f), scale, theme) {
                if (samples.isNotEmpty() && !starting) {
                    starting = true
                    startError = null
                    viewModel.startPdfTask(
                        onStarted = {
                            starting = false
                            nav.navigate(AppRoute.SmartCardGenerating(project.id))
                        },
                        onFailure = { code ->
                            starting = false
                            startError = code ?: "GENERATION_FAILED"
                        },
                    )
                }
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
