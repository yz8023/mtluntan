package io.mtluntan.app.data.parser

import io.mtluntan.app.domain.model.Post
import io.mtluntan.app.domain.model.ThreadDetail
import io.mtluntan.app.util.TextUtil
import io.mtluntan.app.util.UrlUtil
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Parses the thread detail page. Primary path is the mobile Comiis template
 * (served with &mobile=2, `div.comiis_postli` containers); a PC `table[id^=pid]`
 * fallback handles desktop-serving for other views.
 */
object ThreadDetailParser : BaseParser() {

    fun parse(html: String, page: Int = 1): ThreadDetail {
        val doc = Parsing.doc(html)
        val check = Parsing.checkPageError(doc, html)
        if (check.isError) {
            return ThreadDetail(
                loginRequired = check.loginRequired,
                errorMessage = check.message,
                currentPage = page,
            )
        }

        val tid = Parsing.findTid(doc).takeIf { it > 0 } ?: UrlUtil.tid(doc.select("input[name=tid]").firstOrNull()?.attr("value"))
        val formhash = Parsing.valueWithJsFallback(html, "formhash")
        val pagination = Parsing.extractPagination(doc)
        val totalPages = if (pagination.total <= 0) extractMobileTotalPages(doc) else pagination.total

        // Mobile Comiis first (matches the live site), PC table as fallback.
        val postlis = doc.select("div.comiis_postli")
        val tables = if (postlis.isEmpty())
            doc.select("table[id^=\"pid\"]").toList() else emptyList()

        if (postlis.isEmpty() && tables.isEmpty()) {
            // Fallback: any element that actually contains a message body.
            val fallback = doc.select("div.comiis_message, td.t_f, div.message, div.postbody, article").firstOrNull()
            if (fallback != null) {
                val tid2 = tid
                val title = Parsing.text(doc.select("div.comiis_viewtit h2 div.km_tits, #thread_subject, h1.ph").firstOrNull())
                val boardName = Parsing.text(doc.select("div.comiis_head a.kmtit[href*=forum-], .comiis_vrx1 a[href*=forum-], a[href*=forumdisplay]").firstOrNull())
                val fb = fallback.clone()
                fb.select("script, style, div.comiis_favshare, div.comiis_postli_time, a.followmod").remove()
                val fbHtml = fb.html().trim()
                return ThreadDetail(
                    tid = tid2,
                    title = title,
                    forumName = boardName,
                    mainPost = Post(pid = 0, contentHtml = fbHtml, contentBbc = BbcToHtml.htmlToBbc(fb), isMainPost = true),
                    posts = emptyList(),
                    currentPage = pagination.current,
                    totalPages = totalPages,
                    formhash = formhash,
                    errorMessage = if (fbHtml.isEmpty()) "未能解析帖子内容，页面结构可能已变更" else "",
                )
            }
            return ThreadDetail(
                tid = tid,
                title = Parsing.text(doc.select("div.comiis_viewtit h2 div.km_tits, #thread_subject, h1.ph").firstOrNull()),
                currentPage = page,
                errorMessage = "未能解析帖子内容，页面结构可能已变更",
            )
        }

        if (postlis.isNotEmpty()) {
            return parseComiis(postlis, tid, formhash, pagination, totalPages, doc)
        }
        return parsePcTables(tables, tid, formhash, pagination, totalPages, doc)
    }

    // ---------- mobile Comiis template ----------

    private fun parseComiis(
        postlis: List<Element>,
        tid: Long,
        formhash: String,
        pagination: Parsing.Pagination,
        totalPages: Int,
        doc: Document,
    ): ThreadDetail {
        val title = Parsing.text(doc.select("div.comiis_viewtit h2 div.km_tits, #thread_subject, h1.ph").firstOrNull())
        val boardName = Parsing.text(doc.select("div.comiis_head a.kmtit[href*=forum-], a[href*=forumdisplay]").firstOrNull())

        val op = postlis.first()
        val opPid = op.id().takeIf { it.startsWith("pid") }?.substring(3)?.toLongOrNull() ?: 0L
        val opTop = op.select("div.comiis_postli_top").firstOrNull()
        val opMsg = op.select("div.comiis_message").firstOrNull()

        val mainPost = Post(
            pid = opPid,
            uid = uidFrom(opTop?.select("a.top_user.f_b")?.firstOrNull()?.attr("href")),
            authorName = Parsing.text(opTop?.select("a.top_user.f_b")?.firstOrNull()),
            avatarUrl = opTop?.select("a.postli_top_tximg img.top_tximg")?.firstOrNull()?.attr("src")?.let { abs(it) } ?: "",
            postTime = Parsing.text(opTop?.select("div.comiis_postli_time span.kmtime")?.firstOrNull()),
            floor = 0,
            isMainPost = true,
            contentHtml = opMsg?.let { normalizeCodeBlocks(it) } ?: "",
            contentBbc = opMsg?.let { BbcToHtml.htmlToBbc(it) } ?: "",
            images = opMsg?.let { BbcToHtml.collectImageUrls(it.html()) }?.distinct()?.take(50) ?: emptyList(),
            likes = opMsg?.select("em.comiis_recommend_num")?.firstOrNull()?.let { TextUtil.intOf(it.text()) } ?: 0,
            replyQuote = opMsg?.select("div.comiis_quote")?.firstOrNull()?.let { Parsing.cleanText(it) } ?: "",
            hideFlag = opMsg?.select("div.comiis_quote")?.isNotEmpty() == true,
        )

        val posts = postlis.drop(1).mapIndexedNotNull { idx, rp ->
            val rTop = rp.select("div.comiis_postli_top").firstOrNull() ?: return@mapIndexedNotNull null
            val rMsg = rp.select("div.comiis_message").firstOrNull() ?: return@mapIndexedNotNull null
            Post(
                pid = rp.id().takeIf { it.startsWith("pid") }?.substring(3)?.toLongOrNull() ?: 0L,
                uid = uidFrom(rTop.select("a.top_user.f_b").firstOrNull()?.attr("href")),
                authorName = Parsing.text(rTop.select("a.top_user.f_b").firstOrNull()),
                avatarUrl = rTop.select("a.postli_top_tximg img.top_tximg").firstOrNull()?.attr("src")?.let { abs(it) } ?: "",
                postTime = Parsing.text(rp.select("div.comiis_postli_times span.comiis_tm").firstOrNull()),
                floor = idx + 1,
                isMainPost = false,
                contentHtml = normalizeCodeBlocks(rMsg),
                contentBbc = BbcToHtml.htmlToBbc(rMsg),
                images = BbcToHtml.collectImageUrls(rMsg.html()).distinct().take(50),
                replyQuote = rMsg.select("div.comiis_quote, blockquote, .quote").firstOrNull()?.let { Parsing.cleanText(it) } ?: "",
            )
        }

        val liked = doc.select("a[href*=\"recommend\"][href*=\"do=add\"].recommend_ok, a.recommend_ok, [class*=\"recommend_ok\"]").isNotEmpty()
        val favorited = doc.select("#comiis_favorite_a span.comiis_favorite_a_num[style*=green], span[class*=fa_cgox]").isNotEmpty()

        return ThreadDetail(
            tid = tid,
            title = title,
            mainPost = mainPost,
            posts = posts,
            currentPage = pagination.current,
            totalPages = totalPages,
            formhash = formhash,
            likedByCurrent = liked,
            favorited = favorited,
            forumName = boardName,
        )
    }

    // ---------- PC table fallback ----------

    private fun parsePcTables(
        tables: List<Element>,
        tid: Long,
        formhash: String,
        pagination: Parsing.Pagination,
        totalPages: Int,
        doc: Document,
    ): ThreadDetail {
        val title = Parsing.text(doc.select("#thread_subject, h1.ph, .comiis_vrx h1").firstOrNull())
        val boardName = Parsing.text(doc.select(".comiis_vrx1 a[href*=forum-], .mtm a[href*=forum-], a[href*=forumdisplay]").firstOrNull())
        val liked = doc.select("a[href*=\"recommend\"][href*=\"do=add\"].recommend_ok, a.recommend_ok, [class*=\"recommend_ok\"]").isNotEmpty()
        val favorited = doc.select("a[href*=\"favorite\"][href*=\"handlekey=favorite\"][style*=green], span[class*=fa_cgox]").isNotEmpty()

        var mainPost: Post? = null
        val posts = mutableListOf<Post>()
        if (tables.isNotEmpty()) {
            mainPost = parsePcPost(tables[0], isOp = true, floor = 0)
            for (i in 1 until tables.size) {
                posts += parsePcPost(tables[i], isOp = false, floor = i)
            }
        }

        return ThreadDetail(
            tid = tid,
            title = title,
            mainPost = mainPost,
            posts = posts,
            currentPage = pagination.current,
            totalPages = totalPages,
            formhash = formhash,
            likedByCurrent = liked,
            favorited = favorited,
            forumName = boardName,
        )
    }

    private fun parsePcPost(table: Element, isOp: Boolean, floor: Int): Post {
        val pid = UrlUtil.pid(table.id())
        val pls = table.selectFirst("td.pls")
        var uid = 0L
        var username = ""
        if (pls != null) {
            val nameLink = pls.select(".pi .authi a").firstOrNull()
            if (nameLink != null) {
                username = Parsing.text(nameLink)
                uid = UrlUtil.uid(nameLink.attr("href"))
            }
        }
        val plc = table.selectFirst("td.plc")
        var postTime = ""
        var contentBbc = ""
        var html = ""
        if (plc != null) {
            val tmEl = plc.select("em[id^=\"authorposton\"]").firstOrNull()
            if (tmEl != null) {
                postTime = Parsing.text(tmEl).replace(Regex("^发表于\\s*"), "").trim()
            }
            val msgEl = plc.select("td.t_f[id^=\"postmessage_\"], div.t_f[id^=\"postmessage_\"]").firstOrNull()
            if (msgEl != null) {
                html = msgEl.outerHtml()
                contentBbc = BbcToHtml.htmlToBbc(msgEl)
            }
        }
        return Post(
            pid = pid,
            uid = uid,
            authorName = username,
            avatarUrl = if (uid > 0) avatarOf(uid) else "",
            postTime = postTime,
            floor = floor,
            isMainPost = isOp,
            contentBbc = contentBbc,
            contentHtml = html,
            images = BbcToHtml.collectImageUrls(html).distinct().take(50),
        )
    }

    // ---------- helpers ----------

    private fun uidFrom(href: String?): Long = UrlUtil.uid(href)

    private fun extractMobileTotalPages(doc: Document): Int {
        var max = 1
        doc.select("div.pg a[href*=-1.html]").forEach {
            Regex("thread-\\d+-(\\d+)-1\\.html").find(it.attr("href"))?.groupValues?.getOrNull(1)
                ?.toIntOrNull()?.let { p -> if (p > max) max = p }
        }
        if (max <= 1) {
            doc.select("div.pg a[href*=page=]").forEach {
                Regex("[&?]page=(\\d+)").find(it.attr("href"))?.groupValues?.getOrNull(1)
                    ?.toIntOrNull()?.let { p -> if (p > max) max = p }
            }
        }
        return max
    }

    private fun normalizeCodeBlocks(msg: Element): String {
        val clone = msg.clone()
        return clone.html().trim()
    }
}