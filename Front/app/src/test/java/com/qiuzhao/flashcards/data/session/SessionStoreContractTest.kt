package com.qiuzhao.flashcards.data.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Locks the [SessionStore] contract with a pure-JVM fake. The Keystore-backed implementation
 * needs the Android runtime and is verified on device, not here.
 */
class SessionStoreContractTest {
    private val store: SessionStore = InMemorySessionStore()

    @Test fun `load returns null before anything is saved`() {
        assertNull(store.load())
    }

    @Test fun `save then load round-trips token and user`() {
        val user = SessionUser(userId = "user-1", username = "alice", createdAt = "2026-08-14T00:00:00Z")
        store.save(token = "token-abc", user = user)
        assertEquals(Session(token = "token-abc", user = user), store.load())
    }

    @Test fun `saving again replaces the previous session`() {
        store.save("token-old", SessionUser("user-old", "old-user", "2026-01-01T00:00:00Z"))
        val newer = SessionUser("user-new", "new-user", "2026-02-01T00:00:00Z")
        store.save("token-new", newer)
        assertEquals(Session(token = "token-new", user = newer), store.load())
    }

    @Test fun `clear removes the stored session`() {
        store.save("token-abc", SessionUser("user-1", "alice", "2026-08-14T00:00:00Z"))
        store.clear()
        assertNull(store.load())
    }

    @Test fun `loadQuietly swallows a throwing store and degrades to signed out`() {
        val throwing = object : SessionStore {
            override fun save(token: String, user: SessionUser) {}
            override fun load(): Session? = error("keystore unavailable")
            override fun clear() {}
        }
        assertNull(throwing.loadQuietly())
    }
}
