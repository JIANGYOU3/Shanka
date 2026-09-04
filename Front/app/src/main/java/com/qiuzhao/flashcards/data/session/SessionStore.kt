package com.qiuzhao.flashcards.data.session

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

/**
 * Persists the signed-in session. The interface deliberately carries no password parameter:
 * passwords are never stored, and only the bearer [Session.token] is persisted.
 */
interface SessionStore {
    fun save(token: String, user: SessionUser)
    fun load(): Session?
    fun clear()
}

data class SessionUser(val userId: String, val username: String, val createdAt: String)

data class Session(val token: String, val user: SessionUser)

/**
 * Reads the stored session without ever throwing: the interface does not promise a
 * no-throw [SessionStore.load], and a storage failure (e.g. an unavailable Keystore)
 * must degrade to signed-out instead of crashing the caller's request path.
 */
fun SessionStore.loadQuietly(): Session? = runCatching { load() }.getOrNull()

/**
 * The session payload is encrypted at rest. The token is a credential, so callers must treat
 * the returned [Session] as secret and never log it.
 */
class KeystoreSessionStore(context: Context) : SessionStore {
    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE)

    override fun save(token: String, user: SessionUser) {
        val payload = JSONObject()
            .put("token", token)
            .put("user", JSONObject()
                .put("user_id", user.userId)
                .put("username", user.username)
                .put("created_at", user.createdAt))
            .toString()
        preferences.edit().putString(SESSION_KEY, encrypt(payload)).apply()
    }

    /** Returns null and wipes the stored blob when it is missing or fails to decrypt/parse. */
    override fun load(): Session? {
        val stored = preferences.getString(SESSION_KEY, null) ?: return null
        val session = stored.let { decrypt(it)?.let { payload -> runCatching { sessionFrom(payload) }.getOrNull() } }
        if (session == null) preferences.edit().remove(SESSION_KEY).apply()
        return session
    }

    override fun clear() {
        preferences.edit().remove(SESSION_KEY).apply()
    }

    private fun sessionFrom(payload: String): Session {
        val value = JSONObject(payload)
        val user = value.getJSONObject("user")
        return Session(
            token = value.getString("token"),
            user = SessionUser(
                userId = user.getString("user_id"),
                username = user.getString("username"),
                createdAt = user.getString("created_at")
            )
        )
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(value: String): String = Cipher.getInstance(TRANSFORMATION).run {
        init(Cipher.ENCRYPT_MODE, key())
        Base64.encodeToString(iv + doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String? = runCatching {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        require(bytes.size > IV_LENGTH)
        Cipher.getInstance(TRANSFORMATION).run {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, bytes.copyOfRange(0, IV_LENGTH)))
            String(doFinal(bytes.copyOfRange(IV_LENGTH, bytes.size)), Charsets.UTF_8)
        }
    }.getOrNull()

    private companion object {
        const val PREFERENCES_FILE = "auth_session"
        const val SESSION_KEY = "session"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "shanka_session_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
