package com.qiuzhao.flashcards.data.remote

import com.qiuzhao.flashcards.data.remote.http.ErrorEnvelope
import com.qiuzhao.flashcards.data.session.Session
import com.qiuzhao.flashcards.data.session.SessionStore
import com.qiuzhao.flashcards.data.session.SessionUser
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Header

/**
 * Result of one repository call. Same shape the legacy handwritten transport exposed so the
 * [AuthViewModel] session machine keeps its exact semantics: [status] is the HTTP status (503
 * for transport-unreachable), [code] the server error code (NETWORK_UNAVAILABLE /
 * INVALID_RESPONSE / …) and [localizationKey] the optional i18n key.
 */
sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>
    data class Failure(
        val status: Int,
        val code: String?,
        val localizationKey: String?,
        val message: String?,
    ) : ApiResult<Nothing>
}

/** A 401 whose code means the stored session is dead; credential and network failures never qualify. */
fun ApiResult.Failure.isAuthFailure(): Boolean =
    status == 401 && (code == "AUTH_REQUIRED" || code == "AUTH_INVALID")

/** Maps the success lane only; a failure passes through unchanged. */
internal inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(value))
    is ApiResult.Failure -> this
}

/**
 * The auth surface of the repository (Architecture 4.1). Kept as an interface so the session
 * state machine stays JVM-testable with a fake instead of the Android-bound implementation.
 */
interface AuthRepository {
    suspend fun register(username: String, email: String, password: String): ApiResult<Session>
    suspend fun login(email: String, password: String): ApiResult<Session>
    suspend fun refreshMe(): ApiResult<SessionUser>

    /**
     * Revokes the given token on the server. The token is explicit because the local store is
     * cleared *before* revocation fires (logout is local-first); reading it back from the store
     * here would silently skip the server call.
     */
    suspend fun logout(token: String): ApiResult<Unit>
}

// --- wire types -------------------------------------------------------------------------------------

@Serializable
internal data class RegisterRequest(
    @SerialName("username") val username: String,
    @SerialName("email") val email: String,
    @SerialName("password") val password: String,
)

@Serializable
internal data class LoginRequest(
    @SerialName("email") val email: String,
    @SerialName("password") val password: String,
)

/** `/auth/me` user object; email/avatar are the V2.5 additions the session ignores. */
@Serializable
internal data class SessionUserDto(
    @SerialName("user_id") val userId: String,
    @SerialName("username") val username: String,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("email") val email: String? = null,
    @SerialName("avatar_key") val avatarKey: String? = null,
)

@Serializable
internal data class SessionUserResponse(@SerialName("user") val user: SessionUserDto)

/** register/login body: {"user": {...}, "access_token": ..., "token_type": ..., "expires_at": ...}. */
@Serializable
internal data class AuthSessionDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("user") val user: SessionUserDto? = null,
)

/** Typed Retrofit surface of the four auth endpoints on the shared [NetworkStack]. */
internal interface AuthApi {
    /** register/login are unauthenticated: the anonymous marker strips any stale session token. */
    @Headers("X-Shanka-Anonymous: 1")
    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): AuthSessionDto

    @Headers("X-Shanka-Anonymous: 1")
    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): AuthSessionDto

    /** Logout carries the explicit pre-clear token so it works even after the store was emptied. */
    @POST("auth/logout")
    suspend fun logout(
        @Header("Authorization") bearer: String,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): Unit

    @GET("auth/me")
    suspend fun me(): SessionUserResponse
}

/**
 * The Retrofit/[okhttp3.OkHttp] implementation of [AuthRepository]: one shared OkHttp stack
 * (connection pool, bearer session, 429 Retry-After, evidence) with the V2.5 client, so there
 * is exactly one dispatcher and no second handwritten transport.
 *
 * Semantics preserved from the replaced client:
 * - register/login never carry a bearer token (anonymous marker) and never an Idempotency-Key
 *   (contract FR-19: no automatic retry that could silently create sessions);
 * - a login/register success is persisted to the [SessionStore]; a storage failure never
 *   surfaces as a login error (the session stays usable in memory);
 * - a session-death 401 on refreshMe clears the store; credential and network failures never do.
 */
class RemoteAuthRepository internal constructor(
    private val api: AuthApi,
    private val sessionStore: SessionStore,
) : AuthRepository {

    override suspend fun register(username: String, email: String, password: String): ApiResult<Session> =
        call { api.register(RegisterRequest(username, email, password)) }.toSession()

    override suspend fun login(email: String, password: String): ApiResult<Session> =
        call { api.login(LoginRequest(email, password)) }.toSession()

    override suspend fun refreshMe(): ApiResult<SessionUser> {
        val result = call { api.me() }.map { response -> response.user.toDomain() }
        if (result is ApiResult.Failure && result.isAuthFailure()) runCatching { sessionStore.clear() }
        return result
    }

    override suspend fun logout(token: String): ApiResult<Unit> {
        val result = call { api.logout("Bearer $token", UUID.randomUUID().toString()) }
        if (result is ApiResult.Success || (result as? ApiResult.Failure)?.isAuthFailure() == true) {
            runCatching { sessionStore.clear() }
        }
        return result
    }

    private fun ApiResult<AuthSessionDto>.toSession(): ApiResult<Session> = when (this) {
        is ApiResult.Success -> {
            val user = value.user
                ?: return ApiResult.Failure(200, "INVALID_RESPONSE", null, "Auth session response missing user")
            val session = Session(token = value.accessToken, user = user.toDomain())
            runCatching { sessionStore.save(session.token, session.user) }
            ApiResult.Success(session)
        }
        is ApiResult.Failure -> this
    }

    private fun SessionUserDto.toDomain() = SessionUser(
        userId = userId,
        username = username,
        createdAt = createdAt.orEmpty(),
    )

    private suspend fun <T> call(block: suspend () -> T): ApiResult<T> = try {
        ApiResult.Success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Throwable) {
        // The error body is a one-shot source: parse the envelope exactly once here.
        val envelope = failure.errorEnvelope()
        ApiResult.Failure(
            failure.status(),
            envelope?.code?.takeIf { it.isNotBlank() } ?: failure.fallbackCode(),
            envelope?.localizationKey,
            envelope?.message ?: failure.message,
        )
    }

    private fun Throwable.status(): Int = when (this) {
        is HttpException -> code()
        is IOException -> 503
        else -> 200
    }

    private fun Throwable.fallbackCode(): String = when (this) {
        is IOException -> "NETWORK_UNAVAILABLE"
        is SerializationException -> "INVALID_RESPONSE"
        is IllegalArgumentException -> "INVALID_RESPONSE"
        else -> "INVALID_RESPONSE"
    }

    private fun Throwable.errorEnvelope(): ErrorEnvelope? = when (this) {
        is HttpException -> runCatching { response()?.errorBody()?.string() }
            .getOrNull()?.let { ErrorEnvelope.parse(it) }
        else -> null
    }
}
