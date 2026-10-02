package io.mtluntan.app.data.repo

import io.mtluntan.app.data.network.ApiUris
import io.mtluntan.app.data.network.CookieRepository
import io.mtluntan.app.data.network.IsolatedClient
import io.mtluntan.app.data.network.Site
import io.mtluntan.app.data.parser.Parsing
import io.mtluntan.app.data.parser.SignParser
import io.mtluntan.app.util.LogCenter
import io.mtluntan.app.util.LogCenter.LogTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 会话守卫（对应 Java 版 SessionGuard / HeadlessSiteVerifier）。
 *
 * 掉线判定不看本地 Cookie 是否存在 —— 本地常有**过期**的 `_auth`，
 * 那正是 v4.1 修过的坑：本地看着「登录态正常」，自动重登根本不执行。
 * 真正的判定是**拿签到页去问服务端**：Discuz 只给已认证会话输出 formhash。
 */
class SessionGuard(private val cookieRepo: CookieRepository) {

    data class Status(
        val loggedIn: Boolean,
        val nickname: String = "",
        val message: String = "",
        val repaired: Boolean = false,
    )

    /** 用签到页验证目标账号会话；未登录时若有托管密码则静默重登。 */
    suspend fun verify(account: String, password: String?, repair: Boolean = true): Status =
        withContext(Dispatchers.IO) {
            val client = IsolatedClient(cookieRepo, account)
            val first = probe(client)
            if (first.loggedIn) return@withContext first

            if (!repair) return@withContext first
            if (password.isNullOrEmpty()) {
                LogCenter.fail(LogTag.RUN, "掉线：$account", "没有托管密码，无法自动重登")
                return@withContext first.copy(message = "会话已失效，且未保存密码")
            }

            val login = login(client, account, password)
            if (!login.ok) {
                LogCenter.fail(LogTag.RUN, "重登失败：$account", login.message)
                return@withContext first.copy(message = login.message)
            }
            val second = probe(client)
            LogCenter.log(
                LogTag.RUN,
                if (second.loggedIn) "重登成功：$account" else "重登后仍未通过校验：$account",
                second.message,
                ok = second.loggedIn,
            )
            second.copy(repaired = second.loggedIn, nickname = second.nickname)
        }

    /** 只探测，不改动任何状态。 */
    suspend fun probe(client: IsolatedClient): Status {
        val html = try {
            client.get(ApiUris.signPage())
        } catch (t: Throwable) {
            return Status(false, message = t.message ?: "网络错误")
        }
        if (SignParser.looksChallenge(html)) {
            return Status(false, message = "站点验证未通过")
        }
        if (SignParser.looksLoggedOut(html)) {
            return Status(false, message = "未登录")
        }
        val nickname = Parsing.text(
            Parsing.doc(html).select(".comiis_yhname, .km_username, #um a[href*=space], .mt_username").firstOrNull()
        )
        return Status(true, nickname = nickname)
    }

    data class LoginResult(val ok: Boolean, val message: String = "")

    /**
     * Discuz 账号密码登录（移动版）：
     * 1. 打开登录页拿 loginhash / formhash
     * 2. POST loginsubmit=yes
     * 3. 回签到页校验 formhash 是否出现（唯一可信的成功判据）
     */
    suspend fun login(client: IsolatedClient, username: String, password: String): LoginResult {
        val loginUrl = ApiUris.loginPageRaw()
        val page = try {
            client.get(loginUrl)
        } catch (t: Throwable) {
            return LoginResult(false, "登录页打不开: ${t.message}")
        }
        if (SignParser.looksChallenge(page)) return LoginResult(false, "站点验证未通过")

        val doc = Parsing.doc(page)
        val loginhash = Regex("loginhash=([A-Za-z0-9]+)").find(page)?.groupValues?.getOrNull(1)
            ?: doc.selectFirst("input[name=loginhash]")?.attr("value").orEmpty()
        val formhash = Parsing.valueWithJsFallback(page, "formhash")

        val submitUrl = buildString {
            append("${Site.baseUrl}/member.php?mod=logging&action=login&loginsubmit=yes&handlekey=login&mobile=2")
            if (loginhash.isNotEmpty()) append("&loginhash=$loginhash")
        }
        val params = linkedMapOf(
            "formhash" to formhash,
            "referer" to Site.baseUrl + "/",
            "username" to username,
            "password" to password,
            "questionid" to "0",
            "answer" to "",
            "cookietime" to (30 * 24 * 3600).toString(),
        )
        val result = try {
            client.postForm(submitUrl, params, referer = loginUrl)
        } catch (t: Throwable) {
            return LoginResult(false, "登录请求失败: ${t.message}")
        }
        if (result.contains("密码错误") || result.contains("用户名无效") || result.contains("登录失败")) {
            val msg = Parsing.cleanText(Parsing.doc(result).select("#messagetext, .alert_error").firstOrNull())
            return LoginResult(false, msg.ifEmpty { "账号或密码不正确" })
        }
        return LoginResult(true, "已提交登录")
    }
}
