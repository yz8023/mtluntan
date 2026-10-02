package io.mtluntan.app.data.parser

import io.mtluntan.app.domain.model.Notice
import io.mtluntan.app.domain.model.UserProfile
import io.mtluntan.app.util.TextUtil
import io.mtluntan.app.util.UrlUtil

/** Parses profile/space, my-posts, notifications and PM pages. */
object UserPagesParser : BaseParser() {

    /**
     * 个人页（space / profile）解析。
     *
     * 选择器直接沿用 Java 版 `ForumParser.parseUserProfile`（在真机上跑通的版本）：
     * Comiis 模板的头像是 `.comiis_space_tx img`，用户名在 `.comiis_space_tx h2`，
     * 等级是 `.kmlevs.kmlv`（Lv.X），用户组是 `.kmlev`（硕士生），
     * UID 在「用户ID」那一行的 `.profile_rs`。
     *
     * 特别注意：**绝不拿 <title> 或站点名当用户名** —— 所有账号的标题都一样，
     * 用它当账号标识会让第二个账号直接覆盖第一个账号。
     */
    fun parseProfile(html: String): UserProfile {
        val doc = Parsing.doc(html)
        val check = Parsing.checkPageError(doc, html)
        if (check.isError) return UserProfile()

        var uid = Parsing.findUid(doc)
        if (uid <= 0) {
            val raw = Parsing.text(doc.select(".comiis_space_profile li:contains(用户ID) .profile_rs, .uid, em:contains(UID)").firstOrNull())
            uid = Regex("\\d{1,12}").find(raw)?.value?.toLongOrNull() ?: 0
        }
        if (uid <= 0) uid = Parsing.findUidInScripts(doc)

        val username = sanitizeUsername(
            Parsing.text(
                doc.select(
                    ".comiis_space_tx h2, .comiis_fssjname, .comiis_me_name, .mt_username, " +
                        ".username, .user_name, .profile_name, h1.ph a, .xl3 a, .mt_u_name"
                ).firstOrNull()
            )
        ).ifEmpty {
            // 再看一眼带 space-uid 的链接文字
            sanitizeUsername(Parsing.text(doc.select("a[href*=space-uid], a[href*=\"space&uid=\"]").firstOrNull()))
        }

        // 头像：先找个人页头部容器里的（不能抓导航栏那个「当前登录用户」头像），
        // 最后按 uid 直接构造 uc_server 地址。
        val avatarEl = doc.select(".comiis_space_tx .user_img img, .comiis_space_tx img[src*=avatar], .comiis_space_tx img")
            .firstOrNull() ?: doc.select("img[src*=avatar]").firstOrNull()
        val avatar = avatarEl?.attr("src").orEmpty()
            .ifEmpty { avatarEl?.attr("data-original").orEmpty() }
            .ifEmpty { avatarEl?.attr("data-src").orEmpty() }
            .let { if (it.isNotEmpty()) abs(it) else "" }
            .let { if (it.startsWith("data:")) "" else it }
            .ifEmpty { if (uid > 0) avatarOf(uid) else "" }

        val level = Parsing.text(doc.select(".comiis_space_tx .kmlevs.kmlv, .comiis_space_tx .kmlv, em:contains(Lv)").firstOrNull())
        val groupName = Parsing.text(doc.select(".comiis_space_tx .kmlev, p.mt_u_1 a, .comiis_me_gr, dt a[href*=usergroup], a[href*=usergroup]").firstOrNull())
            .ifEmpty { Parsing.text(doc.select("li:contains(用户组) em, li:contains(用户组) span").firstOrNull()) }
        val registerTime = Parsing.text(doc.select("li:contains(注册时间) em, li:contains(注册时间) span").firstOrNull())
        val lastVisit = Parsing.text(doc.select("li:contains(最后活跃) em, li:contains(最后访问) em, li:contains(最后访问) span").firstOrNull())
        val signature = Parsing.text(doc.select("p.sign, .sx2, .comiis_qm textarea").firstOrNull())

        val stat = StatExtractor.fromListItems(doc)
        val creditsText = Parsing.text(doc.select(".comiis_space_profilejf ul li").firstOrNull())

        return UserProfile(
            uid = uid,
            username = username,
            avatarUrl = avatar,
            groupName = groupName.ifBlank { level },
            registerTime = registerTime,
            lastVisit = lastVisit,
            signature = signature,
            posts = stat.posts,
            threads = stat.threads,
            credits = stat.credits,
            goldCoin = stat.goldCoin,
            reputation = stat.reputation,
            creditsText = creditsText.replace(Regex("\\s+"), " ").trim().take(60),
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

    /**
     * 用户名清洗 / 校验。
     *
     * 从页面里抓到的「用户名」有可能是页面标题、站点名、提示语。
     * 这类字符串每个账号都一样，一旦入库就会互相覆盖，所以这里必须挡掉。
     */
    fun sanitizeUsername(raw: String): String {
        var name = raw.trim().replace(Regex("\\s+"), " ")
        if (name.isEmpty()) return ""
        // 站点有些页面会把名字和说明拼在一起（「xxx 的个人资料」「xxx的空间」）→ 只留名字
        name = name.substringBefore(" 的 ").substringBefore("的个人资料").substringBefore("的空间")
            .substringBefore("的资料").substringBefore("的空间首页").substringBefore(" - ")
            .substringBefore("｜").substringBefore("|").trim()
        // 「欢迎您回来，xxx」这类登录提示
        Regex("欢迎(?:您)?回来[，,]\\s*([^，,。\\s]+)").find(name)?.let { name = it.groupValues[1] }
        if (name.length > 24) return ""
        val junk = listOf(
            "论坛", "Powered", "powered", "登录", "注册", "提示", "个人资料", "搜索结果",
            "空间", "首页", "Discuz", "discuz", "错误", "无权", "该用户", "无效", "用户组",
        )
        if (junk.any { name.contains(it) }) return ""
        if (!name.any { it.isLetter() || it.isDigit() }) return ""
        if (name.all { it.isDigit() }) return ""   // 纯数字不是合法用户名
        return name
    }
}

/**
 * Small helper to pull a uid from arbitrary space links.
 */
fun findUidFromDoc(doc: org.jsoup.nodes.Document): Long = Parsing.findUid(doc)