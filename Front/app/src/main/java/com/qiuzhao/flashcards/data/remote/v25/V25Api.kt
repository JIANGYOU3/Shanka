package com.qiuzhao.flashcards.data.remote.v25

import com.qiuzhao.flashcards.data.remote.http.ShankaOps
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Typed Retrofit surface of every production-reachable V2.5 endpoint (Architecture section 4).
 * The interface is the single wire description of the API: paths, methods, query parameters,
 * Idempotency-Key headers and multipart frames are declared here once and both the production
 * client and the MockWebServer contract tests share it.
 *
 * Wire rules locked by tests:
 * - Reads never carry an Idempotency-Key; writes do (caller-fixed key when one is supplied, so
 *   a retried user operation replays the identical key — automatic 429 retries re-proceed the
 *   same request object and therefore never regenerate it).
 * - `null` means "clear" for `current_project_id` / `custom_requirements`: those request bodies
 *   serialize an explicit JSON null.
 * - DELETE endpoints return Unit; Retrofit consumes (and closes) any response body.
 */
internal interface V25Api {

    // --- account profile (Architecture 4.1) ---------------------------------------------------

    @Headers("X-Shanka-Op: ${ShankaOps.GET_AUTH_USER}")
    @GET("auth/me")
    suspend fun getAuthUser(): AuthUserResponse

    @Headers("X-Shanka-Op: ${ShankaOps.UPDATE_AUTH_USER}")
    @PATCH("auth/me")
    suspend fun updateAuthUser(
        @Body body: AuthMeUpdateRequest,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): AuthUserResponse

    /** Logout carries the explicit pre-clear token; the shared [com.qiuzhao.flashcards.data.remote.AuthApi] is the auth surface. */
    @Headers("X-Shanka-Op: ${ShankaOps.LOGOUT}")
    @POST("auth/logout")
    suspend fun logout(
        @Header("Authorization") bearer: String,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): Unit

    // --- preferences (Architecture 4.1) ---------------------------------------------------------

    @Headers("X-Shanka-Op: ${ShankaOps.GET_PREFERENCES}")
    @GET("preferences")
    suspend fun getPreferences(): PreferencesDto

    @Headers("X-Shanka-Op: ${ShankaOps.UPDATE_PREFERENCES}")
    @PATCH("preferences")
    suspend fun updatePreferences(
        @Body body: PreferencesPatchRequest,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): PreferencesDto

    @Headers("X-Shanka-Op: ${ShankaOps.SET_CURRENT_PROJECT}")
    @PATCH("preferences")
    suspend fun setCurrentProject(
        @Body body: SetCurrentProjectRequest,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): PreferencesDto

    // --- learning projects and chapters (Architecture 4.2) --------------------------------------

    /** Two-step creation step one: the JSON body carries only the project name (contract 6.2). */
    @Headers("X-Shanka-Op: ${ShankaOps.CREATE_PROJECT}")
    @POST("projects")
    suspend fun createProject(
        @Body body: CreateProjectRequest,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): ProjectDto

    @Headers("X-Shanka-Op: ${ShankaOps.LIST_MATERIALS}")
    @GET("projects/{project_id}/materials")
    suspend fun listProjectMaterials(@Path("project_id") projectId: String): ItemsResponse<MaterialDto>

    @Headers("X-Shanka-Op: ${ShankaOps.ADD_MATERIAL_PDF}")
    @Multipart
    @POST("projects/{project_id}/materials/pdf")
    suspend fun addProjectMaterialPdf(
        @Path("project_id") projectId: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Part file: MultipartBody.Part,
    ): MaterialDto

    @Headers("X-Shanka-Op: ${ShankaOps.ADD_MATERIAL_TEXT}")
    @POST("projects/{project_id}/materials/text")
    suspend fun addProjectMaterialText(
        @Path("project_id") projectId: String,
        @Body body: TextMaterialCreateRequest,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): MaterialDto

    @Headers("X-Shanka-Op: ${ShankaOps.DELETE_MATERIAL}")
    @DELETE("projects/{project_id}/materials/{material_id}")
    suspend fun deleteProjectMaterial(
        @Path("project_id") projectId: String,
        @Path("material_id") materialId: String,
        @Query("retain_cards") retainCards: Boolean?,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): ProjectDto

    @Headers("X-Shanka-Op: ${ShankaOps.REPLACE_MATERIAL_PDF}")
    @Multipart
    @POST("projects/{project_id}/materials/{material_id}/replace")
    suspend fun replaceProjectMaterialPdf(
        @Path("project_id") projectId: String,
        @Path("material_id") materialId: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Part file: MultipartBody.Part,
    ): MaterialDto

    @Headers("X-Shanka-Op: ${ShankaOps.LIST_PROJECTS}")
    @GET("projects")
    suspend fun listProjects(): ItemsResponse<ProjectDto>

    @Headers("X-Shanka-Op: ${ShankaOps.GET_PROJECT}")
    @GET("projects/{project_id}")
    suspend fun getProject(@Path("project_id") projectId: String): ProjectDto

    @Headers("X-Shanka-Op: ${ShankaOps.PROJECT_PROGRESS}")
    @GET("projects/{project_id}/progress")
    suspend fun projectProgress(@Path("project_id") projectId: String): ProgressDto

    @Headers("X-Shanka-Op: ${ShankaOps.RENAME_PROJECT}")
    @PATCH("projects/{project_id}")
    suspend fun renameProject(
        @Path("project_id") projectId: String,
        @Body body: RenameRequest,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): ProjectDto

    @Headers("X-Shanka-Op: ${ShankaOps.DELETE_PROJECT}")
    @DELETE("projects/{project_id}")
    suspend fun deleteProject(
        @Path("project_id") projectId: String,
        @Query("retain_decks") retainDecks: Boolean,
        @Header("Idempotency-Key") idempotencyKey: String?,
    ): Unit

    @Headers("X-Shanka-Op: ${ShankaOps.PROJECT_DELETION_PREFLIGHT}")
    @GET("projects/{project_id}/deletion-preflight")
    suspend fun projectDeletionPreflight(
        @Path("project_id") projectId: String,
        @Query("retain_decks") retainDecks: Boolean?,
        @Query("cancel_active_tasks") cancelActiveTasks: Boolean?,
    ): DeletionPreflightDto

    @Headers("X-Shanka-Op: ${ShankaOps.UPDATE_CHAPTER}")
    @PATCH("projects/{project_id}/chapters/{chapter_id}")
    suspend fun updateChapter(
        @Path("project_id") projectId: String,
        @Path("chapter_id") chapterId: String,
        @Body body: ChapterEditRequest,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): ChapterDto

    @Headers("X-Shanka-Op: ${ShankaOps.DELETE_CHAPTER}")
    @DELETE("projects/{project_id}/chapters/{chapter_id}")
    suspend fun deleteChapter(
        @Path("project_id") projectId: String,
        @Path("chapter_id") chapterId: String,
        @Query("delete_cards") deleteCards: Boolean?,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): Unit

    @Headers("X-Shanka-Op: ${ShankaOps.CONFIRM_CHAPTERS}")
    @POST("projects/{project_id}/confirm-chapters")
    suspend fun confirmChapters(
        @Path("project_id") projectId: String,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): ProjectDto

    @Headers("X-Shanka-Op: ${ShankaOps.GET_STUDY_SETTINGS}")
    @GET("projects/{project_id}/study-settings")
    suspend fun getStudySettings(@Path("project_id") projectId: String): StudySettingsDto

    @Headers("X-Shanka-Op: ${ShankaOps.UPDATE_STUDY_SETTINGS}")
    @PATCH("projects/{project_id}/study-settings")
    suspend fun updateStudySettings(
        @Path("project_id") projectId: String,
        @Body body: StudySettingsPatchRequest,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): StudySettingsDto

    // --- generation tasks (Architecture 4.3) ----------------------------------------------------

    @Headers("X-Shanka-Op: ${ShankaOps.CREATE_TASK}")
    @POST("projects/{project_id}/tasks")
    suspend fun createTask(
        @Path("project_id") projectId: String,
        @Body body: TaskCreateRequest,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): TaskDto

    @Headers("X-Shanka-Op: ${ShankaOps.LIST_TASKS}")
    @GET("tasks")
    suspend fun listTasks(
        @Query("project_id") projectId: String?,
        @Query("status") status: String?,
    ): ItemsResponse<TaskDto>

    @Headers("X-Shanka-Op: ${ShankaOps.GET_TASK}")
    @GET("tasks/{task_id}")
    suspend fun getTask(@Path("task_id") taskId: String): TaskDto

    @Headers("X-Shanka-Op: ${ShankaOps.UPDATE_TASK}")
    @PATCH("tasks/{task_id}")
    suspend fun updateTaskConfig(
        @Path("task_id") taskId: String,
        @Body body: TaskConfigPatchRequest,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): TaskDto

    @Headers("X-Shanka-Op: ${ShankaOps.GENERATE_SAMPLES}")
    @POST("tasks/{task_id}/samples")
    suspend fun generateSamples(
        @Path("task_id") taskId: String,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): TaskDto

    @Headers("X-Shanka-Op: ${ShankaOps.START_TASK}")
    @POST("tasks/{task_id}/start")
    suspend fun startTask(
        @Path("task_id") taskId: String,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): TaskDto

    @Headers("X-Shanka-Op: ${ShankaOps.ABANDON_TASK}")
    @POST("tasks/{task_id}/abandon")
    suspend fun abandonTask(
        @Path("task_id") taskId: String,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): TaskDto

    @Headers("X-Shanka-Op: ${ShankaOps.RETRY_TASK}")
    @POST("tasks/{task_id}/retry")
    suspend fun retryTask(
        @Path("task_id") taskId: String,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): TaskDto

    @Headers("X-Shanka-Op: ${ShankaOps.DELETE_TASK}")
    @DELETE("tasks/{task_id}")
    suspend fun deleteTask(
        @Path("task_id") taskId: String,
        @Query("delete_generated_cards") deleteGeneratedCards: Boolean?,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): Unit

    // --- decks and cards (Architecture 4.4) -------------------------------------------------------

    @Headers("X-Shanka-Op: ${ShankaOps.LIST_DECKS}")
    @GET("decks")
    suspend fun listDecks(@Query("project_id") projectId: String?): ItemsResponse<DeckDto>

    @Headers("X-Shanka-Op: ${ShankaOps.CREATE_DECK}")
    @POST("decks")
    suspend fun createDeck(
        @Body body: CreateDeckRequest,
        @Header("Idempotency-Key") idempotencyKey: String?,
    ): DeckDto

    @Headers("X-Shanka-Op: ${ShankaOps.GET_DECK}")
    @GET("decks/{deck_id}")
    suspend fun getDeck(@Path("deck_id") deckId: String): DeckDto

    @Headers("X-Shanka-Op: ${ShankaOps.ATTACH_DECK}")
    @POST("projects/{project_id}/decks/{deck_id}/attach")
    suspend fun attachDeckToProject(
        @Path("project_id") projectId: String,
        @Path("deck_id") deckId: String,
        @Header("Idempotency-Key") idempotencyKey: String?,
    ): DeckDto

    @Headers("X-Shanka-Op: ${ShankaOps.RENAME_DECK}")
    @PATCH("decks/{deck_id}")
    suspend fun renameDeck(
        @Path("deck_id") deckId: String,
        @Body body: RenameRequest,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): DeckDto

    @Headers("X-Shanka-Op: ${ShankaOps.DELETE_DECK}")
    @DELETE("decks/{deck_id}")
    suspend fun deleteDeck(
        @Path("deck_id") deckId: String,
        @Header("Idempotency-Key") idempotencyKey: String?,
    ): Unit

    @Headers("X-Shanka-Op: ${ShankaOps.DECK_DELETION_PREFLIGHT}")
    @GET("decks/{deck_id}/deletion-preflight")
    suspend fun deckDeletionPreflight(
        @Path("deck_id") deckId: String,
        @Query("cancel_active_tasks") cancelActiveTasks: Boolean?,
    ): DeletionPreflightDto

    @Headers("X-Shanka-Op: ${ShankaOps.LIST_CARDS}")
    @GET("decks/{deck_id}/cards")
    suspend fun listCards(
        @Path("deck_id") deckId: String,
        @Query("order") order: String,
        @Query("content_difficulty") contentDifficulty: String?,
        @Query("mastery") mastery: String,
    ): ItemsResponse<CardDto>

    @Headers("X-Shanka-Op: ${ShankaOps.IMPORT_CARDS}")
    @POST("decks/{deck_id}/cards/import")
    suspend fun importCards(
        @Path("deck_id") deckId: String,
        @Body body: CardsImportRequest,
        @Header("Idempotency-Key") idempotencyKey: String?,
    ): ImportResponse

    @Headers("X-Shanka-Op: ${ShankaOps.UPDATE_CARD}")
    @PATCH("cards/{card_id}")
    suspend fun updateCard(
        @Path("card_id") cardId: String,
        @Body body: CardPatchRequest,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): CardDto

    @Headers("X-Shanka-Op: ${ShankaOps.DELETE_CARD}")
    @DELETE("cards/{card_id}")
    suspend fun deleteCard(
        @Path("card_id") cardId: String,
        @Query("delete_batch_id") deleteBatchId: String?,
        @Header("Idempotency-Key") idempotencyKey: String?,
    ): DeletionBatchDto

    // --- deletion undo (Architecture 4.4 / 3.7) ---------------------------------------------------

    @Headers("X-Shanka-Op: ${ShankaOps.PENDING_DELETION_BATCHES}")
    @GET("card-deletion-batches/pending")
    suspend fun pendingDeletionBatches(): ItemsResponse<DeletionBatchDto>

    @Headers("X-Shanka-Op: ${ShankaOps.UNDO_DELETION_BATCH}")
    @POST("card-deletion-batches/{delete_batch_id}/undo")
    suspend fun undoDeletionBatch(
        @Path("delete_batch_id") deleteBatchId: String,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): Unit

    // --- AI rewrite previews (Architecture 4.4 / 3.8) ---------------------------------------------

    @Headers("X-Shanka-Op: ${ShankaOps.CREATE_REWRITE_PREVIEW}")
    @POST("cards/{card_id}/rewrite-previews")
    suspend fun createRewritePreview(
        @Path("card_id") cardId: String,
        @Body body: RewritePreviewRequest,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): RewritePreviewDto

    @Headers("X-Shanka-Op: ${ShankaOps.APPLY_REWRITE_PREVIEW}")
    @POST("cards/{card_id}/rewrite-previews/{rewrite_id}/apply")
    suspend fun applyRewritePreview(
        @Path("card_id") cardId: String,
        @Path("rewrite_id") rewriteId: String,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): CardDto

    @Headers("X-Shanka-Op: ${ShankaOps.CANCEL_REWRITE_PREVIEW}")
    @DELETE("cards/{card_id}/rewrite-previews/{rewrite_id}")
    suspend fun cancelRewritePreview(
        @Path("card_id") cardId: String,
        @Path("rewrite_id") rewriteId: String,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): Unit

    // --- study and review (Architecture 4.5) --------------------------------------------------------

    @Headers("X-Shanka-Op: ${ShankaOps.GET_STUDY_PLAN}")
    @GET("study/plan")
    suspend fun getStudyPlan(): StudyPlanDto

    @Headers("X-Shanka-Op: ${ShankaOps.UPDATE_STUDY_PLAN}")
    @PUT("study/plan")
    suspend fun updateStudyPlan(
        @Body body: StudyPlanRequest,
        @Header("Idempotency-Key") idempotencyKey: String?,
    ): StudyPlanDto

    @Headers("X-Shanka-Op: ${ShankaOps.TODAY_PLAN}")
    @GET("study/today")
    suspend fun todayPlan(): TodayPlanDto

    @Headers("X-Shanka-Op: ${ShankaOps.STUDY_PLAN_BACKLOG}")
    @GET("study/today/backlog")
    suspend fun studyPlanBacklog(
        @Query("offset") offset: Int,
        @Query("limit") limit: Int,
    ): ItemsResponse<CardDto>

    @Headers("X-Shanka-Op: ${ShankaOps.REVIEW_QUEUE}")
    @GET("decks/{deck_id}/review")
    suspend fun deckReviewQueue(@Path("deck_id") deckId: String): ItemsResponse<CardDto>

    @Headers("X-Shanka-Op: ${ShankaOps.SUBMIT_REVIEW}")
    @POST("review-events")
    suspend fun submitReview(
        @Body body: ReviewEventRequest,
        @Header("Idempotency-Key") idempotencyKey: String?,
    ): RatingResultDto

    // --- statistics (Architecture 4.5) ----------------------------------------------------------------

    @Headers("X-Shanka-Op: ${ShankaOps.DASHBOARD}")
    @GET("stats/dashboard")
    suspend fun statsDashboard(): DashboardDto

    // --- AI service key (V25-SET-FR-05) ----------------------------------------------------------------

    @Headers("X-Shanka-Op: ${ShankaOps.API_KEY_STATUS}")
    @GET("api-key/status")
    suspend fun apiKeyStatus(): ApiKeyStatusDto

    @Headers("X-Shanka-Op: ${ShankaOps.SAVE_API_KEY}")
    @PUT("api-key")
    suspend fun saveApiKey(
        @Body body: ApiKeyRequest,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): ApiKeyStatusDto
}
