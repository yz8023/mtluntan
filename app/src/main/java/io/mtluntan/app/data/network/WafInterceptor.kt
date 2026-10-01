package io.mtluntan.app.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * Transparent anti-bot ("acw_sc__v2") challenge interceptor.
 *
 * When the CDN returns its JS challenge page instead of the real document we
 * solve the challenge on a background thread and retry the exact same request
 * with the computed cookie. Callers see only the final real response.
 */
class WafInterceptor(
    private val cookieRepo: CookieRepository,
    private val accountProvider: () -> String?,
) : Interceptor {

    companion object {
        const val COOKIE_NAME = "acw_sc__v2"
        private const val MAX_RETRIES = 2
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        var attempts = 0
        while (true) {
            val response = chain.proceed(request)
            if (attempts >= MAX_RETRIES) return response
            if (!isPotentialChallenge(response)) return response

            val body = response.body?.string().orEmpty()
            response.close()
            if (!looksLikeChallenge(body)) {
                return response.newBuilder()
                    .body(body.toResponseBody(response.body?.contentType()))
                    .build()
            }

            val solved = runBlocking {
                withContext(Dispatchers.IO) {
                    WafChallengeSolver.solve(body)
                }
            }
            if (solved.isNullOrEmpty()) {
                return response.newBuilder().body(body.toResponseBody(null)).build()
            }
            // Persist cookie for the current account so every subsequent request
            // carries it. Expire at 3600s like the server does.
            cookieRepo.setCookie(
                host = request.url.host,
                name = COOKIE_NAME,
                value = solved.removePrefix("acw_sc__v2=").trim(),
                account = accountProvider.invoke(),
                maxAgeSeconds = 3600,
            )
            attempts++
            request = request.newBuilder()
                .header("Cookie", solved)
                .build()
        }
    }

    private fun isPotentialChallenge(response: Response): Boolean {
        val ct = response.header("Content-Type") ?: return false
        return ct.contains("text/html", ignoreCase = true)
    }

    private fun looksLikeChallenge(body: String): Boolean {
        if (body.length > 20000) return false
        return body.contains("var arg1=") && body.contains("acw_sc__v2")
    }
}