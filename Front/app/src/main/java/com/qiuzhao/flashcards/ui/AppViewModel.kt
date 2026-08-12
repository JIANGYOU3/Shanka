package com.qiuzhao.flashcards.ui

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qiuzhao.flashcards.BuildConfig
import com.qiuzhao.flashcards.data.CardDraft
import com.qiuzhao.flashcards.data.remote.ApiKeyStatus
import com.qiuzhao.flashcards.data.remote.ApiResult
import com.qiuzhao.flashcards.data.remote.Dashboard
import com.qiuzhao.flashcards.data.remote.DeckProgress
import com.qiuzhao.flashcards.data.remote.FlashcardEntity
import com.qiuzhao.flashcards.data.remote.GeneratedTask
import com.qiuzhao.flashcards.data.remote.PdfChapter
import com.qiuzhao.flashcards.data.remote.PdfFile
import com.qiuzhao.flashcards.data.remote.Rating
import com.qiuzhao.flashcards.data.remote.RemoteFlashcardRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class PdfGenerationConfig(
    val quantity: String = "BALANCED",
    val basic: Float = .4f,
    val understanding: Float = .4f,
    val application: Float = .2f,
    val requirement: String = ""
)

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
private const val DEFAULT_WEEKLY_GOAL = 50

/**
 * Business data is server-authoritative. DataStore keeps only user preferences and the network
 * layer owns the encrypted device id and debug evidence; Room is deliberately not instantiated.
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = RemoteFlashcardRepository(application)

    val decks = repository.decks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val dueCount = repository.dueCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val darkTheme: StateFlow<Boolean?> = application.dataStore.data.map { preferences ->
        if (preferences.contains(DARK_THEME)) preferences[DARK_THEME] else null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val weeklyGoal = application.dataStore.data.map { it[WEEKLY_GOAL] ?: DEFAULT_WEEKLY_GOAL }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DEFAULT_WEEKLY_GOAL)

    private val _studyCards = MutableStateFlow<List<FlashcardEntity>>(emptyList())
    val studyCards: StateFlow<List<FlashcardEntity>> = _studyCards
    private val _dashboard = MutableStateFlow<Dashboard?>(null)
    val dashboard: StateFlow<Dashboard?> = _dashboard
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

    init {
        refreshDecks()
        refreshDashboard()
    }

    fun refreshDecks() = viewModelScope.launch { logFailure("list_decks", repository.refreshDecks()) }
    fun refreshDashboard() = viewModelScope.launch {
        val goal = getApplication<Application>().dataStore.data.first()[WEEKLY_GOAL] ?: DEFAULT_WEEKLY_GOAL
        when (val result = repository.dashboard(goal)) {
            is ApiResult.Success -> _dashboard.value = result.value
            is ApiResult.Failure -> logFailure("dashboard", result)
        }
    }

    fun setWeeklyGoal(value: Int) = viewModelScope.launch {
        getApplication<Application>().dataStore.edit { it[WEEKLY_GOAL] = value.coerceAtLeast(1) }
        refreshDashboard()
    }

    fun startStudy(deckId: String, reviewMode: Boolean) = viewModelScope.launch {
        when (val result = repository.loadCards(deckId, reviewMode)) {
            is ApiResult.Success -> _studyCards.value = result.value
            is ApiResult.Failure -> logFailure("load_study", result)
        }
    }

    fun refreshCards(deckId: String) = viewModelScope.launch { logFailure("list_cards", repository.refreshCards(deckId)) }
    fun rate(cardId: String, rating: Rating) = viewModelScope.launch {
        when (val result = repository.rate(cardId, rating)) {
            is ApiResult.Success -> refreshDecks()
            is ApiResult.Failure -> logFailure("submit_review", result)
        }
    }
    fun deckProgress(deckId: String): Flow<DeckProgress> = repository.deckProgress(deckId)
    fun cards(deckId: String): Flow<List<FlashcardEntity>> = repository.cards(deckId)

    fun importDeck(name: String, drafts: List<CardDraft>, onDone: (String) -> Unit) = viewModelScope.launch {
        if (name.isBlank() || drafts.isEmpty()) return@launch
        when (val result = repository.importDeck(name, drafts)) {
            is ApiResult.Success -> onDone(result.value)
            is ApiResult.Failure -> logFailure("import_deck", result)
        }
    }

    fun addCardsToDeck(deckId: String, drafts: List<CardDraft>, onDone: () -> Unit) = viewModelScope.launch {
        if (drafts.none { it.front.isNotBlank() && it.back.isNotBlank() }) return@launch
        when (val result = repository.addCardsToDeck(deckId, drafts)) {
            is ApiResult.Success -> onDone()
            is ApiResult.Failure -> logFailure("add_cards", result)
        }
    }

    fun deleteDeck(deckId: String, onSuccess: () -> Unit = {}, onFailure: () -> Unit = {}) = viewModelScope.launch {
        when (val result = repository.deleteDeck(deckId)) {
            is ApiResult.Success -> onSuccess()
            is ApiResult.Failure -> { logFailure("delete_deck", result); onFailure() }
        }
    }
    fun rewriteCard(cardId: String) = viewModelScope.launch { logFailure("rewrite_card", repository.rewriteCard(cardId)) }

    fun refreshApiKeyStatus() = viewModelScope.launch {
        when (val result = repository.apiKeyStatus()) {
            is ApiResult.Success -> _apiKeyStatus.value = result.value
            is ApiResult.Failure -> logFailure("api_key_status", result)
        }
    }

    fun saveApiKey(key: String, onFinished: () -> Unit = {}) = viewModelScope.launch {
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
        when (val result = repository.updateDeckPresentation(deckId, name, themeKey)) {
            is ApiResult.Success -> Unit
            is ApiResult.Failure -> { logFailure("update_deck", result); onFailure() }
        }
    }

    fun updateCard(card: FlashcardEntity, onFailure: () -> Unit = {}) = viewModelScope.launch {
        when (val result = repository.updateCard(card)) {
            is ApiResult.Success -> Unit
            is ApiResult.Failure -> { logFailure("update_card", result); onFailure() }
        }
    }

    fun deleteCard(card: FlashcardEntity, onFailure: () -> Unit = {}) = viewModelScope.launch {
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
