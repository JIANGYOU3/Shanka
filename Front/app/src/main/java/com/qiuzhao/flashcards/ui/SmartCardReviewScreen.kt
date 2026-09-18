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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
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
import com.qiuzhao.flashcards.domain.v25.V25Card
import com.qiuzhao.flashcards.domain.v25.V25Difficulty
import com.qiuzhao.flashcards.ui.auth.ErrorMessages
import com.qiuzhao.flashcards.ui.navigation.AppNavigator
import com.qiuzhao.flashcards.ui.navigation.AppRoute
import com.qiuzhao.flashcards.ui.motion.AppMotion

/**
 * Figma 849:6467 卡片列表（复审页，交接文档 4.10）. The finished-but-unconfirmed
 * task's STAGED cards are read read-only from GET /tasks/{id}/cards — flipping
 * reveals the answer; there is no swipe edit/delete (决策⑤⑨). 重新生成 re-enters the
 * sample flow (retry-supersede), 完成设置 confirms publication and lands on 项目-卡组.
 */
@Composable
internal fun SmartCardReviewScreen(
    route: AppRoute.SmartCardReview,
    nav: AppNavigator,
    viewModel: AppViewModel
) {
    val scale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(.75f, 1f)
    val theme = DeckThemes.firstOrNull { it.key == route.themeKey } ?: DeckThemes.first()
    var cards by remember { mutableStateOf<List<V25Card>?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var confirming by remember { mutableStateOf(false) }
    var regenerating by remember { mutableStateOf(false) }
    LaunchedEffect(route.taskId) {
        viewModel.loadTaskCards(route.taskId) { list, error ->
            cards = list
            loadError = error
        }
    }
    val loaded = cards.orEmpty()
    Box(Modifier.fillMaxSize().background(AppColors.BaseBackground)) {
        ScreenTopInformationBar(
            title = "卡片列表",
            subtitle = cards?.let { "${it.size} 张卡片" },
            onBack = nav::goBack,
            backContainer = theme.cardPanel,
            titleColor = theme.text
        )
        when {
            cards == null && loadError == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            loadError != null -> Box(Modifier.fillMaxSize().padding((16 * scale).dp), contentAlignment = Alignment.Center) {
                CardHint("无法加载生成卡片：${ErrorMessages.forCode(loadError)}", designScale = scale, error = true)
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().statusBarsPadding()
                    .padding(start = (16 * scale).dp, top = (88 * scale).dp, end = (16 * scale).dp)
                    .clip(RoundedCornerShape((AppScrollableContentClipRadius * scale).dp)),
                contentPadding = PaddingValues(bottom = (fixedBottomControlScrollTail(bottomOffset = 16) * scale).dp),
                verticalArrangement = Arrangement.spacedBy((16 * scale).dp)
            ) {
                if (loaded.isEmpty()) {
                    item {
                        // Park guarantees ≥1 STAGED card; an empty read is an honest anomaly.
                        CardHint("本次未生成卡片。", designScale = scale)
                    }
                } else {
                    item {
                        // Figma 849:6469 hint banner — read-only review (决策⑨), so the
                        // 左滑 edit/delete sentence is intentionally dropped.
                        Surface(
                            color = theme.cardPanel,
                            shape = RoundedCornerShape((AppNestedShapeRadius * scale).dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            AppText(
                                "点击卡片可以查看答案。",
                                AppTextRole.Supporting,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = (24 * scale).dp, vertical = (16 * scale).dp),
                                color = AppColors.TextIconDark,
                                designScale = scale,
                            )
                        }
                    }
                    items(loaded, key = { it.cardId }) { card ->
                        ReviewFlipCard(route, card, theme, scale)
                    }
                }
            }
        }
        BottomContentFade(scale, Modifier.align(Alignment.BottomCenter), color = AppColors.BaseBackground)
        Row(
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                .padding(horizontal = (16 * scale).dp, vertical = (16 * scale).dp)
                .fillMaxWidth().height((68 * scale).dp).zIndex(1f),
            horizontalArrangement = Arrangement.spacedBy((12 * scale).dp)
        ) {
            // Figma 849:6478 重新生成: family Primary-Secondary, cycle icon.
            Surface(
                onClick = {
                    if (regenerating || confirming) return@Surface
                    regenerating = true
                    viewModel.regenerateFromTask(
                        route.taskId,
                        onReady = {
                            regenerating = false
                            nav.replaceTop(AppRoute.SmartCardSampleWait(route.projectId))
                        },
                        onFailure = {
                            regenerating = false
                        },
                    )
                },
                color = theme.secondary, contentColor = AppColors.TextIconDark,
                shape = RoundedCornerShape((24 * scale).dp),
                modifier = Modifier.weight(1f).height((68 * scale).dp)
            ) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    MaterialSymbol("cycle", null, tint = LocalContentColor.current, size = fixedSp(24 * scale), filled = true)
                    Spacer(Modifier.width((8 * scale).dp))
                    AppText(if (regenerating) "正在重建任务" else "重新生成", AppTextRole.Label, color = LocalContentColor.current, designScale = scale, maxLines = 1)
                }
            }
            // Figma 849:6483 完成设置: Primary, celebration icon — the real publish gate.
            Surface(
                onClick = {
                    if (confirming || regenerating) return@Surface
                    confirming = true
                    viewModel.confirmGeneratedDeck(
                        route.taskId,
                        onSuccess = {
                            confirming = false
                            nav.returnToTopLevel()
                            nav.navigate(AppRoute.ProjectDetail(route.projectId))
                        },
                        onFailure = {
                            // 409 etc. surface through uiMessage; stay on the review page.
                            confirming = false
                        },
                    )
                },
                color = theme.primary, contentColor = theme.onPrimary,
                shape = RoundedCornerShape((24 * scale).dp),
                modifier = Modifier.weight(1f).height((68 * scale).dp)
            ) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    MaterialSymbol("celebration", null, tint = LocalContentColor.current, size = fixedSp(24 * scale), filled = true)
                    Spacer(Modifier.width((8 * scale).dp))
                    AppText(if (confirming) "正在发布" else "完成设置", AppTextRole.Label, color = LocalContentColor.current, designScale = scale, maxLines = 1)
                }
            }
        }
    }
}

/** Difficulty pill on the review question face; null (unlabeled) hides it. */
private fun reviewDifficultyBadge(difficulty: V25Difficulty?, theme: DeckTheme): SmartReviewBadge? = when (difficulty) {
    V25Difficulty.BASIC -> SmartReviewBadge("基础记忆", theme.secondary, theme.strongText)
    V25Difficulty.UNDERSTANDING -> SmartReviewBadge("理解分析", AppColors.Green.primarySecondary, AppColors.Green.ink)
    V25Difficulty.DEEP_QUESTION -> SmartReviewBadge("综合应用", AppColors.WarningSecondary, AppColors.WarningInk)
    null -> null
}

private data class SmartReviewBadge(val label: String, val background: Color, val content: Color)

@Composable
private fun ReviewFlipCard(taskRoute: AppRoute.SmartCardReview, card: V25Card, theme: DeckTheme, scale: Float) {
    var flipped by remember(card.cardId) { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (flipped) 180f else 0f, animationSpec = AppMotion.emphasisSpring(), label = "review flip")
    val shape = RoundedCornerShape((AppShapeRadius * scale).dp)
    val density = LocalDensity.current.density
    val index = card.position
    Box(
        Modifier.fillMaxWidth().height((208 * scale).dp).clip(shape)
            .clickable(interactionSource = remember(card.cardId) { MutableInteractionSource() }, indication = null) { flipped = !flipped }
    ) {
        ReviewFace(taskRoute, card, theme, answer = false, number = index, rotation = rotation, alpha = if (rotation <= 90f) 1f else 0f, shape, density, scale)
        ReviewFace(taskRoute, card, theme, answer = true, number = index, rotation = rotation, alpha = if (rotation > 90f) 1f else 0f, shape, density, scale)
    }
}

@Composable
private fun ReviewFace(
    @Suppress("UNUSED_PARAMETER") taskRoute: AppRoute.SmartCardReview,
    card: V25Card,
    theme: DeckTheme,
    answer: Boolean,
    number: Int,
    rotation: Float,
    alpha: Float,
    shape: RoundedCornerShape,
    density: Float,
    scale: Float
) {
    val badge = reviewDifficultyBadge(if (answer) null else card.targetDifficulty, theme)
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
                    AppText(
                        if (answer) "答案" else (number + 1).toString(),
                        AppTextRole.SectionTitle,
                        color = if (answer) theme.strongText else AppColors.TextIconLight,
                        designScale = scale
                    )
                }
                badge?.let {
                    Surface(shape = RoundedCornerShape(999.dp), color = it.background) {
                        AppText(it.label, AppTextRole.Label, modifier = Modifier.padding(horizontal = (16 * scale).dp, vertical = (8 * scale).dp), color = it.content, designScale = scale, maxLines = 1)
                    }
                }
            }
            AppText(
                if (answer) card.back else card.front,
                AppTextRole.Body,
                color = if (answer) theme.strongText else AppColors.TextIconLight,
                designScale = scale,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
