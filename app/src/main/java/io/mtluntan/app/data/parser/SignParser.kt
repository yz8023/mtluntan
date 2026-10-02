package io.mtluntan.app.data.parser

import io.mtluntan.app.domain.model.CheckInState
import io.mtluntan.app.domain.model.SignOutcome
import io.mtluntan.app.util.TextUtil

/**
 * k_misign 签到页 / 签到时返回值的解析。
 *
 * 判定依据来自 Java 参考版在 bbs.binmt.cc 上的实测（SignParser.java 的注释）：
 *  - 游客访问签到页：整页 **没有一次** `formhash`，且带「您需要先登录 / 立即登录」
 *  - 页面里的 `k_misign`、`spacecp` 字样游客也能看到（侧边栏推广链接），
 *    **不能**拿来当登录态标记 —— 这是最容易写错的地方
 *
 * 所以「登录态」只认 formhash 与「签到操作入口」两件事。
 */
object SignParser {

    const val SIGN_URL = "plugin.php?id=k_misign:sign"

    /** 已签到关键词（服务端文案在不同版本间变过，全部收录） */
    private val ALREADY_SIGNED = listOf(
        "今日已签", "今日已经签到", "您今天已经签到过了", "您已经签到过了",
        "已经签到过", "已经签到", "重复签到", "请勿重复", "请不要重复", "签到过了",
    )

    /** 登录成功标志 */
    private val LOGIN_SUCCESS = listOf("succeed", "欢迎您回来", "login_reult_1")

    /** 明确的未登录提示 */
    private val LOGGED_OUT_HINTS = listOf("您需要先登录", "请先登录", "立即登录", "请登录后再试")

    // ---------- 登录态 ----------

    /**
     * 签到页是否处于未登录状态。
     * 核心依据：Discuz 只给已认证会话输出 formhash。
     */
    fun looksLoggedOut(html: String): Boolean {
        if (html.isBlank()) return true
        if (!html.contains("formhash")) return true
        val hasSignEntry = html.contains("qiandao") || html.contains("midaben_sign")
        return LOGGED_OUT_HINTS.any { html.contains(it) } && !hasSignEntry
    }

    /** 登录响应是否成功。 */
    fun looksLoginSuccess(html: String): Boolean =
        LOGIN_SUCCESS.any { html.contains(it) } &&
            !html.contains("密码错误") && !html.contains("登录失败") && !html.contains("验证码")

    /** 站点防护拦截（阿里云 ESA / JS 挑战）。 */
    fun looksChallenge(html: String): Boolean =
        html.contains("acw_sc__v2") || html.contains("arg1=") || html.contains("captcha")

    // ---------- 签到页 ----------

    fun parseSignPage(html: String, baseUrl: String = "https://bbs.binmt.cc"): CheckInState {
        val state = parsePage(html, baseUrl)
        return CheckInState(
            signedToday = state.alreadySigned || state.ok,
            signDays = state.days,
            goldCoin = TextUtil.intOf(state.reward),
            message = state.message,
        )
    }

    /** 完整签到页解析：已签/未签、连续天数、排名、奖励文案。 */
    fun parsePage(html: String, baseUrl: String = "https://bbs.binmt.cc"): SignOutcome {
        if (looksLoggedOut(html)) {
            return SignOutcome(ok = false, loggedOut = true, message = "未登录")
        }
        val text = Parsing.doc(html).text()
        val already = ALREADY_SIGNED.any { text.contains(it) }
        return SignOutcome(
            ok = false,
            alreadySigned = already,
            days = extractDays(text),
            rank = extractRank(text),
            reward = extractReward(text),
            message = if (already) "今日已签到" else "未签到",
        )
    }

    /** 页面里能直接读到的 formhash（签到按钮所在表单）。 */
    fun formhashOf(html: String): String = Parsing.valueWithJsFallback(html, "formhash")

    // ---------- 签到结果 ----------

    /**
     * k_misign 的 ajax 返回：`1|签到成功|获得 5 金币|第 12 名` 这类竖线文本，
     * 也有版本直接返回 JSON。这里统一按「成功 / 已签 / 失败 + 奖励 + 排名」解析。
     */
    fun parseAjaxResult(body: String): SignOutcome {
        val t = body.trim()
        if (t.isEmpty()) return SignOutcome(ok = false, message = "空响应")
        val already = ALREADY_SIGNED.any { t.contains(it) }
        val ok = already ||
            t.contains("签到成功") ||
            t.contains("获得") ||
            (Regex("^\\s*1\\s*\\|").containsMatchIn(t))
        return SignOutcome(
            ok = ok,
            alreadySigned = already,
            days = extractDays(t),
            rank = extractRank(t),
            reward = extractReward(t),
            message = cleanMessage(t),
        )
    }

    // ---------- 提取器 ----------

    fun extractDays(text: String): Int {
        val patterns = listOf(
            Regex("(?:连续签到|已连续|连续)\\s*(\\d+)\\s*天"),
            Regex("签到\\s*(\\d+)\\s*天"),
            Regex("(\\d+)\\s*天"),
        )
        for (p in patterns) {
            val m = p.find(text) ?: continue
            val v = m.groupValues[1].toIntOrNull() ?: continue
            if (v in 1..9999) return v
        }
        return 0
    }

    fun extractRank(text: String): Int {
        val patterns = listOf(
            Regex("第\\s*(\\d+)\\s*名"),
            Regex("排名\\s*[:：]?\\s*(\\d+)"),
            Regex("(\\d+)\\s*位"),
        )
        for (p in patterns) {
            val m = p.find(text) ?: continue
            val v = m.groupValues[1].toIntOrNull() ?: continue
            if (v in 1..999999) return v
        }
        return 0
    }

    /** 奖励文案：优先带单位的金币/积分，其次「获得 X」。 */
    fun extractReward(text: String): String {
        val withUnit = Regex("(?:获得|奖励|赠送)[^，。;；\\n|]{0,12}?(\\d+)\\s*(金币|积分|威望|经验|M币)")
            .find(text)
        if (withUnit != null) return "${withUnit.groupValues[1]} ${withUnit.groupValues[2]}"
        val gained = Regex("(?:获得|奖励|赠送)\\s*[:：]?\\s*([0-9]+)").find(text)
        if (gained != null) return gained.groupValues[1]
        val coin = Regex("(\\d+)\\s*(?:枚)?金币").find(text)
        if (coin != null) return "${coin.groupValues[1]} 金币"
        return ""
    }

    private fun cleanMessage(raw: String): String {
        val line = raw.split("|").firstOrNull { part ->
            part.isNotBlank() && !part.trim().matches(Regex("^-?\\d+$")) && !part.trim().startsWith("{")
        } ?: raw
        val stripped = line.replace(Regex("<[^>]+>"), " ").trim()
        return stripped.take(120)
    }
}
