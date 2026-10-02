package io.mtluntan.app.data.network

import android.content.Context
import io.mtluntan.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

/**
 * Central HTTP client. Owns the OkHttp engine wired with:
 *  - persisted per-account cookies ([AppCookieJar])
 *  - transparent WAF challenge solving ([WafInterceptor])
 *  - browser-like request headers
 *
 * All calls are suspend and run on the IO dispatcher, so nothing ever blocks
 * the main thread.
 */
class Net(context: Context) {

    companion object {
        @Volatile
        var instance: Net? = null
        fun init(context: Context): Net =
            instance ?: synchronized(this) {
                instance ?: Net(context.applicationContext).also { instance = it }
            }
    }

    val cookieRepo = CookieRepository(context)
    private val cookieJar = AppCookieJar(cookieRepo)
    private val sessionBridge = object : SessionState {
        override val activeAccount: String? get() = SessionHolder.activeAccount.value
        override val guestHandle: String get() = "guest"
    }

    init {
        cookieJar.bindSession(sessionBridge)
        SessionHolder.activeAccount.value?.let { cookieRepo.setActiveAccount(it) }
    }

    val client: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG)
                HttpLoggingInterceptor.Level.BASIC
            else
                HttpLoggingInterceptor.Level.NONE
        }
        OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .addInterceptor(WafInterceptor(cookieRepo) { SessionHolder.activeAccount.value })
            .addInterceptor(logging)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    @Volatile
    var forceDesktop = false

    private fun userAgent(): String =
        if (forceDesktop || (SessionHolder.desktopMode.value)) Site.UA_PC else Site.UA_MOBILE

    private fun defaultHeaders(): Map<String, String> = buildMap {
        put("User-Agent", userAgent())
        put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        put("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
        put("Referer", Site.baseUrl + "/")
    }

    fun setActiveAccount(name: String?) {
        cookieRepo.setActiveAccount(name)
    }

    /** Performs a GET and returns the (decoded) response body. */
    suspend fun get(url: String, ajax: Boolean = false, foreground: Boolean = false): String =
        withContext(Dispatchers.IO) {
            RequestThrottle.acquire(foreground)
            val builder = Request.Builder().url(url)
            defaultHeaders().forEach { (k, v) -> builder.header(k, v) }
            if (ajax) builder.header("X-Requested-With", "XMLHttpRequest")
            client.newCall(builder.build()).execute().use { processBody(it) }
        }

    suspend fun getResponse(url: String, foreground: Boolean = false): Response =
        withContext(Dispatchers.IO) {
            RequestThrottle.acquire(foreground)
            val builder = Request.Builder().url(url)
            defaultHeaders().forEach { (k, v) -> builder.header(k, v) }
            client.newCall(builder.build()).execute()
        }

    /** Performs a form POST and returns the decoded body. */
    suspend fun postForm(
        url: String,
        params: Map<String, String>,
        ajax: Boolean = true,
        foreground: Boolean = false,
        referer: String = "",
    ): String =
        withContext(Dispatchers.IO) {
            RequestThrottle.acquire(foreground)
            val form = FormBody.Builder()
            params.forEach { (k, v) -> form.add(k, v) }
            val builder = Request.Builder().url(url).post(form.build())
            defaultHeaders().forEach { (k, v) -> builder.header(k, v) }
            if (ajax) builder.header("X-Requested-With", "XMLHttpRequest")
            // Discuz 的回复/发帖接口会校验来源页，缺 Referer 会被当成机器人直接拒掉
            if (referer.isNotEmpty()) builder.header("Referer", referer)
            client.newCall(builder.build()).execute().use { processBody(it) }
        }

    suspend fun postMultipart(url: String, builder: okhttp3.MultipartBody.Builder): String =
        withContext(Dispatchers.IO) {
            RequestThrottle.acquire(false)
            val rb = url.startsWith("https://") || url.startsWith("http://")
            val req = Request.Builder()
                .url(if (rb) url else Site.baseUrl + url)
                .post(builder.build())
                .apply {
                    header("User-Agent", userAgent())
                    header("Accept", "*/*")
                    header("X-Requested-With", "XMLHttpRequest")
                    header("Referer", Site.baseUrl + "/")
                }
                .build()
            client.newCall(req).execute().use { processBody(it) }
        }

    private fun processBody(response: Response): String {
        val body = response.body?.bytes()
        return if (body == null) "" else decodeBody(body, response)
    }

    private fun decodeBody(body: ByteArray, response: Response): String {
        // Try to honor declared charset; default to UTF-8.
        val contentType = response.header("Content-Type") ?: ""
        val declared = Regex("charset=([^;\\s]+)")
            .find(contentType)?.groupValues?.getOrNull(1)?.lowercase() ?: ""
        return when {
            declared.contains("gb") -> String(body, Charset.forName("GB18030"))
            declared.contains("utf-8") -> String(body, Charsets.UTF_8)
            else -> {
                // sniff: if UTF8 decode yields replacement chars, retry as GBK
                val u = String(body, Charsets.UTF_8)
                if ('\uFFFD' in u) {
                    val g = String(body, Charset.forName("GB18030"))
                    if ('\uFFFD' !in g) g else u
                } else u
            }
        }
    }

    fun newCall(request: Request): Call = client.newCall(request)
}