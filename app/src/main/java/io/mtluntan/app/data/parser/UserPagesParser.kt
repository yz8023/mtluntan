package io.mtluntan.app.data.parser

import io.mtluntan.app.domain.model.Notice
import io.mtluntan.app.domain.model.UserProfile
import io.mtluntan.app.util.TextUtil
import io.mtluntan.app.util.UrlUtil

/** Parses profile/space, my-posts, notifications and PM pages. */
object UserPagesParser : BaseParser() {

    fun parseProfile(html: String): UserProfile {
        val doc = Parsing.doc(html)
        val check = Parsing.checkPageError(doc, html)
        if (check.isError) return UserProfile()

        val uid = Parsing.findUid(doc)
        val username = Parsing.text(doc.select(".comiis_fssjname, .mt_username, h1.ph a, .xl3 a").firstOrNull())
            .ifEmpty { Parsing.text(doc.select("title").firstOrNull()) }
        val groupName = Parsing.text(doc.select("p.mt_u_1 a, .comiis_me_gr, dt a[href*=usergroup]").firstOrNull())
        val registerTime = Parsing.text(doc.select("li:contains(注册时间) em, li:contains(注册时间) span").firstOrNull())
        val lastVisit = Parsing.text(doc.select("li:contains(最后活跃) em, li:contains(最后访问) em, li:contains(最后访问) span").firstOrNull())
        val signature = Parsing.text(doc.select("p.sign, .sx2, .comiis_qm textarea").firstOrNull())

        val stat = StatExtractor.fromListItems(doc)

        return UserProfile(
            uid = uid,
            username = username,
            avatarUrl = if (uid > 0) avatarOf(uid) else "",
            groupName = groupName,
            registerTime = registerTime,
            lastVisit = lastVisit,
            signature = signature,
            posts = stat.posts,
            threads = stat.threads,
            credits = stat.credits,
            goldCoin = stat.goldCoin,
            reputation = stat.reputation,
        )
    }

    private object StatExtractor {
        fun fromListItems(doc: org.jsoup.nodes.Document): Stats {
            var posts = 0; var threads = 0; var credits = 0; var goldCoin = 0; var reputation = 0
            for (li in doc.select("li, p, dd, div")) {
                val t = li.text()
                when {
                    t.contains("帖子") -> posts = TextUtil.intOf(t)
                    t.contains("主题") -> threads = TextUtil.intOf(t)
                    t.contains("积分") && !t.contains("积分余额") -> credits = TextUtil.intOf(t)
                    t.contains("金币") -> goldCoin = TextUtil.intOf(t)
                    t.contains("威望") -> reputation = TextUtil.intOf(t)
                }
            }
            return Stats(posts, threads, credits, goldCoin, reputation)
        }
    }

    private data class Stats(val posts: Int, val threads: Int, val credits: Int, val goldCoin: Int, val reputation: Int)

    fun parseNotices(html: String): List<Notice> {
        val doc = Parsing.doc(html)
        // mobile: dl.list.cl items with links to redirect
        val items = documentNoticeItems(doc)
        if (items.isNotEmpty()) return items
        // desktop: ul.nts li
        val out = mutableListOf<Notice>()
        for (li in doc.select("ul.nts li")) {
            val a = li.selectFirst("a[href]") ?: continue
            val url = UrlUtil.absolute(baseUrl, a.attr("href"))
            val text = Parsing.cleanText(li)
            val m = Regex("^(.+)\\s+(来自:\\s*)?([^\\s]+)").find(text)
            out += Notice(type = 0, body = text, fromAuthor = "", url = url, time = "")
        }
        return out
    }

    private fun documentNoticeItems(doc: org.jsoup.nodes.Document): List<Notice> {
        val out = mutableListOf<Notice>()
        for (a in doc.select("a[href*=\"redirect&pid=\"], a[href*=\"notice&do=url\"]")) {
            val item = a.parent()
            val text = Parsing.cleanText(item)
            if (text.isBlank()) continue
            val time = Parsing.text(item?.select(".xg1, time, .mtn")?.firstOrNull())
            out += Notice(type = 0, body = text, fromAuthor = "", url = abs(a.attr("href")), time = time)
        }
        return out
    }

    fun parsePmList(html: String): List<Notice> {
        val doc = Parsing.doc(html)
        val out = mutableListOf<Notice>()
        for (li in doc.select("li.cl")) {
            val a = li.selectFirst("a[href*=\"pm&subop=view\"]") ?: continue
            val name = Parsing.text(li.select(".msg_avt img").firstOrNull())
            val body = Parsing.cleanText(li)
            out += Notice(type = 1, body = body.ifBlank { Parsing.text(a) }, fromAuthor = name, url = abs(a.attr("href")))
        }
        if (out.isEmpty()) {
            for (tr in doc.select("tr.cl")) {
                val name = Parsing.text(tr.select("td:first-child a").firstOrNull())
                val a = tr.selectFirst("a[href*=\"pm&subop=view\"]") ?: continue
                out += Notice(type = 1, body = Parsing.cleanText(a), fromAuthor = name, url = abs(a.attr("href")))
            }
        }
        return out
    }
}

/**
 * Small helper to pull a uid from arbitrary space links.
 */
fun findUidFromDoc(doc: org.jsoup.nodes.Document): Long = Parsing.findUid(doc)