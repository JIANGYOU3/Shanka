package com.qiuzhao.flashcards.data.session

/** Test double for [SessionStore]: in-memory only, no persistence, no encryption. */
class InMemorySessionStore : SessionStore {
    private var session: Session? = null

    override fun save(token: String, user: SessionUser) {
        session = Session(token, user)
    }

    override fun load(): Session? = session

    override fun clear() {
        session = null
    }
}
