package io.mtluntan.app.data.repo

import io.mtluntan.app.data.network.ApiUris
import io.mtluntan.app.data.network.IsolatedClient
import io.mtluntan.app.data.network.Net
import io.mtluntan.app.data.network.Site
import io.mtluntan.app.data.parser.EditorParser
import io.mtluntan.app.data.parser.ForumIndexParser
import io.mtluntan.app.data.parser.Parsing
import io.mtluntan.app.data.parser.SignParser
import io.mtluntan.app.data.parser.SocialParser
import io.mtluntan.app.data.parser.ThreadDetailParser
import io.mtluntan.app.data.parser.ThreadExtrasParser
import io.mtluntan.app.data.parser.ThreadListParser
import io.mtluntan.app.data.parser.UserPagesParser
import io.mtluntan.app.domain.model.Attachment
import io.mtluntan.app.domain.model.BadgeCounts
import io.mtluntan.app.domain.model.ChatMessage
import io.mtluntan.app.domain.model.CreditItem
import io.mtluntan.app.domain.model.EditorMeta
import io.mtluntan.app.domain.model.ForumCategory
import io.mtluntan.app.domain.model.LikeUser
import io.mtluntan.app.domain.model.Notice
import io.mtluntan.app.domain.model.PmSession
import io.mtluntan.app.domain.model.SubmitResult
import io.mtluntan.app.domain.model.ThreadDetail
import io.mtluntan.app.domain.model.ThreadItem
import io.mtluntan.app.domain.model.UserProfile
import io.mtluntan.app.util.LogCenter
import io.mtluntan.app.util.LogCenter.LogTag
import io.mtluntan.app.util.PerfLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/** 全部服务端操作。挂起函数，任意作用域可调用。 */
class ForumRepository(private val net: Net) {

    // ---------------- 列表 ----------------

    suspend fun guide(view: String, page: Int): List<ThreadItem> = timed("导读·$view") {
        val html = net.get(ApiUris.guide(view, page), foreground = true)
        ThreadListParser.parse(html).items
    }

    suspend fun forumIndex(): List<ForumCategory> = timed("版块首页") {
        val html = net.get(ApiUris.forumIndex(), foreground = true)
        ForumIndexParser.parse(html)
    }

    suspend fun forumThreads(
        fid: Long,
        page: Int,
        orderby: String = "",
        filter: String = "",
    ): List<ThreadItem> = timed("版块列表") {
        val html = net.get(ApiUris.forumDisplay(fid, page, orderby, filter), foreground = true)
        ThreadListParser.parse(html).items
    }

    suspend fun search(q: String, page: Int): List<ThreadItem> = timed("搜索") {
        val html = net.get(ApiUris.search(q, page), foreground = true)
        ThreadListParser.parse(html).items
    }

    suspend fun myThreads(uid: Long, page: Int): List<ThreadItem> = timed("我的主题") {
        val html = net.get(ApiUris.myThreads(uid, page), foreground = true)
        ThreadListParser.parse(html).items
    }

    suspend fun myReplies(uid: Long, page: Int): List<ThreadItem> = timed("我的回复") {
        val html = net.get(ApiUris.myReplies(uid, page), foreground = true)
        ThreadListParser.parse(html).items
    }

    // ---------------- 帖子 ----------------

    /** 原始 HTML：离线保存、自动解锁判定、二次解析都要它。 */
    suspend fun threadHtml(tid: Long, page: Int): String = timed("帖子HTML", log = false) {
        net.get(ApiUris.viewThread(tid, page), foreground = true)
    }

    suspend fun threadDetail(tid: Long, page: Int): ThreadDetail = timed("帖子 $tid") {
        val html = net.get(ApiUris.viewThread(tid, page), foreground = true)
        ThreadDetailParser.parse(html, page)
    }

    suspend fun threadDetailPc(tid: Long, page: Int): ThreadDetail {
        val html = net.get(ApiUris.viewThreadPc(tid, page), foreground = true)
        return ThreadDetailParser.parse(html, page)
    }

    suspend fun attachments(tid: Long, page: Int = 1): List<Attachment> =
        ThreadExtrasParser.attachments(threadHtml(tid, page))

    suspend fun likeUsers(tid: Long, pid: Long): List<LikeUser> = try {
        val html = net.get(ApiUris.recommendUsers(tid, pid), foreground = true)
        ThreadExtrasParser.likeUsers(html)
    } catch (t: Throwable) {
        emptyList()
    }

    /** 编辑页（同时用于「读取原帖内容」与「整张表单原样回提」两种用途）。 */
    suspend fun editPageHtml(fid: Long, tid: Long, pid: Long): String =
        net.get(ApiUris.editPost(fid, tid, pid), foreground = true)

    suspend fun deletePost(tid: Long, fid: Long, pid: Long, formhash: String): SubmitResult =
        withContext(Dispatchers.IO) {
            val url = "${ApiUris.SITE}/forum.php?mod=post&action=delpost&fid=$fid&tid=$tid&pid=$pid&mobile=2"
            try {
                val html = net.postForm(
                    url,
                    mapOf("formhash" to formhash, "tid" to tid.toString(), "pid" to pid.toString(), "delete" to "1"),
                    foreground = true,
                )
                val result = EditorParser.parseSubmit(html, url)
                if (result.ok) LogCenter.ok(LogTag.RUN, "删除回复成功", "pid=$pid")
                else LogCenter.fail(LogTag.RUN, "删除回复失败", result.error)
                result
            } catch (t: Throwable) {
                SubmitResult(ok = false, error = t.message ?: "删除失败")
            }
        }

    suspend fun reportPost(tid: Long, pid: Long, formhash: String, reason: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val url = "${ApiUris.SITE}/forum.php?mod=misc&action=report&tid=$tid&pid=$pid&mobile=2"
                val html = net.postForm(
                    url,
                    mapOf("formhash" to formhash, "reason" to reason, "reportsubmit" to "yes"),
                    foreground = true,
                )
                html.contains("成功") || html.contains("举报")
            } catch (t: Throwable) {
                false
            }
        }

    // ---------------- 编辑器 / 提交 ----------------

    suspend fun fetchEditor(url: String): EditorMeta = withContext(Dispatchers.IO) {
        val html = net.get(url, foreground = true)
        EditorParser.parseEditor(html, url)
    }

    suspend fun submitThread(fid: Long, formhash: String, title: String, message: String): SubmitResult =
        withContext(Dispatchers.IO) {
            val url = "${ApiUris.SITE}/forum.php?mod=post&action=newthread&fid=$fid&topicsubmit=yes&mobile=2"
            val html = net.postForm(url, mapOf(
                "formhash" to formhash, "posttime" to "", "wysiwyg" to "0",
                "subject" to title, "message" to message, "usesig" to "1", "noticetrimstr" to "",
            ), foreground = true)
            EditorParser.parseSubmit(html, url)
        }

    /**
     * 回复。带上 repquote/noticeauthor 才能正确@楼层。
     * formula 与 Java 版一致：整张表单字段照抄，不猜。
     */
    suspend fun submitReply(
        tid: Long,
        formhash: String,
        message: String,
        fid: Long = 2,
        reppid: Long = 0,
        repquote: Long = 0,
        noticeAuthor: String = "",
        notifyAuthor: Boolean = false,
    ): SubmitResult = withContext(Dispatchers.IO) {
        val url = "${ApiUris.SITE}/forum.php?mod=post&action=reply&tid=$tid&fid=$fid&replysubmit=yes&mobile=2"
        val params = mutableMapOf(
            "formhash" to formhash, "posttime" to "", "wysiwyg" to "0",
            "message" to message, "usesig" to "1",
            "reppid" to reppid.toString(), "repquote" to repquote.toString(),
        )
        if (noticeAuthor.isNotEmpty()) {
            params["noticeauthor"] = noticeAuthor
            params["noticetrimstr"] = ""
            params["noticeauthormsg"] = ""
            if (notifyAuthor) params["notifyauthor"] = "1"
        }
        val html = net.postForm(url, params, foreground = true)
        val result = EditorParser.parseSubmit(html, url)
        if (result.ok) LogCenter.ok(LogTag.RUN, "回复成功", "tid=$tid")
        else LogCenter.fail(LogTag.RUN, "回复失败", result.error)
        result
    }

    suspend fun submitEdit(fid: Long, tid: Long, pid: Long, formhash: String, title: String, message: String): SubmitResult =
        withContext(Dispatchers.IO) {
            val url = "${ApiUris.SITE}/forum.php?mod=post&action=edit&fid=$fid&tid=$tid&pid=$pid&editsubmit=yes&mobile=2"
            val params = mutableMapOf<String, String>(
                "formhash" to formhash, "posttime" to "", "wysiwyg" to "0",
                "message" to message, "usesig" to "1",
            )
            if (title.isNotEmpty()) params["subject"] = title
            val html = net.postForm(url, params, foreground = true)
            EditorParser.parseSubmit(html, url)
        }

    /** 图片上传（发帖 / 回复共用）。返回可插入正文的附件 URL。 */
    suspend fun uploadImage(
        bytes: ByteArray,
        fileName: String,
        mime: String,
        fid: Long,
        uploadHash: String,
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val url = "${ApiUris.SITE}/forum.php?mod=misc&action=upload&fid=$fid" +
                (if (uploadHash.isNotEmpty()) "&hash=$uploadHash" else "") + "&mobile=2"
            val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("Filedata", fileName, bytes.toRequestBody(mime.toMediaType()))
                .addFormDataPart("filetype", mime)
                .addFormDataPart("hash", uploadHash)
            val raw = net.postMultipart(url, builder)
            // Discuz 返回 {"aid":"..","url":".."}；有的版本回 BBCode 或纯 URL
            val picked = Regex("\"url\"\\s*:\\s*\"([^\"]+)\"").find(raw)?.groupValues?.getOrNull(1)
                ?: Regex("(https?://[^\"'\\s]+?/forum\\.php\\?mod=attachment[^\"'\\s]*)").find(raw)?.groupValues?.getOrNull(1)
                ?: Regex("\\[attach\\](\\d+)\\[/attach\\]").find(raw)?.groupValues?.getOrNull(1)?.let { "aid:$it" }
            if (picked == null) {
                LogCenter.fail(LogTag.RUN, "图片上传失败", raw.take(160))
                false to raw.take(160)
            } else {
                LogCenter.ok(LogTag.RUN, "图片上传成功", picked)
                true to picked
            }
        } catch (t: Throwable) {
            LogCenter.fail(LogTag.RUN, "图片上传异常", t.message.orEmpty())
            false to (t.message ?: "上传失败")
        }
    }

    // ---------------- 交互 ----------------

    suspend fun like(tid: Long, pid: Long, formhash: String, add: Boolean = true): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val html = net.postForm(
                    ApiUris.likeAction(tid, pid, formhash, add),
                    mapOf("formhash" to formhash, "tid" to tid.toString(), "pid" to pid.toString(), "add" to if (add) "1" else "0"),
                    ajax = true, foreground = true,
                )
                html.contains("成功") && !html.contains("失败")
            } catch (t: Throwable) {
                false
            }
        }

    suspend fun favorite(tid: Long, formhash: String, add: Boolean = true): Boolean =
        withContext(Dispatchers.IO) {
            try {
                net.postForm(
                    ApiUris.favoriteAction(tid, formhash, add),
                    mapOf("formhash" to formhash, "favoritesubmit" to "yes", "delete" to if (add) "0" else "1"),
                    foreground = true,
                )
                true
            } catch (t: Throwable) {
                false
            }
        }

    // ---------------- 用户 / 社交 ----------------

    suspend fun profile(uid: Long): UserProfile = timed("用户页") {
        val html = net.get(ApiUris.space(uid), foreground = true)
        SocialParser.enrichProfile(UserPagesParser.parseProfile(html), html)
    }

    /**
     * 用**指定会话**识别「我」是谁：请求个人页，由服务端返回真实 uid / 用户名。
     * 登录导入、检测会话都走这里，绝不从 Cookie 名字猜身份。
     */
    /**
     * 用**指定会话**识别「我」是谁（登录导入 / 检测会话都走这里）。
     *
     * 三级兜底，任何一级拿到身份就返回：
     *   1. 移动版个人页 `home.php?mod=space&do=profile&mobile=2`（首选，信息最全）
     *   2. PC 版个人页（移动版模板被换 / 被防护拦时）
     *   3. 索引页里的内联登录态 `discuz_uid = '12345'`（只保证 uid，名字留给用户填）
     *
     * 注意：**不猜名字**。拿到的用户名要过 [UserPagesParser.sanitizeUsername]，
     * 页面标题 / 站点名这种「所有账号都一样」的字符串一律丢弃。
     */
    suspend fun identityOf(client: IsolatedClient): UserProfile = withContext(Dispatchers.IO) {
        var profile = UserProfile()
        repeat(2) { attempt ->
            val html = runCatching { client.get(ApiUris.space(0, doWhat = "profile")) }.getOrDefault("")
            if (html.isNotBlank()) {
                profile = SocialParser.enrichProfile(UserPagesParser.parseProfile(html), html)
                if (profile.uid > 0 || profile.username.isNotBlank()) return@withContext profile
                // 会话是死的（游客页）→ 立刻换 PC 页试试
                if (Parsing.looksLikeLoginPage(html)) return@withContext identityFromFallback(client, html)
            }
            if (attempt == 0) delay(900)
        }
        identityFromFallback(client, "")
    }

    /** 个人页拿不到身份时的兜底：PC 个人页 → 内联 discuz_uid。 */
    private suspend fun identityFromFallback(client: IsolatedClient, mobileHtml: String): UserProfile {
        val pcHtml = runCatching {
            client.get(ApiUris.SITE + "/home.php?mod=space&do=profile")
        }.getOrDefault("")
        if (pcHtml.isNotBlank()) {
            val pc = SocialParser.enrichProfile(UserPagesParser.parseProfile(pcHtml), pcHtml)
            if (pc.uid > 0 || pc.username.isNotBlank()) return pc
        }
        // 最后一招：任何登录页都能在脚本里挖到 discuz_uid
        val anyHtml = mobileHtml.ifBlank {
            runCatching { client.get(ApiUris.SITE + "/forum.php?mobile=2") }.getOrDefault("")
        }
        val uid = Parsing.findUidInScripts(Parsing.doc(anyHtml))
        return if (uid > 0) UserProfile(uid = uid, avatarUrl = UserPagesParser.avatarOf(uid)) else UserProfile()
    }

    suspend fun myProfile(): UserProfile = withContext(Dispatchers.IO) {
        val html = net.get(ApiUris.space(0, doWhat = "profile"), foreground = true)
        SocialParser.enrichProfile(UserPagesParser.parseProfile(html), html)
    }

    suspend fun friends(uid: Long): List<SocialParser.FriendEntry> = withContext(Dispatchers.IO) {
        val html = net.get(ApiUris.friends(uid), foreground = true)
        SocialParser.parseFriends(html)
    }

    suspend fun followingUids(uid: Long): List<Long> = withContext(Dispatchers.IO) {
        val html = net.get(ApiUris.following(uid), foreground = true)
        SocialParser.parseFollowingUids(html)
    }

    suspend fun follow(uid: Long, formhash: String, follow: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "${ApiUris.SITE}/home.php?mod=spacecp&ac=follow&op=" + (if (follow) "add" else "delete") + "&mobile=2"
            val html = net.postForm(
                url,
                mapOf("formhash" to formhash, "followuid" to uid.toString(), "hash" to ""),
                foreground = true,
            )
            html.contains("成功") || html.contains("关注")
        } catch (t: Throwable) {
            false
        }
    }

    suspend fun credits(uid: Long): List<CreditItem> = withContext(Dispatchers.IO) {
        val html = net.get(ApiUris.credits(uid), foreground = true)
        SocialParser.parseCredits(html)
    }

    // ---------------- 消息 ----------------

    suspend fun badgeCounts(): BadgeCounts = withContext(Dispatchers.IO) {
        val noticeHtml = runCatching { net.get(ApiUris.notice(), foreground = false) }.getOrDefault("")
        val pmHtml = runCatching { net.get(ApiUris.noticePm(), foreground = false) }.getOrDefault("")
        SocialParser.parseBadgeCounts(noticeHtml, pmHtml)
    }

    suspend fun notices(): List<Notice> = withContext(Dispatchers.IO) {
        val html = net.get(ApiUris.notice(), foreground = true)
        val list = SocialParser.parseNoticeList(html)
        if (list.isNotEmpty()) list else UserPagesParser.parseNotices(html)
    }

    suspend fun pms(): List<PmSession> = withContext(Dispatchers.IO) {
        val html = net.get(ApiUris.noticePm(), foreground = true)
        SocialParser.parsePmSessions(html)
    }

    suspend fun chatMessages(touid: Long, myName: String): List<ChatMessage> = withContext(Dispatchers.IO) {
        val html = net.get(ApiUris.pmView(touid), foreground = true)
        SocialParser.parseChatMessages(html, myName)
    }

    suspend fun sendPm(touid: Long, message: String, formhash: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "${ApiUris.SITE}/home.php?mod=spacecp&ac=pm&op=send&touid=$touid&pmid=0&mobile=2"
            val html = net.postForm(
                url,
                mapOf(
                    "formhash" to formhash, "touid" to touid.toString(), "pmid" to "0",
                    "message" to message, "pmsubmit" to "yes",
                ),
                foreground = true,
            )
            html.contains("成功") || html.contains("短消息")
        } catch (t: Throwable) {
            false
        }
    }

    suspend fun markNoticesRead(): Boolean = withContext(Dispatchers.IO) {
        try {
            net.get(ApiUris.noticeMarkRead(), foreground = true)
            true
        } catch (t: Throwable) {
            false
        }
    }

    // ---------------- 签到 ----------------

    suspend fun signState(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val html = net.get(ApiUris.signPage())
            val state = SignParser.parsePage(html)
            if (state.alreadySigned) Pair(true, "今日已签到")
            else Pair(false, SignParser.formhashOf(html))
        } catch (e: Exception) {
            Pair(false, "")
        }
    }

    suspend fun doSignIn(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val html = net.get(ApiUris.signPage())
            val formhash = SignParser.formhashOf(html)
            if (formhash.isEmpty()) {
                val state = SignParser.parsePage(html)
                return@withContext if (state.alreadySigned) Pair(true, "今日已签到")
                else Pair(false, "未找到签到表单")
            }
            val res = net.get(Site.baseUrl + "/" + ApiUris.signActionPath(formhash), ajax = true)
            val state = SignParser.parseAjaxResult(res)
            Pair(state.ok || state.alreadySigned, state.message)
        } catch (e: Exception) {
            Pair(false, "网络错误: ${e.message}")
        }
    }

    // ---------------- 工具 ----------------

    private suspend fun <T> timed(name: String, log: Boolean = true, block: suspend () -> T): T {
        val started = System.currentTimeMillis()
        val result = withContext(Dispatchers.IO) { block() }
        val cost = System.currentTimeMillis() - started
        if (log && cost > 400) {
            PerfLog.record(PerfLog.Span(name = name, netMs = cost, extra = "含解析"))
        }
        return result
    }

    private fun String.toMediaType(): okhttp3.MediaType =
        this.toMediaTypeOrNull() ?: "image/jpeg".toMediaTypeOrNull()!!
}
