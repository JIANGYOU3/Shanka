package com.qiuzhao.flashcards.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.qiuzhao.flashcards.ui.auth.ErrorMessages

/** 项目制卡全流程的共享确认/状态组件（Figma 1130:8079 / 784:4624 族）。 */

/** Server/parse failure code → the 「原因：xxx」 line every failure surface shares. */
internal fun failureReasonText(errorCode: String?): String =
    errorCode?.let { ErrorMessages.forCode(it) } ?: ErrorMessages.UNKNOWN_ERROR_MESSAGE

/**
 * Figma 1130:8079 删除确认弹窗：331dp、#E87F77、圆角 36、16dp padding；标题为
 * Section Title（rgba(0,0,0,0.8)），按钮行「不是喵」白底 +「是的喵」#BD3F3F（60dp 高、圆角 24）。
 * 项目版标题为「是否删除项目及所属卡组？」，卡组版为「是否删除卡组？」（交接文档 决策②）。
 */
@Composable
internal fun CuteConfirmDialog(
    title: String,
    busy: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        Surface(
            color = Color(0xFFE87F77),
            shape = RoundedCornerShape(36.dp),
            modifier = Modifier.width(331.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AppText(
                    title,
                    AppTextRole.SectionTitle,
                    color = Color(0xCC000000),
                    textAlign = TextAlign.Center
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Surface(
                        onClick = onDismiss,
                        enabled = !busy,
                        color = Color.White,
                        contentColor = Color(0xCC000000),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.weight(1f).height(60.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            AppText("不是喵", AppTextRole.Label, color = LocalContentColor.current)
                        }
                    }
                    Surface(
                        onClick = onConfirm,
                        enabled = !busy,
                        color = Color(0xFFBD3F3F),
                        contentColor = Color.White.copy(alpha = .9f),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.weight(1f).height(60.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            AppText("是的喵", AppTextRole.Label, color = LocalContentColor.current)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Figma 784:4624 识别内容弹窗族：331dp、圆角 36、24dp padding、20dp 间距的居中列——
 * 80dp 进度环（或失败 80dp error 图标）+ Page Title + 副标题。等待/生成态用 [container]
 * （默认 #EEF4FA，主题页传 theme.background）与 #8C939A 副标题；失败态整卡 #E87F77、
 * 标题「解析失败/生成失败，点击重试」、副标题「原因：xxx」（#670700），整卡可点重试。
 * [paused] 为本地展示暂停（交接文档 决策①）：环停转、显示 pause 图标，服务端不受影响。
 */
@Composable
internal fun StatusProgressCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    container: Color = Color(0xFFEEF4FA),
    ringColor: Color = Color(0xFF716FDD),
    failed: Boolean = false,
    failureReason: String? = null,
    paused: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        onClick = onClick ?: {},
        enabled = onClick != null,
        color = if (failed) Color(0xFFE87F77) else container,
        shape = RoundedCornerShape(36.dp),
        modifier = modifier.width(331.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            if (failed) {
                MaterialSymbol("error", null, tint = Color(0xCC000000), size = fixedSp(80f), filled = true)
            } else if (paused) {
                MaterialSymbol("pause_circle", null, tint = ringColor, size = fixedSp(80f), filled = true)
            } else {
                CircularProgressIndicator(
                    color = ringColor,
                    trackColor = Color.Transparent,
                    strokeCap = StrokeCap.Round,
                    strokeWidth = 6.dp,
                    modifier = Modifier.size(80.dp)
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AppText(
                    title,
                    AppTextRole.PageTitle,
                    color = Color(0xCC000000),
                    textAlign = TextAlign.Center
                )
                AppText(
                    if (failed) "原因：${failureReason ?: "未知原因"}" else subtitle,
                    AppTextRole.CardSubtitle,
                    color = if (failed) Color(0xFF670700) else Color(0xFF8C939A),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/** Figma 807:4441: the failure caption under a failed material card. */
@Composable
internal fun MaterialFailureHint(reason: String, designScale: Float = 1f) =
    AppText(
        "解析失败：$reason\n点击重试",
        AppTextRole.CardSubtitle,
        color = Color(0x80000000),
        designScale = designScale
    )
