package io.mtluntan.app.data.parser

import io.mtluntan.app.domain.model.CheckInState
import io.mtluntan.app.domain.model.SignOutcome
import io.mtluntan.app.util.TextUtil

/**
 * k_misign 签到页 / 签到返回值的解析。
 *
 * 判定依据来自 Java 参考版在 bbs.binmt.cc 的实测（`session/SignParser.java` 注释）：
 *  - 游客访问签到页：整页**没有一次** `formhash`，且带「您需要先登录 / 立即登录」
 *  - 页面里的 `k_misign`、`spacecp` 字样游客也能看到（侧边栏推广），
 *    **不能**当登录态标记 —— 这是最容易写错的地方
 *
 * 本轮修复（用户反馈「签到失败」）：
 *  1. 补上站点防护拦截判定（403 / acw_sc__v2 / 人机验证），否则被拦时会被当成「未登录」
 *  2. 签到接口返回值补 `<root><![CDATA[...]]></root>` 解析（旧实现只按竖线文本猜）
 *  3. formhash 提取改成「隐藏域优先、URL 参数兜底」的多重回退
 *  4. 奖励优先读页面隐藏域 `lxreward`，其次才是文案
 *  5. 排名读取 `您的签到排名：N`（页面里的原文格式）
 */
object SignParser {

    const val SIGN_PAGE = "k_misign-sign.html"
    const val SIGN_PLUGIN = "plugin.php?id=k_misign:sign"

    /** 已签到关键词（服务端文案各版本变过，全部收录） */
    private val ALREADY_SIGNED = listOf(
        "今日已签", "今日已经签到", "您今天已经签到过了", "您已经签到过了",
        "已经签到过", "已经签到", "重复签到", "请勿重复", "请不要重复", "签到过了",
    )

    /** 登录成功标志 */
    private val LOGIN_SUCCESS = listOf("succeed", "欢迎您回来", "login_reult_1")

    /** 明确的未登录提示 */
    private val LOGGED_OUT_HINTS = listOf("您需要先登录", "请先登录", "立即登录", "请登录后再试")

    /** 签到返回里的失败文案 */
    private val FAILURE_HINTS = listOf("请先登录", "您需要先登录", "没有权限", "非法操作", "请重新登录")

    // ---------- 防护 / 登录态 ----------

    /**
     * 是否被站点防护拦截。
     * 关键点：挑战页里也可能出现 `formhash` 之类的词，所以「有论坛特征」时不算拦截。
     */
    fun isBlocked(html: String): Boolean {
        if (html.isBlank()) return false
        val lower = html.lowercase()
        val forum = lower.contains("discuz_uid") || lower.contains("comiis_") ||
            lower.contains("discuz_tips") || lower.contains("formhash")
        val challenge = lower.contains("acw_sc__v2") || lower.contains("aliyungf_tc") ||
            lower.contains("__jsl_clearance") || lower.contains("window._config_") ||
            lower.contains("waf challenge") || lower.contains("esa challenge") ||
            html.contains("人机验证") || html.contains("安全验证") ||
            (lower.contains("document.cookie") && (lower.contains("challenge") || lower.contains("arg1")))
        return lower.contains("you have been blocked") ||
            lower.contains("403 forbidden") ||
            lower.contains("access denied") ||
            lower.contains("attention required") ||
            (challenge && !forum)
    }

    fun looksLoggedOut(html: String): Boolean {
        if (html.isBlank()) return true
        if (!html.contains("formhash")) return true
        val hasSignEntry = html.contains("qiandao") || html.contains("midaben_sign")
        return LOGGED_OUT_HINTS.any { html.contains(it) } && !hasSignEntry
    }

    fun looksLoginSuccess(html: String): Boolean =
        LOGIN_SUCCESS.any { html.contains(it) } &&
            !html.contains("密码错误") && !html.contains("登录失败") && !html.contains("验证码")

    /** 兼容旧调用点。 */
    fun looksChallenge(html: String): Boolean = isBlocked(html) && !html.contains("discuz_uid")

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

    fun parsePage(html: String, baseUrl: String = "https://bbs.binmt.cc"): SignOutcome {
        if (isBlocked(html)) {
            return SignOutcome(ok = false, message = "被站点防护拦截（403），过几分钟再试")
        }
        if (looksLoggedOut(html)) {
            return SignOutcome(ok = false, loggedOut = true, message = "未登录")
        }
        val text = Parsing.doc(html).text()
        val already = isAlreadySigned(html) || ALREADY_SIGNED.any { text.contains(it) }
        return SignOutcome(
            ok = false,
            alreadySigned = already,
            days = extractDays(text),
            rank = extractRank(text) ?: extractRankFromHtml(html),
            reward = extractReward(html),
            message = if (already) "今日已签到" else "未签到",
        )
    }

    fun isAlreadySigned(html: String): Boolean =
        ALREADY_SIGNED.any { html.contains(it) } || extractReward(html).isNotEmpty()

    /** 页面里能直接读到的 formhash（隐藏域优先，URL 参数兜底）。 */
    fun formhashOf(html: String): String = extractFormhash(html)

    fun extractFormhash(html: String): String {
        val patterns = listOf(
            Regex("name=\"formhash\"[^>]*?value=\"([a-zA-Z0-9]+)\""),
            Regex("value=\"([a-zA-Z0-9]+)\"[^>]*?name=\"formhash\""),
            Regex("formhash\"\\s*value=\"([a-zA-Z0-9]+)\""),
            Regex("formhash=([a-zA-Z0-9]+)"),
        )
        for (p in patterns) {
            val m = p.find(html) ?: continue
            val v = m.groupValues[1]
            if (v.isNotBlank()) return v
        }
        return ""
    }

    // ---------- 签到结果 ----------

    /**
     * k_misign 的签到返回：
     *  - 正常：`<root><![CDATA[签到成功]]></root>` 或一句短文本
     *  - 异常：返回整页 HTML（formhash 过期 / 伪静态被拦 / 风控页）→ 返回空串，
     *    交给上层「回读签到页看是否已签」来判定
     */
    fun parseSignResponse(raw: String): String {
        if (raw.isBlank()) return ""
        extract(raw, "(?s)<root><!\\[CDATA\\[(.*?)\\]\\]></root>")?.let { return stripTags(it) }
        extract(raw, "(?s)<root>(.*?)</root>")?.let { return stripTags(it) }
        val trimmed = raw.trim()
        return if (trimmed.isNotEmpty() && trimmed.length <= 200 &&
            !trimmed.startsWith("<!DOCTYPE") && !trimmed.startsWith("<html") && !trimmed.contains("<body")
        ) {
            stripTags(trimmed)
        } else ""
    }

    /** 把接口文案 + 页面状态合成为最终结果。 */
    fun combine(apiText: String, pageAfter: String, rawBody: String): SignOutcome {
        val already = isAlreadySigned(pageAfter)
        val success = isSuccessText(apiText) || already
        val failed = isFailureText(apiText)
        val reward = extractRewardFromText(rawBody).ifBlank { extractReward(pageAfter) }
        return SignOutcome(
            ok = success && !failed,
            alreadySigned = already,
            days = extractDays(Parsing.doc(pageAfter).text()),
            rank = extractRank(Parsing.doc(pageAfter).text()) ?: extractRankFromHtml(pageAfter),
            reward = reward,
            message = when {
                failed -> apiText
                already -> "今日已签到"
                success -> "签到成功"
                else -> apiText.ifBlank { "签到失败" }
            },
        )
    }

    fun isSuccessText(text: String): Boolean {
        if (text.isBlank()) return false
        val lower = text.lowercase()
        return text.contains("签到成功") || text.contains("恭喜") ||
            ALREADY_SIGNED.any { text.contains(it) } ||
            lower.contains("succeed") || lower.contains("success")
    }

    fun isFailureText(text: String): Boolean =
        text.isNotBlank() && FAILURE_HINTS.any { text.contains(it) }

    /** 兼容旧调用：直接解析接口返回文本。 */
    fun parseAjaxResult(body: String): SignOutcome {
        val text = parseSignResponse(body).ifBlank { body.trim() }
        if (text.isEmpty()) return SignOutcome(ok = false, message = "空响应")
        val already = ALREADY_SIGNED.any { text.contains(it) }
        val ok = already || isSuccessText(text)
        return SignOutcome(
            ok = ok,
            alreadySigned = already,
            days = extractDays(text),
            rank = extractRank(text) ?: 0,
            reward = extractRewardFromText(text),
            message = if (already) "今日已签到" else text.take(120),
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

    fun extractRank(text: String): Int? {
        val patterns = listOf(
            Regex("您的签到排名[：:]\\s*([0-9]+)"),
            Regex("第\\s*(\\d+)\\s*名"),
            Regex("排名\\s*[:：]?\\s*(\\d+)"),
            Regex("(\\d+)\\s*位"),
        )
        for (p in patterns) {
            val m = p.find(text) ?: continue
            val v = m.groupValues[1].toIntOrNull() ?: continue
            if (v in 1..999999) return v
        }
        return null
    }

    /** 排名在页面里常常被标签包着，单独扫一遍 HTML。 */
    fun extractRankFromHtml(html: String): Int {
        extract(html, "您的签到排名[：:](.*?)</div>")?.let { raw ->
            TextUtil.intOf(stripTags(raw)).takeIf { it > 0 }?.let { return it }
        }
        return extractRank(Parsing.doc(html).text()) ?: 0
    }

    /** 奖励：优先隐藏域 lxreward，其次文案（页面里「已签到」时常只剩文案）。 */
    fun extractReward(html: String): String {
        if (html.isBlank()) return ""
        val patterns = listOf(
            Regex("id=\"lxreward\"[^>]*value=\"(.*?)\""),
            Regex("name=\"lxreward\"[^>]*value=\"(.*?)\""),
            Regex("value=\"(.*?)\"[^>]*id=\"lxreward\""),
            Regex("value=\"(.*?)\"[^>]*name=\"lxreward\""),
            Regex("lxreward[^>]*?value=\"(.*?)\""),
        )
        for (p in patterns) {
            val m = p.find(html) ?: continue
            val v = m.groupValues[1].trim()
            if (v.isNotEmpty()) return v
        }
        return extractRewardFromText(stripTags(html))
    }

    /** 从任意文本里抓奖励数字。 */
    fun extractRewardFromText(text: String): String {
        if (text.isBlank()) return ""
        val patterns = listOf(
            Regex("奖励\\s*([0-9]+)\\s*(?:金币|威望|贡献|积分|金钱|MT币)?"),
            Regex("获得(?:随机)?(?:奖励)?\\s*([0-9]+)\\s*(?:金币|威望|贡献|积分|金钱|MT币)"),
            Regex("增加\\s*([0-9]+)\\s*(?:金币|威望|贡献|积分|金钱|MT币)?"),
            Regex("本次签到.{0,40}?([0-9]+)\\s*(?:金币|威望|贡献|积分|金钱|MT币)"),
            Regex("\\+\\s*([0-9]+)\\s*(?:金币|威望|贡献|积分|金钱|MT币)"),
            Regex("获得\\s*[:：]?\\s*([0-9]+)"),
            Regex("([0-9]+)\\s*(?:枚)?金币"),
        )
        for (p in patterns) {
            val m = p.find(text) ?: continue
            val v = m.groupValues[1]
            if (v.isNotBlank()) return v
        }
        return ""
    }

    // ---------- 内部 ----------

    private fun stripTags(raw: String): String =
        raw.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()

    private fun extract(source: String, pattern: String): String? =
        Regex(pattern, RegexOption.IGNORE_CASE).find(source)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
}
