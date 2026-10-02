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

    /** 单个账号签到。流程对齐 Java 参考版 `MtSignApi.doSign`。 */
    suspend fun signOne(entity: AccountEntity): SignOutcome = withContext(Dispatchers.IO) {
        val account = entity.username
        var client = IsolatedClient(cookieRepo, account)

        // ① 签到页：一次请求同时拿 formhash / 是否已签 / 排名，也顺带验会话
        var html = fetchPage(client)
        if (html == null) {
            LogCenter.fail(LogTag.SIGN, "$account 签到失败", "签到页打不开（网络或防护）")
            return@withContext SignOutcome(ok = false, message = "签到页打不开").also { auth.recordSign(account, it) }
        }
        if (SignParser.isBlocked(html)) {
            val outcome = SignOutcome(ok = false, message = "被站点防护拦截（请求太频繁），稍后再试")
            auth.recordSign(account, outcome)
            LogCenter.fail(LogTag.SIGN, "$account 签到失败", outcome.message)
            return@withContext outcome
        }

        // ② 掉线 → 静默重登 → 重新取页
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

        // ③ 已经签过就别再提交：省一次请求，也避免被风控
        val pageState = SignParser.parsePage(html)
        if (pageState.alreadySigned) {
            LogCenter.ok(LogTag.SIGN, "$account 今日已签到", pageState.reward.ifBlank { pageState.message })
            // recordSign 会把 lastSignedAt 更新为现在 → 今天不会再签第二次
            return@withContext pageState.copy(ok = true, message = "今日已签到").also { auth.recordSign(account, it) }
        }

        val formhash = SignParser.formhashOf(html)
        if (formhash.isEmpty()) {
            val outcome = SignOutcome(ok = false, message = "未找到签到入口（页面结构可能已变）")
            auth.recordSign(account, outcome)
            LogCenter.fail(LogTag.SIGN, "$account 签到失败", outcome.message)
            return@withContext outcome
        }

        // ④ 提交签到（format=text 返回最干净），失败再用伪静态按钮接口兜底
        var raw = try {
            client.get(ApiUris.signAction(formhash), ajax = true, referer = ApiUris.signPage())
        } catch (t: Throwable) {
            ""
        }
        var text = SignParser.parseSignResponse(raw)
        if (text.isEmpty()) {
            val raw2 = try {
                client.get(ApiUris.signActionButton(formhash), referer = ApiUris.signPage())
            } catch (t: Throwable) {
                ""
            }
            if (raw2.isNotBlank()) {
                raw = raw2
                text = SignParser.parseSignResponse(raw2)
            }
        }

        // ⑤ 回读签到页：以页面真实状态为准（接口文案各版本差异太大）
        val after = fetchPage(client) ?: html
        var outcome = SignParser.combine(text, after, raw)

        // 接口返回是整页 HTML（formhash 过期等）时，上面 combine 会用页面状态兜住；
        // 这里再补一次：接口完全没信息、页面也没变 → 明确报失败而不是假成功
        if (!outcome.ok && !outcome.alreadySigned && text.isEmpty() && !SignParser.isAlreadySigned(after)) {
            outcome = outcome.copy(message = outcome.message.ifBlank { "签到请求没有返回结果，请稍后重试" })
        }

        if (outcome.ok || outcome.alreadySigned) {
            LogCenter.ok(
                LogTag.SIGN,
                "$account ${if (outcome.alreadySigned && !outcome.ok) "今日已签" else "签到成功"}",
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

    private suspend fun fetchPage(client: IsolatedClient): String? {
        // 论坛偶发 unexpected end of stream：这些请求全是幂等的，直接退避重试
        repeat(2) { attempt ->
            try {
                return client.get(ApiUris.signPage())
            } catch (t: Throwable) {
                if (attempt == 0) delay(1200)
            }
        }
        // 伪静态页被拦 / 404 时回退插件入口
        return try {
            client.get(ApiUris.signPagePlugin())
        } catch (t: Throwable) {
            null
        }
    }

    /**
     * 「打开 App / 进我的页」时的自动签到：**真正的防重复**。
     *
     * 三重判断，任何一重命中就直接跳过，绝不提交第二次：
     *   1. 本地签到记录里今天已经成功过（sign_records）；
     *   2. 账号自己的 lastSignedAt 就是今天；
     *   3. 签到页面上写着「今日已签」（signOne 第 ③ 步，页面为准）。
     *
     * 同一个进程里还有 10 分钟节流，避免频繁切换页面时反复请求（风控风险）。
     */
    suspend fun autoSignOnOpen(force: Boolean = false): Summary? = withContext(Dispatchers.IO) {
        if (!settings.snapshotAutoSign()) return@withContext null
        val now = System.currentTimeMillis()
        if (!force && now - lastAutoRun < AUTO_RUN_INTERVAL_MS) {
            LogCenter.skip("自动签到节流", "距上次不足 ${AUTO_RUN_INTERVAL_MS / 60000} 分钟")
            return@withContext null
        }
        lastAutoRun = now

        val accounts = db.accountDao().enabledAccounts()
        if (accounts.isEmpty()) return@withContext Summary(0, 0, 0, emptyList())

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val pending = accounts.filter { acc ->
            val recorded = db.signRecordDao().todayForAccount(acc.username, today)?.ok == true
            val stamped = acc.lastSignedAt > 0 &&
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(acc.lastSignedAt)) == today
            if (recorded || stamped) {
                LogCenter.skip("自动签到跳过", "${acc.username} 今日已签到")
                false
            } else true
        }
        if (pending.isEmpty()) {
            LogCenter.log(LogTag.SIGN, "自动签到跳过", "全部账号今日已签到")
            return@withContext Summary(accounts.size, 0, accounts.size, emptyList())
        }

        val spacingMs = settings.snapshotSpacing() * 1000L
        var ok = 0
        var already = 0
        val failed = mutableListOf<Pair<String, String>>()
        pending.forEachIndexed { index, entity ->
            if (index > 0 && spacingMs > 0) delay(spacingMs)
            val outcome = signOne(entity)
            when {
                outcome.alreadySigned -> already++
                outcome.ok -> ok++
                else -> failed += entity.username to outcome.message.ifEmpty { "失败" }
            }
        }
        val summary = Summary(pending.size, ok, already, failed)
        settings.setLastSignRun(System.currentTimeMillis(), summary.describe())
        LogCenter.log(LogTag.SIGN, "打开 App 自动签到", summary.detailLines(), ok = failed.isEmpty())
        if (settings.snapshotNotify() && (ok > 0 || failed.isNotEmpty())) {
            Notifier.notify(
                context = appContext,
                title = if (failed.isEmpty()) "自动签到完成" else "自动签到有失败",
                content = summary.describe(),
                bigText = summary.detailLines(),
            )
        }
        summary
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

    companion object {
        /** 同一进程内自动签到的最小间隔，防止页面来回切时反复请求。 */
        private const val AUTO_RUN_INTERVAL_MS = 10 * 60 * 1000L

        @Volatile
        private var lastAutoRun = 0L
    }

    suspend fun todayRecord(account: String): Boolean = withContext(Dispatchers.IO) {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        db.signRecordDao().todayForAccount(account, date)?.ok == true
    }
}
