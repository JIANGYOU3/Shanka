package com.qiuzhao.flashcards.ui.auth

/**
 * Full error-code → user-facing message map (spec §4.1): every backend error code has
 * a dedicated Chinese message; unknown codes fall back to [UNKNOWN_ERROR_MESSAGE].
 * Transport failures never reach this table — they are mapped to the network message
 * before lookup (see AuthViewModel.authErrorMessage).
 */
object ErrorMessages {
    const val UNKNOWN_ERROR_MESSAGE = "操作失败，请稍后重试"

    private val byCode: Map<String, String> = mapOf(
        "VALIDATION_ERROR" to "请求参数有误，请检查输入",
        "AUTH_REQUIRED" to "请先登录",
        "AUTH_INVALID" to "登录已失效，请重新登录",
        "INVALID_CREDENTIALS" to "邮箱或密码错误",
        "EMAIL_TAKEN" to "邮箱已被占用",
        "RATE_LIMITED" to "请求过于频繁，请稍后重试",
        "IDEMPOTENCY_CONFLICT" to "请求冲突，请勿重复提交",
        "INTERNAL_ERROR" to "服务器内部错误，请稍后重试",
        "PDF_UPLOAD_INVALID" to "文件不符合要求，请上传有效 PDF",
        "PDF_PARSE_FAILED" to "PDF 解析失败，请换一份文件重试",
        "PDF_TOC_MISSING" to "PDF 缺少目录结构，无法生成",
        "PDF_NOT_FOUND" to "文件不存在或已删除",
        "CHAPTER_NOT_FOUND" to "章节不存在或已删除",
        "PROJECT_NOT_FOUND" to "项目不存在或已删除",
        "PROJECT_NAME_TAKEN" to "项目名称已存在",
        "PROJECT_STATE_CONFLICT" to "项目当前状态无法执行此操作，请刷新后重试",
        "PROJECT_HAS_ACTIVE_TASK" to "项目仍有进行中的生成任务；准备阶段任务可在删除确认中一起放弃",
        "API_KEY_UNAVAILABLE" to "AI 服务暂不可用，请稍后重试",
        "API_KEY_NOT_SET" to "请先在设置中配置 API Key",
        "TASK_NOT_FOUND" to "任务不存在或已删除",
        "TASK_STATE_CONFLICT" to "任务状态已变化，请刷新重试",
        "TASK_ZERO_CARDS" to "当前章节没有可生成的卡片",
        "SAMPLE_STALE" to "样卡已失效，请重新生成后确认",
        "TASK_NOT_RESUMABLE" to "任务无法继续",
        "TASK_IN_PROGRESS" to "资源正被任务使用；准备阶段任务可先放弃，正式生成需等待完成",
        "GENERATION_FAILED" to "生成失败，请稍后重试",
        "DECK_NOT_FOUND" to "牌组不存在或已删除",
        "CARD_NOT_FOUND" to "卡片不存在或已删除",
        "CARD_DELETE_WINDOW_EXPIRED" to "撤销时间已结束",
        "CARD_REWRITE_UNAVAILABLE" to "暂时无法生成改写预览",
        "CARD_VERSION_CONFLICT" to "卡片已被更新，请刷新后重试",
        "INVALID_LEARNING_TIMEZONE" to "学习时区无效，请在系统设置后重试",
        "INVALID_PREFERENCES" to "学习设置不符合要求，请检查后重试",
        "GENERATION_ITEM_CONFLICT" to "生成项冲突，请刷新重试",
        "IMPORT_PARSE_ERROR" to "导入内容解析失败，请检查格式",
        "REWRITE_SCHEMA_INVALID" to "改写结果不符合要求，请重试",
        "REVIEW_EVENT_INVALID" to "学习记录无效",
        "REVIEW_EVENT_CONFLICT" to "学习记录冲突，请刷新重试",
    )

    fun forCode(code: String?): String = code?.let { byCode[it] } ?: UNKNOWN_ERROR_MESSAGE
}
