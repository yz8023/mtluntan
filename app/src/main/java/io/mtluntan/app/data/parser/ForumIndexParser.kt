package io.mtluntan.app.data.parser

import io.mtluntan.app.domain.model.Forum
import io.mtluntan.app.domain.model.ForumCategory
import io.mtluntan.app.util.TextUtil
import io.mtluntan.app.util.UrlUtil
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/** Parses the community / forum index page (forum.php?forumlist=1). */
object ForumIndexParser : BaseParser() {

    fun parse(html: String): List<ForumCategory> {
        val doc = Parsing.doc(html)
        val primary = parsePrimary(doc)
        if (primary.isNotEmpty()) return primary
        return parsePostDialog(doc)
    }

    /** 主方案：div.comiis_forumlist groups. */
    private fun parsePrimary(doc: Document): List<ForumCategory> {
        val blocks = doc.select("div.comiis_forumlist")
        if (blocks.isEmpty()) return emptyList()
        val cats = mutableListOf<ForumCategory>()
        for (block in blocks) {
            try {
                val titleEl = block.select("div.comiis_bbs_show h2 a").firstOrNull() ?: continue
                val categoryName = Parsing.text(titleEl)
                if (categoryName.isEmpty()) continue
                val forums = mutableListOf<Forum>()
                for (link in block.select("div.comiis_forum_nbox ul li a[href*=forum-], div.comiis_forum_nbox ul li a[href*=forumdisplay]")) {
                    val href = link.attr("href")
                    val fid = UrlUtil.fid(href)
                    if (fid == 0L) continue
                    val name = link.select("img[alt]").firstOrNull()?.attr("alt")
                        ?.takeIf { it.isNotBlank() } ?: Parsing.text(link.select("p").firstOrNull())
                    if (name.isBlank()) continue
                    val icon = link.select("em img").firstOrNull()?.attr("src")?.let { abs(it) } ?: ""
                    val today = link.select("em span.bg_a.f_f").firstOrNull()
                        ?.let { TextUtil.intOf(it.text()) } ?: 0
                    forums += Forum(
                        id = fid, name = name, iconUrl = icon,
                        today = today, threads = today, posts = today,
                    )
                }
                if (forums.isNotEmpty()) cats += ForumCategory(name = categoryName, forums = forums)
            } catch (e: Exception) { /* skip */ }
        }
        return cats
    }

    /** 备用：comiis post-dialog structure. */
    private fun parsePostDialog(doc: Document): List<ForumCategory> {
        val groupLis = doc.select("div.comiis_bbslists_gid ul > li.comiis_fxpostlistkey")
        if (groupLis.isEmpty()) return emptyList()
        val cats = mutableListOf<ForumCategory>()
        for (li in groupLis) {
            val gid = li.attr("fid")
            val name = Parsing.text(li.select("a").firstOrNull())
            if (gid.isEmpty() || name.isEmpty()) continue
            val forums = mutableListOf<Forum>()
            for (sub in doc.select("ul.comiis_fxpostlistbox_$gid > li")) {
                val link = sub.select("a[href*=fid=]").firstOrNull() ?: continue
                val fid = UrlUtil.fid(link.attr("href"))
                if (fid == 0L) continue
                val img = link.select("img[alt]").firstOrNull()
                val fName = img?.attr("alt")?.takeIf { it.isNotBlank() }
                    ?: Parsing.text(sub.select("a.post_tit em").firstOrNull())
                if (fName.isBlank()) continue
                val icon = img?.attr("src")?.let { abs(it) } ?: ""
                val today = sub.select("em span.bg_a.f_f").firstOrNull()?.let { TextUtil.intOf(it.text()) } ?: 0
                forums += Forum(id = fid, name = fName, iconUrl = icon, today = today, threads = today, posts = today)
            }
            if (forums.isNotEmpty()) cats += ForumCategory(name = name, forums = forums)
        }
        return cats
    }
}