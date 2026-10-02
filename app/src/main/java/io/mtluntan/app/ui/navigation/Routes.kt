package io.mtluntan.app.ui.navigation

import java.net.URLEncoder

object Routes {
    // 底栏四个 Tab
    const val GUIDE = "guide"
    const val COMMUNITY = "community"
    const val NOTICE = "notice"
    const val MINE = "mine"

    // 内容
    const val FORUM = "forum/{fid}/{name}"
    const val THREAD = "thread/{tid}"
    const val REPLY = "reply/{tid}"
    const val EDIT_REPLY = "editreply/{tid}/{pid}"
    const val NEW_THREAD = "new/{fid}"
    const val SEARCH = "search"

    // 本地
    const val SETTINGS = "settings"
    const val HISTORY = "history"
    const val FAVORITES = "favorites"
    const val DRAFTS = "drafts"
    const val RECORDS = "records"
    const val OFFLINE = "offline"

    // 账号
    const val LOGIN = "login"
    const val ACCOUNTS = "accounts"
    const val SIGN_RECORDS = "signrecords"

    // 社交
    const val PROFILE = "profile/{uid}"
    const val PM_LIST = "pmlist"
    const val NOTICE_LIST = "notices/{view}"
    const val FOLLOWERS = "followers"
    const val PM = "pm/{uid}/{name}"
    const val FRIENDS = "friends/{uid}"
    const val BLACKLIST = "blacklist"
    const val CREDITS = "credits"

    // AI
    const val AI_SESSIONS = "ai"
    const val AI_CHAT = "ai/{sessionId}"
    const val AI_CONFIG = "aiconfig"

    fun forum(fid: Long, name: String) = "forum/$fid/${enc(name)}"
    fun thread(tid: Long) = "thread/$tid"
    fun reply(tid: Long) = "reply/$tid"
    fun editReply(tid: Long, pid: Long) = "editreply/$tid/$pid"
    fun newThread(fid: Long) = "new/$fid"
    fun profile(uid: Long) = "profile/$uid"
    fun noticeList(view: String) = "notices/$view"
    fun pm(uid: Long, name: String) = "pm/$uid/${enc(name)}"
    fun friends(uid: Long) = "friends/$uid"
    fun aiChat(sessionId: Long) = "ai/$sessionId"

    fun dec(value: String?): String = try {
        java.net.URLDecoder.decode(value ?: "", "UTF-8")
    } catch (t: Throwable) {
        value.orEmpty()
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
}
