package com.qiuzhao.flashcards.ui

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qiuzhao.flashcards.BuildConfig
import com.qiuzhao.flashcards.data.CardDraft
import com.qiuzhao.flashcards.data.remote.ApiKeyStatus
import com.qiuzhao.flashcards.data.remote.ApiResult
import com.qiuzhao.flashcards.data.remote.Dashboard
import com.qiuzhao.flashcards.data.remote.DeckProgress
import com.qiuzhao.flashcards.data.remote.DeckSummary
import com.qiuzhao.flashcards.data.remote.FlashcardEntity
import com.qiuzhao.flashcards.data.remote.GeneratedTask
import com.qiuzhao.flashcards.data.remote.PdfChapter
import com.qiuzhao.flashcards.data.remote.PdfFile
import com.qiuzhao.flashcards.data.remote.MaterialSummary
import com.qiuzhao.flashcards.data.remote.MaterialStatus
import com.qiuzhao.flashcards.data.remote.MaterialType
import com.qiuzhao.flashcards.data.remote.ProjectSummary
import com.qiuzhao.flashcards.data.remote.Rating
import com.qiuzhao.flashcards.data.remote.RemoteFlashcardRepository
import com.qiuzhao.flashcards.data.remote.projectsForDisplay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

data class PdfGenerationConfig(
    val quantity: String = "BALANCED",
    val basic: Float = .4f,
    val understanding: Float = .4f,
    val application: Float = .2f,
    val requirement: String = ""
)

/** Ephemeral handoff from pasted text into the shared smart-card generation flow. */
data class TextImportFlow(val deckName: String, val cards: List<CardDraft>)
data class LocalAccount(val nickname: String, val email: String)
data class AccountBootstrap(val loaded: Boolean = false, val account: LocalAccount? = null)

data class PdfReadFailure(val title: String, val detail: String)

/** Server-derived weekly activity. It is not calculated from a local review history. */
data class WeeklyActivityData(
    val dailyCounts: List<Int> = List(7) { 0 },
    val total: Int = 0,
    val previousTotal: Int = 0,
    val changePercent: Int? = null
)

private fun dashboardWeeklyActivity(dashboard: Dashboard?): WeeklyActivityData {
    val raw = dashboard?.raw ?: return WeeklyActivityData()
    val activity = raw.optJSONArray("weekly_activity")
    val dailyCounts = List(7) { index -> activity?.optInt(index, 0) ?: 0 }
    val changePercent = if (raw.has("week_change_rate") && !raw.isNull("week_change_rate")) {
        (raw.optDouble("week_change_rate") * 100).roundToInt()
    } else {
        null
    }
    return WeeklyActivityData(
        dailyCounts = dailyCounts,
        total = raw.optInt("weekly_total", dailyCounts.sum()),
        changePercent = changePercent
    )
}

private val Application.dataStore by preferencesDataStore("preferences")
private val DARK_THEME = booleanPreferencesKey("dark_theme")
private val WEEKLY_GOAL = intPreferencesKey("weekly_goal")
private val FRONTEND_TEST_MODE = booleanPreferencesKey("frontend_test_mode")
private val ACCOUNT_EMAIL = stringPreferencesKey("account_email")
private val ACCOUNT_NICKNAME = stringPreferencesKey("account_nickname")
private val ACCOUNT_LOGGED_IN = booleanPreferencesKey("account_logged_in")
private const val DEFAULT_WEEKLY_GOAL = 50

/** Local-only fixture data for visual and interaction testing. Edit these values to exercise UI states without changing a server record. */
private object FrontendTestFixtures {
    val projects: List<ProjectSummary> = listOf(
        ProjectSummary(id = "frontend-project-design", name = "世界现代设计史", themeKey = "violet", materialCount = 2),
        ProjectSummary(id = "frontend-project-mechanics", name = "机械振动基础", themeKey = "azure", materialCount = 1)
    )

    val materials: List<MaterialSummary> = listOf(
        MaterialSummary("frontend-material-design-pdf", "世界现代设计史.pdf", MaterialType.PDF, MaterialStatus.READY, listOf("frontend-project-design"), chapterCount = 12),
        MaterialSummary("frontend-material-design-md", "课堂笔记.md", MaterialType.MARKDOWN, MaterialStatus.READY, listOf("frontend-project-design")),
        MaterialSummary("frontend-material-mechanics-pdf", "振动系统.pdf", MaterialType.PDF, MaterialStatus.PARSING, listOf("frontend-project-mechanics"), chapterCount = 3)
    )

    val decks: List<DeckSummary> = listOf(
        DeckSummary(id = "frontend-test-design", name = "现代设计史", chapter = 3, source = "FRONTEND_TEST", themeKey = "violet", cardCount = 48, dueCount = 12, masteredCards = 31, reviewCount = 86, masteryRatio = .65f, projectId = "frontend-project-design", materialScopes = listOf(com.qiuzhao.flashcards.data.remote.DeckMaterialScope("frontend-material-design-pdf", listOf("chapter-1")))),
        DeckSummary(id = "frontend-test-agent", name = "乌尔姆学院", chapter = 6, source = "FRONTEND_TEST", themeKey = "violet", cardCount = 26, dueCount = 6, masteredCards = 14, reviewCount = 43, masteryRatio = .54f, projectId = "frontend-project-design", materialScopes = listOf(com.qiuzhao.flashcards.data.remote.DeckMaterialScope("frontend-material-design-pdf", listOf("chapter-2")))),
        DeckSummary(id = "frontend-test-legacy", name = "未归类卡组", source = "FRONTEND_TEST", themeKey = "azure", cardCount = 12, dueCount = 3, masteredCards = 4, reviewCount = 9)
    )

    val cards: Map<String, List<FlashcardEntity>> = mapOf(
        "frontend-test-design" to listOf(
            FlashcardEntity("frontend-test-design-1", "frontend-test-design", "可用性原则关注什么？", "让用户一眼理解界面元素能做什么，以及如何操作。", position = 0, source = "FRONTEND_TEST"),
            FlashcardEntity("frontend-test-design-2", "frontend-test-design", "反馈在交互中的作用是什么？", "及时展示操作结果，帮助用户确认系统已经响应。", position = 1, source = "FRONTEND_TEST"),
            FlashcardEntity("frontend-test-design-3", "frontend-test-design", "什么是认知负荷？", "用户为了理解界面、完成任务而消耗的心理资源。", position = 2, source = "FRONTEND_TEST")
        ),
        "frontend-test-agent" to listOf(
            FlashcardEntity("frontend-test-agent-1", "frontend-test-agent", "Agent 的工具调用需要哪些约束？", "明确输入、输出、权限和失败后的恢复策略。", position = 0, source = "FRONTEND_TEST"),
            FlashcardEntity("frontend-test-agent-2", "frontend-test-agent", "为什么要保留可追踪的执行记录？", "它能帮助定位失败、复现问题并评估改进效果。", position = 1, source = "FRONTEND_TEST")
        )
    )

    val dashboard = Dashboard(
        hasData = true, weeklyGoal = 60, completed = 42, masteryRatio = .68f,
        raw = JSONObject()
            .put("weekly_activity", JSONArray(listOf(4, 7, 5, 9, 6, 8, 3)))
            .put("weekly_total", 42)
            .put("week_change_rate", .2)
            .put("recall_accuracy", .91)
            .put("first_answer_accuracy", .78)
            .put("retention_rate", .86)
            .put("mastered_card_count", 1360)
            .put("streak_days", 12)
    )

    val pdfChapters = listOf(
        PdfChapter("frontend-pdf-chapter-1", "第一章 设计的基本原则", 1, 18),
        PdfChapter("frontend-pdf-chapter-2", "第二章 用户认知与反馈", 19, 36),
        PdfChapter("frontend-pdf-chapter-3", "第三章 信息架构", 37, 52)
    )

    val pdfSamples = listOf(
        CardDraft("可用性原则关注什么？", "让用户一眼理解界面元素能做什么，以及如何操作。"),
        CardDraft("反馈在交互中的作用是什么？", "及时展示操作结果，帮助用户确认系统已经响应。"),
        CardDraft("什么是认知负荷？", "用户为了理解界面、完成任务而消耗的心理资源。")
    )
}

/**
 * Business data is server-authoritative. DataStore keeps only user preferences and the network
 * layer owns the encrypted device id and debug evidence; Room is deliberately not instantiated.
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = RemoteFlashcardRepository(application)

    val darkTheme: StateFlow<Boolean?> = application.dataStore.data.map { preferences ->
        if (preferences.contains(DARK_THEME)) preferences[DARK_THEME] else null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val frontendTestMode: StateFlow<Boolean> = application.dataStore.data
        .map { preferences -> preferences[FRONTEND_TEST_MODE] ?: false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val accountBootstrap: StateFlow<AccountBootstrap> = application.dataStore.data.map { preferences ->
        val account = if (preferences[ACCOUNT_LOGGED_IN] == true) {
            val email = preferences[ACCOUNT_EMAIL].orEmpty()
            val nickname = preferences[ACCOUNT_NICKNAME].orEmpty()
            if (email.isBlank() || nickname.isBlank()) null else LocalAccount(nickname, email)
        } else null
        AccountBootstrap(loaded = true, account = account)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountBootstrap())
    val weeklyGoal = application.dataStore.data.map { it[WEEKLY_GOAL] ?: DEFAULT_WEEKLY_GOAL }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DEFAULT_WEEKLY_GOAL)

    private val _frontendTestDecks: MutableStateFlow<List<DeckSummary>> = MutableStateFlow(FrontendTestFixtures.decks)
    private val _frontendTestCards: MutableStateFlow<Map<String, List<FlashcardEntity>>> = MutableStateFlow(FrontendTestFixtures.cards)
    private val _frontendTestProjects: MutableStateFlow<List<ProjectSummary>> = MutableStateFlow(FrontendTestFixtures.projects)
    private val _frontendTestMaterials: MutableStateFlow<List<MaterialSummary>> = MutableStateFlow(FrontendTestFixtures.materials)
    val decks: StateFlow<List<DeckSummary>> = combine(frontendTestMode, repository.decks, _frontendTestDecks) { enabled, remote, test ->
        if (enabled) test else remote
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val dueCount: StateFlow<Int> = combine(frontendTestMode, repository.dueCount(), _frontendTestDecks) { enabled, remote, test ->
        if (enabled) test.sumOf { it.dueCount } else remote
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    /** Production decks remain visible in a per-device legacy project until the project API is live. */
    val projects: StateFlow<List<ProjectSummary>> = combine(frontendTestMode, repository.decks, _frontendTestProjects, _frontendTestDecks) { enabled, remote, testProjects, testDecks ->
        if (enabled) projectsForDisplay(testProjects, testDecks) else projectsForDisplay(emptyList(), remote)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    /** Real materials stay empty until the backend confirms the materials list endpoint. */
    val materials: StateFlow<List<MaterialSummary>> = combine(frontendTestMode, _frontendTestMaterials) { enabled, test ->
        if (enabled) test else emptyList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _studyCards = MutableStateFlow<List<FlashcardEntity>>(emptyList())
    val studyCards: StateFlow<List<FlashcardEntity>> = _studyCards
    private val _dashboard = MutableStateFlow<Dashboard?>(null)
    val dashboard: StateFlow<Dashboard?> = combine(frontendTestMode, _dashboard) { enabled, remote ->
        if (enabled) FrontendTestFixtures.dashboard else remote
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val weeklyActivity = dashboard.map(::dashboardWeeklyActivity)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeeklyActivityData())
    private val _apiKeyStatus = MutableStateFlow<ApiKeyStatus?>(null)
    val apiKeyStatus: StateFlow<ApiKeyStatus?> = _apiKeyStatus
    private val _pdfFile = MutableStateFlow<PdfFile?>(null)
    val pdfFile: StateFlow<PdfFile?> = _pdfFile
    private val _pdfSamples = MutableStateFlow<List<CardDraft>>(emptyList())
    val pdfSamples: StateFlow<List<CardDraft>> = _pdfSamples
    private val _pdfTask = MutableStateFlow<GeneratedTask?>(null)
    val pdfTask: StateFlow<GeneratedTask?> = _pdfTask
    private val _pdfTaskDeckId = MutableStateFlow<String?>(null)
    val pdfTaskDeckId: StateFlow<String?> = _pdfTaskDeckId
    private val _textImportFlow = MutableStateFlow<TextImportFlow?>(null)
    val textImportFlow: StateFlow<TextImportFlow?> = _textImportFlow

    init {
        refreshDecks()
        refreshDashboard()
    }

    fun refreshDecks() = viewModelScope.launch {
        if (!frontendTestMode.value) logFailure("list_decks", repository.refreshDecks())
    }
    fun refreshDashboard() = viewModelScope.launch {
        if (frontendTestMode.value) return@launch
        val goal = getApplication<Application>().dataStore.data.first()[WEEKLY_GOAL] ?: DEFAULT_WEEKLY_GOAL
        when (val result = repository.dashboard(goal)) {
            is ApiResult.Success -> _dashboard.value = result.value
            is ApiResult.Failure -> logFailure("dashboard", result)
        }
    }

    fun setWeeklyGoal(value: Int) = viewModelScope.launch {
        getApplication<Application>().dataStore.edit { it[WEEKLY_GOAL] = value.coerceAtLeast(1) }
        if (!frontendTestMode.value) refreshDashboard()
    }

    fun setFrontendTestMode(enabled: Boolean) = viewModelScope.launch {
        getApplication<Application>().dataStore.edit { it[FRONTEND_TEST_MODE] = enabled }
    }

    /**
     * Project creation is intentionally local to the visual-test mode until the
     * project write contract is supplied by the service. This prevents a new UI
     * from guessing a production endpoint or silently creating orphaned decks.
     */
    fun createFrontendTestProject(name: String, themeKey: String, onResult: (String?) -> Unit) = viewModelScope.launch {
        val normalizedName = name.trim()
        when {
            !frontendTestMode.value -> onResult("项目服务尚未接入，当前只能在 UI 测试模式中创建项目")
            normalizedName.isBlank() -> onResult("请填写项目名称")
            else -> {
                val id = "frontend-project-${System.currentTimeMillis()}"
                _frontendTestProjects.value = _frontendTestProjects.value + ProjectSummary(
                    id = id,
                    name = normalizedName,
                    themeKey = themeKey
                )
                onResult(null)
            }
        }
    }

    /** Local-only account shell until the authentication service is connected. */
    fun login(email: String, password: String, onResult: (String?) -> Unit) = viewModelScope.launch {
        val normalizedEmail = email.trim()
        when {
            normalizedEmail.isBlank() -> onResult("请输入邮箱")
            password.isBlank() -> onResult("请输入密码")
            else -> {
                getApplication<Application>().dataStore.edit { preferences ->
                    val savedEmail = preferences[ACCOUNT_EMAIL]
                    val savedNickname = preferences[ACCOUNT_NICKNAME]
                    preferences[ACCOUNT_EMAIL] = normalizedEmail
                    preferences[ACCOUNT_NICKNAME] = if (savedEmail == normalizedEmail && !savedNickname.isNullOrBlank()) {
                        savedNickname
                    } else normalizedEmail.substringBefore('@').ifBlank { "学习者" }
                    preferences[ACCOUNT_LOGGED_IN] = true
                }
                onResult(null)
            }
        }
    }

    fun register(nickname: String, email: String, password: String, confirmation: String, onResult: (String?) -> Unit) = viewModelScope.launch {
        val normalizedNickname = nickname.trim()
        val normalizedEmail = email.trim()
        when {
            normalizedNickname.isBlank() -> onResult("请输入昵称")
            normalizedEmail.isBlank() || !normalizedEmail.contains('@') -> onResult("请输入有效邮箱")
            password.length < 6 -> onResult("密码至少需要 6 位")
            password != confirmation -> onResult("两次输入的密码不一致")
            else -> {
                getApplication<Application>().dataStore.edit { preferences ->
                    preferences[ACCOUNT_NICKNAME] = normalizedNickname
                    preferences[ACCOUNT_EMAIL] = normalizedEmail
                    preferences[ACCOUNT_LOGGED_IN] = false
                }
                onResult(null)
            }
        }
    }

    fun startStudy(deckId: String, reviewMode: Boolean) = viewModelScope.launch {
        if (frontendTestMode.value) {
            val testCards = _frontendTestCards.value[deckId].orEmpty()
            val due = _frontendTestDecks.value.firstOrNull { it.id == deckId }?.dueCount ?: 0
            _studyCards.value = if (reviewMode) testCards.take(due.coerceAtLeast(0)) else testCards
            return@launch
        }
        when (val result = repository.loadCards(deckId, reviewMode)) {
            is ApiResult.Success -> _studyCards.value = result.value
            is ApiResult.Failure -> logFailure("load_study", result)
        }
    }

    fun refreshCards(deckId: String) = viewModelScope.launch {
        if (!frontendTestMode.value) logFailure("list_cards", repository.refreshCards(deckId))
    }
    fun rate(cardId: String, rating: Rating) = viewModelScope.launch {
        if (frontendTestMode.value) {
            val reviewed = _studyCards.value.firstOrNull { it.id == cardId }
            _studyCards.value = _studyCards.value.filterNot { it.id == cardId }
            reviewed?.let { card ->
                _frontendTestDecks.value = _frontendTestDecks.value.map { deck ->
                    if (deck.id == card.deckId) deck.copy(
                        dueCount = (deck.dueCount - 1).coerceAtLeast(0),
                        reviewCount = deck.reviewCount + 1,
                        masteredCards = if (rating == Rating.GOOD || rating == Rating.EASY) {
                            (deck.masteredCards + 1).coerceAtMost(deck.cardCount)
                        } else deck.masteredCards
                    ) else deck
                }
            }
            return@launch
        }
        when (val result = repository.rate(cardId, rating)) {
            is ApiResult.Success -> refreshDecks()
            is ApiResult.Failure -> logFailure("submit_review", result)
        }
    }
    @OptIn(ExperimentalCoroutinesApi::class)
    fun deckProgress(deckId: String): Flow<DeckProgress> = frontendTestMode.flatMapLatest { enabled ->
        if (enabled) _frontendTestDecks.map { decks ->
            decks.firstOrNull { it.id == deckId }?.let { DeckProgress(it.cardCount, it.dueCount, it.masteredCards, it.reviewCount) }
                ?: DeckProgress(0, 0, 0, 0)
        } else repository.deckProgress(deckId)
    }
    @OptIn(ExperimentalCoroutinesApi::class)
    fun cards(deckId: String): Flow<List<FlashcardEntity>> = frontendTestMode.flatMapLatest { enabled ->
        if (enabled) _frontendTestCards.map { it[deckId].orEmpty() } else repository.cards(deckId)
    }

    fun importDeck(name: String, drafts: List<CardDraft>, onDone: (String) -> Unit) = viewModelScope.launch {
        if (name.isBlank() || drafts.isEmpty()) return@launch
        if (frontendTestMode.value) {
            val validDrafts = drafts.filter { it.front.isNotBlank() && it.back.isNotBlank() }
            if (validDrafts.isEmpty()) return@launch
            val id = "frontend-test-${System.nanoTime()}"
            _frontendTestCards.value = _frontendTestCards.value + (id to validDrafts.mapIndexed { index, draft ->
                FlashcardEntity("$id-card-$index", id, draft.front, draft.back, position = index, source = "FRONTEND_TEST")
            })
            _frontendTestDecks.value = _frontendTestDecks.value + DeckSummary(
                id = id, name = name.trim(), source = "FRONTEND_TEST", themeKey = "azure",
                cardCount = validDrafts.size, dueCount = validDrafts.size
            )
            onDone(id)
            return@launch
        }
        when (val result = repository.importDeck(name, drafts)) {
            is ApiResult.Success -> onDone(result.value)
            is ApiResult.Failure -> logFailure("import_deck", result)
        }
    }

    fun beginTextImportFlow(name: String, drafts: List<CardDraft>) {
        _textImportFlow.value = TextImportFlow(deckName = name, cards = drafts)
    }

    fun clearTextImportFlow() {
        _textImportFlow.value = null
    }

    fun addCardsToDeck(deckId: String, drafts: List<CardDraft>, onDone: () -> Unit) = viewModelScope.launch {
        if (drafts.none { it.front.isNotBlank() && it.back.isNotBlank() }) return@launch
        if (frontendTestMode.value) {
            val validDrafts = drafts.filter { it.front.isNotBlank() && it.back.isNotBlank() }
            val existing = _frontendTestCards.value[deckId].orEmpty()
            val appended = validDrafts.mapIndexed { index, draft ->
                FlashcardEntity("$deckId-added-${System.nanoTime()}-$index", deckId, draft.front, draft.back, position = existing.size + index, source = "FRONTEND_TEST")
            }
            _frontendTestCards.value = _frontendTestCards.value + (deckId to (existing + appended))
            _frontendTestDecks.value = _frontendTestDecks.value.map { deck ->
                if (deck.id == deckId) deck.copy(cardCount = deck.cardCount + appended.size, dueCount = deck.dueCount + appended.size) else deck
            }
            onDone()
            return@launch
        }
        when (val result = repository.addCardsToDeck(deckId, drafts)) {
            is ApiResult.Success -> onDone()
            is ApiResult.Failure -> logFailure("add_cards", result)
        }
    }

    fun deleteDeck(deckId: String, onSuccess: () -> Unit = {}, onFailure: () -> Unit = {}) = viewModelScope.launch {
        if (frontendTestMode.value) {
            _frontendTestDecks.value = _frontendTestDecks.value.filterNot { it.id == deckId }
            _frontendTestCards.value = _frontendTestCards.value - deckId
            onSuccess()
            return@launch
        }
        when (val result = repository.deleteDeck(deckId)) {
            is ApiResult.Success -> onSuccess()
            is ApiResult.Failure -> { logFailure("delete_deck", result); onFailure() }
        }
    }
    fun rewriteCard(cardId: String) = viewModelScope.launch {
        if (!frontendTestMode.value) logFailure("rewrite_card", repository.rewriteCard(cardId))
    }

    fun refreshApiKeyStatus() = viewModelScope.launch {
        if (frontendTestMode.value) {
            _apiKeyStatus.value = ApiKeyStatus("AVAILABLE", "frontend-test")
            return@launch
        }
        when (val result = repository.apiKeyStatus()) {
            is ApiResult.Success -> _apiKeyStatus.value = result.value
            is ApiResult.Failure -> logFailure("api_key_status", result)
        }
    }

    fun saveApiKey(key: String, onFinished: () -> Unit = {}) = viewModelScope.launch {
        if (frontendTestMode.value) {
            _apiKeyStatus.value = ApiKeyStatus("AVAILABLE", "frontend-test")
            onFinished()
            return@launch
        }
        when (val result = repository.saveApiKey(key)) {
            is ApiResult.Success -> _apiKeyStatus.value = result.value
            is ApiResult.Failure -> logFailure("save_api_key", result)
        }
        onFinished()
    }

    /** A task must never create a deck before the device has a usable server-side key. */
    fun checkApiKeyForGeneration(
        onAvailable: () -> Unit,
        onUnavailable: (String) -> Unit,
        onFailure: () -> Unit
    ) = viewModelScope.launch {
        if (frontendTestMode.value) {
            onAvailable()
            return@launch
        }
        when (val result = repository.apiKeyStatus()) {
            is ApiResult.Success -> {
                _apiKeyStatus.value = result.value
                if (result.value.status.equals("AVAILABLE", ignoreCase = true)) onAvailable()
                else onUnavailable(result.value.status)
            }
            is ApiResult.Failure -> {
                logFailure("api_key_for_generation", result)
                onFailure()
            }
        }
    }

    fun uploadPdf(uri: Uri, onParsed: (List<PdfChapter>) -> Unit, onFailure: (PdfReadFailure) -> Unit) = viewModelScope.launch {
        if (frontendTestMode.value) {
            val file = PdfFile(
                id = "frontend-test-pdf",
                name = "设计心理学课件.pdf",
                status = "PARSED",
                chapters = FrontendTestFixtures.pdfChapters
            )
            _pdfFile.value = file
            delay(750)
            onParsed(file.chapters)
            return@launch
        }
        when (val result = repository.uploadPdf(uri)) {
            is ApiResult.Success -> { _pdfFile.value = result.value; pollPdf(result.value, onParsed, onFailure) }
            is ApiResult.Failure -> { logFailure("upload_pdf", result); onFailure(pdfFailure(result)) }
        }
    }

    fun updatePdfChapter(chapter: PdfChapter) = viewModelScope.launch {
        val file = _pdfFile.value ?: return@launch
        when (val result = repository.updatePdfChapter(file.id, chapter)) {
            is ApiResult.Success -> _pdfFile.value = file.copy(chapters = file.chapters.map { if (it.id == result.value.id) result.value else it })
            is ApiResult.Failure -> logFailure("update_pdf_chapter", result)
        }
    }

    fun generatePdfSamples(
        chapterIds: List<String>,
        config: PdfGenerationConfig,
        onReady: () -> Unit,
        onFailure: (String?) -> Unit = {}
    ) = viewModelScope.launch {
        if (frontendTestMode.value) {
            _pdfSamples.value = FrontendTestFixtures.pdfSamples
            onReady()
            return@launch
        }
        val file = _pdfFile.value ?: run { onFailure("PDF_NOT_READY"); return@launch }
        when (val result = repository.generateSamples(file.id, chapterIds, config.quantity, config.basic, config.understanding, config.application, config.requirement)) {
            is ApiResult.Success -> { _pdfSamples.value = result.value; onReady() }
            is ApiResult.Failure -> { logFailure("generate_samples", result); onFailure(result.code) }
        }
    }

    fun createPdfTask(
        existingDeckId: String?,
        deckName: String,
        chapterIds: List<String>,
        config: PdfGenerationConfig,
        onStarted: () -> Unit,
        onFailure: (String?) -> Unit = {}
    ) = viewModelScope.launch {
        if (frontendTestMode.value) {
            val samples = _pdfSamples.value.ifEmpty { FrontendTestFixtures.pdfSamples }
            val deckId = existingDeckId ?: "frontend-pdf-${System.nanoTime()}"
            if (existingDeckId == null) {
                _frontendTestDecks.value = _frontendTestDecks.value + DeckSummary(
                    id = deckId,
                    name = deckName.ifBlank { "PDF 智能制卡" },
                    source = "FRONTEND_TEST",
                    themeKey = "azure",
                    cardCount = samples.size,
                    dueCount = samples.size
                )
                _frontendTestCards.value = _frontendTestCards.value + (deckId to samples.mapIndexed { index, draft ->
                    FlashcardEntity("$deckId-card-$index", deckId, draft.front, draft.back, position = index, source = "FRONTEND_TEST")
                })
            } else {
                val existing = _frontendTestCards.value[deckId].orEmpty()
                _frontendTestCards.value = _frontendTestCards.value + (deckId to (existing + samples.mapIndexed { index, draft ->
                    FlashcardEntity("$deckId-card-${existing.size + index}", deckId, draft.front, draft.back, position = existing.size + index, source = "FRONTEND_TEST")
                }))
                _frontendTestDecks.value = _frontendTestDecks.value.map { deck ->
                    if (deck.id == deckId) deck.copy(cardCount = deck.cardCount + samples.size, dueCount = deck.dueCount + samples.size) else deck
                }
            }
            _pdfTaskDeckId.value = deckId
            _pdfTask.value = GeneratedTask(
                id = "frontend-pdf-task-${System.nanoTime()}",
                status = "COMPLETED",
                stage = "DONE",
                generatedCardCount = samples.size
            )
            onStarted()
            return@launch
        }
        val file = _pdfFile.value ?: run { onFailure("PDF_NOT_READY"); return@launch }
        val deckId = existingDeckId ?: when (val create = repository.createDeck(deckName.ifBlank { "PDF 智能制卡" })) {
            is ApiResult.Success -> create.value
            is ApiResult.Failure -> { logFailure("create_deck_for_task", create); onFailure(create.code); return@launch }
        }
        when (val result = repository.createTask(file.id, deckId, chapterIds, config.quantity, config.basic, config.understanding, config.application, config.requirement)) {
            is ApiResult.Success -> { _pdfTaskDeckId.value = deckId; _pdfTask.value = result.value; onStarted(); pollTask(result.value) }
            is ApiResult.Failure -> { logFailure("create_task", result); onFailure(result.code) }
        }
    }

    fun resumePdfTask() = viewModelScope.launch {
        val task = _pdfTask.value ?: return@launch
        when (val result = repository.resumeTask(task.id)) {
            is ApiResult.Success -> { _pdfTask.value = result.value; pollTask(result.value) }
            is ApiResult.Failure -> logFailure("resume_task", result)
        }
    }

    fun updateDeckPresentation(deckId: String, name: String, themeKey: String, onFailure: () -> Unit = {}) = viewModelScope.launch {
        if (frontendTestMode.value) {
            _frontendTestDecks.value = _frontendTestDecks.value.map { deck ->
                if (deck.id == deckId) deck.copy(name = name, themeKey = themeKey) else deck
            }
            return@launch
        }
        when (val result = repository.updateDeckPresentation(deckId, name, themeKey)) {
            is ApiResult.Success -> Unit
            is ApiResult.Failure -> { logFailure("update_deck", result); onFailure() }
        }
    }

    fun updateCard(card: FlashcardEntity, onFailure: () -> Unit = {}) = viewModelScope.launch {
        if (frontendTestMode.value) {
            _frontendTestCards.value = _frontendTestCards.value.mapValues { (_, cards) ->
                cards.map { if (it.id == card.id) card else it }
            }
            return@launch
        }
        when (val result = repository.updateCard(card)) {
            is ApiResult.Success -> Unit
            is ApiResult.Failure -> { logFailure("update_card", result); onFailure() }
        }
    }

    fun deleteCard(card: FlashcardEntity, onFailure: () -> Unit = {}) = viewModelScope.launch {
        if (frontendTestMode.value) {
            _frontendTestCards.value = _frontendTestCards.value.mapValues { (_, cards) -> cards.filterNot { it.id == card.id } }
            _frontendTestDecks.value = _frontendTestDecks.value.map { deck ->
                if (deck.id == card.deckId) deck.copy(cardCount = (deck.cardCount - 1).coerceAtLeast(0)) else deck
            }
            return@launch
        }
        when (val result = repository.deleteCard(card)) {
            is ApiResult.Success -> Unit
            is ApiResult.Failure -> { logFailure("delete_card", result); onFailure() }
        }
    }

    fun deletePdfChapter(chapter: PdfChapter, onFailure: () -> Unit = {}) = viewModelScope.launch {
        val file = _pdfFile.value ?: return@launch
        when (val result = repository.deletePdfChapter(file.id, chapter.id)) {
            is ApiResult.Success -> _pdfFile.value = file.copy(chapters = file.chapters.filterNot { it.id == chapter.id })
            is ApiResult.Failure -> { logFailure("delete_pdf_chapter", result); onFailure() }
        }
    }

    fun setDarkTheme(value: Boolean?) = viewModelScope.launch {
        getApplication<Application>().dataStore.edit { preferences ->
            if (value == null) preferences.remove(DARK_THEME) else preferences[DARK_THEME] = value
        }
    }

    private suspend fun pollPdf(initial: PdfFile, onParsed: (List<PdfChapter>) -> Unit, onFailure: (PdfReadFailure) -> Unit) {
        var current = initial
        repeat(120) {
            when (current.status.uppercase()) {
                "PARSED" -> { onParsed(current.chapters); return }
                "FAILED" -> { onFailure(PdfReadFailure("PDF 解析失败", "服务端无法解析这份 PDF 的文字或目录。")); return }
            }
            delay(2_500)
            when (val result = repository.getPdf(current.id)) {
                is ApiResult.Success -> { current = result.value; _pdfFile.value = current }
                is ApiResult.Failure -> { logFailure("get_pdf", result); onFailure(pdfFailure(result)); return }
            }
        }
        onFailure(PdfReadFailure("PDF 解析超时", "服务端长时间未返回解析结果，请稍后重试。"))
    }

    private suspend fun pollTask(initial: GeneratedTask) {
        var current = initial
        repeat(240) {
            when (current.status.uppercase()) {
                "COMPLETED" -> { refreshDecks(); return }
                "FAILED", "CANCELLED", "PAUSED" -> return
            }
            delay(2_500)
            when (val result = repository.getTask(current.id)) {
                is ApiResult.Success -> { current = result.value; _pdfTask.value = current }
                is ApiResult.Failure -> { logFailure("get_task", result); return }
            }
        }
        unavailable("task_poll_timeout")
    }

    private fun pdfFailure(result: ApiResult.Failure): PdfReadFailure = when (result.code) {
        "PDF_FILE_UNREADABLE" -> PdfReadFailure("无法访问这份 PDF", "请重新选择文件并确认应用有读取权限。")
        "NETWORK_UNAVAILABLE" -> PdfReadFailure("PDF 上传网络异常", "上传连接被中断，请检查网络后重试。")
        else -> PdfReadFailure("PDF 上传失败", "服务暂时无法处理上传，请稍后重试。")
    }

    private fun unavailable(operation: String) {
        if (BuildConfig.DEBUG) Log.w("ShankaNetwork", "op=$operation status=UNAVAILABLE reason=backend_route_missing")
    }

    private fun <T> logFailure(operation: String, result: ApiResult<T>) {
        if (result is ApiResult.Failure && BuildConfig.DEBUG) Log.w("ShankaNetwork", "op=$operation status=${result.status} code=${result.code ?: "-"} localization=${result.localizationKey ?: "-"}")
    }
}
