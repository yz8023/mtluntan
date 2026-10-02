package io.mtluntan.app.data.parser

import io.mtluntan.app.data.network.Site
import io.mtluntan.app.domain.model.Attachment
import io.mtluntan.app.domain.model.LikeUser
import io.mtluntan.app.util.TextUtil
import io.mtluntan.app.util.UrlUtil
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * 帖子详情页的「附加信息」解析，与主解析器 [ThreadDetailParser] 解耦：
 * 隐藏块、附件、本人楼层、编辑/删除表单、点赞名单。
 *
 * 隐藏块判定的唯一可信文案（Java 版 v3.7 定的规则，避免把正文里偶然出现的
 * 「隐藏内容」字样当成锁定信号而反复回复）：
 *   `如果您要查看` / `隐藏内容请` / `回复可见` / `需要回复`
 */
object ThreadExtrasParser : BaseParser() {

    data class HiddenBlock(
        val pid: Long,
        val locked: Boolean,
        val html: String,
        val text: String,
    )

    data class EditForm(
        val actionUrl: String,
        val fields: Map<String, String>,
        val textareaName: String,
        val textareaValue: String,
    )

    /** 页面里所有隐藏内容块（主楼 + 楼层）。 */
    fun hiddenBlocks(html: String): List<HiddenBlock> {
        if (html.isBlank()) return emptyList()
        val doc = Parsing.doc(html)
        val out = mutableListOf<HiddenBlock>()
        val selectors = listOf(
            "div.comiis_hide", "div.comiis_locked", "div[class*=hide]", "div.locked",
            "div.comiis_postli div.comiis_hide",
        )
        for (sel in selectors) {
            for (el in doc.select(sel)) {
                val text = Parsing.cleanText(el)
                if (text.isBlank()) continue
                val pidEl = el.closest("[id^=pid]")
                val pid = pidEl?.id()?.removePrefix("pid")?.toLongOrNull() ?: 0L
                out += HiddenBlock(pid = pid, locked = isLocked(text), html = el.outerHtml(), text = text)
            }
            if (out.isNotEmpty()) break
        }
        return out.distinctBy { it.pid to it.text }
    }

    /** 是否仍是「回复可见」的锁定态。 */
    fun isLocked(text: String): Boolean {
        if (text.isBlank()) return true
        return text.contains("如果您要查看") || text.contains("隐藏内容请") ||
            text.contains("回复可见") || text.contains("需要回复")
    }

    /** 页面是否含隐藏块（列表打标 & 自动解锁的开关条件）。 */
    fun hasHiddenContent(html: String): Boolean = hiddenBlocks(html).any { it.locked }

    /** 已解锁的隐藏正文（服务端返回的明文）。 */
    fun unlockedHiddenText(html: String): String =
        hiddenBlocks(html).filterNot { it.locked }.joinToString("\n") { it.text }

    // ---------------- 附件 ----------------

    /**
     * 从页面里抠 fid（版块 id）。
     * 回复接口必须带正确的 fid，解析不到时这里兜底：先看 action=reply&fid= 的表单，
     * 再看版块链接 / 面包屑。
     */
    fun fidOf(html: String): Long {
        val doc = Parsing.doc(html)
        Regex("action=reply&(?:amp;)?fid=(\\d+)").find(html)?.let {
            return it.groupValues[1].toLongOrNull() ?: 0L
        }
        val link = doc.select("a[href*=forum-], a[href*=forumdisplay]").firstOrNull()?.attr("href").orEmpty()
        Regex("forum-(\\d+)-").find(link)?.let { return it.groupValues[1].toLongOrNull() ?: 0L }
        Regex("[?&]fid=(\\d+)").find(link)?.let { return it.groupValues[1].toLongOrNull() ?: 0L }
        return 0L
    }

    fun attachments(html: String): List<Attachment> {
        if (html.isBlank()) return emptyList()
        val doc = Parsing.doc(html)
        val out = mutableListOf<Attachment>()
        val links = doc.select(
            "a[href*=mod=attachment], a[href*=attachment], dl.tattl a, p.attnm a, a[href*=attachpay]"
        )
        for (a in links) {
            val href = abs(a.attr("href"))
            if (href.isBlank()) continue
            val li = a.closest("li, p, dl, div")
            val text = Parsing.text(li)
            val size = Regex("(\\d+(?:\\.\\d+)?\\s*(?:KB|MB|GB|B))", RegexOption.IGNORE_CASE)
                .find(text)?.groupValues?.get(1) ?: ""
            val cost = Regex("(?:售价|需要|支付|花费)\\s*(\\d+)\\s*(?:金币|积分|M币)").find(text)
                ?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val name = Parsing.text(a).ifEmpty { text.take(60) }
            out += Attachment(name = name, url = href, size = size, cost = cost)
        }
        return out.distinctBy { it.url }
    }

    // ---------------- 本人楼层 / 编辑删除 ----------------

    /** pid → 编辑表单 action URL（能拿到说明是自己发的帖 / 有编辑权限）。 */
    fun editablePids(html: String): Set<Long> {
        val doc = Parsing.doc(html)
        return doc.select("a[href*=action=edit][href*=pid=]")
            .mapNotNull { UrlUtil.pid(it.attr("href")).takeIf { p -> p > 0 } }
            .toSet()
    }

    fun deletablePids(html: String): Set<Long> {
        val doc = Parsing.doc(html)
        return doc.select("a[href*=action=delpost], a[href*=action=delete]")
            .mapNotNull { el -> UrlUtil.pid(el.attr("href")).takeIf { p -> p > 0 } }
            .toSet()
    }

    /**
     * 取出编辑表单**全部字段**（Java 版 v3.6 的教训：删除/编辑都别再猜参数，
     * 整张表单原样回提，服务端要什么就给什么）。
     */
    fun editForm(html: String, pid: Long): EditForm? {
        val doc = Parsing.doc(html)
        val form = findFormForPid(doc, pid) ?: return null
        val action = abs(form.attr("action").ifEmpty {
            "/forum.php?mod=post&action=edit&pid=$pid"
        })
        val fields = linkedMapOf<String, String>()
        for (input in form.select("input[name]")) {
            val name = input.attr("name")
            val type = input.attr("type").lowercase()
            val value = when (type) {
                "checkbox", "radio" -> if (input.hasAttr("checked")) input.attr("value") else ""
                else -> input.attr("value")
            }
            if (name.isNotBlank() && value.isNotEmpty()) fields[name] = value
        }
        for (sel in form.select("select[name]")) {
            val name = sel.attr("name")
            val selected = sel.select("option[selected]").firstOrNull() ?: sel.select("option").firstOrNull()
            val v = selected?.attr("value").orEmpty()
            if (name.isNotBlank() && v.isNotEmpty()) fields[name] = v
        }
        val textarea = form.select("textarea[name]").firstOrNull()
        return EditForm(
            actionUrl = action,
            fields = fields,
            textareaName = textarea?.attr("name").orEmpty().ifEmpty { "message" },
            textareaValue = textarea?.text().orEmpty(),
        )
    }

    private fun findFormForPid(doc: Document, pid: Long): Element? {
        // 编辑页：action=edit 的表单里带着 pid
        doc.select("form").forEach { form ->
            val action = form.attr("action")
            if (!action.contains("action=edit")) return@forEach
            val formPid = Regex("[?&]pid=(\\d+)").find(action)?.groupValues?.getOrNull(1)
                ?: form.selectFirst("input[name=pid]")?.attr("value")
            if (formPid == pid.toString()) return form
        }
        // 兜底：整页只有一个编辑表单（编辑页本身就是它）
        val forms = doc.select("form[action*=action=edit]")
        if (forms.size == 1) return forms.first()
        return doc.selectFirst("form#postform")
    }

    // ---------------- 点赞名单 ----------------

    fun likeUsers(html: String): List<LikeUser> {
        if (html.isBlank()) return emptyList()
        val doc = Parsing.doc(html)
        val out = mutableListOf<LikeUser>()
        for (item in doc.select("li, dl, div.comiis_recommend_li")) {
            val link = item.selectFirst("a[href*=space&uid], a[href*=space-uid-]") ?: continue
            val uid = UrlUtil.uid(link.attr("href"))
            if (uid <= 0) continue
            val name = Parsing.text(link).ifEmpty { Parsing.text(item.selectFirst("img[alt]")) }
            if (name.isBlank()) continue
            out += LikeUser(
                uid = uid,
                name = name,
                avatarUrl = Site.avatarUrl(uid),
                time = Parsing.text(item.select(".xg1, time, .comiis_tm").firstOrNull()),
            )
        }
        return out.distinctBy { it.uid }
    }

    /** 楼层里显示的「评分/金币」总数，用于楼层右下角展示。 */
    fun rateSummary(html: String): String {
        val doc = Parsing.doc(html)
        return Parsing.text(doc.select(".comiis_rate, .rate_sum, .mtm").firstOrNull())
    }

    fun textInt(s: String): Int = TextUtil.intOf(s)
}
