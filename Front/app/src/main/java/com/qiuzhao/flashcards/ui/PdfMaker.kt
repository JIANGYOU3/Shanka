package com.qiuzhao.flashcards.ui

import android.app.Activity
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
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
import androidx.compose.ui.unit.IntOffset
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
import com.qiuzhao.flashcards.data.remote.Dashboard
import com.qiuzhao.flashcards.data.ImportParser
import com.qiuzhao.flashcards.data.remote.Rating
import com.qiuzhao.flashcards.R
import com.qiuzhao.flashcards.ui.motion.AppMotion
import com.qiuzhao.flashcards.ui.navigation.AppNavigator
import com.qiuzhao.flashcards.ui.navigation.AppRoute
import com.qiuzhao.flashcards.ui.navigation.rememberAppNavigationState
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

// Root list content begins with a card. A rounded top crop would remove content
// from that card's 24dp inset, so only the lower viewport corners are rounded.
private val BottomRoundedViewportShape = RoundedCornerShape(
    bottomStart = AppShapeRadius.dp,
    bottomEnd = AppShapeRadius.dp
)

private enum class PdfMakerStep { HOME, READING, READ_ERROR, CHAPTERS, SETTINGS, PREVIEW, TASK }
internal enum class PdfTaskState { GENERATING, PAUSED, COMPLETE }
internal data class PdfGenerationBlock(val title: String, val detail: String, val canOpenSettings: Boolean = false)

private fun apiKeyGenerationBlock(status: String): PdfGenerationBlock = when (status.uppercase()) {
    "INVALID" -> PdfGenerationBlock("API Key 不可用", "请在设置中更新有效的 DeepSeek API Key。", canOpenSettings = true)
    "INSUFFICIENT_BALANCE" -> PdfGenerationBlock("API Key 余额不足", "请在设置中更新可用的 DeepSeek API Key。", canOpenSettings = true)
    else -> PdfGenerationBlock("需要 API Key", "请先在设置中保存可用的 DeepSeek API Key。", canOpenSettings = true)
}

private fun taskGenerationBlock(code: String?): PdfGenerationBlock = when (code) {
    "API_KEY_NOT_SET", "API_KEY_INVALID", "API_KEY_INSUFFICIENT_BALANCE" -> apiKeyGenerationBlock(code.removePrefix("API_KEY_"))
    "PDF_NOT_READY" -> PdfGenerationBlock("PDF 状态异常", "请返回上一步重新选择并解析 PDF。")
    else -> PdfGenerationBlock("暂时无法开始生成", "服务暂时无法创建任务，请稍后重试。")
}

private fun sampleGenerationBlock(code: String?): PdfGenerationBlock = when (code) {
    "API_KEY_NOT_SET", "API_KEY_INVALID", "API_KEY_INSUFFICIENT_BALANCE" -> apiKeyGenerationBlock(code.removePrefix("API_KEY_"))
    "PDF_NOT_READY" -> PdfGenerationBlock("PDF 状态异常", "请返回上一步重新选择并解析 PDF。")
    "VALIDATION_ERROR" -> PdfGenerationBlock("无法生成样卡", "当前生成参数未被服务端接受，请调整后重试。")
    else -> PdfGenerationBlock("暂时无法生成样卡", "服务暂时无法生成样卡，请稍后重试。")
}

private data class PdfChapter(val remoteId: String? = null, val title: String, val start: Int, val end: Int, val selected: Boolean = true)
private data class SmartImportFile(
    val id: String,
    val uri: Uri,
    val name: String,
    val format: String,
    val selected: Boolean = false
)

private fun displayNameFor(uri: Uri, context: android.content.Context): String {
    return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
        }
        ?: uri.lastPathSegment.orEmpty().substringAfterLast('/').ifBlank { "未命名文件" }
}

private fun formatForFileName(name: String): String? {
    val suffix = name.substringAfterLast('.', "").lowercase()
    return when (suffix) {
        "pdf" -> "pdf"
        "txt", "md" -> ".${suffix}"
        else -> null
    }
}

@Composable
internal fun PdfSmartCardsFlow(decks: List<DeckSummary>, viewModel: AppViewModel, nav: ScreenNavigator) {
    val context = LocalContext.current
    val remotePdf by viewModel.pdfFile.collectAsState()
    val remoteSamples by viewModel.pdfSamples.collectAsState()
    val remoteTask by viewModel.pdfTask.collectAsState()
    val remoteTaskDeckId by viewModel.pdfTaskDeckId.collectAsState()
    val textImport by viewModel.textImportFlow.collectAsState()
    var step by remember(textImport) { mutableStateOf(if (textImport != null) PdfMakerStep.SETTINGS else PdfMakerStep.HOME) }
    val importedFiles = remember { mutableStateListOf<SmartImportFile>() }
    val chapters = remember { mutableStateListOf<PdfChapter>() }
    var editingChapter by remember { mutableStateOf<Int?>(null) }
    var useExistingDeck by remember { mutableStateOf(false) }
    var selectedExistingDeckId by remember { mutableStateOf<String?>(null) }
    var deckName by remember(textImport) { mutableStateOf(textImport?.deckName.orEmpty()) }
    var coverage by remember { mutableStateOf("均匀") }
    var requirement by remember { mutableStateOf("") }
    var taskState by remember { mutableStateOf(PdfTaskState.GENERATING) }
    var pdfReadFailure by remember { mutableStateOf<PdfReadFailure?>(null) }
    var chapterDeleteFailed by remember { mutableStateOf(false) }
    var generationBlocked by remember { mutableStateOf<PdfGenerationBlock?>(null) }
    var sampleRequestInFlight by remember { mutableStateOf(false) }
    var generationCheckInFlight by remember { mutableStateOf(false) }
    var generationConfig by remember { mutableStateOf(PdfGenerationConfig()) }
    var textImportDeckId by remember(textImport) { mutableStateOf<String?>(null) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri ->
            val name = displayNameFor(uri, context)
            val format = formatForFileName(name)
            if (format != null && importedFiles.none { it.uri == uri }) {
                importedFiles += SmartImportFile(uri.toString(), uri, name, format)
            }
        }
    }

    LaunchedEffect(remoteTask?.status) {
        when (remoteTask?.status?.uppercase()) {
            "COMPLETED" -> taskState = PdfTaskState.COMPLETE
            "PAUSED" -> taskState = PdfTaskState.PAUSED
            "PENDING", "RUNNING" -> taskState = PdfTaskState.GENERATING
        }
    }
    LaunchedEffect(chapterDeleteFailed) {
        if (chapterDeleteFailed) {
            delay(1_800)
            chapterDeleteFailed = false
        }
    }

    when (step) {
        PdfMakerStep.HOME -> SmartFileImportScreen(
            files = importedFiles,
            onChoose = { filePicker.launch(arrayOf("application/pdf")) },
            onToggle = { id ->
                val index = importedFiles.indexOfFirst { it.id == id }
                if (index >= 0) importedFiles[index] = importedFiles[index].copy(selected = !importedFiles[index].selected)
            },
            onDelete = { id -> importedFiles.removeAll { it.id == id } },
            onPreview = {
                importedFiles.firstOrNull { it.selected }?.let { file ->
                    step = PdfMakerStep.READING
                    pdfReadFailure = null
                    viewModel.uploadPdf(file.uri, onParsed = { parsed ->
                        chapters.clear()
                        chapters.addAll(parsed.map { PdfChapter(it.id, it.name, it.startPage, it.endPage) })
                        step = PdfMakerStep.CHAPTERS
                    }, onFailure = { failure -> pdfReadFailure = failure; step = PdfMakerStep.READ_ERROR })
                }
            },
            onBack = nav::popBackStack
        )
        PdfMakerStep.READING -> PdfReadingScreen(onBack = { step = PdfMakerStep.HOME })
        PdfMakerStep.READ_ERROR -> PdfReadErrorScreen(failure = pdfReadFailure, onBack = { step = PdfMakerStep.HOME }, onRetry = { step = PdfMakerStep.HOME })
        PdfMakerStep.CHAPTERS -> PdfChapterScreen(
            chapters = chapters,
            onToggle = { index -> chapters[index] = chapters[index].copy(selected = !chapters[index].selected) },
            onEdit = { editingChapter = it },
            onDelete = { index ->
                val removed = chapters.removeAt(index)
                removed.remoteId?.let { chapterId ->
                    viewModel.deletePdfChapter(
                        com.qiuzhao.flashcards.data.remote.PdfChapter(
                            id = chapterId,
                            name = removed.title,
                            startPage = removed.start,
                            endPage = removed.end
                        )
                    ) {
                        chapters.add(index.coerceIn(0, chapters.size), removed)
                        chapterDeleteFailed = true
                    }
                }
            },
            deleteFailed = chapterDeleteFailed,
            onNext = { step = PdfMakerStep.SETTINGS },
            onBack = { step = PdfMakerStep.HOME }
        )
        PdfMakerStep.SETTINGS -> PdfSettingsScreen(
            decks = decks, useExistingDeck = useExistingDeck, onUseExisting = { useExistingDeck = it },
            selectedExistingDeckId = selectedExistingDeckId,
            onSelectedExistingDeck = { selectedExistingDeckId = it },
            deckName = deckName, onDeckNameChange = { deckName = it }, coverage = coverage,
            onCoverageChange = { coverage = it }, requirement = requirement, onRequirementChange = { requirement = it },
            onPreview = { basic, analysis ->
                generationConfig = PdfGenerationConfig(
                    quantity = when (coverage) {
                        "精简" -> "COMPACT"
                        "充分" -> "EXTENSIVE"
                        else -> "BALANCED"
                    },
                    basic = basic / 100f,
                    understanding = (analysis - basic) / 100f,
                    application = (100f - analysis) / 100f,
                    requirement = requirement
                )
                val selected = chapters.filter { it.selected }.mapNotNull { it.remoteId }
                if (textImport != null) {
                    step = PdfMakerStep.PREVIEW
                } else if (selected.isEmpty()) {
                    generationBlocked = PdfGenerationBlock("未选择章节", "请返回上一步选择至少一个章节。")
                } else if (!sampleRequestInFlight) {
                    sampleRequestInFlight = true
                    viewModel.generatePdfSamples(
                        chapterIds = selected,
                        config = generationConfig,
                        onReady = {
                            sampleRequestInFlight = false
                            step = PdfMakerStep.PREVIEW
                        },
                        onFailure = { code ->
                            sampleRequestInFlight = false
                            generationBlocked = sampleGenerationBlock(code)
                        }
                    )
                }
            }, onBack = {
                if (textImport != null) {
                    viewModel.clearTextImportFlow()
                    nav.replaceTop(AppRoute.Import)
                } else step = PdfMakerStep.CHAPTERS
            }
        )
        PdfMakerStep.PREVIEW -> PdfPreviewScreen(
            samples = textImport?.cards ?: remoteSamples,
            onBack = { step = PdfMakerStep.SETTINGS },
            onGenerate = {
                val selected = chapters.filter { it.selected }.mapNotNull { it.remoteId }
                val activeTextImport = textImport
                if (activeTextImport != null) {
                    taskState = PdfTaskState.GENERATING
                    step = PdfMakerStep.TASK
                    val completeTextImport: (String) -> Unit = { deckId ->
                        textImportDeckId = deckId
                        taskState = PdfTaskState.COMPLETE
                    }
                    val existingDeckId = selectedExistingDeckId
                    if (useExistingDeck && existingDeckId != null) {
                        viewModel.addCardsToDeck(existingDeckId, activeTextImport.cards) {
                            completeTextImport(existingDeckId)
                        }
                    } else {
                        viewModel.importDeck(deckName.ifBlank { activeTextImport.deckName }, activeTextImport.cards, completeTextImport)
                    }
                } else if (selected.isEmpty()) {
                    generationBlocked = PdfGenerationBlock("未选择章节", "请返回上一步选择至少一个章节。")
                } else if (!generationCheckInFlight) {
                    generationCheckInFlight = true
                    viewModel.checkApiKeyForGeneration(
                        onAvailable = {
                            viewModel.createPdfTask(
                                existingDeckId = if (useExistingDeck) selectedExistingDeckId else null,
                                deckName = deckName,
                                chapterIds = selected,
                                config = generationConfig,
                                onStarted = {
                                    generationCheckInFlight = false
                                    taskState = PdfTaskState.GENERATING
                                    step = PdfMakerStep.TASK
                                },
                                onFailure = { code ->
                                    generationCheckInFlight = false
                                    generationBlocked = taskGenerationBlock(code)
                                }
                            )
                        },
                        onUnavailable = { status ->
                            generationCheckInFlight = false
                            generationBlocked = apiKeyGenerationBlock(status)
                        },
                        onFailure = {
                            generationCheckInFlight = false
                            generationBlocked = PdfGenerationBlock("无法确认 API Key", "请检查网络后重试。")
                        }
                    )
                }
            }
        )
        PdfMakerStep.TASK -> PdfTaskScreen(
            state = taskState,
            onPause = { taskState = PdfTaskState.PAUSED },
            onResume = {
                taskState = PdfTaskState.GENERATING
                if (textImport == null) viewModel.resumePdfTask()
            },
            onBack = { step = PdfMakerStep.PREVIEW },
            onViewDeck = {
                (textImportDeckId ?: remoteTaskDeckId)?.let { deckId ->
                    viewModel.clearTextImportFlow()
                    nav.replaceInclusive(AppRoute.PdfMaker, AppRoute.CardList(deckId))
                }
            }
        )
    }

    editingChapter?.let { index ->
        PdfChapterEditDialog(
            chapter = chapters[index],
            onSave = {
                chapters[index] = it
                it.remoteId?.let { id -> remotePdf?.id?.let { fileId -> viewModel.updatePdfChapter(com.qiuzhao.flashcards.data.remote.PdfChapter(id, it.title, it.start, it.end)) } }
                editingChapter = null
            },
            onDismiss = { editingChapter = null }
        )
    }
    generationBlocked?.let { block ->
        PdfGenerationBlockedDialog(
            block = block,
            onDismiss = { generationBlocked = null },
            onOpenSettings = {
                generationBlocked = null
                nav.navigate(AppRoute.Settings)
            }
        )
    }
}

@Composable
internal fun PdfFlowLayout(title: String, onBack: () -> Unit, footer: @Composable (() -> Unit)? = null, content: LazyListScope.() -> Unit) {
    val scale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(.75f, 1f)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(start = (16 * scale).dp, top = (132 * scale).dp, end = (16 * scale).dp).clip(BottomRoundedViewportShape),
                contentPadding = PaddingValues(bottom = if (footer == null) 36.dp else (148 * scale).dp),
                verticalArrangement = Arrangement.spacedBy((16 * scale).dp), content = content
            )
            DeckDetailHeader(title, scale, onBack, modifier = Modifier.zIndex(1f))
            if (footer != null) {
                BottomContentFade(scale, Modifier.align(Alignment.BottomCenter))
                Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = (16 * scale).dp, end = (16 * scale).dp, bottom = (24 * scale).dp).zIndex(1f)) { footer() }
            }
        }
    }
}

@Composable
private fun SmartFileImportScreen(
    files: List<SmartImportFile>,
    onChoose: () -> Unit,
    onToggle: (String) -> Unit,
    onDelete: (String) -> Unit,
    onPreview: () -> Unit,
    onBack: () -> Unit
) {
    val scale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(.75f, 1f)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
                    .padding(start = (16 * scale).dp, top = (132 * scale).dp, end = (16 * scale).dp)
                    .clip(BottomRoundedViewportShape),
                contentPadding = PaddingValues(bottom = (148 * scale).dp),
                verticalArrangement = Arrangement.spacedBy((16 * scale).dp)
            ) {
                item { SmartInfoCard("上传教材、课件或其他学习资料。\n暂不支持扫描版PDF。", scale) }
                item { SmartSectionLabel("导入的资料", scale) }
                items(files, key = { it.id }) { file ->
                    SmartImportFileCard(file, scale, { onToggle(file.id) }, { onDelete(file.id) })
                }
            }
            DeckDetailHeader("智能制卡", scale, onBack, modifier = Modifier.zIndex(1f))
            BottomContentFade(scale, Modifier.align(Alignment.BottomCenter))
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                    .padding(start = (16 * scale).dp, end = (16 * scale).dp, bottom = (32 * scale).dp)
                    .height((60 * scale).dp).zIndex(1f),
                horizontalArrangement = Arrangement.spacedBy((16 * scale).dp)
            ) {
                ImportActionButton("选择文件", "folder_open", false, Modifier.weight(1f), scale, onChoose)
                ImportActionButton("识别并预览", "screen_search_desktop", true, Modifier.weight(1f), scale, onPreview)
            }
        }
    }
}

@Composable
private fun SmartInfoCard(text: String, scale: Float) {
    Surface(
        color = AppColors.Blue.background,
        shape = RoundedCornerShape((32 * scale).dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        AppText(
            text = text,
            role = AppTextRole.Supporting,
            modifier = Modifier.padding((24 * scale).dp),
            color = AppColors.TextIconDark.copy(alpha = .75f),
            designScale = scale
        )
    }
}

@Composable
private fun SmartSectionLabel(text: String, scale: Float) {
    AppText(
        text = text,
        role = AppTextRole.Supporting,
        modifier = Modifier.padding(start = (8 * scale).dp),
        color = AppColors.TextIconDark,
        designScale = scale
    )
}

@Composable
private fun SmartImportFileCard(file: SmartImportFile, scale: Float, onToggle: () -> Unit, onDelete: () -> Unit) {
    SmartSwipeDeleteContainer(file.id, scale, "删除文件", onDelete) { cardModifier ->
        SmartSelectableCard(
            title = file.name,
            subtitle = "26/8/11 导入",
            badge = file.format,
            selected = file.selected,
            selectedIcon = "check_circle",
            unselectedIcon = "picture_as_pdf",
            action = onToggle,
            scale = scale,
            modifier = cardModifier,
            onClick = onToggle
        )
    }
}

/** Shared Figma swipe geometry: 112dp action panel, 16dp overlap, 96dp reveal. */
@Composable
private fun SmartSwipeDeleteContainer(
    key: Any,
    scale: Float,
    deleteLabel: String,
    onDelete: () -> Unit,
    content: @Composable (Modifier) -> Unit
) {
    val shape = RoundedCornerShape((32 * scale).dp)
    val actionWidth = (112 * scale).dp
    val revealWidthPx = with(LocalDensity.current) { ((112 - 16) * scale).dp.toPx() }
    var dragOffset by remember(key) { mutableFloatStateOf(0f) }
    val animatedOffset by animateFloatAsState(dragOffset, label = "$key smart delete swipe")
    val dragState = rememberDraggableState { delta ->
        dragOffset = (dragOffset + delta).coerceIn(-revealWidthPx, 0f)
    }
    Box(Modifier.fillMaxWidth().height((104 * scale).dp).clip(shape)) {
        // Keep the action mounted behind the card at rest. This matches the
        // deck/card-list implementation and prevents a one-frame pop-in as a
        // drag first crosses the reveal threshold.
        Surface(
            onClick = onDelete,
            shape = shape,
            color = AppColors.Warning,
            contentColor = AppColors.TextIconLight,
            modifier = Modifier.align(Alignment.CenterEnd).width(actionWidth).fillMaxHeight()
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(start = (24 * scale).dp, end = (16 * scale).dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                MaterialSymbol("delete", null, tint = LocalContentColor.current, size = fixedSp(24 * scale), filled = true)
                Spacer(Modifier.height((4 * scale).dp))
                Text(deleteLabel, fontFamily = AppFonts.MiSansBold, fontWeight = FontWeight.Normal, fontSize = fixedSp(16 * scale), lineHeight = fixedSp(20 * scale))
            }
        }
        content(
            Modifier.fillMaxSize().offset { IntOffset(animatedOffset.roundToInt(), 0) }
                .draggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    onDragStopped = {
                        dragOffset = if (dragOffset < -revealWidthPx / 2f) -revealWidthPx else 0f
                    }
                )
        )
    }
}

@Composable
private fun SmartSelectableCard(
    title: String,
    subtitle: String,
    badge: String,
    selected: Boolean,
    selectedIcon: String,
    unselectedIcon: String,
    action: (() -> Unit)?,
    scale: Float,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    // Figma 167:9679 / 222:4713.  These two selectable components share the
    // exact blue/green state pair; keeping it here prevents the file and
    // chapter flows from drifting apart again.
    val surface = if (selected) AppColors.Green.background else AppColors.Blue.background
    val accent = if (selected) AppColors.Green.primary else AppColors.Blue.primary
    val primary = AppColors.TextIconDark
    val onAccent = if (selected) AppColors.Green.background else AppColors.Blue.background
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape((32 * scale).dp),
        color = surface,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding((24 * scale).dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy((16 * scale).dp)
        ) {
            Surface(shape = RoundedCornerShape((16 * scale).dp), color = accent, modifier = Modifier.size((56 * scale).dp)) {
                Box(contentAlignment = Alignment.Center) {
                    MaterialSymbol(if (selected) selectedIcon else unselectedIcon, null, tint = onAccent, size = fixedSp(24 * scale), filled = true)
                }
            }
            Column(
                Modifier.weight(1f).height((56 * scale).dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                AppText(
                    text = title,
                    role = AppTextRole.CardTitle,
                    color = primary,
                    designScale = scale,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                AppText(
                    text = subtitle,
                    role = AppTextRole.CardSubtitle,
                    color = AppColors.TextIconDark.copy(alpha = .625f),
                    designScale = scale,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Surface(
                onClick = { action?.invoke() },
                shape = RoundedCornerShape((20 * scale).dp),
                color = accent,
                modifier = Modifier.height((56 * scale).dp)
            ) {
                Box(Modifier.padding(horizontal = (16 * scale).dp), contentAlignment = Alignment.Center) {
                    AppText(badge, AppTextRole.Label, color = onAccent, designScale = scale, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun PdfReadingScreen(onBack: () -> Unit) = PdfFlowLayout("正在识别", onBack, footer = {
    DetailPrimaryButton("暂停生成", "pause_circle", true, 1f) { }
}) {
    item { PdfRecognitionProgressCard() }
}

@Composable
private fun PdfRecognitionProgressCard() {
    val scale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(.75f, 1f)
    Surface(
        shape = RoundedCornerShape((32 * scale).dp),
        color = AppColors.Blue.background,
        modifier = Modifier.fillMaxWidth().height((265 * scale).dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding((24 * scale).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy((20 * scale).dp)
        ) {
            PdfRecognitionRing(scale)
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy((8 * scale).dp)) {
                AppText("正在识别文件内容", AppTextRole.PageTitle, color = AppColors.TextIconDark, designScale = scale)
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy((4 * scale).dp)) {
                    listOf("已识别文件", "正在整理内容", "正在检查结果").forEach { label ->
                        AppText(label, AppTextRole.CardSubtitle, color = AppColors.TextIconDark.copy(alpha = .55f), designScale = scale)
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfRecognitionRing(scale: Float) {
    val transition = rememberInfiniteTransition(label = "pdf recognition ring")
    val rotation by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "pdf recognition ring rotation")
    Canvas(Modifier.size((80 * scale).dp)) {
        val stroke = with(this) { 8.dp.toPx() }
        val inset = stroke / 2f
        val bounds = androidx.compose.ui.geometry.Rect(inset, inset, size.width - inset, size.height - inset)
        drawArc(AppColors.Blue.primarySecondary, rotation - 220f, 220f, false, bounds.topLeft, bounds.size, style = Stroke(stroke, cap = StrokeCap.Round))
        drawArc(AppColors.Blue.primary, rotation + 50f, 44f, false, bounds.topLeft, bounds.size, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

@Composable
private fun PdfReadErrorScreen(failure: PdfReadFailure?, onBack: () -> Unit, onRetry: () -> Unit) = PdfFlowLayout("PDF 智能制卡", onBack) {
    item { PdfStatusCard(failure?.title ?: "PDF 处理失败", failure?.detail ?: "请重新选择文件后再试。", icon = "error") }
    item { Button(onClick = onRetry, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(24.dp)) { Text("重新选择") } }
}

@Composable
private fun PdfStatusCard(title: String, subtitle: String, loading: Boolean = false, icon: String = "picture_as_pdf") = Card(shape = RoundedCornerShape(AppShapeRadius.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
    Row(Modifier.padding(24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        if (loading) CircularProgressIndicator(modifier = Modifier.size(40.dp), strokeWidth = 4.dp) else MaterialSymbol(icon, null, tint = MaterialTheme.colorScheme.error, size = fixedSp(40f), filled = true)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MixedLanguageText(text = title, color = PageForegroundColor(), chineseFont = AppFonts.MiSansSemibold, latinFont = AppFonts.GoogleSansFlexSemibold, fontSize = fixedSp(19f), lineHeight = fixedSp(24f))
            MixedLanguageText(text = subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, chineseFont = AppFonts.MiSansMedium, latinFont = AppFonts.GoogleSansFlex, fontSize = fixedSp(14f), lineHeight = fixedSp(18f))
        }
    }
}

@Composable
private fun PdfChapterScreen(
    chapters: List<PdfChapter>,
    onToggle: (Int) -> Unit,
    onEdit: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    deleteFailed: Boolean,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    val scale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(.75f, 1f)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
                    .padding(start = (16 * scale).dp, top = (132 * scale).dp, end = (16 * scale).dp)
                    .clip(BottomRoundedViewportShape),
                contentPadding = PaddingValues(bottom = (148 * scale).dp),
                verticalArrangement = Arrangement.spacedBy((16 * scale).dp)
            ) {
                item { SmartInfoCard("选择要制作闪卡的章节。", scale) }
                item { SmartSectionLabel("章节", scale) }
                items(chapters.indices.toList()) { index ->
                    val chapter = chapters[index]
                    SmartSwipeDeleteContainer("chapter-${chapter.title}-${chapter.start}", scale, "删除章节", { onDelete(index) }) { cardModifier ->
                        SmartSelectableCard(
                            title = chapter.title,
                            subtitle = "${chapter.start}-${chapter.end} 页",
                            badge = "编辑",
                            selected = chapter.selected,
                            selectedIcon = "check_circle",
                            unselectedIcon = "book_ribbon",
                            action = { onEdit(index) },
                            scale = scale,
                            modifier = cardModifier,
                            onClick = { onToggle(index) }
                        )
                    }
                }
            }
            DeckDetailHeader("智能制卡", scale, onBack, modifier = Modifier.zIndex(1f))
            BottomContentFade(scale, Modifier.align(Alignment.BottomCenter))
            Surface(
                onClick = onNext,
                color = AppColors.Blue.primary,
                contentColor = AppColors.TextIconLight,
                shape = RoundedCornerShape((24 * scale).dp),
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                    .padding(start = (16 * scale).dp, end = (16 * scale).dp, bottom = (32 * scale).dp)
                    .height((60 * scale).dp).fillMaxWidth().zIndex(1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    AppText("下一步", AppTextRole.Label, color = LocalContentColor.current, designScale = scale)
                }
            }
            DeleteFailureHint(
                visible = deleteFailed,
                modifier = Modifier.align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = (108 * scale).dp)
            )
        }
    }
}

@Composable
private fun PdfChapterEditDialog(chapter: PdfChapter, onSave: (PdfChapter) -> Unit, onDismiss: () -> Unit) {
    var title by remember(chapter) { mutableStateOf(chapter.title) }
    var start by remember(chapter) { mutableStateOf(chapter.start.toString()) }
    var end by remember(chapter) { mutableStateOf(chapter.end.toString()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("编辑章节", fontFamily = AppFonts.MiSansSemibold, fontWeight = FontWeight.Normal) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(title, { title = it }, label = { AppText("章节名称", AppTextRole.Label) }, modifier = Modifier.fillMaxWidth(), textStyle = appInputTextStyle(), visualTransformation = rememberBilingualInputTransformation())
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(start, { start = it.filter(Char::isDigit) }, label = { AppText("起始页", AppTextRole.Label) }, modifier = Modifier.weight(1f), textStyle = appInputTextStyle(AppTextRole.MetricXSmall), visualTransformation = rememberBilingualInputTransformation(AppTextRole.MetricXSmall))
                OutlinedTextField(end, { end = it.filter(Char::isDigit) }, label = { AppText("结束页", AppTextRole.Label) }, modifier = Modifier.weight(1f), textStyle = appInputTextStyle(AppTextRole.MetricXSmall), visualTransformation = rememberBilingualInputTransformation(AppTextRole.MetricXSmall))
            }
        }
    }, confirmButton = { TextButton(onClick = { onSave(chapter.copy(title = title.ifBlank { chapter.title }, start = start.toIntOrNull() ?: chapter.start, end = end.toIntOrNull() ?: chapter.end)) }) { Text("保存") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}
