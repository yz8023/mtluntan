package io.mtluntan.app.ui.navigation

object Routes {
    const val GUIDE = "guide"
    const val COMMUNITY = "community"
    const val NOTICE = "notice"
    const val MINE = "mine"

    const val FORUM = "forum/{fid}/{name}"
    const val THREAD = "thread/{tid}"
    const val REPLY = "reply/{tid}"
    const val NEW_THREAD = "new/{fid}"
    const val SETTINGS = "settings"
    const val HISTORY = "history"
    const val FAVORITES = "favorites"
    const val LOGIN = "login"

    fun forum(fid: Long, name: String) = "forum/$fid/${java.net.URLEncoder.encode(name, "UTF-8")}"
    fun thread(tid: Long) = "thread/$tid"
    fun reply(tid: Long) = "reply/$tid"
    fun newThread(fid: Long) = "new/$fid"
}