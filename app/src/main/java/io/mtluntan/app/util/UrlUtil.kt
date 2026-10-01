package io.mtluntan.app.util

/** URL helpers: normalization and id extraction. */
object UrlUtil {

    fun absolute(baseUrl: String, href: String?): String {
        if (href.isNullOrEmpty()) return ""
        if (href.startsWith("//")) return "https:$href"
        if (href.startsWith("http://") || href.startsWith("https://")) return href
        if (href.startsWith("/")) {
            val host = Regex("^https?://[^/]+").find(baseUrl)?.value ?: baseUrl
            return host + href
        }
        return baseUrl + "/" + href
    }

    fun uid(url: String?): Long {
        if (url.isNullOrEmpty()) return 0L
        Regex("uid=(\\d+)").find(url)?.let { return it.groupValues[1].toLongOrNull() ?: 0L }
        Regex("uid-(\\d+)").find(url)?.let { return it.groupValues[1].toLongOrNull() ?: 0L }
        return 0L
    }

    fun tid(url: String?): Long {
        if (url.isNullOrEmpty()) return 0L
        Regex("tid=(\\d+)").find(url)?.let { return it.groupValues[1].toLongOrNull() ?: 0L }
        Regex("thread-(\\d+)").find(url)?.let { return it.groupValues[1].toLongOrNull() ?: 0L }
        return 0L
    }

    fun fid(url: String?): Long {
        if (url.isNullOrEmpty()) return 0L
        Regex("fid=(\\d+)").find(url)?.let { return it.groupValues[1].toLongOrNull() ?: 0L }
        Regex("forum-(\\d+)").find(url)?.let { return it.groupValues[1].toLongOrNull() ?: 0L }
        return 0L
    }

    fun pid(url: String?): Long {
        if (url.isNullOrEmpty()) return 0L
        Regex("pid=(\\d+)").find(url)?.let { return it.groupValues[1].toLongOrNull() ?: 0L }
        Regex("pid(\\d+)").find(url)?.let { return it.groupValues[1].toLongOrNull() ?: 0L }
        return 0L
    }
}