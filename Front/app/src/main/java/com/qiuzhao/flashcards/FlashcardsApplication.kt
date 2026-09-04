package com.qiuzhao.flashcards

import android.app.Application
import android.content.Context
import com.qiuzhao.flashcards.data.local.ShankaV25Database
import com.qiuzhao.flashcards.data.local.V25CacheStore
import com.qiuzhao.flashcards.data.offline.ObservationEngine
import com.qiuzhao.flashcards.data.offline.OfflineFirstV25Repository
import com.qiuzhao.flashcards.data.offline.RefreshPolicy
import com.qiuzhao.flashcards.data.offline.RequestLanes
import com.qiuzhao.flashcards.data.offline.ReviewSyncCoordinator
import com.qiuzhao.flashcards.data.remote.AuthApi
import com.qiuzhao.flashcards.data.remote.AuthRepository
import com.qiuzhao.flashcards.data.remote.RemoteAuthRepository
import com.qiuzhao.flashcards.data.remote.http.NetworkEvidence
import com.qiuzhao.flashcards.data.remote.http.NetworkStack
import com.qiuzhao.flashcards.data.remote.v25.RemoteV25Repository
import com.qiuzhao.flashcards.data.session.KeystoreSessionStore
import com.qiuzhao.flashcards.data.session.SessionStore
import com.qiuzhao.flashcards.data.session.loadQuietly
import com.qiuzhao.flashcards.work.ProcessingSyncWorker
import com.qiuzhao.flashcards.work.ReviewSyncWorker
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Lightweight manual assembly (single module, no Hilt): exactly one of each process-level
 * object — one OkHttp [NetworkStack], one `shanka-v25.db`, one cache store, one request-lane
 * sequencer, one review-sync coordinator — and the ViewModel factory reads from here.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    /** Process-level scope for fire-and-forget sync kicks; failures never kill siblings. */
    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val clock: Clock = Clock.systemDefaultZone()

    val sessionStore: SessionStore = KeystoreSessionStore(appContext)

    /** The single network foundation: pool, dispatcher, bearer session, 429 policy, evidence. */
    val networkStack: NetworkStack = NetworkStack(sessionStore, evidence = NetworkEvidence(appContext))

    val authRepository: AuthRepository =
        RemoteAuthRepository(networkStack.retrofit().create(AuthApi::class.java), sessionStore)

    /** The remote (online) implementation the offline-first repository delegates to. */
    val remoteV25: RemoteV25Repository = RemoteV25Repository.create(networkStack)

    val database: ShankaV25Database = ShankaV25Database.build(appContext)

    val cache: V25CacheStore = V25CacheStore(database)

    val lanes: RequestLanes = RequestLanes(applicationScope)

    val reviewSync: ReviewSyncCoordinator = ReviewSyncCoordinator(
        remote = remoteV25,
        cache = cache,
        sessionUser = { sessionStore.loadQuietly()?.user?.userId },
        clock = clock,
        lanes = lanes,
        scope = applicationScope,
        onAuthoritativeRefreshNeeded = ::authoritativeRefresh,
    )

    /** The repository the AppViewModel consumes: offline-first reads + outbox ratings. */
    val v25Repository: OfflineFirstV25Repository = OfflineFirstV25Repository(
        remote = remoteV25,
        cache = cache,
        sessionStore = sessionStore,
        lanes = lanes,
        reviewSync = reviewSync,
        clock = clock,
    )

    /**
     * The single polling mechanism (V25-D-34): per-resource pollers while a parse or a
     * generation task is in flight, writing Room only. A polled task turning terminal fires
     * one authoritative decks/today/dashboard refresh — the completion chain the old
     * per-screen loops each reimplemented.
     */
    val observationEngine = ObservationEngine(
        repository = v25Repository,
        sessionUser = { sessionStore.loadQuietly()?.user?.userId },
        scope = applicationScope,
        onTaskTerminal = { _ -> authoritativeRefresh() },
    )

    /**
     * Exactly one authoritative pull after a permanent outbox failure: force the fast
     * resources once, then return to soft-TTL revalidation.
     */
    private suspend fun authoritativeRefresh() {
        val userId = sessionStore.loadQuietly()?.user?.userId ?: return
        if (userId.isBlank()) return
        v25Repository.currentPolicy = RefreshPolicy.FORCE
        try {
            runCatching { v25Repository.listDecks() }
            runCatching { v25Repository.todayPlan() }
            runCatching { v25Repository.statsDashboard() }
        } finally {
            v25Repository.currentPolicy = RefreshPolicy.SOFT_TTL
        }
    }

    /** Sign-in: resume a 401-paused sync and enqueue the per-user WorkManager backstops. */
    fun onUserSignedIn() {
        val userId = sessionStore.loadQuietly()?.user?.userId ?: return
        v25Repository.onSignedIn()
        observationEngine.start()
        observationEngine.reconcile()
        ReviewSyncWorker.enqueue(appContext, userId)
        ProcessingSyncWorker.enqueueOneTime(appContext)
        ProcessingSyncWorker.enqueuePeriodic(appContext)
    }

    /** Sign-out: cancel background revalidation, pause syncing, keep the account-isolated cache. */
    fun onUserSignedOut() {
        observationEngine.stop()
        applicationScope.launch { runCatching { v25Repository.onSignedOut() } }
    }
}

/** The application entry that owns the [AppContainer]. */
class FlashcardsApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
