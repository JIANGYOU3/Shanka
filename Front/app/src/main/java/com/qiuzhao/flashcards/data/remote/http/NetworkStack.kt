package com.qiuzhao.flashcards.data.remote.http

import com.qiuzhao.flashcards.BuildConfig
import com.qiuzhao.flashcards.data.session.SessionStore
import com.qiuzhao.flashcards.data.session.loadQuietly
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * The single network foundation of the app (Architecture §8): one OkHttp connection pool and
 * dispatcher, one bearer-session interceptor, one 429 Retry-After policy and one evidence sink.
 * Auth and V2.5 build their Retrofit API surfaces on this same stack, so there is exactly one
 * dispatcher, one endpoint authority and one shared connection pool; there is no second
 * handwritten transport anywhere in the app.
 *
 * Interceptor order (outermost first):
 * 1. [TraceInterceptor] — stamps the per-request trace id (evidence lineage);
 * 2. [BearerInterceptor] — attaches the stored bearer token / honours the anonymous marker;
 * 3. [Retry429Interceptor] — one Retry-After driven retry re-proceeding the identical request,
 *    so a caller-fixed Idempotency-Key is replayed verbatim;
 * 4. [EvidenceInterceptor] — per-attempt metadata-only evidence (innermost = every attempt).
 */
class NetworkStack(
    /** The shared bearer-session source; exposed so repositories can run local-first logout. */
    val sessionStore: SessionStore,
    /** Tests and preview stacks inject the MockWebServer URL; production resolves lazily. */
    baseUrlOverride: String? = null,
    private val evidence: NetworkEvidence? = null,
) {
    /** Retrofit requires a trailing slash so relative (@GET) paths join it. */
    val baseUrl: String = (baseUrlOverride ?: EndpointAuthority.baseUrl()).trimEnd('/') + "/"

    /** Wire strictness: unknown fields are tolerated, required fields stay mandatory. */
    val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = false
        // Nulls encode explicitly: PATCH bodies where `null` means "clear" serialize as null.
        explicitNulls = true
        namingStrategy = JsonNamingStrategy.SnakeCase
    }

    /** JSON surface: 8s connect / 25s read (same budget as the replaced handwritten client). */
    val apiClient: OkHttpClient = buildClient(
        TraceInterceptor(),
        BearerInterceptor(sessionStore),
        Retry429Interceptor(),
        evidence?.let(::EvidenceInterceptor),
    )

    /** Large multipart uploads: shared pool + dispatcher + auth, longer read timeout. */
    val uploadClient: OkHttpClient = apiClient.newBuilder().readTimeout(60, TimeUnit.SECONDS).build()

    fun retrofit(client: OkHttpClient = apiClient): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    private fun buildClient(vararg interceptors: Interceptor?): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
        interceptors.filterNotNull().forEach(builder::addInterceptor)
        return builder.build()
    }
}

/**
 * Build variants are authoritative: the debug build's API_BASE_URL already
 * resolves to the emulator loopback by default, or to the -PshankaDebugApiBaseUrl
 * override used with `adb reverse`; the release build fixes production there.
 */
object EndpointAuthority {
    fun baseUrl(): String = BuildConfig.API_BASE_URL
}

/**
 * Attaches the stored bearer token unless the request already carries an explicit
 * Authorization header (logout revocation uses the pre-clear token) or is marked anonymous
 * (register/login must never leave with a stale session token).
 */
class BearerInterceptor(private val sessionStore: SessionStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.header("Authorization") != null) return chain.proceed(request)
        if (request.header(ANONYMOUS_HEADER) != null) {
            return chain.proceed(request.newBuilder().removeHeader(ANONYMOUS_HEADER).build())
        }
        val token = sessionStore.loadQuietly()?.token ?: return chain.proceed(request)
        return chain.proceed(request.newBuilder().header("Authorization", "Bearer $token").build())
    }

    companion object {
        const val ANONYMOUS_HEADER = "X-Shanka-Anonymous"
    }
}

/**
 * One 429 retry honoring the server's Retry-After (1..30s, beyond which the response is kept).
 * The request is re-proceeded with the identical request object — the Idempotency-Key the
 * caller fixed is part of that immutable header map, so an automatic retry can never generate
 * a new key.
 */
class Retry429Interceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var attempts = 0
        while (true) {
            val response = chain.proceed(chain.request())
            if (attempts >= 1 || response.code != 429 || chain.call().isCanceled()) return response
            val seconds = response.header("Retry-After")?.toLongOrNull()?.coerceIn(1, 30)
                ?: return response
            response.close()
            Thread.sleep(seconds * 1_000)
            attempts++
        }
    }
}
