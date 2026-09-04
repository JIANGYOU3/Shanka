package com.qiuzhao.flashcards.data.session

/** androidTest 侧的 [SessionStore] 内存实现：注入缝测试无需触碰真实 Keystore 存储。 */
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
