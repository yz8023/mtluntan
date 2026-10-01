package io.mtluntan.app.data.repo

import io.mtluntan.app.data.network.ApiUris
import io.mtluntan.app.data.network.Net
import io.mtluntan.app.data.parser.EditorParser
import io.mtluntan.app.data.parser.ForumIndexParser
import io.mtluntan.app.data.parser.SignParser
import io.mtluntan.app.data.parser.ThreadDetailParser
import io.mtluntan.app.data.parser.ThreadListParser
import io.mtluntan.app.data.parser.UserPagesParser
import io.mtluntan.app.domain.model.EditorMeta
import io.mtluntan.app.domain.model.ForumCategory
import io.mtluntan.app.domain.model.SubmitResult
import io.mtluntan.app.domain.model.ThreadDetail
import io.mtluntan.app.domain.model.ThreadItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** All server-facing operations. Suspending; safe to call from any scope. */
class ForumRepository(private val net: Net) {

    suspend fun guide(view: String, page: Int): List<ThreadItem> = withContext(Dispatchers.IO) {
        val html = net.get(ApiUris.guide(view, page))
        ThreadListParser.parse(html).items
    }

    suspend fun forumIndex(): List<ForumCategory> = withContext(Dispatchers.IO) {
        val html = net.get(ApiUris.forumIndex())
        ForumIndexParser.parse(html)
    }

    suspend fun forumThreads(
        fid: Long,
        page: Int,
        orderby: String = "",
        filter: String = "",
    ): List<ThreadItem> = withContext(Dispatchers.IO) {
        val html = net.get(ApiUris.forumDisplay(fid, page, orderby, filter))
        ThreadListParser.parse(html).items
    }

    suspend fun threadDetail(tid: Long, page: Int): ThreadDetail = withContext(Dispatchers.IO) {
        val html = net.get(ApiUris.viewThread(tid, page))
        ThreadDetailParser.parse(html, page)
    }

    suspend fun threadDetailPc(tid: Long, page: Int): ThreadDetail = withContext(Dispatchers.IO) {
        val html = net.get(ApiUris.viewThreadPc(tid, page))
        ThreadDetailParser.parse(html, page)
    }

    suspend fun fetchEditor(url: String): EditorMeta = withContext(Dispatchers.IO) {
        val html = net.get(url)
        EditorParser.parseEditor(html, url)
    }

    /** Submits a new thread. */
    suspend fun submitThread(fid: Long, formhash: String, title: String, message: String): SubmitResult =
        withContext(Dispatchers.IO) {
            val url = "${ApiUris.SITE}/forum.php?mod=post&action=newthread&fid=$fid&topicsubmit=yes&mobile=2"
            val html = net.postForm(url, mapOf(
                "formhash" to formhash, "posttime" to "", "wysiwyg" to "0",
                "subject" to title, "message" to message, "usesig" to "1", "noticetrimstr" to "",
            ))
            EditorParser.parseSubmit(html, url)
        }

    /** Submits a reply. */
    suspend fun submitReply(tid: Long, formhash: String, message: String): SubmitResult =
        withContext(Dispatchers.IO) {
            val url = "${ApiUris.SITE}/forum.php?mod=post&action=reply&tid=$tid&fid=2&replysubmit=yes&mobile=2"
            val html = net.postForm(url, mapOf(
                "formhash" to formhash, "posttime" to "", "wysiwyg" to "0",
                "message" to message, "usesig" to "1", "reppid" to "", "noticeauthor" to "",
            ))
            EditorParser.parseSubmit(html, url)
        }

    /** Edits an existing post (own posts). */
    suspend fun submitEdit(fid: Long, tid: Long, pid: Long, formhash: String, title: String, message: String): SubmitResult =
        withContext(Dispatchers.IO) {
            val url = "${ApiUris.SITE}/forum.php?mod=post&action=edit&fid=$fid&tid=$tid&pid=$pid&editsubmit=yes&mobile=2"
            val params = mutableMapOf<String, String>(
                "formhash" to formhash, "posttime" to "", "wysiwyg" to "0",
                "message" to message, "usesig" to "1",
            )
            if (title.isNotEmpty()) params["subject"] = title
            val html = net.postForm(url, params)
            EditorParser.parseSubmit(html, url)
        }

    suspend fun like(tid: Long, pid: Long, formhash: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val html = net.postForm(
                ApiUris.likeAction(tid, pid, formhash),
                mapOf("formhash" to formhash, "tid" to tid.toString(), "pid" to pid.toString()),
                ajax = true,
            )
            html.contains("成功") && !html.contains("失败")
        } catch (e: Exception) { false }
    }

    suspend fun favorite(tid: Long, formhash: String): Boolean = withContext(Dispatchers.IO) {
        try {
            net.postForm(ApiUris.favoriteAction(tid, formhash), mapOf("formhash" to formhash))
            true
        } catch (e: Exception) { false }
    }

    suspend fun myThreads(uid: Long, page: Int): List<ThreadItem> = withContext(Dispatchers.IO) {
        val html = net.get(ApiUris.myThreads(uid, page))
        ThreadListParser.parse(html).items
    }

    suspend fun myReplies(uid: Long, page: Int): List<ThreadItem> = withContext(Dispatchers.IO) {
        val html = net.get(ApiUris.myReplies(uid, page))
        ThreadListParser.parse(html).items
    }

    suspend fun profile(uid: Long) = withContext(Dispatchers.IO) {
        val html = net.get(ApiUris.space(uid))
        UserPagesParser.parseProfile(html)
    }

    suspend fun myProfile() = withContext(Dispatchers.IO) {
        val html = net.get(ApiUris.space(0, doWhat = "profile"))
        UserPagesParser.parseProfile(html)
    }

    suspend fun search(q: String, page: Int): List<ThreadItem> = withContext(Dispatchers.IO) {
        val html = net.get(ApiUris.search(q, page))
        ThreadListParser.parse(html).items
    }

    suspend fun notices() = withContext(Dispatchers.IO) {
        val html = net.get(ApiUris.notice())
        UserPagesParser.parseNotices(html)
    }

    suspend fun pms() = withContext(Dispatchers.IO) {
        val html = net.get(ApiUris.noticePm())
        UserPagesParser.parsePmList(html)
    }

    // ---------- check-in ----------

    suspend fun signState(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val html = net.get(ApiUris.signPage())
            val state = SignParser.parseSignPage(html)
            if (state.signedToday) Pair(true, "今日已签到")
            else {
                val doc = io.mtluntan.app.data.parser.Parsing.doc(html)
                val formhash = io.mtluntan.app.data.parser.Parsing.value(doc, "formhash")
                Pair(false, formhash)
            }
        } catch (e: Exception) {
            Pair(false, "")
        }
    }

    /** Executes 签到 for the active session. Returns (ok, message). */
    suspend fun doSignIn(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val html = net.get(ApiUris.signPage())
            val doc = io.mtluntan.app.data.parser.Parsing.doc(html)
            val formhash = io.mtluntan.app.data.parser.Parsing.value(doc, "formhash")
            if (formhash.isEmpty()) {
                val state = SignParser.parseSignPage(html)
                return@withContext if (state.signedToday) Pair(true, "今日已签到") else Pair(false, "未找到签到表单")
            }
            val res = net.postForm(ApiUris.signAction(formhash), mapOf("formhash" to formhash, "operation" to "qiandao"), ajax = true)
            val state = SignParser.parseAjaxResult(res)
            Pair(state.signedToday, state.message)
        } catch (e: Exception) {
            Pair(false, "网络错误: ${e.message}")
        }
    }
}