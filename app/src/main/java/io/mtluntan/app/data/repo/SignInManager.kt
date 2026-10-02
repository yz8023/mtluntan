package io.mtluntan.app.data.repo

import io.mtluntan.app.data.db.AppDatabase
import io.mtluntan.app.data.db.entity.AccountEntity
import io.mtluntan.app.data.local.AppSettings
import io.mtluntan.app.data.network.ApiUris
import io.mtluntan.app.data.network.CookieRepository
import io.mtluntan.app.data.network.IsolatedClient
import io.mtluntan.app.data.parser.SignParser
import io.mtluntan.app.domain.model.SignOutcome
import io.mtluntan.app.util.LogCenter
import io.mtluntan.app.util.LogCenter.LogTag
import io.mtluntan.app.util.Notifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 多账号签到（对应 Java 版 MultiSignInManager / AutoSignInManager）。
 *
 * 几个必须保持的行为：
 *  - **隔离会话**：每个账号用 [IsolatedClient]，绝不碰前台账号的 Cookie
 *  - **账号间隔**：默认 5 秒，避免站点按 IP 限流直接 403
 *  - **掉线先修**：签不动的第一嫌疑永远是会话失效，先用托管密码静默重登再签
 *  - **签到成功与否必须有第二判据**：ajax 返回没认出来时，回签到页看「已签到」文案
 */
class SignInManager(
    /** 用 applicationContext，只用于发通知，不会泄漏 Activity。 */
    private val appContext: android.content.Context,
    private val db: AppDatabase,
    private val cookieRepo: CookieRepository,
    private val settings: AppSettings,
    private val auth: AuthRepository,
) {

    data class Summary(
        val total: Int = 0,
        val ok: Int = 0,
        val already: Int = 0,
        val failed: List<Pair<String, String>> = emptyList(),
        val at: Long = System.currentTimeMillis(),
    ) {
        fun describe(): String = buildString {
            append("共 $total 个账号：成功 $ok")
            if (already > 0) append("，今日已签 $already")
            if (failed.isNotEmpty()) append("，失败 ${failed.size}（${failed.joinToString("、") { it.first }}）")
        }

        fun detailLines(): String = buildString {
            appendLine(describe())
            failed.forEach { (name, reason) -> appendLine("· $name：$reason") }
        }
    }

    /** 单个账号签到。 */
    suspend fun signOne(entity: AccountEntity): SignOutcome = withContext(Dispatchers.IO) {
        val account = entity.username
        var client = IsolatedClient(cookieRepo, account)

        var html = fetchPage(client)
        if (html == null) {
            LogCenter.fail(LogTag.SIGN, "$account 签到失败", "签到页打不开")
            return@withContext SignOutcome(ok = false, message = "签到页打不开").also { auth.recordSign(account, it) }
        }

        // 掉线 → 静默重登 → 重新取页
        if (SignParser.looksLoggedOut(html)) {
            LogCenter.skip("未登录", "$account 尝试用托管密码重登")
            val password = auth.passwordFor(account)
            val status = SessionGuard(cookieRepo).verify(account, password, repair = password.isNotEmpty())
            if (!status.loggedIn) {
                val outcome = SignOutcome(ok = false, loggedOut = true, message = status.message.ifEmpty { "需要重新登录" })
                auth.markExpired(account, true)
                auth.recordSign(account, outcome)
                LogCenter.fail(LogTag.SIGN, "$account 签到失败", outcome.message)
                return@withContext outcome
            }
            auth.refreshCookieSnapshot(account)
            client = IsolatedClient(cookieRepo, account)
            html = fetchPage(client) ?: html
        }

        val pageState = SignParser.parsePage(html)
        if (pageState.alreadySigned) {
            LogCenter.ok(LogTag.SIGN, "$account 今日已签到", pageState.reward)
            return@withContext pageState.copy(ok = true).also { auth.recordSign(account, it) }
        }

        val formhash = SignParser.formhashOf(html)
        if (formhash.isEmpty()) {
            val outcome = SignOutcome(ok = false, message = "未找到签到入口（页面结构可能已变）")
            auth.recordSign(account, outcome)
            LogCenter.fail(LogTag.SIGN, "$account 签到失败", outcome.message)
            return@withContext outcome
        }

        val body = try {
            client.get(ApiUris.signAction(formhash), ajax = true, referer = ApiUris.signPage())
        } catch (t: Throwable) {
            null
        }
        var outcome = body?.let { SignParser.parseAjaxResult(it) } ?: SignOutcome(ok = false, message = "签到请求失败")

        // 第二判据：ajax 没认出来就回页面核对（Java 版「失败其实已成功」的坑）
        if (!outcome.ok && !outcome.alreadySigned) {
            val verifyHtml = fetchPage(client)
            if (verifyHtml != null) {
                val verify = SignParser.parsePage(verifyHtml)
                if (verify.alreadySigned) {
                    outcome = verify.copy(ok = true, message = "已签到（页面核对）")
                }
            }
        }

        if (outcome.ok || outcome.alreadySigned) {
            LogCenter.ok(
                LogTag.SIGN,
                "$account ${if (outcome.alreadySigned) "今日已签" else "签到成功"}",
                buildString {
                    if (outcome.reward.isNotEmpty()) append("奖励 ${outcome.reward} ")
                    if (outcome.rank > 0) append("排名 ${outcome.rank} ")
                    if (outcome.days > 0) append("连续 ${outcome.days} 天")
                }.trim(),
            )
        } else {
            LogCenter.fail(LogTag.SIGN, "$account 签到失败", outcome.message)
        }
        auth.recordSign(account, outcome)
        outcome
    }

    private suspend fun fetchPage(client: IsolatedClient): String? = try {
        client.get(ApiUris.signPage())
    } catch (t: Throwable) {
        // 论坛偶发 unexpected end of stream：退避后重试一次
        delay(1200)
        try {
            client.get(ApiUris.signPage())
        } catch (t2: Throwable) {
            null
        }
    }

    /** 一键全部签到（只跑 enabled 的账号）。 */
    suspend fun signAll(notify: Boolean = true): Summary = withContext(Dispatchers.IO) {
        val accounts = db.accountDao().enabledAccounts()
        val spacingMs = settings.snapshotSpacing() * 1000L
        var ok = 0
        var already = 0
        val failed = mutableListOf<Pair<String, String>>()

        accounts.forEachIndexed { index, entity ->
            if (index > 0 && spacingMs > 0) delay(spacingMs)
            val outcome = signOne(entity)
            when {
                outcome.alreadySigned -> already++
                outcome.ok -> ok++
                else -> failed += entity.username to outcome.message.ifEmpty { "失败" }
            }
        }

        val summary = Summary(accounts.size, ok, already, failed)
        settings.setLastSignRun(System.currentTimeMillis(), summary.describe())
        LogCenter.log(LogTag.SIGN, "批量签到完成", summary.detailLines(), ok = failed.isEmpty())

        if (notify && settings.snapshotNotify()) {
            Notifier.notify(
                context = appContext,
                title = if (failed.isEmpty()) "签到完成" else "签到有失败",
                content = summary.describe(),
                bigText = summary.detailLines(),
            )
        }
        summary
    }

    /** 当前活动账号单独签到（「我的」页手动点）。 */
    suspend fun signActive(): SignOutcome? = withContext(Dispatchers.IO) {
        val name = auth.activeAccountName() ?: return@withContext null
        val entity = db.accountDao().byUsername(name) ?: return@withContext null
        signOne(entity)
    }

    suspend fun todayRecord(account: String): Boolean = withContext(Dispatchers.IO) {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        db.signRecordDao().todayForAccount(account, date)?.ok == true
    }
}
