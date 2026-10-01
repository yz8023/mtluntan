package io.mtluntan.app.data.network

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * OkHttp [CookieJar] that transparently routes to [CookieRepository] for the
 * currently active account. The active account is swapped by the auth layer so
 * every request automatically uses the right session.
 */
class AppCookieJar(private val repo: CookieRepository) : CookieJar {

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        repo.saveFromResponse(url, cookies, currentAccount)
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return repo.loadForRequest(url, currentAccount)
    }

    private var shared: SessionState? = null

    fun bindSession(state: SessionState) {
        shared = state
    }

    val currentAccount: String?
        get() = shared?.let { it.activeAccount ?: it.guestHandle } ?: "guest"
}

interface SessionState {
    val activeAccount: String?
    val guestHandle: String
}