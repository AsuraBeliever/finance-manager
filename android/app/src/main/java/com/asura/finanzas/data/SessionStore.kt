package com.asura.finanzas.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.asura.finanzas.BuildConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private val Context.sessionDataStore by preferencesDataStore("broke_session")
private val SESSION_KEY = stringPreferencesKey("session_cookie")
private const val SESSION_COOKIE_NAME = "session"

/**
 * The backend authenticates with an HttpOnly session cookie (`session`, 30 days,
 * see worker/src/auth). OkHttp forgets cookies on process death unless something
 * persists them, so this jar keeps that one cookie in DataStore — the phone stays
 * signed in across restarts exactly like the browser does.
 *
 * Nothing else is stored: no password, no token copy, and only for the API host.
 */
class SessionCookieJar(private val context: Context) : CookieJar {

    @Volatile
    private var cached: Cookie? = null

    /** Load the persisted cookie. Call once before the first request goes out. */
    suspend fun restore() {
        val raw = context.sessionDataStore.data.first()[SESSION_KEY] ?: return
        val url = BuildConfig.API_BASE.toHttpUrlOrNull() ?: return
        cached = Cookie.parse(url, raw)?.takeIf { it.expiresAt > System.currentTimeMillis() }
    }

    suspend fun clear() {
        cached = null
        context.sessionDataStore.edit { it.remove(SESSION_KEY) }
    }

    /** True when we hold a live cookie — lets the app skip the login screen. */
    fun hasSession(): Boolean =
        cached?.let { it.expiresAt > System.currentTimeMillis() } == true

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val session = cookies.lastOrNull { it.name == SESSION_COOKIE_NAME } ?: return
        // Logout arrives as the same cookie already expired; persisting that
        // would keep a dead value around, so treat expiry as a clear.
        val expired = session.expiresAt <= System.currentTimeMillis()
        cached = if (expired) null else session
        runBlocking {
            context.sessionDataStore.edit { prefs ->
                if (expired) prefs.remove(SESSION_KEY) else prefs[SESSION_KEY] = session.toString()
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        cached?.takeIf { it.expiresAt > System.currentTimeMillis() }
            ?.let { listOf(it) }
            .orEmpty()
}
