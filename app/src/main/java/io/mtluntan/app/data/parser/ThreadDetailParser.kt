package io.mtluntan.app.data.parser

import io.mtluntan.app.domain.model.Post
import io.mtluntan.app.domain.model.ThreadDetail
import io.mtluntan.app.util.TextUtil
import io.mtluntan.app.util.UrlUtil
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Parses the thread detail page (PC template `table#pidXX.plhin` inside
 * `#postlist`, which the server serves for viewthread).
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
        val title = Parsing.text(doc.select("#thread_subject").firstOrNull())
            .ifEmpty { Parsing.text(doc.select("h1.ph, .comiis_vrx h1").firstOrNull()) }
        val pagination = Parsing.extractPagination(doc)
        val formhash = Parsing.value(doc, "formhash")

        // PC board info
        val boardName = Parsing.text(doc.select(".comiis_vrx1 a[href*=forum-], .mtm a[href*=forum-], a[href*=\"forum-2-1\"], a[href*=forumdisplay]").firstOrNull())

        // collect post tables
        var tables = doc.select("#postlist > table[id^=\"pid\"]").toList()
        if (tables.isEmpty()) tables = doc.select("#postlist .comiis_vrx > table[id^=\"pid\"]").toList()
        if (tables.isEmpty()) tables = doc.select("#postlist table[id^=\"pid\"]").toList()
        if (tables.isEmpty()) tables = doc.select("table[id^=\"pid\"]").toList()

        // recommend (like) state
        val liked = doc.select("a[href*=\"recommend\"][href*=\"do=add\"].recommend_ok, a.recommend_ok, [class*=\"recommend_ok\"]").isNotEmpty()
        val favorited = doc.select("a[href*=\"favorite\"][href*=\"handlekey=favorite\"][style*=green], span[class*=fa_cgox]").isNotEmpty()

        var mainPost: Post? = null
        val posts = mutableListOf<Post>()
        if (page <= 1) {
            if (tables.isNotEmpty()) {
                mainPost = parsePost(tables[0], isOp = true, floor = 0)
                for (i in 1 until tables.size) {
                    posts += parsePost(tables[i], isOp = false, floor = i)
                }
            }
        } else {
            tables.forEachIndexed { i, t -> posts += parsePost(t, isOp = false, floor = i + 1) }
        }

        val detail = ThreadDetail(
            tid = tid,
            title = title,
            mainPost = mainPost,
            posts = posts,
            currentPage = pagination.current,
            totalPages = pagination.total,
            formhash = formhash,
            likedByCurrent = liked,
            favorited = favorited,
            forumName = boardName,
        )
        if (tables.isEmpty() && tid == 0L && page <= 1) {
            return detail.copy(errorMessage = "未能解析帖子内容，页面结构可能已变更")
        }
        return detail
    }

    private fun parsePost(table: Element, isOp: Boolean, floor: Int): Post {
        val pid = UrlUtil.pid(table.id())
        // author column
        val pls = table.selectFirst("td.pls")
        var uid = 0L
        var username = ""
        var userTitle = ""
        if (pls != null) {
            val nameLink = pls.select(".pi .authi a").firstOrNull()
            if (nameLink != null) {
                username = Parsing.text(nameLink)
                uid = UrlUtil.uid(nameLink.attr("href"))
            }
            userTitle = Parsing.text(pls.select("p em a").firstOrNull())
        }

        // content column
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
                // keep an HTML copy for rendering
                html = msgEl.outerHtml()
                contentBbc = BbcToHtml.htmlToBbc(msgEl)
            }
        }

        val images = BbcToHtml.collectImageUrls(html)

        return Post(
            pid = pid,
            uid = uid,
            authorName = username,
            authorTitle = userTitle,
            avatarUrl = if (uid > 0) avatarOf(uid) else "",
            postTime = postTime,
            floor = floor,
            isMainPost = isOp,
            contentBbc = contentBbc,
            contentHtml = html,
            images = images.distinct().take(50),
        )
    }
}