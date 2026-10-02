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
            // 「用户ID」那一行（两种模板都可能出现）→ 再兜底 .uid / em:UID
            val raw = Parsing.text(
                doc.select(
                    ".comiis_space_profile li:contains(用户ID) .profile_rs, " +
                        "li:contains(用户ID) .profile_rs, .comiis_space_profile .profile_rs, " +
                        ".uid, em:contains(UID)"
                ).firstOrNull()
            )
            uid = Regex("\\d{1,12}").find(raw)?.value?.toLongOrNull() ?: 0
        }
        if (uid <= 0) uid = Parsing.findUidInScripts(doc)
        if (uid <= 0) {
            // 最后一张牌：头像地址里就带着 uid（avatar.php?uid=123）
            val avatarSrc = doc.select("img[src*=avatar], img[src^=data-original]").joinToString(" ") { it.attr("src") + " " + it.attr("data-original") }
            uid = UrlUtil.uid(avatarSrc)
        }

        val username = resolveUsername(doc, html)

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
    /**
     * 用户名解析（用户反馈「账号名字识别错，要手动改」→ 这里做成一整套候选 + 校验）。
     *
     * 顺序（从最可靠的开始）：
     *  1. **头像所在的容器**里找名字 —— 结构最稳，不管站点换成哪个 class 都在头像旁边；
     *  2. 明确的选择器优先级（`.comiis_space_tx h2` / `h2.fyy` 是 Comiis 模板的写法）；
     *  3. 页面里的「欢迎您回来，xxx」明文（登录校验页只有这一处提到名字）；
     *  4. 最后才是带 `space-uid` 的链接文字。
     * 每一步都过 [looksLikeUsername]，不合格就继续找下一个，避免把用户组/等级/时间当名字。
     */
    private fun resolveUsername(doc: org.jsoup.nodes.Document, html: String): String {
        fromAvatarContext(doc)?.let { return it }
        val selectors = listOf(
            ".comiis_space_tx h2", "h2.fyy", ".comiis_space_name", ".comiis_fssjname",
            ".comiis_me_name", ".mt_username", ".mt_u_name", ".comiis_myname",
            "#uhd h2", ".comiis_users h2", ".comiis_space_hd h2", ".user_name", ".username",
            ".profile_name", "h1.ph a", ".xl3 a", ".comiis_space_info h2", "h1",
        )
        for (sel in selectors) {
            val text = sanitizeUsername(Parsing.text(doc.select(sel).firstOrNull()))
            if (looksLikeUsername(text)) return text
        }
        usernameFromWelcomeText(html)?.let { return it }
        for (a in doc.select("a[href*=space-uid], a[href*=\"space&uid=\"]").take(6)) {
            val text = sanitizeUsername(Parsing.text(a))
            if (looksLikeUsername(text)) return text
        }
        return ""
    }

    /** 头像容器里找名字：往上找 4 层，挑第一个像人名的元素。 */
    private fun fromAvatarContext(doc: org.jsoup.nodes.Document): String? {
        val avatar = doc.select(
            ".comiis_space_tx img, .comiis_space_tx .user_img img, img[src*=avatar], .comiis_avatar img, #uhd img"
        ).firstOrNull() ?: return null
        var node: org.jsoup.nodes.Element? = avatar.parent()
        repeat(4) {
            val current = node ?: return null
            for (el in current.select("h1, h2, h3, h4, strong, b, .name, .username, .user_name, .fyy, dt a")) {
                if (isNoisyElement(el)) continue
                val text = sanitizeUsername(Parsing.text(el))
                if (looksLikeUsername(text)) return text
            }
            node = current.parent()
        }
        return null
    }

    /** 明显的「不是名字」的元素：等级 / 用户组 / 时间 / 统计。 */
    private fun isNoisyElement(el: org.jsoup.nodes.Element): Boolean {
        val cls = el.classNames().joinToString(" ").lowercase()
        val noise = listOf("kmlev", "level", "lv", "time", "xg1", "stat", "ico", "nav", "btn", "icon", "count")
        if (noise.any { cls.contains(it) }) return true
        val text = el.text()
        val junkWords = listOf("帖子", "主题", "积分", "金币", "威望", "在线", "最后", "注册", "Lv", "等级", "用户组", "日志", "相册", "分享", "留言", "关注", "粉丝", "访客")
        return junkWords.any { text.contains(it) }
    }

    /** 登录校验页 / 提示条里的「欢迎您回来，xxx」。 */
    private fun usernameFromWelcomeText(html: String): String? {
        val patterns = listOf(
            Regex("欢迎您?回来[，,：:\\s]*([^\\s<>，,。\\\"'&]{1,24})"),
            Regex("([^\\s<>，,。\\\"'&]{1,24})[，,]?\\s*欢迎您?回来"),
            Regex("已登录[，,：:\\s]*([^\\s<>，,。\\\"'&]{1,24})"),
        )
        for (p in patterns) {
            val m = p.find(html) ?: continue
            val name = sanitizeUsername(m.groupValues[1])
            if (looksLikeUsername(name)) return name
        }
        return null
    }

    /** 名字合法性校验：长度、字符集、以及各种噪声词。 */
    fun looksLikeUsername(name: String): Boolean {
        if (name.length < 1 || name.length > 24) return false
        if (name.all { it.isDigit() }) return false
        if (!name.any { it.isLetter() }) return false
        val junk = listOf(
            "论坛", "登录", "注册", "Powered", "discuz", "Discuz", "个人资料", "用户组", "等级",
            "Lv.", "time", "首页", "欢迎", "提示", "错误", "无权", "积分", "金币", "帖子", "主题",
            "更多", "全部", "下一页", "上一页", "手机版", "客户端",
        )
        if (junk.any { name.contains(it) }) return false
        // 名字里不该出现这些符号（多半是拼进来的句子）
        if (name.any { it in "，,。；;：:！!？?（）()【】[]<>/\\|" }) return false
        return true
    }

    fun sanitizeUsername(raw: String): String {
        var name = raw.trim().replace(Regex("\\s+"), " ")
        if (name.isEmpty()) return ""
        // 站点有些页面会把名字和说明拼在一起（「xxx 的个人资料」「xxx的空间」）→ 只留名字
        name = name.substringBefore(" 的 ").substringBefore("的个人资料").substringBefore("的空间")
            .substringBefore("的资料").substringBefore("的空间首页").substringBefore(" - ")
            .substringBefore("｜").substringBefore("|").trim()
        // 「欢迎您回来，xxx」这类登录提示
        Regex("欢迎(?:您)?回来[，,]?\\s*([^，,。\\s]+)").find(name)?.let { name = it.groupValues[1] }
        name = name.trim().trimStart(',', '，', ':', '：', '-', '·').trim()
        // 尾部的标点/问候语
        name = name.trimEnd('，', ',', '。', '.', '!', '！', '~')
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