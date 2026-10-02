package io.mtluntan.app.data.network

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.Cookie
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-account cookie store, persisted as JSON files under
 * `filesDir/cookies/<account>.json`. Provides the jar for the active account
 * ("guest" when null), plus a public API to inject the WAF challenge cookie.
 */
class CookieRepository(context: Context) {

    companion object {
        private const val TAG = "CookieRepo"
        private const val COOKIE_DIR = "cookies"
        private const val SESSION_COOKIE_HOST = "bbs.binmt.cc"
        private const val GUEST = "guest"
    }

    private val baseDir = context.getDir(COOKIE_DIR, Context.MODE_PRIVATE)
    private val lock = Object()
    private val jars = ConcurrentHashMap<String, MutableMap<String, List<Cookie>>>()
    private val gson = Gson()
    private val cookieListType = object : TypeToken<List<SerializedCookie>>() {}.type

    @Volatile
    private var activeAccount: String? = null

    /** Maps an account name (null → guest) to its storage key. */
    fun keyFor(account: String?): String = account?.takeIf { it.isNotBlank() } ?: GUEST

    fun setActiveAccount(account: String?) {
        synchronized(lock) {
            activeAccount = account
        }
    }

    fun activeKey(): String = keyFor(activeAccount)

    // ---- serialization model ----

    private data class SerializedCookie(
        val name: String,
        val value: String,
        val expiresAt: Long,
        val domain: String,
        val path: String,
        val secure: Boolean,
        val httpOnly: Boolean,
        val hostOnly: Boolean,
    )

    private fun Cookie.toSerialized(): SerializedCookie =
        SerializedCookie(name, value, expiresAt, domain, path, secure, httpOnly, hostOnly)

    private fun SerializedCookie.toCookie(url: HttpUrl): Cookie = Cookie.Builder()
        .name(name).value(value)
        .expiresAt(expiresAt)
        .path(path)
        .apply {
            if (domain.isNotEmpty()) { domain(domain) }
            if (secure) secure()
            if (hostOnly) hostOnlyDomain(url.host)
            // httpOnly is not enforced by okhttp for outgoing; ignore.
        }.build()

    // ---- in-memory jar ----

    private fun jarFor(key: String): MutableMap<String, List<Cookie>> =
        jars.getOrPut(key) { loadFromDisk(key) }

    /** Loads cookies for a host (persisted + session). */
    fun loadForRequest(url: HttpUrl, account: String?): List<Cookie> {
        val key = keyFor(account)
        val jar = jarFor(key)
        return jar[url.host] ?: emptyList()
    }

    fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>, account: String?) {
        if (cookies.isEmpty()) return
        val key = keyFor(account)
        val jar = jarFor(key)
        synchronized(lock) {
            val existing = jar.getOrDefault(url.host, emptyList()).toMutableList()
            val now = System.currentTimeMillis()
            for (c in cookies) {
                // skip expired
                if (c.expiresAt <= now) {
                    existing.removeAll { it.name == c.name && it.path == c.path }
                    continue
                }
                // some challenge/acw cookies come without domain — store them for host
                val host = if (c.domain.isNotBlank()) c.domain else url.host
                // normalize to the domain for matching later
                existing.removeAll { it.name == c.name }
                if (host.isNotBlank()) existing.add(c)
            }
            jar[url.host] = existing.distinctBy { "${it.domain}|${it.name}|${it.path}" }
            persist(key, jar)
        }
    }

    /** Injects the acw_sc__v2 challenge cookie for the given account. */
    fun setCookie(host: String, name: String, value: String, account: String?, maxAgeSeconds: Long = 3600) {
        val key = keyFor(account)
        val jar = jarFor(key)
        synchronized(lock) {
            val cookies = Cookie.Builder()
                .name(name).value(value)
                .expiresAt(System.currentTimeMillis() + maxAgeSeconds * 1000)
                .domain(host)
                .path("/")
                .build()
            val existing = jar.getOrDefault(host, emptyList()).toMutableList()
            existing.removeAll { it.name == name }
            existing.add(cookies)
            jar[host] = existing
            persist(key, jar)
        }
    }

    /** Applies a raw cookie string from a WebView login into the active jar. */
    fun importCookieString(cookieStr: String, account: String?, host: String = SESSION_COOKIE_HOST) {
        val key = keyFor(account)
        val jar = jarFor(key)
        synchronized(lock) {
            val url = HttpUrl.Builder().scheme("https").host(host).build()
            val names = mutableSetOf<String>()
            val list = cookieStr.split(";").mapNotNull { part ->
                val p = part.trim()
                val eq = p.indexOf('=')
                if (eq <= 0) return@mapNotNull null
                val name = p.substring(0, eq).trim()
                val value = p.substring(eq + 1).trim()
                if (name.isEmpty()) return@mapNotNull null
                names.add(name)
                try {
                    Cookie.Builder().name(name).value(value)
                        .domain(host).path("/")
                        .expiresAt(System.currentTimeMillis() + 30L * 24 * 3600 * 1000)
                        .build()
                } catch (e: Exception) { null }
            }
            val existing = jar.getOrDefault(host, emptyList())
                .filter { it.name !in names }
            jar[host] = existing + list
            persist(key, jar)
        }
    }

    fun clearForAccount(account: String?) {
        val key = keyFor(account)
        jars.remove(key)
        getFile(key).delete()
    }

    /** 导出该账号当前整串 Cookie（账号快照回存用；auth 必须与 saltkey 配套，整串存取）。 */
    fun exportCookieString(account: String?): String {
        val key = keyFor(account)
        val jar = jarFor(key)
        val now = System.currentTimeMillis()
        return jar.values.flatten()
            .filter { it.expiresAt > now }
            .distinctBy { it.name }
            .joinToString("; ") { "${it.name}=${it.value}" }
    }

    // ---- disk ----

    private fun getFile(key: String) = java.io.File(baseDir, "$key.json")

    private fun persist(key: String, jar: MutableMap<String, List<Cookie>>) {
        try {
            val flat = jar.flatMap { (host, list) -> list.map { it.toSerialized() } }
            getFile(key).writeText(gson.toJson(flat))
        } catch (e: Exception) {
            Log.w(TAG, "persist failed: ${e.message}")
        }
    }

    private fun loadFromDisk(key: String): MutableMap<String, List<Cookie>> {
        val result = HashMap<String, List<Cookie>>()
        try {
            val f = getFile(key)
            if (!f.exists()) return result
            val json = f.readText()
            val list: List<SerializedCookie> = gson.fromJson(json, cookieListType) ?: emptyList()
            val now = System.currentTimeMillis()
            val buckets = HashMap<String, MutableList<Cookie>>()
            for (sc in list) {
                if (sc.expiresAt <= now) continue
                val url = HttpUrl.Builder().scheme("https").host(sc.domain.ifEmpty { SESSION_COOKIE_HOST }).build()
                try {
                    val c = sc.toCookie(url)
                    buckets.getOrPut(url.host) { mutableListOf() }.add(c)
                } catch (e: Exception) { /* skip bad cookie */ }
            }
            result.putAll(buckets)
        } catch (e: Exception) {
            Log.w(TAG, "load failed: ${e.message}")
        }
        return result
    }

    fun debugSummary(): String = "accounts=${jars.size}"
}