package io.mtluntan.app.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

/**
 * 指定账号的**隔离**会话（对应 Java 版 MtSignApi「每次调用新建带独立 CookieJar 的
 * OkHttpClient，全程不碰全局 HttpClient」的做法）。
 *
 * 这是「后台给 N 个账号签到、前台账号照常用」的前提：所有请求只读写目标账号的
 * jar，绝不经过 [SessionHolder.activeAccount]，也不会改到前台会话。
 *
 * 另外固定 HTTP/1.1 + Connection: close：论坛前置的 ESA 对 HTTP/2 复用连接偶发
 * `unexpected end of stream`，Java 版实测这样最稳。
 */
class IsolatedClient(
    private val cookieRepo: CookieRepository,
    val account: String,
) {

    private val jar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) =
            cookieRepo.saveFromResponse(url, cookies, account)

        override fun loadForRequest(url: HttpUrl): List<Cookie> =
            cookieRepo.loadForRequest(url, account)
    }

    val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(jar)
        .addInterceptor(WafInterceptor(cookieRepo) { account })
        .protocols(listOf(Protocol.HTTP_1_1))
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun get(url: String, ajax: Boolean = false, referer: String = Site.baseUrl + "/"): String =
        withContext(Dispatchers.IO) {
            execute(Request.Builder().url(url).header("User-Agent", Site.UA_MOBILE).apply {
                header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                header("Connection", "close")
                header("Referer", referer)
                if (ajax) header("X-Requested-With", "XMLHttpRequest")
            }.build())
        }

    suspend fun postForm(
        url: String,
        params: Map<String, String>,
        referer: String = Site.baseUrl + "/",
        ajax: Boolean = false,
    ): String = withContext(Dispatchers.IO) {
        val form = FormBody.Builder()
        params.forEach { (k, v) -> form.add(k, v) }
        val request = Request.Builder().url(url).post(form.build()).apply {
            header("User-Agent", Site.UA_MOBILE)
            header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            header("Connection", "close")
            header("Referer", referer)
            header("Origin", Site.baseUrl)
            if (ajax) header("X-Requested-With", "XMLHttpRequest")
        }.build()
        execute(request)
    }

    private fun execute(request: Request): String {
        client.newCall(request).execute().use { response: Response ->
            val bytes = response.body?.bytes() ?: ByteArray(0)
            return decode(bytes, response)
        }
    }

    private fun decode(body: ByteArray, response: Response): String {
        val contentType = response.header("Content-Type") ?: ""
        val charset = Regex("charset=([^;\\s]+)").find(contentType)?.groupValues?.getOrNull(1)
        return when {
            charset == null -> {
                val utf8 = String(body, Charsets.UTF_8)
                if ('\uFFFD' in utf8) String(body, Charset.forName("GB18030")) else utf8
            }
            charset.contains("gb", true) -> String(body, Charset.forName("GB18030"))
            else -> String(body, Charsets.UTF_8)
        }
    }

    /** 该账号当前的整串 Cookie（账号快照回存用，auth 必须与 saltkey 配套）。 */
    fun exportCookies(): String = cookieRepo.exportCookieString(account)
}
