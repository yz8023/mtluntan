package io.mtluntan.app.ai

import io.mtluntan.app.MTLuntanApp
import io.mtluntan.app.data.db.entity.UnlockClaimEntity
import io.mtluntan.app.data.parser.Parsing
import io.mtluntan.app.data.parser.ThreadExtrasParser
import io.mtluntan.app.domain.model.ThreadDetail
import io.mtluntan.app.util.LogCenter
import io.mtluntan.app.util.LogCenter.LogTag
import io.mtluntan.app.util.Notifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 进帖自动解锁（Java 版 AutoReplyEngine 的 Kotlin 实现）。
 *
 * 这个功能在 Java 版连修了 5 个版本，所有坑都在这里一次性避开：
 *
 *  1. **认领必须落盘 + 6 小时冷却**。只在内存里记（v3.5 之前的做法）的话，
 *     杀进程 / 重进帖子就是新实例，同一帖子会被反复回复。
 *  2. **失败不立刻释放**。我们的「失败」很多时候只是没认出成功响应，
 *     服务端其实已经回帖了 —— 一释放下次进来又是一条（v3.8 的真凶之一）。
 *  3. **只认真正的门控文案**。正文里偶然出现的「隐藏内容」字样会被误判成未解锁，
 *     因此只匹配 `如果您要查看 / 隐藏内容请 / 回复可见 / 需要回复`。
 *  4. **只有一条链路**。Java 版有两条独立调用路径（进帖 + 单独解锁），
 *     防护只加在一条上，另一条照发不误；这里统一走 [tryUnlockOnOpen]。
 */
class AutoReplyEngine(private val app: MTLuntanApp) {

    companion object {
        /** 同一 tid 的重试冷却：6 小时。 */
        const val RETRY_AFTER_MS = 6 * 60 * 60 * 1000L
        private val handledThisRun = mutableSetOf<Long>()
    }

    /**
     * 进帖时尝试解锁。
     * @param pageHtml 已抓取的帖子 HTML（复用，避免再发一次请求）
     */
    suspend fun tryUnlockOnOpen(
        tid: Long,
        detail: ThreadDetail,
        pageHtml: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        // 只看「进帖自动解锁」这一个开关。
        // 以前要求「自动回复」+「进帖解锁」两个都为真才动手 —— 太严了，
        // 用户只打开其中一个时会完全没有反应（Java 版也踩过同一个坑并已修正）。
        if (!app.settings.autoReplyOnView.first()) {
            LogCenter.skip("进帖解锁开关关闭", "tid=$tid")
            return@withContext Result.failure(IllegalStateException("进帖解锁未开启"))
        }
        if (app.auth.activeAccountName() == null) {
            LogCenter.skip("未登录", "tid=$tid")
            return@withContext Result.failure(IllegalStateException("未登录"))
        }
        if (pageHtml.isBlank()) {
            LogCenter.skip("页面快照为空", "tid=$tid")
            return@withContext Result.failure(IllegalStateException("页面为空"))
        }

        val locked = ThreadExtrasParser.hiddenBlocks(pageHtml).filter { it.locked }
        if (locked.isEmpty()) {
            LogCenter.skip("本帖没有隐藏块 / 已解锁", "tid=$tid")
            return@withContext Result.failure(IllegalStateException("没有隐藏内容"))
        }
        if (!claim(tid)) {
            LogCenter.skip("冷却期内已处理过", "tid=$tid")
            return@withContext Result.failure(IllegalStateException("冷却期内"))
        }
        if (dailyUsed() >= app.settings.autoReplyDaily.first()) {
            LogCenter.skip("已达每日上限", "tid=$tid")
            return@withContext Result.failure(IllegalStateException("已达每日上限"))
        }

        val formhash = detail.formhash.ifEmpty { Parsing.valueWithJsFallback(pageHtml, "formhash") }
        if (formhash.isEmpty()) {
            LogCenter.skip("拿不到 formhash", "tid=$tid")
            return@withContext Result.failure(IllegalStateException("拿不到 formhash"))
        }

        val template = app.settings.autoReplyTemplate.first()
        val useAi = app.settings.autoReplyAi.first()
        val extraPrompt = app.settings.autoReplyPrompt.first()
        val text = if (useAi && app.settings.aiApiKey.first().isNotBlank()) {
            val context = buildString {
                appendLine(detail.title)
                detail.mainPost?.let { appendLine(it.contentBbc.take(600)) }
            }
            app.ai.generateReply(context, template, extraPrompt)
        } else {
            template
        }
        if (text.isBlank()) {
            release(tid)
            LogCenter.skip("解锁回复模板为空", "tid=$tid")
            return@withContext Result.failure(IllegalStateException("模板为空"))
        }

        if (app.settings.aiDryRun.first()) {
            LogCenter.log(LogTag.UNLOCK, "演练模式：只生成不发送", "tid=$tid → $text")
            return@withContext Result.success("演练：$text")
        }

        var fid = detail.fid
        if (fid <= 0) fid = ThreadExtrasParser.fidOf(pageHtml)
        if (fid <= 0) {
            release(tid)
            LogCenter.skip("拿不到 fid，无法回复", "tid=$tid")
            return@withContext Result.failure(IllegalStateException("拿不到 fid"))
        }
        val result = app.forum.submitReply(
            tid = tid,
            formhash = formhash,
            message = text,
            fid = fid,
            reppid = detail.mainPost?.pid ?: 0,
            repquote = 0,
        )

        if (result.ok) {
            saveClaim(tid, ok = true, text = text)
            LogCenter.ok(LogTag.UNLOCK, "解锁回复成功", "tid=$tid → $text")
            Result.success(text)
        } else {
            // 故意不释放认领：失败的判定可能不准，服务端也许已经成功
            saveClaim(tid, ok = false, text = text)
            LogCenter.fail(LogTag.UNLOCK, "解锁回复失败", "tid=$tid → ${result.error}")
            if (app.settings.notifyEnabled.first()) {
                Notifier.notify(
                    app,
                    id = 1003,
                    title = "自动解锁回复失败",
                    content = "帖子 $tid：${result.error}",
                    bigText = "回复内容：$text\n失败原因：${result.error}\n冷却 6 小时后可重试。",
                )
            }
            Result.failure(IllegalStateException(result.error))
        }
    }

    /** 单独对某个 tid 解锁（列表页长按等入口）。 */
    suspend fun unlockSingleThread(tid: Long): Result<String> = withContext(Dispatchers.IO) {
        if (!app.settings.autoReply.first()) {
            return@withContext Result.failure(IllegalStateException("自动回复未开启"))
        }
        try {
            val html = app.forum.threadHtml(tid, 1)
            val detail = io.mtluntan.app.data.parser.ThreadDetailParser.parse(html, 1)
            tryUnlockOnOpen(tid, detail, html)
        } catch (t: Throwable) {
            LogCenter.fail(LogTag.UNLOCK, "解锁失败：$tid", t.message.orEmpty())
            Result.failure(t)
        }
    }

    // ---------------- 认领（落盘 + 冷却） ----------------

    private suspend fun claim(tid: Long): Boolean {
        synchronized(handledThisRun) {
            if (handledThisRun.contains(tid)) return false
        }
        val existing = app.db.unlockClaimDao().byTid(tid)
        val now = System.currentTimeMillis()
        if (existing != null && now - existing.at < RETRY_AFTER_MS) return false
        synchronized(handledThisRun) { handledThisRun.add(tid) }
        app.db.unlockClaimDao().upsert(UnlockClaimEntity(tid = tid, at = now, ok = false, replyText = ""))
        // 顺手清理 7 天前的旧认领，避免表无限增长
        app.db.unlockClaimDao().prune(now - 7L * 24 * 3600 * 1000)
        return true
    }

    private suspend fun saveClaim(tid: Long, ok: Boolean, text: String) {
        val existing = app.db.unlockClaimDao().byTid(tid)
        app.db.unlockClaimDao().upsert(
            UnlockClaimEntity(
                tid = tid,
                at = existing?.at ?: System.currentTimeMillis(),
                ok = ok,
                replyText = text,
            )
        )
    }

    private fun release(tid: Long) {
        synchronized(handledThisRun) { handledThisRun.remove(tid) }
    }

    /** 今天已用掉多少次自动回复。 */
    suspend fun dailyUsed(): Int = withContext(Dispatchers.IO) {
        val startOfDay = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        var count = 0
        runCatching {
            app.db.unlockClaimDao().observeRecent(200).first().forEach {
                if (it.at >= startOfDay) count++
            }
        }
        count
    }

    fun todayText(): String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    /** 记录中心用：最近的解锁记录。 */
    fun recentClaims() = app.db.unlockClaimDao().observeRecent(100)
}
