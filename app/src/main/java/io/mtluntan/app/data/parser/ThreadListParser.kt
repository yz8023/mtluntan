package io.mtluntan.app.data.parser

import io.mtluntan.app.domain.model.ThreadItem
import io.mtluntan.app.util.TextUtil
import io.mtluntan.app.util.UrlUtil
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Parses thread lists (guide, forumdisplay, search results, my posts) across
 * the multiple DOM layouts the target site can serve:
 *
 *  1. mobile comiis "card" layout   — li.forumlist_li.comiis_znalist
 *  2. mobile comiis "table" layout  — tbody[id^=normalthread_] div.comiis_postlist
 *  3. desktop discuz table layout   — tbody[id^=normalthread_] (no comiis)
 *  4. space / user-post layout
 */
object ThreadListParser : BaseParser() {

    data class Result(val items: List<ThreadItem>, val parser: String, val matched: Boolean)

    fun parse(html: String): Result {
        val doc = Parsing.doc(html)
        val card = parseComiisCard(doc)
        if (card.isNotEmpty()) return Result(card, "comiis_card", true)
        val comiisTable = parseComiisTable(doc)
        if (comiisTable.isNotEmpty()) return Result(comiisTable, "comiis_table", true)
        val discuzTable = parseDiscuzTable(doc)
        if (discuzTable.isNotEmpty()) return Result(discuzTable, "discuz_table", true)
        val space = parseSpace(doc)
        if (space.isNotEmpty()) return Result(space, "space_thread", true)
        return Result(emptyList(), "NoParser", false)
    }

    // ---------- mobile comiis card ----------

    private fun parseComiisCard(doc: Document): List<ThreadItem> {
        // Reference-tested selector: li.forumlist_li (broad). Real mobile HTML
        // does not reliably carry the .comiis_znalist subclass.
        var items = doc.select("li.forumlist_li")
        if (items.isEmpty()) {
            // Fallback: any li containing a thread link (thread- / viewthread / tid=)
            val parents = mutableListOf<Element>()
            doc.select("a[href*=\"thread-\"], a[href*=\"viewthread\"], a[href*=\"tid=\"]").forEach { link ->
                val p = link.closest("li")
                if (p != null && parents.none { it === p }) parents.add(p)
            }
            items = org.jsoup.select.Elements(parents)
        }
        return items.mapNotNull { li ->
            val titleEl = li.select(".mmlist_li_box h2 a[href*=\"thread-\"], .mmlist_li_box h2 a[href*=\"viewthread\"], .mmlist_li_box h2 a[href*=\"tid=\"]").firstOrNull()
                ?: li.select("a[href*=\"thread-\"], a[href*=\"viewthread\"], a[href*=\"tid=\"]").firstOrNull() ?: return@mapNotNull null
            val href = titleEl.attr("href")
            val tid = UrlUtil.tid(href)
            if (tid == 0L) return@mapNotNull null
            val boardEl = li.select(".comiis_xznalist_bk a[href*=\"forum-\"]").firstOrNull()
                ?: li.select("a[href*=\"forum-\"], a[href*=\"forumdisplay\"]").firstOrNull()
            val avatarHref = li.select(".forumlist_li_top .top_tximg, a[href*=\"space&uid=\"]").firstOrNull()?.attr("href")
            val authorEl = li.select(".forumlist_li_top .top_user, .top_user, a[href*=\"space-username-\"]").firstOrNull()
            val statLis = li.select(".comiis_xznalist_bottom li .comiis_tm")
            var likes = 0; var replies = 0; var views = 0
            if (statLis.size > 0) likes = TextUtil.intOf(statLis[0].text())
            if (statLis.size > 1) replies = TextUtil.intOf(statLis[1].text())
            if (statLis.size > 2) views = TextUtil.intOf(statLis[2].text())
            val imgs = li.select(".comiis_pyqlist_imgs img, .comiis_pyqlist_imgs li img, .mmlist_li_box img")
                .map { firstNonEmptyAttr(it, "comiis_loadimages", "data-original", "data-src", "data-file", "file", "src") }
                .filter { it.isNotEmpty() }
                .map { abs(it) }
                .take(9)
            val summaryEl = li.select(".list_body .f_b").firstOrNull()
            val hidden = summaryEl?.text()?.contains("本内容被作者隐藏") == true
            val timeEl = li.select(".forumlist_li_time .f_d, .forumlist_li_time").firstOrNull()
            ThreadItem(
                threadId = tid,
                title = titleEl.ownText().trim().ifEmpty { Parsing.text(titleEl) },
                summary = summaryEl?.let { Parsing.cleanText(it) } ?: "",
                authorUid = UrlUtil.uid(avatarHref),
                authorName = Parsing.text(authorEl),
                avatarUrl = UrlUtil.uid(avatarHref).let { if (it > 0) avatarOf(it) else "" },
                boardId = UrlUtil.fid(boardEl?.attr("href")),
                boardName = Parsing.text(boardEl)
                    .replace(Regex("^[\\s\\u00a0]*来自?"), "").trim(),
                likes = likes,
                replies = replies,
                views = views,
                postTime = Parsing.text(timeEl),
                images = imgs,
                hasHiddenContent = hidden,
            )
        }
    }

    private fun firstNonEmptyAttr(el: Element, vararg names: String): String {
        for (n in names) {
            val v = el.attr(n)
            if (v.isNotEmpty() && !v.endsWith("none.gif") && !v.endsWith("blank.gif") && !v.endsWith("common_empty.gif")) return v
        }
        return ""
    }

    // ---------- comiis table (PC + mobile hybrid) ----------

    private fun parseComiisTable(doc: Document): List<ThreadItem> {
        val rows = doc.select("tbody[id^=\"normalthread_\"]")
        if (rows.isEmpty()) return emptyList()
        if (rows.first().select("td div.comiis_postlist").isEmpty()) return emptyList()
        return rows.mapNotNull { row ->
            val pl = row.select("td div.comiis_postlist").firstOrNull() ?: return@mapNotNull null
            val titleEl = pl.select(".comiis_common a[href*=\"thread-\"]").firstOrNull()
                ?: pl.select("a[href*=\"thread-\"]").firstOrNull() ?: return@mapNotNull null
            val href = titleEl.attr("href")
            val tid = UrlUtil.tid(href)
            val p = pl.select("p").firstOrNull()
            val authorEl = p?.select(".km_user a")?.firstOrNull()
            var uid = UrlUtil.uid(pl.select(".comiis_listtx a").firstOrNull()?.attr("href"))
            if (uid == 0L && authorEl != null) uid = UrlUtil.uid(authorEl.attr("href"))
            ThreadItem(
                threadId = tid,
                title = Parsing.text(titleEl),
                authorUid = uid,
                authorName = authorEl?.let { Parsing.text(it) } ?: "",
                avatarUrl = if (uid > 0) avatarOf(uid) else "",
                replies = p?.select(".km_reply a")?.firstOrNull()?.let { TextUtil.intOf(it.text()) } ?: 0,
                views = p?.select(".km_view")?.firstOrNull()?.let { TextUtil.intOf(it.text()) } ?: 0,
                isSticky = row.id().startsWith("stickthread_"),
                isDigest = pl.select("img[alt=精华]").isNotEmpty() || pl.select(".comiis_jing").isNotEmpty(),
                images = pl.select("img[src*=forum.php?mod=image], img[src*=attachment]")
                    .map { abs(it.attr("src")) }.filter { it.isNotEmpty() }.take(6),
            )
        }
    }

    // ---------- desktop discuz table ----------

    private fun parseDiscuzTable(doc: Document): List<ThreadItem> {
        val rows = doc.select("tbody[id^=\"normalthread_\"]")
        if (rows.isEmpty()) return emptyList()
        return rows.mapNotNull { row ->
            val titleEl = row.select("th a.xst, td a").firstOrNull() ?: return@mapNotNull null
            val href = titleEl.attr("href")
            val tid = UrlUtil.tid(href)
            if (tid == 0L) return@mapNotNull null
            val authorEl = row.select("cite a, td a[href*=\"home.php?mod=space\"]").firstOrNull()
            var uid = UrlUtil.uid(authorEl?.attr("href"))
            val icon = row.select("td.icon img") // folder icon, use for read state
            val replies = row.select(".num a").firstOrNull()?.let { TextUtil.intOf(it.text()) } ?: 0
            ThreadItem(
                threadId = tid,
                title = Parsing.text(titleEl),
                authorUid = uid,
                authorName = authorEl?.let { Parsing.text(it) } ?: "",
                avatarUrl = if (uid > 0) avatarOf(uid) else "",
                replies = replies,
                views = TextUtil.intOf(row.select(".num").firstOrNull()?.text()) - replies,
                isSticky = row.id().startsWith("stickthread_"),
                isDigest = row.select("img[alt=精华]").isNotEmpty(),
                images = row.select("img[src*=attachment], img[src*=forum.php?mod=image]")
                    .map { abs(it.attr("src")) }.filter { it.isNotEmpty() }.take(6),
            )
        }
    }

    // ---------- space / user posts ----------

    private fun parseSpace(doc: Document): List<ThreadItem> {
        // mobile comiis "mine" list: li.service_toplist items or table.nts
        val items = doc.select("li.cl[id^=\"normalthread_\"], dl.cl[id^=\"d_normalthread_\"]")
        val list = items.mapNotNull { it ->
            val a = it.select("a[href*=\"thread-\"], a[href*=\"tid=\"]").firstOrNull() ?: return@mapNotNull null
            val href = a.attr("href")
            val tid = UrlUtil.tid(href)
            if (tid == 0L) return@mapNotNull null
            ThreadItem(threadId = tid, title = Parsing.text(a))
        }
        if (list.isNotEmpty()) return list
        // desktop: div.nts li / td
        val tds = doc.select("tbody[id^=\"normalthread_\"] td")
        return tds.mapNotNull { td ->
            val a = td.select("a[href*=\"thread-\"]").firstOrNull() ?: return@mapNotNull null
            val tid = UrlUtil.tid(a.attr("href"))
            if (tid == 0L) return@mapNotNull null
            ThreadItem(threadId = tid, title = Parsing.text(a))
        }
    }
}