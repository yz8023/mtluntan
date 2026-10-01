package io.mtluntan.app.data.network

/** Builders for every Discuz endpoint the client talks to. */
object ApiUris {

    const val SITE = "https://bbs.binmt.cc"

    /** Guide (导读): view ∈ newthread|new|hot|digest|comiis_last … */
    fun guide(view: String, page: Int, desktop: Boolean = false): String =
        "$SITE/forum.php?mod=guide&index=1&view=$view&page=$page&mobile=2"

    /** Forum index / community page. */
    fun forumIndex(): String = "$SITE/forum.php?forumlist=1&mobile=2"

    /** Thread list of a board. */
    fun forumDisplay(fid: Long, page: Int, orderby: String = "", filter: String = ""): String {
        val sb = StringBuilder("$SITE/forum.php?mod=forumdisplay&fid=$fid&page=$page&mobile=2")
        if (orderby.isNotEmpty()) sb.append("&orderby=$orderby")
        if (filter.isNotEmpty()) sb.append("&filter=$filter")
        return sb.toString()
    }

    /** Thread detail with replies. */
    fun viewThread(tid: Long, page: Int = 1, authorId: Long = 0): String {
        val sb = StringBuilder("$SITE/forum.php?mod=viewthread&tid=$tid&page=$page&mobile=2")
        if (authorId > 0) sb.append("&authorid=$authorId")
        return sb.toString()
    }

    fun viewThreadPc(tid: Long, page: Int = 1): String =
        "$SITE/forum.php?mod=viewthread&tid=$tid&page=$page"

    /** New thread editor page. */
    fun newThread(fid: Long, desktop: Boolean = false): String =
        "$SITE/forum.php?mod=post&action=newthread&fid=$fid&mobile=2"

    /** Reply editor page. */
    fun reply(tid: Long, repquote: Long = 0): String {
        val q = if (repquote > 0) "&repquote=$repquote" else ""
        return "$SITE/forum.php?mod=post&action=reply&fid=2&tid=$tid$q&mobile=2"
    }

    /** Edit post page. */
    fun editPost(fid: Long, tid: Long, pid: Long): String =
        "$SITE/forum.php?mod=post&action=edit&fid=$fid&tid=$tid&pid=$pid&page=1&mobile=2"

    fun postAction(action: String): String = "$SITE/forum.php?mod=post&action=$action&mobile=2"

    /** User profile. */
    fun space(uid: Long, doWhat: String = ""): String {
        val extra = if (doWhat.isNotEmpty()) "&do=$doWhat" else ""
        return "$SITE/home.php?mod=space&uid=$uid$extra&mobile=2"
    }

    fun myThreads(uid: Long, page: Int): String =
        "$SITE/home.php?mod=space&uid=$uid&do=thread&view=me&type=thread&from=space&page=$page&mobile=2"

    fun myReplies(uid: Long, page: Int): String =
        "$SITE/home.php?mod=space&uid=$uid&do=thread&view=me&type=reply&from=space&page=$page&mobile=2"

    fun favorites(uid: Long?): String {
        val u = uid?.let { "&uid=$it" } ?: ""
        return "$SITE/home.php?mod=space&do=favorite$u&mobile=2"
    }

    /** Notifications page. */
    fun notice(): String = "$SITE/home.php?mod=space&do=notice&mobile=2"

    fun noticePm(): String = "$SITE/home.php?mod=space&do=pm&filter=privatepm&mobile=2"

    /** Search form + results. */
    fun searchForm(): String = "$SITE/search.php?mod=forum&mobile=2"

    fun search(q: String, page: Int): String =
        "$SITE/search.php?mod=forum&srchtxt=${java.net.URLEncoder.encode(q, "UTF-8")}" +
            "&searchsubmit=yes&page=$page&mobile=2"

    /** Check-in plugin endpoint (k_misign). */
    fun signPage(): String = "$SITE/plugin.php?id=k_misign:sign&mobile=2"

    fun signAction(formhash: String): String =
        "$SITE/plugin.php?id=k_misign:sign&operation=qiandao&format=text&formhash=$formhash"

    /** like (支持/评分) action. */
    fun likeAction(tid: Long, pid: Long, formhash: String): String =
        "$SITE/forum.php?mod=misc&action=like&tid=$tid&pid=$pid&formhash=$formhash&mobile=2"

    /** favorite action */
    fun favoriteAction(tid: Long, formhash: String): String =
        "$SITE/forum.php?mod=collect&action=favorite&tid=$tid&formhash=$formhash&mobile=2"

    fun loginPage(): String = "$SITE/member.php?mod=logging&action=login&mobile=2"

    fun loginPageRaw(): String = "$SITE/member.php?mod=logging&action=login"
}