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
import com.qiuzhao.flashcards.domain.v25.V25ErrorCodes
import com.qiuzhao.flashcards.domain.v25.V25InternalStage
import com.qiuzhao.flashcards.domain.v25.V25MaterialStatus
import com.qiuzhao.flashcards.domain.v25.V25MaterialType
import com.qiuzhao.flashcards.domain.v25.V25ProjectStatus
import com.qiuzhao.flashcards.domain.v25.V25TaskStatus
import com.qiuzhao.flashcards.ui.auth.ErrorMessages
import com.qiuzhao.flashcards.ui.navigation.AppRoute
import com.qiuzhao.flashcards.ui.motion.AppMotion

/**
 * Figma 1130:8101 "正在生成". The V2.5 generation task runs in the background; this
 * screen renders its [StatusProgressCard] (stage subtitle from `internal_stage`) and
 * hands control to the user: 后台生成 closes into the project page, 暂停生成 is a
 * local display pause only (交接文档 决策① — the server has no pause). The task
 * reaching `AWAITING_CONFIRMATION` auto-opens the read-only review screen (4.10).
 */
@Composable
internal fun SmartCardGeneratingScreen(project: ProjectSummary, nav: ScreenNavigator, viewModel: AppViewModel) {
    val scale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(.75f, 1f)
    val theme = deckTheme(project)
    val task by viewModel.pdfTask.collectAsState()
    val status = task?.status
    // Local display pause (决策①): the ring stops and the button flips to 继续生成;
    // the server task keeps running and nothing is cancelled.
    var paused by remember { mutableStateOf(false) }
    // Navigation only: the observation engine (V25-D-34) polls the task and the projection
    // flow re-emits each status advance — this screen runs no loop of its own.
    LaunchedEffect(task?.taskId, status) {
        if (status == V25TaskStatus.AWAITING_CONFIRMATION) {
            task?.let { nav.replaceTop(AppRoute.SmartCardReview(it.taskId, project.id, theme.key)) }
        }
    }
    val failed = status == V25TaskStatus.FAILED
    val abandoned = status == V25TaskStatus.ABANDONED
    // 状态卡副标题按 internalStage（交接文档 4.9）；无阶段时落到 Figma 的 稍安勿躁。
    val stageSubtitle = when (task?.internalStage) {
        V25InternalStage.PLANNING -> "已识别文件"
        V25InternalStage.GENERATING -> "正在整理内容"
        V25InternalStage.SCORING, V25InternalStage.PUBLISHING -> "正在检查结果"
        null -> "稍安勿躁"
    }
    Box(Modifier.fillMaxSize().background(AppColors.BaseBackground)) {
        ScreenTopInformationBar(
            title = "正在生成", subtitle = null, onBack = nav::goBack,
            backContainer = theme.cardPanel, titleColor = theme.text
        )
        StatusProgressCard(
            title = when {
                failed -> "生成失败，点击重试"
                abandoned -> "任务已停止"
                else -> "正在生成卡片"
            },
            subtitle = when {
                abandoned -> "本次任务已停止，可以返回项目重新设置。"
                else -> stageSubtitle
            },
            modifier = Modifier.align(Alignment.Center),
            container = theme.background,
            ringColor = theme.primary,
            failed = failed,
            failureReason = failureReasonText(task?.errorCode),
            paused = paused && !failed && !abandoned,
            onClick = if (failed) {
                { viewModel.retryPdfTask { nav.replaceTop(AppRoute.SmartCardPreview(project.id)) } }
            } else null,
        )
        BottomContentFade(scale, Modifier.align(Alignment.BottomCenter), color = AppColors.BaseBackground)
        if (!failed && !abandoned) {
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                    .padding(horizontal = (16 * scale).dp, vertical = (16 * scale).dp)
                    .fillMaxWidth().height((68 * scale).dp).zIndex(1f),
                horizontalArrangement = Arrangement.spacedBy((12 * scale).dp)
            ) {
                // Figma 1130:8193 后台生成: family Secondary-Primary surface.
                Surface(
                    onClick = {
                        nav.returnToTopLevel()
                        nav.navigate(AppRoute.ProjectDetail(project.id))
                    },
                    color = theme.secondary, contentColor = AppColors.TextIconDark,
                    shape = RoundedCornerShape((24 * scale).dp),
                    modifier = Modifier.weight(1f).height((68 * scale).dp)
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        AppText("后台生成", AppTextRole.Label, color = LocalContentColor.current, designScale = scale, maxLines = 1)
                    }
                }
                // Figma 1130:8103 暂停生成: fixed 224dp primary action; local display only.
                Surface(
                    onClick = { paused = !paused },
                    color = theme.primary, contentColor = theme.onPrimary,
                    shape = RoundedCornerShape((24 * scale).dp),
                    modifier = Modifier.width((224 * scale).dp).height((68 * scale).dp)
                ) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        MaterialSymbol(
                            if (paused) "play_circle" else "pause_circle", null,
                            tint = LocalContentColor.current, size = fixedSp(24 * scale), filled = true
                        )
                        Spacer(Modifier.width((8 * scale).dp))
                        AppText(if (paused) "继续生成" else "暂停生成", AppTextRole.Label, color = LocalContentColor.current, designScale = scale, maxLines = 1)
                    }
                }
            }
        } else if (failed) {
            Surface(
                onClick = { viewModel.retryPdfTask { nav.replaceTop(AppRoute.SmartCardPreview(project.id)) } },
                color = theme.primary, contentColor = theme.onPrimary,
                shape = RoundedCornerShape((24 * scale).dp),
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                    .padding(horizontal = (16 * scale).dp, vertical = (16 * scale).dp)
                    .fillMaxWidth().height((68 * scale).dp).zIndex(1f)
            ) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    MaterialSymbol("replay", null, tint = LocalContentColor.current, size = fixedSp(24 * scale), filled = true)
                    Spacer(Modifier.width((8 * scale).dp))
                    AppText("重试生成", AppTextRole.Label, color = LocalContentColor.current, designScale = scale, maxLines = 1)
                }
            }
        }
    }
}

/**
 * Figma 849:6541 "正在生成"（样卡等待）. No bottom buttons (交接文档 决策①): the screen
 * only observes the Room projection — samples landing (or the task reaching
 * `AWAITING_SAMPLE_CONFIRMATION`) auto-advance to the preview, a FAILED task offers
 * its in-place retry.
 */
@Composable
internal fun SmartCardSampleWaitScreen(project: ProjectSummary, nav: ScreenNavigator, viewModel: AppViewModel) {
    val theme = deckTheme(project)
    val task by viewModel.pdfTask.collectAsState()
    val samples by viewModel.pdfSamples.collectAsState()
    val status = task?.status
    var loadingSamples by remember { mutableStateOf(false) }
    LaunchedEffect(task?.taskId, status, samples.size) {
        when {
            // Freshly generated samples ride in memory; the jump renders them directly.
            samples.isNotEmpty() -> nav.replaceTop(AppRoute.SmartCardPreview(project.id))
            status == V25TaskStatus.AWAITING_SAMPLE_CONFIRMATION && !loadingSamples -> {
                loadingSamples = true
                viewModel.loadPdfSamples { loadingSamples = false }
            }
            // A retry that lands as DRAFT (no confirmed samples to reuse) needs its
            // sample request re-armed; requestPdfSamples is idempotent.
            status == V25TaskStatus.DRAFT -> viewModel.requestPdfSamples()
        }
    }
    val failed = status == V25TaskStatus.FAILED
    Box(Modifier.fillMaxSize().background(AppColors.BaseBackground)) {
        ScreenTopInformationBar(
            title = "正在生成", subtitle = null, onBack = nav::goBack,
            backContainer = theme.cardPanel, titleColor = theme.text
        )
        StatusProgressCard(
            title = if (failed) "生成失败，点击重试" else "正在生成预览卡片",
            subtitle = "稍安勿躁",
            modifier = Modifier.align(Alignment.Center),
            container = theme.background,
            ringColor = theme.primary,
            failed = failed,
            failureReason = failureReasonText(task?.errorCode),
            onClick = if (failed) {
                { viewModel.retryPdfTask { } }
            } else null,
        )
    }
}

/**
 * Figma 836:5895 / 839:6220 "智能制卡". After the user picks files and taps
 * "下一步" on generation settings, the parsed 部分 list is shown; picking at least
 * one and tapping 下一步 opens the sample-wait page immediately while the task is
 * being created in the background.
 */
@Composable
internal fun SmartCardChapterScreen(project: ProjectSummary, nav: ScreenNavigator, viewModel: AppViewModel) {
    val scale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(.75f, 1f)
    val theme = deckTheme(project)
    val activeProject by viewModel.activePdfProject.collectAsState()
    val generationDraft by viewModel.projectGenerationDraft.collectAsState()
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var requestError by remember { mutableStateOf<String?>(null) }
    var replacingPdf by remember { mutableStateOf(false) }
    var showAiRetry by remember { mutableStateOf(false) }
    var preparing by remember { mutableStateOf(false) }
    val active = activeProject?.takeIf { it.projectId == project.id }
    // Sections span every material of the project (contract 3.2a): PDF sections show their page
    // span, TEXT material sections are whole-content with no pages.
    val sections = active?.chapters.orEmpty().map { chapter ->
        SmartChapter(chapter.id, chapter.name, chapter.pageSpanLabel ?: "全文", chapter.isAiPlanned)
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
    LaunchedEffect(sections) {
        if (selectedIds.isEmpty() && sections.isNotEmpty()) selectedIds = sections.map { it.id }.toSet()
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
            contentPadding = PaddingValues(bottom = (fixedBottomControlScrollTail(bottomOffset = 16, controlCount = 2, gapBetweenControls = 12) * scale).dp),
            verticalArrangement = Arrangement.spacedBy((16 * scale).dp)
        ) {
            item {
                // Figma 839:6234 导入说明: family Surface banner, 24dp radius.
                Surface(
                    color = theme.cardPanel,
                    shape = RoundedCornerShape((AppNestedShapeRadius * scale).dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AppText(
                        "选择要制作闪卡的部分。",
                        AppTextRole.Supporting,
                        modifier = Modifier.fillMaxWidth().padding((24 * scale).dp),
                        color = AppColors.TextIconDark,
                        designScale = scale,
                    )
                }
            }
            requestError?.let { error ->
                item {
                    CardHint(
                        "无法生成样卡：${ErrorMessages.forCode(error)}",
                        designScale = scale,
                        error = true,
                    )
                }
            }
            if (parsing) {
                item {
                    // Figma 836:5895 识别内容弹窗-已识别文件, in-page at full content width.
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        StatusProgressCard(
                            title = "正在识别文件内容",
                            subtitle = "已识别文件",
                            container = theme.background,
                            ringColor = theme.primary,
                        )
                    }
                }
            }
            if (parseFailed) {
                item {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        StatusProgressCard(
                            title = "解析失败，点击重试",
                            subtitle = "",
                            container = theme.background,
                            ringColor = theme.primary,
                            failed = true,
                            failureReason = failureReasonText(failedMaterial?.errorCode),
                            onClick = if (!replacingPdf) {
                                {
                                    if (failedMaterial?.materialId != null &&
                                        isAiChapterRetryable(failedMaterial.errorCode)
                                    ) {
                                        showAiRetry = true
                                    } else {
                                        pdfPicker.launch(arrayOf("application/pdf"))
                                    }
                                }
                            } else null,
                        )
                    }
                }
            }
            item {
                // 交接文档 决策⑥：「章节」改名「部分」。
                AppText("部分", AppTextRole.SectionTitle, modifier = Modifier.padding(start = (8 * scale).dp), color = theme.text, designScale = scale)
            }
            items(sections, key = { it.id }) { section ->
                SmartChapterCard(section, selected = section.id in selectedIds, theme, scale) {
                    selectedIds = if (it in selectedIds) selectedIds - it else selectedIds + it
                }
            }
        }
        if (showAiRetry) {
            val failedId = failedMaterial?.materialId
            AiChapterFailureDialog(
                theme = theme,
                onReparse = {
                    showAiRetry = false
                    if (failedId != null) {
                        viewModel.reparseProjectMaterial(project.id, failedId) { _, _ -> }
                    }
                },
                onWholeBook = {
                    showAiRetry = false
                    if (failedId != null) {
                        viewModel.fallbackWholeBookChapters(project.id, failedId) { _, _ -> }
                    }
                },
                onReplaceFile = {
                    showAiRetry = false
                    pdfPicker.launch(arrayOf("application/pdf"))
                },
                onDismiss = { showAiRetry = false },
            )
        }
        BottomContentFade(scale, Modifier.align(Alignment.BottomCenter), color = AppColors.BaseBackground)
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                .padding(horizontal = (16 * scale).dp, vertical = (16 * scale).dp)
                .fillMaxWidth().zIndex(1f),
            verticalArrangement = Arrangement.spacedBy((12 * scale).dp)
        ) {
            // Figma 839:6237 button 组: 全选 (family Primary-Secondary) + 下一步 (Primary).
            Surface(
                onClick = {
                    selectedIds = if (selectedIds.size == sections.size) emptySet() else sections.map { it.id }.toSet()
                },
                enabled = sections.isNotEmpty() && !blocked,
                color = theme.secondary, contentColor = AppColors.TextIconDark,
                shape = RoundedCornerShape((24 * scale).dp),
                modifier = Modifier.fillMaxWidth().height((68 * scale).dp)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AppText(
                        if (selectedIds.size == sections.size && sections.isNotEmpty()) "取消全选" else "全选",
                        AppTextRole.Label,
                        color = LocalContentColor.current,
                        designScale = scale,
                        maxLines = 1,
                    )
                }
            }
            Surface(
                onClick = {
                    if (blocked || selectedIds.isEmpty() || preparing || replacingPdf) return@Surface
                    preparing = true
                    requestError = null
                    viewModel.beginPdfSamples(
                        existingDeckId = null,
                        deckName = generationDraft?.deckName.orEmpty().ifBlank { "${project.name} 卡片组" },
                        chapterIds = selectedIds.toList(),
                        config = generationDraft?.config ?: PdfGenerationConfig(),
                        onReady = {
                            preparing = false
                            nav.navigate(AppRoute.SmartCardSampleWait(project.id))
                        },
                        onFailure = { code ->
                            preparing = false
                            requestError = code ?: "GENERATION_FAILED"
                        },
                    )
                },
                color = theme.primary, contentColor = theme.onPrimary,
                shape = RoundedCornerShape((24 * scale).dp),
                modifier = Modifier.fillMaxWidth().height((68 * scale).dp)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AppText(
                        when {
                            parsing -> "正在解析"
                            parseFailed -> "解析失败"
                            replacingPdf -> "正在替换 PDF"
                            preparing -> "正在创建任务"
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
}

private data class SmartChapter(val id: String, val title: String, val pages: String, val aiPlanned: Boolean = false)

/**
 * Figma 222:4713 部分 row. Unselected lifts to the family Background with a
 * Primary icon tile (16dp corner); selected turns green (#D6EEC9 family) with a
 * check tile. 只能被选与未选（交接文档 决策⑥）— no edit, no delete.
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
    color = if (selected) AppColors.Green.surface else theme.background,
    shape = RoundedCornerShape((AppShapeRadius * scale).dp),
    modifier = Modifier.fillMaxWidth()
) {
    Row(Modifier.fillMaxSize().padding((16 * scale).dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            color = if (selected) AppColors.Green.primary else theme.primary,
            shape = RoundedCornerShape((16 * scale).dp),
            modifier = Modifier.size((56 * scale).dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                MaterialSymbol(
                    if (selected) "check_circle" else "book_ribbon", null,
                    tint = if (selected) AppColors.Green.background else theme.background,
                    size = fixedSp(24 * scale), filled = true
                )
            }
        }
        Spacer(Modifier.width((16 * scale).dp))
        Column(Modifier.weight(1f).height((56 * scale).dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppText(chapter.title, AppTextRole.CardTitle, color = theme.text, designScale = scale, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (chapter.aiPlanned) {
                    Spacer(Modifier.width((6 * scale).dp))
                    Surface(color = theme.primary.copy(alpha = .12f), shape = RoundedCornerShape((6 * scale).dp)) {
                        AppText(
                            "AI 规划", AppTextRole.CardSubtitle,
                            color = theme.primary, designScale = scale,
                            modifier = Modifier.padding(horizontal = (6 * scale).dp, vertical = (2 * scale).dp),
                        )
                    }
                }
            }
            AppText(chapter.pages, AppTextRole.CardSubtitle, color = theme.text.copy(alpha = .5f), designScale = scale)
        }
    }
}

/**
 * Figma 835:5784 "卡片预览". The generated sample flashcards are shown as flip
 * cards (question front, answer back); the difficulty chip and the two fixed
 * actions reuse the shared CardListActionButton. 返回调整 unwinds the whole
 * wizard back to generation settings (交接文档 4.8).
 */
@Composable
internal fun SmartCardPreviewScreen(project: ProjectSummary, nav: ScreenNavigator, viewModel: AppViewModel) {
    val scale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(.75f, 1f)
    val theme = deckTheme(project)
    val remoteSamples by viewModel.pdfSamples.collectAsState()
    val task by viewModel.pdfTask.collectAsState()
    var starting by remember { mutableStateOf(false) }
    var startError by remember { mutableStateOf<String?>(null) }
    val samples = remoteSamples.mapIndexed { index, sample ->
        SmartPreviewSample(sample.front, sample.back, "样卡 ${index + 1}")
    }
    // 开始按钮只认任务投影里的「样卡已确认」状态：内存里的样卡可能来自上一个
    // 任务（bindPdfTask 已清槽，但恢复路径仍可能短暂滞后），状态未到就不可开始。
    val sampleConfirmed = task?.status == V25TaskStatus.AWAITING_SAMPLE_CONFIRMATION
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
                CardHint(
                    if (samples.isEmpty()) "服务端尚未返回样卡，请返回重新生成。" else "点击卡片可以查看答案。",
                    designScale = scale,
                )
            }
            startError?.let { error ->
                item {
                    CardHint(
                        "无法开始生成：${ErrorMessages.forCode(error)}",
                        designScale = scale,
                        error = true,
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
                .fillMaxWidth().height((68 * scale).dp).zIndex(1f),
            horizontalArrangement = Arrangement.spacedBy((12 * scale).dp)
        ) {
            CardListActionButton("返回调整", "cycle", false, Modifier.weight(1f), scale, theme, onClick = {
                // 返回调整 lands on 添加卡片组 so every wizard setting stays editable.
                nav.popUntil(AppRoute.DeckGeneration(project.id))
            })
            CardListActionButton(if (starting) "正在开始" else "开始生成", "play_circle", true, Modifier.weight(1f), scale, theme) {
                if (sampleConfirmed && samples.isNotEmpty() && !starting) {
                    starting = true
                    startError = null
                    viewModel.startPdfTask(
                        onStarted = {
                            starting = false
                            nav.navigate(AppRoute.SmartCardGenerating(project.id))
                        },
                        onFailure = { code ->
                            starting = false
                            if (code == V25ErrorCodes.TASK_STATE_CONFLICT) {
                                // 样卡尚未就绪（如执行器正被其他任务的生成批次占用）：
                                // 不算失败，回等待页——样卡落地后自动推进回预览。
                                nav.replaceTop(AppRoute.SmartCardSampleWait(project.id))
                            } else {
                                startError = code ?: "GENERATION_FAILED"
                            }
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
