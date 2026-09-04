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

@Composable
internal fun SettingsScreen(viewModel: AppViewModel, nav: ScreenNavigator) {
    val remoteApiStatus by viewModel.apiKeyStatus.collectAsState()
    val designScale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(0.75f, 1f)
    var showAiKeyDialog by remember { mutableStateOf(false) }
    var apiKey by remember { mutableStateOf("") }
    var savingApiKey by remember { mutableStateOf(false) }
    LaunchedEffect(showAiKeyDialog) { if (showAiKeyDialog) viewModel.refreshApiKeyStatus() }
    LaunchedEffect(remoteApiStatus) { if (remoteApiStatus != null) savingApiKey = false }
    val aiStatus = if (savingApiKey) "验证中" else when (remoteApiStatus?.status?.uppercase()) {
        "AVAILABLE" -> "可用"
        "INVALID" -> "无效"
        "INSUFFICIENT_BALANCE" -> "余额不足"
        else -> "未设置"
    }
    Surface(modifier = Modifier.fillMaxSize(), color = AppColors.Blue.background) {
        Box(Modifier.fillMaxSize()) {
            // Figma 66:4804: this is one deliberately tight menu, with 4dp inside
            // a group and a 20dp break between groups. It scrolls below the fixed header.
            Box(
                Modifier.fillMaxSize()
                    .padding(
                        start = (16 * designScale).dp,
                        top = (148 * designScale).dp,
                        end = (16 * designScale).dp
                    )
                    .clip(RoundedCornerShape(AppScrollableContentClipRadius.dp))
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = (NaturalScrollTail * designScale).dp),
                    verticalArrangement = Arrangement.spacedBy((20 * designScale).dp)
                ) {
                    item {
                        SettingsMenuGroup(designScale) {
                            SettingsMenuRow("API设置", "experiment", AppColors.Purple.primarySecondary, AppColors.Purple.ink, designScale) {
                                showAiKeyDialog = true
                            }
                            SettingsMenuRow("退出登录", "logout", AppColors.Orange.primarySecondary, AppColors.Orange.ink, designScale) {
                                viewModel.logout()
                            }
                        }
                    }
                }
            }
            SettingsPageHeader(
                title = "设置",
                designScale = designScale,
                onBack = nav::popBackStack,
                modifier = Modifier
                    .padding(
                        start = (16 * designScale).dp,
                        top = (64 * designScale).dp,
                        end = (16 * designScale).dp
                    )
                    .zIndex(1f)
            )
        }
    }

    if (showAiKeyDialog) {
        AiServiceDialog(
            currentKey = apiKey,
            status = aiStatus,
            onSave = { key ->
                apiKey = key
                savingApiKey = true
                viewModel.saveApiKey(key) { savingApiKey = false }
            },
            onDismiss = { showAiKeyDialog = false }
        )
    }
}
@Composable
private fun AiServiceDialog(currentKey: String, status: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var key by remember(currentKey) { mutableStateOf(currentKey) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("DeepSeek API", fontFamily = AppFonts.GoogleSansFlexSemibold, fontWeight = FontWeight.Normal) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(key, { key = it }, label = { AppText("DeepSeek API Key", AppTextRole.Label) }, placeholder = { AppText("••••••••••••••", AppTextRole.Supporting) }, singleLine = true, modifier = Modifier.fillMaxWidth(), textStyle = appInputTextStyle(AppTextRole.Supporting), visualTransformation = rememberBilingualInputTransformation(AppTextRole.Supporting))
                MixedLanguageText(
                    text = when (status) {
                        "验证中" -> "正在验证…"
                        "可用" -> "连接可用。"
                        "无效" -> "DeepSeek API Key 已失效。"
                        "余额不足" -> "DeepSeek API 余额不足。"
                        else -> "保存后将由服务端验证。"
                    },
                    color = if (status == "无效" || status == "余额不足") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    chineseFont = AppFonts.MiSansMedium, latinFont = AppFonts.GoogleSansFlex, fontSize = fixedSp(14f), lineHeight = fixedSp(18f)
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(key) }) { AppText("验证并保存", AppTextRole.Label) } },
        dismissButton = { TextButton(onClick = onDismiss) { AppText("完成", AppTextRole.Label) } }
    )
}

@Composable
private fun SettingsPageHeader(
    title: String,
    designScale: Float,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier.fillMaxWidth().height((56 * designScale).dp)) {
        RoundIconButton(
            symbol = "arrow_back",
            description = "返回",
            color = HeaderControlBackgroundColor(),
            onClick = onBack,
            size = (56 * designScale).dp,
            tint = HeaderControlIconColor()
        )
        MixedLanguageText(
            text = title,
            modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = (60 * designScale).dp),
            color = PageForegroundColor(),
            chineseFont = AppFonts.MiSansSemibold,
            latinFont = AppFonts.GoogleSansFlexBold,
            fontSize = fixedSp(24 * designScale),
            lineHeight = fixedSp(32 * designScale),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Figma 66:4804: 76dp menu rows, 52dp icon discs, 4dp row rhythm. */
@Composable
private fun SettingsMenuGroup(designScale: Float, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy((4 * designScale).dp), content = content)
}

@Composable
private fun SettingsMenuRow(
    title: String,
    symbol: String,
    iconBackground: Color,
    iconTint: Color,
    designScale: Float,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = AppColors.Card,
        shape = RoundedCornerShape((24 * designScale).dp),
        modifier = Modifier.fillMaxWidth().height((76 * designScale).dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding((12 * designScale).dp),
            horizontalArrangement = Arrangement.spacedBy((16 * designScale).dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = iconBackground,
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier.size((52 * designScale).dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    MaterialSymbol(symbol, null, tint = iconTint, size = fixedSp(24 * designScale), filled = true)
                }
            }
            MixedLanguageText(
                text = title,
                modifier = Modifier.weight(1f),
                color = AppColors.TextIconDark,
                chineseFont = AppFonts.MiSansSemibold,
                latinFont = AppFonts.GoogleSansFlexSemibold,
                fontSize = fixedSp(20 * designScale),
                lineHeight = fixedSp(24 * designScale),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Box(
                modifier = Modifier.size((52 * designScale).dp),
                contentAlignment = Alignment.Center
            ) {
                MaterialSymbol("arrow_forward", "进入$title", tint = AppColors.TextIconDark, size = fixedSp(24 * designScale))
            }
        }
    }
}
