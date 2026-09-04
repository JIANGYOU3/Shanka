package com.qiuzhao.flashcards.ui

import android.animation.ValueAnimator
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.qiuzhao.flashcards.ui.navigation.AppRoute
import com.qiuzhao.flashcards.ui.motion.AppMotion
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.cos
import kotlin.random.Random

@Composable
internal fun LoginScreen(
    viewModel: AppViewModel,
    nav: ScreenNavigator,
    showBack: Boolean = false
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var loginRevealStarted by remember { mutableStateOf(false) }
    var loginButtonBounds by remember { mutableStateOf<Rect?>(null) }
    val scale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(.75f, 1f)
    // The container transform is atomic: leave the authenticated destination
    // visible only after its source button has completely settled.
    BackHandler(enabled = loginRevealStarted) {}
    Box(
        modifier = Modifier.fillMaxSize().background(AppColors.Blue.background)
    ) {
        LoginBackgroundCards(scale)
        if (showBack) {
            Box(
                modifier = Modifier.align(Alignment.TopStart)
                    .zIndex(2f)
                    .padding(start = (16 * scale).dp, top = (64 * scale).dp)
            ) {
                RoundIconButton(
                    symbol = "arrow_back",
                    description = "返回",
                    color = AppColors.Blue.background,
                    onClick = nav::popBackStack,
                    size = (56 * scale).dp,
                    tint = AppColors.Blue.ink
                )
            }
        }
        Column(
            modifier = Modifier.fillMaxSize().zIndex(1f).imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = (16 * scale).dp, top = (239 * scale).dp, end = (16 * scale).dp, bottom = (32 * scale).dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AppText(
                "欢迎使用，请登录",
                AppTextRole.AuthHeroTitle,
                color = AppColors.TextIconDark,
                designScale = scale,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height((56 * scale).dp))
            Surface(
                color = AppColors.Card,
                shape = RoundedCornerShape((48 * scale).dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding((24 * scale).dp),
                    verticalArrangement = Arrangement.spacedBy((12 * scale).dp)
                ) {
                    AuthField(
                        label = "邮箱", placeholder = "请输入邮箱", icon = "alternate_email",
                        value = email, onValueChange = { email = it }, secret = false, scale = scale,
                        fieldColor = AppColors.Blue.surface, fieldCornerRadius = 32f
                    )
                    AuthField(
                        label = "密码", placeholder = "请输入密码", icon = "lock",
                        value = password, onValueChange = { password = it }, secret = true, scale = scale,
                        fieldColor = AppColors.Blue.surface, fieldCornerRadius = 32f
                    )
                    AppText(
                        "忘记密码？",
                        AppTextRole.CardSubtitle,
                        modifier = Modifier.fillMaxWidth().clickable { message = "请使用注册邮箱联系支持以重置密码。" },
                        color = AppColors.Blue.ink,
                        designScale = scale,
                        textAlign = TextAlign.End
                    )
                    message?.let { AuthMessage(it, scale) { message = null } }
                    AuthIconButton(
                        text = "完成登录",
                        icon = "login",
                        color = AppColors.Blue.primary,
                        contentColor = AppColors.TextIconLight,
                        scale = scale,
                        enabled = !loginRevealStarted,
                        modifier = Modifier.onGloballyPositioned { coordinates ->
                            loginButtonBounds = coordinates.boundsInRoot()
                        }
                    ) {
                        viewModel.login(email, password) { error ->
                            if (error == null) loginRevealStarted = true else message = error
                        }
                    }
                    AuthIconButton(
                        text = "还未注册",
                        icon = "app_registration",
                        color = AppColors.Blue.surface,
                        contentColor = AppColors.TextIconDark,
                        scale = scale,
                        enabled = !loginRevealStarted
                    ) {
                        nav.navigate(AppRoute.Register)
                    }
                }
            }
        }
        if (loginRevealStarted) {
            LoginSuccessReveal(
                buttonBounds = loginButtonBounds,
                scale = scale,
                onFinished = nav::popBackStack
            )
        }
    }
}

/**
 * Figma 184:616 is the light homepage destination. After a successful login,
 * the exact login button surface grows into that white page background before
 * the navigation stack reveals Home underneath it.
 */
@Composable
private fun LoginSuccessReveal(
    buttonBounds: Rect?,
    scale: Float,
    onFinished: () -> Unit
) {
    val progress = remember { Animatable(0f) }
    val latestOnFinished by rememberUpdatedState(onFinished)
    val density = LocalDensity.current
    val interactionSource = remember { MutableInteractionSource() }
    LaunchedEffect(Unit) {
        // Respect the system animation setting: reduced motion goes directly to
        // the authenticated Home destination instead of leaving a blank overlay.
        if (!ValueAnimator.areAnimatorsEnabled()) {
            latestOnFinished()
        } else {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = AppMotion.containerTransformEnter()
            )
            // Let the expanded white surface settle for one rendered frame before
            // Home is exposed below it.
            delay(16)
            latestOnFinished()
        }
    }
    Box(
        modifier = Modifier.fillMaxSize().zIndex(10f)
            // While the login transition owns the screen, consume incidental taps
            // so a button underneath cannot start a second auth action.
            .clickable(interactionSource = interactionSource, indication = null) {}
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val fallbackBounds = with(density) {
                Rect(
                    left = (40f * scale).dp.toPx(),
                    top = (650f * scale).dp.toPx(),
                    right = (362f * scale).dp.toPx(),
                    bottom = (722f * scale).dp.toPx()
                )
            }
            val start = buttonBounds ?: fallbackBounds
            // Overscan guarantees every pixel, including edge-to-edge system-bar
            // space, has become the Figma homepage's white background at 100%.
            val finish = Rect(
                left = -size.width * .12f,
                top = -size.height * .12f,
                right = size.width * 1.12f,
                bottom = size.height * 1.12f
            )
            val fraction = progress.value
            fun between(startValue: Float, endValue: Float) =
                startValue + (endValue - startValue) * fraction
            val current = Rect(
                left = between(start.left, finish.left),
                top = between(start.top, finish.top),
                right = between(start.right, finish.right),
                bottom = between(start.bottom, finish.bottom)
            )
            val startRadius = start.height / 3f
            val currentRadius = startRadius * (1f - fraction)
            // This is a Material container transform rather than a scale effect:
            // its bounds grow from the measured source button, while the corner
            // continuously resolves into the destination page's square edge.
            // The colour starts shifting after the expansion is visibly underway
            // and remains continuous through the Figma homepage's #FFFFFF.
            val color = lerp(
                AppColors.Blue.primary,
                AppColors.Card,
                ((fraction - .12f) / .88f).coerceIn(0f, 1f)
            )
            drawRoundRect(
                color = color,
                topLeft = Offset(current.left, current.top),
                size = Size(current.width, current.height),
                cornerRadius = CornerRadius(currentRadius, currentRadius)
            )
        }
    }
}

@Composable
internal fun RegisterScreen(viewModel: AppViewModel, nav: ScreenNavigator) {
    var nickname by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    AuthLayout(title = "注册", onBack = nav::popBackStack) { scale ->
        item {
            AuthHintCard("注册完成后请使用邮箱与密码登录。", scale)
        }
        item { AuthField("昵称", "请输入昵称", "badge", nickname, { nickname = it }, false, scale) }
        item { AuthField("邮箱", "请输入邮箱", "alternate_email", email, { email = it }, false, scale) }
        item { AuthField("密码", "至少 6 位", "lock", password, { password = it }, true, scale) }
        item { AuthField("确认密码", "再次输入密码", "lock", confirmation, { confirmation = it }, true, scale) }
        message?.let { text -> item { AuthMessage(text, scale) { message = null } } }
        item {
            AuthPrimaryButton("完成注册", scale) {
                viewModel.register(nickname, email, password, confirmation) { error ->
                    if (error == null) nav.popBackStack() else message = error
                }
            }
        }
    }
}

@Composable
private fun AuthLayout(title: String, onBack: () -> Unit, content: androidx.compose.foundation.lazy.LazyListScope.(Float) -> Unit) {
    val scale = (LocalConfiguration.current.screenWidthDp / 402f).coerceIn(.75f, 1f)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().imePadding()
                    .padding(start = (16 * scale).dp, top = (136 * scale).dp, end = (16 * scale).dp)
                    .clip(RoundedCornerShape((AppScrollableContentClipRadius * scale).dp)),
                contentPadding = PaddingValues(bottom = (NaturalScrollTail * scale).dp),
                verticalArrangement = Arrangement.spacedBy((16 * scale).dp)
            ) { content(scale) }
            ScreenTopInformationBar(title, null, onBack, modifier = Modifier.zIndex(1f))
        }
    }
}

@Composable
private fun AuthField(
    label: String,
    placeholder: String,
    icon: String,
    value: String,
    onValueChange: (String) -> Unit,
    secret: Boolean,
    scale: Float,
    fieldColor: Color = AppColors.Blue.background,
    fieldCornerRadius: Float = 16f
) {
    val textRole = if (value.isBlank()) AppTextRole.Supporting else AppTextRole.CardTitle
    Column(verticalArrangement = Arrangement.spacedBy((12 * scale).dp)) {
        AppText(label, AppTextRole.SectionTitle, modifier = Modifier.padding(horizontal = (8 * scale).dp), color = AppColors.TextIconDark, designScale = scale)
        Surface(
            color = fieldColor,
            shape = RoundedCornerShape((fieldCornerRadius * scale).dp),
            modifier = Modifier.fillMaxWidth().height((72 * scale).dp)
        ) {
            Box(Modifier.padding(horizontal = (24 * scale).dp), contentAlignment = Alignment.CenterStart) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = appInputTextStyle(textRole, scale, AppColors.TextIconDark),
                    visualTransformation = if (secret) PasswordVisualTransformation() else rememberBilingualInputTransformation(textRole, scale),
                    singleLine = true,
                    decorationBox = { inner ->
                        Box(Modifier.fillMaxWidth()) {
                            androidx.compose.foundation.layout.Row(
                                modifier = Modifier.align(Alignment.CenterStart),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                MaterialSymbol(icon, null, tint = AppColors.Blue.ink, size = fixedSp(24 * scale), filled = true)
                                Spacer(Modifier.width((16 * scale).dp))
                            }
                            Box(
                                Modifier.fillMaxWidth()
                                    .padding(start = (40 * scale).dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (value.isBlank()) {
                                    AppText(
                                        placeholder,
                                        AppTextRole.Supporting,
                                        color = AppColors.TextIconDark.copy(alpha = .625f),
                                        designScale = scale
                                    )
                                }
                                inner()
                            }
                        }
                    }
                )
            }
        }
    }
}

/** Figma 427:4182's four oversized background flashcards. */
@Composable
private fun LoginBackgroundCards(scale: Float) {
    // The Figma layout uses one intentional accent card: the card at the
    // upper-left starts on its blue reverse side, while the other three begin
    // on the lighter default face.
    val rotations = remember {
        List(4) { cardIndex ->
            Animatable(if (cardIndex == 3) 180f else 0f)
        }
    }
    val manualRoundStarts = remember { Channel<Int>(Channel.CONFLATED) }
    var manualInputLocked by remember { mutableStateOf(false) }
    val flipSpec = remember {
        spring<Float>(
            // The background cards should feel playful, but remain much slower
            // than an in-study flashcard flip.
            dampingRatio = .72f,
            stiffness = 58f
        )
    }
    LaunchedEffect(rotations, manualRoundStarts, flipSpec) {
        while (isActive) {
            val requestedStart = withTimeoutOrNull(
                Random.nextLong(1_000L, 3_001L)
            ) {
                manualRoundStarts.receive()
            }
            if (requestedStart != null) {
                // A manual round always runs to its settled, face-forward end.
                // Cancelling it midway can leave an Animatable at 90°, which
                // projects the card as an incorrectly narrow sliver.
                playBackgroundFlipRound(requestedStart, rotations, flipSpec)
                manualInputLocked = false
            } else {
                // In the resting state, cards have no sequence: one card is
                // chosen at random after each 1–3 second quiet interval.
                val randomCard = Random.nextInt(rotations.size)
                flipBackgroundCard(randomCard, rotations, flipSpec)
            }
        }
    }
    val startManualRound: (Int) -> Unit = { cardIndex ->
        // Ignore all presses while either an automatic turn or the full manual
        // clockwise sequence is in flight. This guarantees a new turn always
        // starts from a stable face, never from its edge-on midpoint.
        if (!manualInputLocked && rotations.none { it.isRunning }) {
            if (manualRoundStarts.trySend(cardIndex).isSuccess) {
                manualInputLocked = true
            }
        }
    }
    // Keep every drawing canvas in a single background layer. Without this
    // wrapper, the individual full-screen canvases become root siblings and
    // can paint over the scrolling login form.
    Box(modifier = Modifier.fillMaxSize()) {
        // The outer bounds are Figma's post-rotation boxes; the inner card retains
        // its original size. Centering it here avoids the positional drift caused by
        // rotating each raw card directly from its top-left corner.
        AnimatedBackgroundCard(
            boxX = -732f, boxY = 132.9f, boxWidth = 1021.88f, boxHeight = 946.847f,
            cardWidth = 820.117f, cardHeight = 628.836f, cornerRadius = 145f,
            flipDegrees = rotations[0].value, scale = scale
        )
        AnimatedBackgroundCard(
            boxX = 120.64f, boxY = 531.99f, boxWidth = 448.3f, boxHeight = 588.762f,
            cardWidth = 202.586f, cardHeight = 560.676f, cornerRadius = 145f,
            flipDegrees = rotations[1].value, scale = scale
        )
        AnimatedBackgroundCard(
            boxX = 148.19f, boxY = -55f, boxWidth = 565.51f, boxHeight = 588.082f,
            cardWidth = 395.741f, cardHeight = 453.285f, cornerRadius = 133f,
            flipDegrees = rotations[2].value, scale = scale
        )
        AnimatedBackgroundCard(
            boxX = -266.45f, boxY = -264f, boxWidth = 570.531f, boxHeight = 597.179f,
            cardWidth = 395.741f, cardHeight = 463.676f, cornerRadius = 99f,
            flipDegrees = rotations[3].value, scale = scale
        )
    }
    // This layer contains only hit targets, so it can sit above the form without
    // intercepting fields or buttons. The old targets reused the oversized Figma
    // wrappers; Compose clamps those wrappers and left only the upper-right
    // target interactive. These bounds instead match each card's visible region.
    Box(
        modifier = Modifier.fillMaxSize().zIndex(2f)
    ) {
        BackgroundCardTapTarget(0f, 0f, 196f, 327f, scale, "左上背景卡片") {
            startManualRound(3)
        }
        BackgroundCardTapTarget(196f, 0f, 206f, 327f, scale, "右上背景卡片") {
            startManualRound(2)
        }
        BackgroundCardTapTarget(0f, 800f, 80f, 74f, scale, "左下背景卡片") {
            startManualRound(0)
        }
        BackgroundCardTapTarget(322f, 800f, 80f, 74f, scale, "右下背景卡片") {
            startManualRound(1)
        }
    }
}

private suspend fun playBackgroundFlipRound(
    startCardIndex: Int,
    rotations: List<Animatable<Float, *>>,
    flipSpec: androidx.compose.animation.core.AnimationSpec<Float>
) = coroutineScope {
    val cardCount = rotations.size
    val startPosition = BackgroundCardClockwiseOrder.indexOf(startCardIndex).coerceAtLeast(0)
    (0 until cardCount).map { step ->
        launch {
            // The cards begin one after another without waiting for the prior
            // card's full spring settle, retaining a gentle staggered rhythm.
            delay(step * 700L)
            flipBackgroundCard(
                BackgroundCardClockwiseOrder[(startPosition + step) % cardCount],
                rotations,
                flipSpec
            )
        }
    }.joinAll()
}

private suspend fun flipBackgroundCard(
    cardIndex: Int,
    rotations: List<Animatable<Float, *>>,
    flipSpec: androidx.compose.animation.core.AnimationSpec<Float>
) {
    val rotation = rotations[cardIndex]
    rotation.animateTo(targetValue = rotation.value + 180f, animationSpec = flipSpec)
}

/** Screen positions in clockwise order: left-top → right-top → right-bottom → left. */
private val BackgroundCardClockwiseOrder = listOf(3, 2, 1, 0)

@Composable
private fun BackgroundCardTapTarget(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    scale: Float,
    description: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier.offset(x = (x * scale).dp, y = (y * scale).dp)
            .size(width = (width * scale).dp, height = (height * scale).dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClickLabel = description,
                onClick = onClick
            )
    )
}

@Composable
private fun AnimatedBackgroundCard(
    boxX: Float,
    boxY: Float,
    boxWidth: Float,
    boxHeight: Float,
    cardWidth: Float,
    cardHeight: Float,
    cornerRadius: Float,
    flipDegrees: Float,
    scale: Float
) {
    // Keep each face front-facing at the end of a 180° flip. Besides matching a
    // physical two-sided flashcard, this keeps either face visible after a turn.
    val turn = ((flipDegrees % 360f) + 360f) % 360f
    val faceRotation = turn % 180f
    val showingBack = turn in 90f..270f
    val faceColor = if (showingBack) AppColors.Blue.primary else AppColors.Blue.primarySecondary
    // The Figma card wrappers are intentionally larger than the viewport. A
    // Compose child using those dimensions is constrained and re-centred by its
    // parent, which displaced the lower-left card. Draw in the viewport instead:
    // the Figma wrapper gives the unrotated card's centre exactly, while Canvas
    // clips only at the actual screen boundary.
    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(
            x = (boxX + boxWidth / 2f) * scale * density,
            y = (boxY + boxHeight / 2f) * scale * density
        )
        val cardSize = Size(
            width = cardWidth * scale * density,
            height = cardHeight * scale * density
        )
        val radius = cornerRadius * scale * density
        // A local X compression is the visible projection of the card's local
        // Y-axis turn. The static Figma tilt is applied outside that turn.
        val localXScale = abs(cos(Math.toRadians(faceRotation.toDouble()))).toFloat()
            .coerceAtLeast(.02f)
        withTransform({
            rotate(degrees = 28.9f, pivot = center)
            scale(scaleX = localXScale, scaleY = 1f, pivot = center)
        }) {
            drawRoundRect(
                color = faceColor,
                topLeft = Offset(
                    x = center.x - cardSize.width / 2f,
                    y = center.y - cardSize.height / 2f
                ),
                size = cardSize,
                cornerRadius = CornerRadius(radius, radius)
            )
        }
    }
}

@Composable
private fun AuthHintCard(text: String, scale: Float) = Surface(
    color = AppColors.Blue.background,
    shape = RoundedCornerShape((AppNestedShapeRadius * scale).dp),
    modifier = Modifier.fillMaxWidth().height((72 * scale).dp)
) {
    Box(
        modifier = Modifier.padding(horizontal = (24 * scale).dp),
        contentAlignment = Alignment.CenterStart
    ) {
        AppText(text, AppTextRole.Supporting, color = AppColors.TextIconDark, designScale = scale)
    }
}

@Composable
private fun AuthPrimaryButton(text: String, scale: Float, onClick: () -> Unit) = Surface(
    onClick = onClick,
    color = AppColors.Blue.primary,
    contentColor = AppColors.TextIconLight,
    shape = RoundedCornerShape((24 * scale).dp),
    modifier = Modifier.fillMaxWidth().height((60 * scale).dp)
) {
    Box(contentAlignment = Alignment.Center) { AppText(text, AppTextRole.Label, color = LocalContentColor.current, designScale = scale) }
}

@Composable
private fun AuthIconButton(
    text: String,
    icon: String,
    color: Color,
    contentColor: Color,
    scale: Float,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) = Surface(
    onClick = onClick,
    enabled = enabled,
    color = color,
    contentColor = contentColor,
    shape = RoundedCornerShape((24 * scale).dp),
    modifier = modifier.fillMaxWidth().height((72 * scale).dp)
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        MaterialSymbol(icon, null, tint = LocalContentColor.current, size = fixedSp(24 * scale), filled = true)
        Spacer(Modifier.width((8 * scale).dp))
        AppText(text, AppTextRole.Label, color = LocalContentColor.current, designScale = scale)
    }
}

@Composable
private fun AuthSecondaryButton(text: String, scale: Float, onClick: () -> Unit) = Surface(
    onClick = onClick,
    color = AppColors.Blue.background,
    contentColor = AppColors.Blue.ink,
    shape = RoundedCornerShape((24 * scale).dp),
    modifier = Modifier.fillMaxWidth().height((56 * scale).dp)
) {
    Box(contentAlignment = Alignment.Center) { AppText(text, AppTextRole.Label, color = LocalContentColor.current, designScale = scale) }
}

@Composable
private fun AuthMessage(text: String, scale: Float, onDismiss: () -> Unit) {
    var countdown by remember(text) { mutableStateOf(3) }
    LaunchedEffect(text) {
        while (countdown > 0) {
            delay(1_000L)
            countdown -= 1
        }
        onDismiss()
    }
    Surface(
        color = AppColors.Pink.background,
        shape = RoundedCornerShape((16 * scale).dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding((16 * scale).dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppText(text, AppTextRole.Supporting, color = AppColors.Warning, designScale = scale)
            Spacer(Modifier.weight(1f))
            AppText("【$countdown】", AppTextRole.Supporting, color = AppColors.Warning, designScale = scale)
        }
    }
}
