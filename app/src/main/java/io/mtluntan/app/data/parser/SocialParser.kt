package io.mtluntan.app.data.parser

import io.mtluntan.app.data.network.Site
import io.mtluntan.app.domain.model.BadgeCounts
import io.mtluntan.app.domain.model.ChatMessage
import io.mtluntan.app.domain.model.CreditItem
import io.mtluntan.app.domain.model.Notice
import io.mtluntan.app.domain.model.PmSession
import io.mtluntan.app.domain.model.UserProfile
import io.mtluntan.app.util.TextUtil
import io.mtluntan.app.util.UrlUtil
import org.jsoup.nodes.Element

/**
 * 消息 / 私信 / 好友 / 积分 / 角标 的解析。
 *
 * 都是「同一站点、两套模板」的老问题：移动 Comiis 与桌面 Discuz 的 DOM 完全不同，
 * 所以每个函数都保持「先移动模板、再 PC 兜底、最后正则兜底」的三段式，
 * 并且**永远返回可用的空集合**而不是抛异常。
 */
object SocialParser : BaseParser() {

    // ---------------- 未读角标 ----------------

    fun parseBadgeCounts(noticeHtml: String, pmHtml: String = ""): BadgeCounts {
        val notices = countUnread(noticeHtml)
        val pms = when {
            pmHtml.isNotBlank() -> countUnread(pmHtml)
            else -> extractInlineCount(noticeHtml, listOf("私信", "短消息", "pm"))
        }
        return BadgeCounts(notices, pms)
    }

    private fun countUnread(html: String): Int {
        if (html.isBlank()) return 0
        val doc = Parsing.doc(html)
        // 1) 明确标记未读的条目
        val marked = doc.select(
            "li.new, li.comiis_new, .notice_new, .pm_new, em.comiis_red, " +
                "span.comiis_new, .unread, dl.new, li[class*=unread]"
        ).size
        if (marked > 0) return marked
        // 2) 「未读 N」文案
        val m = Regex("未读\\s*(?:消息)?\\s*[：:（(]?\\s*(\\d+)").find(doc.text())
        if (m != null) return m.groupValues[1].toIntOrNull() ?: 0
        // 3) 导航上的 (N) 角标
        return extractInlineCount(html, listOf("通知", "提醒", "消息"))
    }

    private fun extractInlineCount(html: String, keywords: List<String>): Int {
        for (kw in keywords) {
            val m = Regex("$kw[^0-9（(]{0,12}[（(](\\d+)[)）]").find(html)
            if (m != null) return m.groupValues[1].toIntOrNull() ?: 0
        }
        return 0
    }

    // ---------------- 通知 ----------------

    /** 通知页：带类型的列表（回复 / 提及 / 系统），保留 url 供跳转。 */
    fun parseNoticeList(html: String): List<Notice> {
        val doc = Parsing.doc(html)
        val out = mutableListOf<Notice>()
        // 移动模板：dl.cl / li.cl 每条通知
        for (item in doc.select("dl.cl, li.cl, dl.comiis_notice, li.comiis_notice")) {
            val link = item.selectFirst("a[href]") ?: continue
            val text = Parsing.cleanText(item)
            if (text.isBlank()) continue
            val time = Parsing.text(item.select(".xg1, time, .mtn, .comiis_tm").firstOrNull())
            val author = Parsing.text(item.select("a[href*=space&uid], a[href*=space-uid-]").firstOrNull())
            val type = when {
                text.contains("私信") || text.contains("短消息") -> 1
                text.contains("系统") || text.contains("管理") -> 2
                text.contains("好友") || text.contains("关注") -> 3
                else -> 0
            }
            out += Notice(
                type = type,
                body = text,
                fromAuthor = author,
                url = abs(link.attr("href")),
                time = time,
                isNew = item.classNames().any { it.contains("new") || it.contains("unread") },
            )
        }
        if (out.isNotEmpty()) return out
        // PC 兜底：ul.nts li
        for (li in doc.select("ul.nts li")) {
            val a = li.selectFirst("a[href]") ?: continue
            out += Notice(
                type = 0,
                body = Parsing.cleanText(li),
                url = abs(a.attr("href")),
                time = Parsing.text(li.select(".xg1, time").firstOrNull()),
                isNew = li.classNames().any { it.contains("new") },
            )
        }
        return out
    }

    // ---------------- 私信 ----------------

    /** 私信会话列表。 */
    fun parsePmSessions(html: String): List<PmSession> {
        val doc = Parsing.doc(html)
        val out = mutableListOf<PmSession>()
        val items = doc.select("dl.cl, li.cl, dl.comiis_pmlist_li, li.comiis_pmlist_li, tr.cl")
        for (item in items) {
            val link = item.selectFirst("a[href*=\"pm&subop=view\"], a[href*=do=pm][href*=touid]") ?: continue
            val href = link.attr("href")
            val touid = Regex("[?&]touid=(\\d+)").find(href)?.groupValues?.getOrNull(1)?.toLongOrNull()
                ?: UrlUtil.uid(item.selectFirst("a[href*=space&uid]")?.attr("href"))
            if (touid == null || touid <= 0L) continue
            val username = Parsing.text(
                item.selectFirst(".comiis_pmname, .pmname, a[href*=space&uid], a[href*=space-uid-]")
            ).ifEmpty { Parsing.text(item.selectFirst("img[alt]")) }
            val last = Parsing.text(item.selectFirst(".comiis_pmmsg, .pmmessage, .overhidden"))
                .ifEmpty { Parsing.cleanText(item).take(80) }
            val time = Parsing.text(item.select(".xg1, time, .mtn, .comiis_tm").firstOrNull())
            val unreadText = Parsing.text(item.select(".comiis_pmnum, .num, em").firstOrNull())
            out += PmSession(
                uid = touid,
                username = username,
                avatarUrl = Site.avatarUrl(touid),
                lastMessage = last,
                time = time,
                unread = TextUtil.intOf(unreadText),
                url = abs(href),
            )
        }
        return out.distinctBy { it.uid }
    }

    /** 私信气泡：判断「我发的」优先看右对齐 class，其次比作者名。 */
    fun parseChatMessages(html: String, myName: String = ""): List<ChatMessage> {
        val doc = Parsing.doc(html)
        val out = mutableListOf<ChatMessage>()
        val blocks = doc.select(
            "div.comiis_pmbox, div.pm_box, div.comiis_pmlist li, dl.comiis_pm, div.pm_item, li.pm_item"
        )
        for ((idx, block) in blocks.withIndex()) {
            val body = Parsing.cleanText(
                block.selectFirst(".comiis_pmcontent, .pmcontent, .pm_body, .content, p")
            )
            if (body.isBlank()) continue
            val author = Parsing.text(block.selectFirst("a[href*=space&uid], .comiis_pmname, .pmname"))
            val classes = block.classNames().joinToString(" ")
            val fromMe = classes.contains("self") || classes.contains("right") || classes.contains("me") ||
                (myName.isNotEmpty() && author == myName)
            val time = Parsing.text(block.select(".xg1, time, .comiis_tm").firstOrNull())
            out += ChatMessage(
                id = idx.toLong(),
                fromMe = fromMe,
                authorName = author,
                body = body,
                time = time,
            )
        }
        return out
    }

    // ---------------- 好友 / 关注 ----------------

    data class FriendEntry(val uid: Long, val name: String, val avatarUrl: String, val online: Boolean = false)

    fun parseFriends(html: String): List<FriendEntry> {
        val doc = Parsing.doc(html)
        val out = mutableListOf<FriendEntry>()
        for (item in doc.select("li.cl, dl.cl, li, tr")) {
            val link = item.selectFirst("a[href*=space&uid], a[href*=space-uid-]") ?: continue
            val uid = UrlUtil.uid(link.attr("href"))
            if (uid <= 0) continue
            val name = Parsing.text(link).ifEmpty { Parsing.text(item.selectFirst("img[alt]")) }
            if (name.isBlank()) continue
            out += FriendEntry(
                uid = uid,
                name = name,
                avatarUrl = Site.avatarUrl(uid),
                online = item.select(".online, .comiis_online").isNotEmpty(),
            )
        }
        return out.distinctBy { it.uid }
    }

    /** 关注状态：好友页 / 空间页上的「加关注 / 已关注」按钮。 */
    fun parseFollowing(html: String): Boolean? {
        val doc = Parsing.doc(html)
        val followLink = doc.select("a[href*=follow], a[href*=friend&add]").firstOrNull() ?: return null
        val text = Parsing.text(followLink)
        return when {
            text.contains("取消关注") || text.contains("已关注") || text.contains("解除") -> true
            text.contains("关注") || text.contains("收听") -> false
            else -> null
        }
    }

    /** 从空间页提取关注/粉丝数量与好友 uid 列表。 */
    fun parseFollowingUids(html: String): List<Long> {
        val doc = Parsing.doc(html)
        return doc.select("a[href*=space&uid], a[href*=space-uid-]")
            .mapNotNull { UrlUtil.uid(it.attr("href")).takeIf { uid -> uid > 0 } }
            .distinct()
    }

    // ---------------- 积分 ----------------

    fun parseCredits(html: String): List<CreditItem> {
        val doc = Parsing.doc(html)
        val out = mutableListOf<CreditItem>()
        for (li in doc.select("li, tr, dd")) {
            val text = Parsing.cleanText(li)
            if (text.isBlank() || text.length > 60) continue
            val m = Regex("^(.{1,10}?)[：:\\s]+(-?\\d+)\\s*(枚|个|点)?$").find(text) ?: continue
            val name = m.groupValues[1].trim()
            if (name.isBlank()) continue
            out += CreditItem(name = name, value = m.groupValues[2])
        }
        if (out.isNotEmpty()) return out.distinctBy { it.name }

        // 明细表：积分 / 变更 / 时间
        for (tr in doc.select("table tr")) {
            val cells = tr.select("td, th").map { Parsing.text(it) }
            if (cells.size < 2) continue
            out += CreditItem(name = cells[0], value = cells.getOrElse(1) { "" }, delta = cells.getOrElse(2) { "" }, time = cells.getOrElse(3) { "" })
        }
        return out.filter { it.name.isNotBlank() }
    }

    /** 空间页增强：金币/积分/好友数等从 dl/dd 里再抓一遍。 */
    fun enrichProfile(profile: UserProfile, html: String): UserProfile {
        val doc = Parsing.doc(html)
        var gold = profile.goldCoin
        for (li in doc.select("li, dd, p")) {
            val t = Parsing.cleanText(li)
            when {
                t.contains("金币") && gold == 0 -> gold = TextUtil.intOf(t)
            }
        }
        return profile.copy(goldCoin = gold)
    }

    fun firstElementText(item: Element?): String = Parsing.cleanText(item)
}
