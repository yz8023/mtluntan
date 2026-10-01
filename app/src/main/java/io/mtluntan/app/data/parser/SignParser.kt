package io.mtluntan.app.data.parser

import io.mtluntan.app.domain.model.CheckInState
import io.mtluntan.app.util.TextUtil

/** Parses the k_misign check-in plugin page and the ajax result. */
object SignParser {

    const val SIGN_URL = "plugin.php?id=k_misign:sign"

    /**
     * The sign plugin page contains:
     *   <a class="signBtn" href="...operation=qiandao...">签到</a>   — not signed yet
     *   signed state                                     — already signed
     */
    fun parseSignPage(html: String, baseUrl: String = "https://bbs.binmt.cc"): CheckInState {
        val doc = Parsing.doc(html)
        // signed already: link contains "已签到" classes or the sign btn is replaced
        val signBtn = doc.select("a[href*=\"operation=qiandao\"], a.signBtn, .sign_button, #sign_function a").firstOrNull()
        val signedText = doc.select(".sign_signed, .km_sign_s, .sign_ok, #sign_top span, .comiis_sign_box").firstOrNull()
        val signed = signedText != null && Parsing.cleanText(signedText).contains("已签")
        val days = countSignDays(doc.text())
        return CheckInState(
            signedToday = signed,
            signDays = days,
            message = Parsing.cleanText(signedText).ifEmpty { "未签到" },
        )
    }

    /**
     * k_misign returns plain text like "1|已签到|签到成功..." or a JSON-object
     * note; we only need success/fail.
     */
    fun parseAjaxResult(body: String): CheckInState {
        val t = body.trim()
        val parts = t.split("|").map { it.trim() }
        val first = parts.getOrNull(0) ?: ""
        val ok = !first.startsWith("0") && (t.contains("成功") || t.contains("签到") || t.contains("连续"))
        val days = countSignDays(t)
        return CheckInState(
            signedToday = ok,
            signDays = days,
            message = t.take(80),
        )
    }

    private fun countSignDays(text: String): Int {
        val m = Regex("(?:连续签到|已连续|连续)?\\s*(\\d+)\\s*(?:天|次)").find(text)
        if (m != null) return TextUtil.intOf(m.groupValues[1])
        return 0
    }
}