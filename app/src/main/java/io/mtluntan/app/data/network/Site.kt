package io.mtluntan.app.data.network

import android.content.Context
import io.mtluntan.app.R

/** Static site configuration. */
object Site {
    val baseUrl: String by lazy { "https://bbs.binmt.cc" }
    val cdnUrl: String by lazy { "https://cdn-bbs.mt2.cn" }
    val host: String get() = "bbs.binmt.cc"

    const val UA_MOBILE =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
    const val UA_PC =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    fun avatarUrl(uid: Long): String = "$baseUrl/uc_server/avatar.php?uid=$uid&size=middle"
}