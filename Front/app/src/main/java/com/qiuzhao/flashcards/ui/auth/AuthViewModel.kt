package com.qiuzhao.flashcards.ui.auth

import com.qiuzhao.flashcards.data.remote.ApiResult
import com.qiuzhao.flashcards.data.remote.AuthRepository
import com.qiuzhao.flashcards.data.remote.isAuthFailure
import com.qiuzhao.flashcards.data.session.Session
import com.qiuzhao.flashcards.data.session.SessionStore
import com.qiuzhao.flashcards.data.session.SessionUser
import com.qiuzhao.flashcards.data.session.loadQuietly
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Auth session state machine. [AuthState.LoggedOut.error] and [AuthState.LoggedIn.error]
 * carry only user-facing message text derived from server error codes; server details are
 * deliberately never exposed.
 */
sealed interface AuthState {
    data object CheckingSession : AuthState

    data class LoggedOut(val error: String? = null) : AuthState

    data class LoggedIn(val user: SessionUser, val error: String? = null) : AuthState
}

const val NETWORK_ERROR_MESSAGE = "网络错误，请稍后重试"

/**
 * Transport failures (no HTTP response at all — the unified OkHttp stack maps them to
 * code NETWORK_UNAVAILABLE) show the network message; every real server error code goes
 * through the full [ErrorMessages] table, unknown codes included (generic fallback).
 */
private fun ApiResult.Failure.authErrorMessage(): String =
    if (code == "NETWORK_UNAVAILABLE") NETWORK_ERROR_MESSAGE else ErrorMessages.forCode(code)

/**
 * A plain state holder (deliberately not an AndroidX ViewModel) so the session logic runs on
 * the JVM in unit tests. [com.qiuzhao.flashcards.ui.AppViewModel] owns one instance, passes
 * its [kotlinx.coroutines.CoroutineScope], and delegates every auth decision to it.
 */
class AuthViewModel(
    private val repository: AuthRepository,
    private val sessionStore: SessionStore,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow<AuthState>(AuthState.CheckingSession)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private val _submitting = MutableStateFlow(false)
    val submitting: StateFlow<Boolean> = _submitting.asStateFlow()

    /**
     * Startup flow: a stored session is verified against /auth/me — 200 enters the app,
     * an auth 401 clears the dead session, and a network failure keeps the session and
     * enters the app with a network hint instead of logging the user out.
     */
    fun checkSession() = scope.launch {
        val session = sessionStore.loadQuietly()
        if (session == null) {
            _state.value = AuthState.LoggedOut()
            return@launch
        }
        when (val result = repository.refreshMe()) {
            is ApiResult.Success -> _state.value = AuthState.LoggedIn(result.value)
            is ApiResult.Failure ->
                if (result.isAuthFailure()) {
                    runCatching { sessionStore.clear() }
                    _state.value = AuthState.LoggedOut()
                } else {
                    _state.value = AuthState.LoggedIn(session.user, error = NETWORK_ERROR_MESSAGE)
                }
        }
    }

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) return
        scope.launch { submitLogin(email, password) }
    }

    fun register(username: String, email: String, password: String) {
        if (username.isBlank() || email.isBlank() || password.isBlank()) return
        scope.launch { submitRegister(username, email, password) }
    }

    /**
     * Suspend wrappers for the upstream AuthScreen trigger points: they reuse the plain
     * [login]/[register] submit path and return the user-facing message text ([authErrorMessage]
     * full error-code table mapping; null = success). Blank input and an in-flight submit
     * return null without touching state — callers that need an onResult callback must guard
     * those cases first.
     */
    suspend fun submitLogin(email: String, password: String): String? {
        if (email.isBlank() || password.isBlank() || _submitting.value) return null
        return submit { repository.login(email.trim(), password) }
    }

    suspend fun submitRegister(username: String, email: String, password: String): String? {
        if (username.isBlank() || email.isBlank() || password.isBlank() || _submitting.value) return null
        return submit { repository.register(username.trim(), email.trim(), password) }
    }

    /**
     * Local-first logout: the session is cleared and the state flips to [AuthState.LoggedOut]
     * immediately (the login screen never waits on the network), then the server token is
     * revoked fire-and-forget. The token is captured before the clear — the repository reads
     * nothing back from the store — so the revocation request still goes out with the right
     * token after the store is emptied; the result is discarded either way.
     */
    fun logout() {
        val session = sessionStore.loadQuietly()
        scope.launch { sessionStore.clear(); _state.value = AuthState.LoggedOut() }
        scope.launch { session?.let { runCatching { repository.logout(it.token) } } }
    }

    /**
     * Guard for business request results: only a session-death 401 (AUTH_REQUIRED/AUTH_INVALID)
     * logs the user out. Credential 401s and network failures must never clear the session, so
     * [com.qiuzhao.flashcards.ui.AppViewModel] routes every business failure through here once.
     */
    fun onBusinessFailure(result: ApiResult<*>) {
        if (result is ApiResult.Failure && result.isAuthFailure()) {
            runCatching { sessionStore.clear() }
            _state.value = AuthState.LoggedOut()
        }
    }

    fun clearError() {
        when (val current = _state.value) {
            is AuthState.LoggedIn -> _state.value = current.copy(error = null)
            is AuthState.LoggedOut -> _state.value = AuthState.LoggedOut()
            is AuthState.CheckingSession -> Unit
        }
    }

    /** Runs one login/register call; returns the user-facing error text (null = success). */
    private suspend fun submit(call: suspend () -> ApiResult<Session>): String? {
        _submitting.value = true
        _state.value = AuthState.LoggedOut()
        val error = when (val result = call()) {
            is ApiResult.Success -> { _state.value = AuthState.LoggedIn(result.value.user); null }
            is ApiResult.Failure -> result.authErrorMessage().also { _state.value = AuthState.LoggedOut(error = it) }
        }
        _submitting.value = false
        return error
    }

    companion object {
        const val PASSWORD_MISMATCH_MESSAGE = "两次输入的密码不一致"
        /** Confirmation is a form-layer rule; pure function so it stays JVM-testable. */
        fun passwordsMatch(password: String, confirmation: String): Boolean = password == confirmation
    }
}
