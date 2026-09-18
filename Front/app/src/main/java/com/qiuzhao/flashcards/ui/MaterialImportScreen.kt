package com.qiuzhao.flashcards.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.qiuzhao.flashcards.ui.navigation.AppNavigator
import com.qiuzhao.flashcards.ui.navigation.AppRoute

/**
 * Figma 807:4441. Recognition is automatic: staging a file or text immediately
 * drives its wire status (autoRecognizeMaterialImport), and the cards mirror the
 * 1100:5634/5644 recognizing/success states. 完成导入 only closes the sheet.
 */
@Composable
internal fun MaterialImportScreen(
    route: AppRoute.MaterialImport,
    viewModel: AppViewModel,
    navigator: AppNavigator
) {
    // The global material-management flow is Azure brand colour. The project
    // creation variant receives the in-progress project's theme explicitly.
    val theme = remember(route.themeKey) {
        DeckThemes.firstOrNull { it.key == route.themeKey }
            ?: DeckThemes.first { it.key == "azure" }
    }
    val scale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(.75f, 1f)
    val materials by viewModel.materialImportDrafts.collectAsState()
    val projectMaterials by viewModel.projectMaterials.collectAsState()
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var editingFile by remember { mutableStateOf<ProjectDraftMaterial?>(null) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.stageMaterialImportFiles(uris)
    }
    // 已落地资料的失败重试 = 换文件 replace 重传（V25-D-30）；尚未落地的草稿由自动识别重传。
    // V25-D-36：AI 章节规划失败/未存 Key 的资料先弹选项（重试解析 / 按整本继续 / 换文件）。
    var replaceTarget by remember { mutableStateOf<ProjectDraftMaterial?>(null) }
    var aiRetryTarget by remember { mutableStateOf<ProjectDraftMaterial?>(null) }
    val replacePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = replaceTarget
        if (uri != null && target?.materialId != null && route.projectId != null) {
            viewModel.replaceProjectMaterial(route.projectId, target.materialId, uri) { _, _ -> }
            viewModel.markMaterialImportParsing(target.id)
        }
        replaceTarget = null
    }
    // 设定替换目标后立即拉起系统文件选择器；取消时回调同样会清空目标。
    LaunchedEffect(replaceTarget) {
        if (replaceTarget != null) replacePicker.launch(arrayOf("application/pdf"))
    }
    // Auto-recognition observer: every list change re-arms the pipeline, which
    // picks up just-staged drafts (idempotent for the rest). FAILED drafts stay
    // put with a readable error until the card's 重试 tap re-arms them —
    // automatic re-picking would re-upload a rejected file in a tight loop.
    LaunchedEffect(materials, route.projectId) {
        viewModel.autoRecognizeMaterialImport(route.projectId)
    }
    // Drafts already committed to the server mirror the Room projection's live parse
    // status, so a replace lands as PARSING→PARSED/FAILED without extra plumbing.
    val liveForProject = route.projectId?.let { projectMaterials[it].orEmpty() }
    val filtered = materials
        .map { draft ->
            val live = liveForProject?.firstOrNull { it.materialId == draft.materialId }
            if (live != null) draft.copy(
                serverStatus = live.serverStatus,
                errorCode = live.errorCode,
                charCount = live.charCount,
            ) else draft
        }
        .filter { it.title.contains(searchQuery.trim(), ignoreCase = true) }

    Box(Modifier.fillMaxSize().background(Color.White)) {
        LazyColumn(
            // Figma 807:4441 owns a 370dp-wide, 24dp-rounded scroll viewport. The
            // clip is deliberately on the viewport so long cards fade/crop cleanly
            // beneath the fixed bottom action.
            modifier = Modifier.fillMaxSize().padding(start = (16 * scale).dp, top = (136 * scale).dp, end = (16 * scale).dp)
                .clip(RoundedCornerShape((24 * scale).dp)),
            contentPadding = PaddingValues(bottom = (116 * scale).dp),
            verticalArrangement = Arrangement.spacedBy((16 * scale).dp)
        ) {
            item {
                ImportSearchField(theme, scale, searchQuery) { searchQuery = it }
            }
            item {
                ImportAddPanel(
                    theme = theme, scale = scale,
                    onChooseFile = { filePicker.launch(arrayOf("application/pdf", "application/zip", "text/html")) },
                    onEnterText = {
                        // 不预创建空草稿：materialId 传 null，编辑器保存有效内容时
                        // upsertMaterialImportText 才把文本资料入列——直接退出不留下
                        // 空资料，也不会触发自动识别上传空文本（服务端 400）。
                        navigator.navigate(
                            AppRoute.ProjectTextEditor(
                                materialId = null,
                                themeKey = theme.key,
                                projectId = route.projectId,
                                stageForMaterialImport = true,
                                editorTitle = "添加文本",
                            )
                        )
                    }
                )
            }
            item {
                ImportPreviewGroup(
                    theme = theme, scale = scale, title = "文件资料", icon = "files",
                    hint = "右滑卡片可编辑、删除文件",
                    materials = filtered.filter { it.type != ProjectDraftMaterialType.TEXT },
                    onEditFile = { editingFile = it },
                    onEditText = {},
                    onRetry = { material ->
                        val onServer = material.materialId != null && route.projectId != null
                        when {
                            onServer && isAiChapterRetryable(material.errorCode) -> aiRetryTarget = material
                            onServer -> replaceTarget = material
                            else -> viewModel.retryMaterialImportDraft(material.id)
                        }
                    },
                    onDelete = viewModel::removeMaterialImportDraft
                )
            }
            item {
                ImportPreviewGroup(
                    theme = theme, scale = scale, title = "文本资料", icon = "description",
                    hint = "右滑卡片可编辑、删除文本",
                    materials = filtered.filter { it.type == ProjectDraftMaterialType.TEXT },
                    emptyHint = "暂无添加",
                    onEditFile = { editingFile = it },
                    onEditText = { material ->
                        navigator.navigate(
                            AppRoute.ProjectTextEditor(
                                materialId = material.id, themeKey = theme.key, projectId = route.projectId,
                                stageForMaterialImport = true, editorTitle = "编辑文本"
                            )
                        )
                    },
                    onRetry = { material -> viewModel.retryMaterialImportDraft(material.id) },
                    onDelete = viewModel::removeMaterialImportDraft
                )
            }
        }

        ScreenTopInformationBar(
            title = "导入资料", subtitle = null, onBack = navigator::goBack,
            backContainer = theme.cardPanel, titleColor = theme.text
        )
        // Shared fixed-action fade: clips the scrolling card region visually
        // before it meets the bottom control, as in Figma 720:2251.
        BottomContentFade(1f, Modifier.align(Alignment.BottomCenter), color = Color.White)
        Surface(
            onClick = { viewModel.finishMaterialImport(route.projectId) { navigator.goBack() } },
            color = theme.primary, contentColor = theme.onPrimary, shape = RoundedCornerShape((24 * scale).dp),
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(vertical = (16 * scale).dp).zIndex(1f)
        ) {
            Row(
                Modifier.padding(horizontal = (36 * scale).dp).height((68 * scale).dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MaterialSymbol("folder_open", null, tint = theme.onPrimary, size = fixedSp(24 * scale), filled = true)
                Spacer(Modifier.width((8 * scale).dp))
                AppText("完成导入", AppTextRole.Label, color = theme.onPrimary, designScale = scale)
            }
        }
    }
    aiRetryTarget?.let { material ->
        val projectId = route.projectId
        val materialId = material.materialId
        AiChapterFailureDialog(
            theme = theme,
            onReparse = {
                aiRetryTarget = null
                if (projectId != null && materialId != null) {
                    viewModel.reparseProjectMaterial(projectId, materialId) { _, _ -> }
                }
            },
            onWholeBook = {
                aiRetryTarget = null
                if (projectId != null && materialId != null) {
                    viewModel.fallbackWholeBookChapters(projectId, materialId) { _, _ -> }
                }
            },
            onReplaceFile = {
                aiRetryTarget = null
                replaceTarget = material
            },
            onDismiss = { aiRetryTarget = null },
        )
    }
    editingFile?.let { material ->
        FileNameEditorDialog(
            theme = theme, initialTitle = material.title,
            onConfirm = { updatedTitle ->
                viewModel.renameMaterialImportFile(material.id, updatedTitle)
                editingFile = null
            },
            onDismiss = { editingFile = null }
        )
    }
}

/** Figma 807:4442: the search panel rides on family Secondary. */
@Composable
private fun ImportSearchField(theme: DeckTheme, scale: Float, query: String, onChange: (String) -> Unit) = Surface(
    color = theme.secondary, shape = RoundedCornerShape((32 * scale).dp), modifier = Modifier.fillMaxWidth()
) {
    Row(
        Modifier.padding((12 * scale).dp),
        horizontalArrangement = Arrangement.spacedBy((10 * scale).dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(color = theme.primary, shape = RoundedCornerShape((24 * scale).dp), modifier = Modifier.size((51 * scale).dp)) {
            Box(contentAlignment = Alignment.Center) {
                MaterialSymbol("search", null, tint = theme.onPrimary, size = fixedSp(28 * scale), filled = true)
            }
        }
        Surface(color = theme.background, shape = RoundedCornerShape((24 * scale).dp), modifier = Modifier.weight(1f)) {
            BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                textStyle = appInputTextStyle(AppTextRole.Body, scale, theme.text),
                visualTransformation = rememberBilingualInputTransformation(AppTextRole.Body, scale),
                modifier = Modifier.fillMaxWidth().padding(horizontal = (24 * scale).dp, vertical = (12 * scale).dp),
                decorationBox = { input ->
                    Box(Modifier.fillMaxWidth()) {
                        if (query.isBlank()) AppText("搜索", AppTextRole.Body, color = theme.text, designScale = scale)
                        input()
                    }
                }
            )
        }
    }
}

/** Figma 807:4443-ish: the two equal-width primary entry buttons. */
@Composable
private fun ImportAddPanel(
    theme: DeckTheme,
    scale: Float,
    onChooseFile: () -> Unit,
    onEnterText: () -> Unit
) = Surface(
    color = theme.background, shape = RoundedCornerShape((36 * scale).dp), modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape((36 * scale).dp))
) {
    Row(Modifier.padding((12 * scale).dp), horizontalArrangement = Arrangement.spacedBy((8 * scale).dp)) {
        ImportActionButton("picture_as_pdf", "添加文件", theme, scale, Modifier.weight(1f), onChooseFile)
        ImportActionButton("file_copy", "添加文本", theme, scale, Modifier.weight(1f), onEnterText)
    }
}

@Composable
private fun ImportActionButton(
    icon: String,
    label: String,
    theme: DeckTheme,
    scale: Float,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) = Surface(
    onClick = onClick,
    color = theme.primary, contentColor = theme.onPrimary,
    shape = RoundedCornerShape((24 * scale).dp),
    modifier = modifier.height((60 * scale).dp)
) {
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        MaterialSymbol(icon, null, tint = LocalContentColor.current, size = fixedSp(24 * scale), filled = true)
        Spacer(Modifier.width((8 * scale).dp))
        AppText(label, AppTextRole.Label, color = LocalContentColor.current, designScale = scale)
    }
}

@Composable
private fun ImportPreviewGroup(
    theme: DeckTheme,
    scale: Float,
    title: String,
    icon: String,
    hint: String,
    materials: List<ProjectDraftMaterial>,
    emptyHint: String = "暂无添加",
    onEditFile: (ProjectDraftMaterial) -> Unit,
    onEditText: (ProjectDraftMaterial) -> Unit,
    onRetry: (ProjectDraftMaterial) -> Unit,
    onDelete: (String) -> Unit
) = Surface(color = theme.background, shape = RoundedCornerShape((36 * scale).dp), modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape((36 * scale).dp))) {
    Column(Modifier.padding((20 * scale).dp), verticalArrangement = Arrangement.spacedBy((16 * scale).dp)) {
        Row(Modifier.padding(horizontal = (8 * scale).dp), horizontalArrangement = Arrangement.spacedBy((10 * scale).dp), verticalAlignment = Alignment.CenterVertically) {
            MaterialSymbol(icon, null, tint = theme.text, size = fixedSp(24 * scale), filled = true)
            AppText(title, AppTextRole.SectionTitle, color = theme.text, designScale = scale)
        }
        if (materials.isEmpty()) {
            CardHint(emptyHint, designScale = scale)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy((16 * scale).dp)) {
                materials.forEach { material ->
                    ProjectCompactMaterialCard(
                        material = material,
                        theme = theme,
                        scale = scale,
                        // Figma 1100:5644 状态=已选择: success shows check_circle.
                        doneIcon = "check_circle",
                        onEdit = {
                            if (material.type != ProjectDraftMaterialType.TEXT) onEditFile(material) else onEditText(material)
                        },
                        onDelete = { onDelete(material.id) },
                        onRetry = { onRetry(material) }
                    )
                }
            }
        }
        CardHint(hint, designScale = scale)
    }
}

private fun Uri.displayName(context: android.content.Context): String {
    context.contentResolver.query(this, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(cursor.getColumnIndexOrThrow(android.provider.OpenableColumns.DISPLAY_NAME))?.let { return it }
    }
    return lastPathSegment?.substringAfterLast('/') ?: "未命名文件"
}
