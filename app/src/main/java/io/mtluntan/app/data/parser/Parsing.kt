package io.mtluntan.app.data.parser

import io.mtluntan.app.util.TextUtil
import io.mtluntan.app.util.UrlUtil
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements

/** Shared helpers for parsing Discuz/Comiis HTML. */
object Parsing {

    fun doc(html: String): Document = Jsoup.parse(html)

    fun text(el: Element?): String = el?.text().let { TextUtil.sanitize(it) }

    fun attr(el: Element?, key: String): String = el?.attr(key).orEmpty().trim()

    /** Removes script/style from an element and returns sanitized inner text. */
    fun cleanText(el: Element?): String {
        if (el == null) return ""
        val clone = el.clone()
        clone.select("script, style").forEach { it.remove() }
        return TextUtil.sanitize(clone.text())
    }

    /** Element text excluding the given tag names (for icon-covered labels). */
    fun textExcluding(el: Element?, excludeTags: Set<String> = setOf()): String {
        if (el == null) return ""
        val clone = el.clone()
        for (tag in excludeTags) clone.select(tag).forEach { it.remove() }
        return TextUtil.sanitize(clone.text())
    }

    // ---- Discuz page error detection ----

    data class PageCheck(val isError: Boolean, val loginRequired: Boolean, val message: String)

    fun checkPageError(doc: Document, raw: String): PageCheck {
        // 1. comiis password/error box
        val pw = doc.select("div.comiis_password_top > p.f_c").firstOrNull()
        if (pw != null) {
            val m = cleanText(pw)
            if (m.isNotEmpty()) return PageCheck(true, false, m)
        }
        // 2. standard messagetext
        val mt = doc.getElementById("messagetext")
        if (mt != null) {
            val m = cleanText(mt)
            if (m.isNotEmpty()) return PageCheck(true, false, m)
        }
        // 3. mobile system error
        val sys = doc.select("#message li").firstOrNull()
        if (sys != null) {
            val m = cleanText(sys)
            if (m.isNotEmpty()) return PageCheck(true, false, m)
        }
        // 4. login page marker
        val login = doc.select("input[name=\"loginsubmit\"]").firstOrNull()
        if (login != null) return PageCheck(true, true, "需要先登录")
        return PageCheck(false, false, "")
    }

    // ---- pagination ----

    data class Pagination(val current: Int, val total: Int)

    fun extractPagination(doc: Document): Pagination {
        // comiis mobile dropdown
        val select = doc.select(".comiis_page select#dumppage").firstOrNull()
        if (select != null) {
            val opts = select.select("option")
            if (opts.isNotEmpty()) {
                val total = opts.last().attr("value").toIntOrNull() ?: 1
                var current = 1
                for (o in opts) {
                    if (o.hasAttr("selected")) {
                        current = o.attr("value").toIntOrNull() ?: 1
                        break
                    }
                }
                return Pagination(current, total)
            }
        }
        // desktop .pg
        val pg = doc.select(".pg").firstOrNull()
        if (pg != null) {
            val strong = pg.select("strong").firstOrNull()
            val current = strong?.text()?.trim()?.toIntOrNull() ?: 1
            var total = 1
            val span = pg.select("span[title]").firstOrNull()
            if (span != null) {
                val m = Regex("共\\s*(\\d+)\\s*页").find(span.attr("title"))
                if (m != null) total = m.groupValues[1].toIntOrNull() ?: 1
            }
            if (total <= 1) {
                val label = pg.select("label").firstOrNull()
                val m = label?.let { Regex("/\\s*(\\d+)\\s*页").find(it.text()) }
                if (m != null) total = m.groupValues[1].toIntOrNull() ?: 1
            }
            return Pagination(current, total)
        }
        // links fallback
        var maxPage = 1
        for (a in doc.select("a[href*=page=], a[href*=thread-]")) {
            val href = a.attr("href")
            val p = Regex("[?&]page=(\\d+)").find(href)?.groupValues?.getOrNull(1)
                ?.toIntOrNull()
                ?: Regex("thread-\\d+-(\\d+)-\\d+\\.html").find(href)
                    ?.groupValues?.getOrNull(1)?.toIntOrNull()
            if (p != null && p > maxPage) maxPage = p
        }
        return Pagination(1, maxPage)
    }

    /** Extracts the first tid found in the page. */
    fun findTid(doc: Document): Long {
        for (a in doc.select("a[href*=tid=], a[href*=viewthread]")) {
            val t = UrlUtil.tid(a.attr("href"))
            if (t > 0) return t
        }
        return 0
    }

    fun findUid(doc: Document): Long {
        for (a in doc.select("a[href*=\"space&uid=\"]")) {
            val u = UrlUtil.uid(a.attr("href"))
            if (u > 0) return u
        }
        for (a in doc.select("a[href*=\"space-uid-\"]")) {
            return UrlUtil.uid(a.attr("href"))
        }
        return 0
    }

    fun findFid(doc: Document): Long {
        for (a in doc.select("a[href*=fid=], a[href*=forumdisplay]")) {
            val f = UrlUtil.fid(a.attr("href"))
            if (f > 0) return f
        }
        return 0
    }

    fun value(doc: Document, name: String): String {
        val byName = doc.selectFirst("input[name=\"$name\"]")
        if (byName != null) return byName.attr("value").orEmpty().trim()
        val byId = doc.selectFirst("input#$name, input[id=\"$name\"]")
        if (byId != null) return byId.attr("value").orEmpty().trim()
        return ""
    }

    fun firstWholePageRoot(doc: Document): Elements = doc.select("#postlist table[id^=\"pid\"]")
}

/** Parser base with URL resolution bound to the site. */
open class BaseParser(val baseUrl: String = "https://bbs.binmt.cc") {

    fun abs(href: String?): String = UrlUtil.absolute(baseUrl, href)

    fun avatarOf(uid: Long): String = "$baseUrl/uc_server/avatar.php?uid=$uid&size=middle"
}