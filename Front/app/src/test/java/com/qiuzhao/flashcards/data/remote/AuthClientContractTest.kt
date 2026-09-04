package com.qiuzhao.flashcards.data.remote

import com.qiuzhao.flashcards.data.remote.http.NetworkStack
import com.qiuzhao.flashcards.data.session.InMemorySessionStore
import com.qiuzhao.flashcards.data.session.Session
import com.qiuzhao.flashcards.data.session.SessionUser
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Locks the auth network contract on the JVM against a real OkHttp/Retrofit stack pointed at a
 * MockWebServer: which requests carry a bearer token, how responses map to sessions and coded
 * failures, and the 401 session-death semantics. Fixture semantics carried over verbatim from
 * the replaced handwritten-transport test: success, missing user/token, auth failure, network
 * failure, anonymous register/login, explicit-token logout.
 */
class AuthClientContractTest {

    private val user = SessionUser(userId = "user-1", username = "alice", createdAt = "2026-08-14T00:00:00Z")
    private val session = Session(token = "token-1", user = user)

    private lateinit var server: MockWebServer
    private lateinit var store: InMemorySessionStore
    private lateinit var repository: AuthRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        store = InMemorySessionStore()
        val stack = NetworkStack(store, baseUrlOverride = server.url("/").toString())
        repository = RemoteAuthRepository(stack.retrofit().create(AuthApi::class.java), store)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    // --- bearer attach / anonymous strip ------------------------------------------------------

    @Test
    fun `signed in requests carry the stored session token`() = runBlocking {
        store.save(session.token, session.user)
        server.enqueue(MockResponse().setBody(meBody()))

        val result = repository.refreshMe()

        assertTrue(result is ApiResult.Success)
        assertEquals("Bearer token-1", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `signed out requests carry no token`() = runBlocking {
        server.enqueue(MockResponse().setBody(meBody()))

        repository.refreshMe()

        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `register and login never carry a bearer token, even when signed in`() = runBlocking {
        store.save(session.token, session.user)
        server.enqueue(MockResponse().setResponseCode(201).setBody(sessionBody()))
        server.enqueue(MockResponse().setResponseCode(200).setBody(sessionBody()))

        repository.register("alice", "alice@example.com", "secret")
        repository.login("alice@example.com", "secret")

        assertNull(server.takeRequest().getHeader("Authorization"))
        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `logout carries the explicit token even after the store was cleared`() = runBlocking {
        store.save(session.token, session.user)
        server.enqueue(MockResponse().setResponseCode(204))

        val result = repository.logout(session.token)

        assertTrue(result is ApiResult.Success)
        val request = server.takeRequest()
        assertEquals("Bearer token-1", request.getHeader("Authorization"))
        assertTrue(request.getHeader("Idempotency-Key")!!.isNotBlank())
    }

    // --- session persistence and parse failures -------------------------------------------------

    @Test
    fun `register and login responses become a persisted session`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody(sessionBody()))

        val result = repository.register("alice", "alice@example.com", "secret")

        assertEquals(ApiResult.Success(session), result)
        assertEquals(session, store.load())
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"username\":\"alice\""))
        assertTrue(body.contains("\"email\":\"alice@example.com\""))
        assertTrue(body.contains("\"password\":\"secret\""))
    }

    @Test
    fun `a login body without an access token is an INVALID_RESPONSE failure`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"user": {"user_id": "user-1", "username": "alice", "created_at": "2026-08-14T00:00:00Z"}}""",
            ),
        )

        val result = repository.login("alice@example.com", "secret") as ApiResult.Failure

        assertEquals("INVALID_RESPONSE", result.code)
        assertNull(store.load())
    }

    @Test
    fun `an auth session body without a user is an INVALID_RESPONSE failure`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"access_token": "token-1"}"""))

        val result = repository.login("alice@example.com", "secret") as ApiResult.Failure

        assertEquals("INVALID_RESPONSE", result.code)
    }

    @Test
    fun `refreshMe maps the me response user shape`() = runBlocking {
        server.enqueue(MockResponse().setBody(meBody()))

        val result = repository.refreshMe()

        assertEquals(ApiResult.Success(user), result)
    }

    // --- failure mapping -------------------------------------------------------------------------

    @Test
    fun `a 401 auth error becomes a coded failure`() = runBlocking {
        store.save(session.token, session.user)
        server.enqueue(
            MockResponse().setResponseCode(401).setBody(
                """{"error": {"code": "AUTH_INVALID", "message": "会话无效", "localization_key": "auth.invalid"}}""",
            ),
        )

        val result = repository.refreshMe() as ApiResult.Failure

        assertEquals(401, result.status)
        assertEquals("AUTH_INVALID", result.code)
        assertEquals("auth.invalid", result.localizationKey)
        assertEquals("会话无效", result.message)
        // A session-death 401 also clears the store.
        assertNull(store.load())
    }

    @Test
    fun `credential failures never clear the stored session`() = runBlocking {
        store.save(session.token, session.user)
        server.enqueue(
            MockResponse().setResponseCode(401).setBody(
                """{"error": {"code": "INVALID_CREDENTIALS", "message": "用户名或密码错误"}}""",
            ),
        )

        val result = repository.login("alice@example.com", "wrong") as ApiResult.Failure

        assertEquals("INVALID_CREDENTIALS", result.code)
        assertFalse(result.isAuthFailure())
        assertEquals(session, store.load())
    }

    @Test
    fun `network failures surface as a 503 NETWORK_UNAVAILABLE result`() = runBlocking {
        server.shutdown()

        val result = repository.login("alice@example.com", "secret") as ApiResult.Failure

        assertEquals(503, result.status)
        assertEquals("NETWORK_UNAVAILABLE", result.code)
        assertFalse(result.isAuthFailure())
    }

    @Test
    fun `logout keeps the local clear even when revocation fails`() = runBlocking {
        store.save(session.token, session.user)
        server.enqueue(
            MockResponse().setResponseCode(401).setBody(
                """{"error": {"code": "AUTH_INVALID", "message": "会话无效"}}""",
            ),
        )

        val result = repository.logout(session.token)

        assertTrue((result as ApiResult.Failure).isAuthFailure())
        assertNull(store.load())
    }

    @Test
    fun `only auth 401s are session death, never credential or network failures`() {
        assertTrue(ApiResult.Failure(401, "AUTH_REQUIRED", null, null).isAuthFailure())
        assertTrue(ApiResult.Failure(401, "AUTH_INVALID", null, null).isAuthFailure())
        assertFalse(ApiResult.Failure(401, "INVALID_CREDENTIALS", null, null).isAuthFailure())
        assertFalse(ApiResult.Failure(503, "NETWORK_UNAVAILABLE", null, null).isAuthFailure())
    }

    // --- fixtures ---------------------------------------------------------------------------------

    private fun sessionBody(): String =
        """{"user": {"user_id": "user-1", "username": "alice", "created_at": "2026-08-14T00:00:00Z"}, "access_token": "token-1", "token_type": "Bearer", "expires_at": "2026-08-21T00:00:00Z"}"""

    private fun meBody(): String =
        """{"user": {"user_id": "user-1", "username": "alice", "created_at": "2026-08-14T00:00:00Z", "email": "alice@example.com", "avatar_key": "mood_01"}}"""
}
