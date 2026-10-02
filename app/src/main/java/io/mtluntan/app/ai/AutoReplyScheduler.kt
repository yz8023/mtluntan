package io.mtluntan.app.ai

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.mtluntan.app.MTLuntanApp
import io.mtluntan.app.data.parser.ThreadExtrasParser
import io.mtluntan.app.util.LogCenter
import io.mtluntan.app.util.LogCenter.LogTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * 后台扫帖自动解锁（Java 版 AutoReplyScheduler）。
 *
 * 只在「自动回复开 + 已登录」时工作，单次最多处理 [MAX_PER_RUN] 个帖子，
 * 每个之间间隔 [SPACING_MS]，每日总量仍受设置里的上限约束 —— 论坛的风控是
 * 按 IP 计数的，批量回复最容易踩线。
 */
class AutoReplyWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "mt_auto_reply_scan"
        private const val MAX_PER_RUN = 3
        private const val SPACING_MS = 20_000L
    }

    override suspend fun doWork(): Result {
        val app = applicationContext as MTLuntanApp
        if (!app.settings.autoReply.first()) return Result.success()
        if (app.auth.activeAccountName() == null) return Result.success()

        return withContext(Dispatchers.IO) {
            try {
                val candidates = findCandidates(app)
                if (candidates.isEmpty()) {
                    LogCenter.log(LogTag.UNLOCK, "后台扫帖：没有发现隐藏帖")
                    return@withContext Result.success()
                }
                var done = 0
                for (tid in candidates.take(MAX_PER_RUN)) {
                    val remaining = app.settings.autoReplyDaily.first() - app.autoReply.dailyUsed()
                    if (remaining <= 0) {
                        LogCenter.log(LogTag.UNLOCK, "后台扫帖：已达每日上限", "剩余 0")
                        break
                    }
                    if (done > 0) delay(SPACING_MS)
                    app.autoReply.unlockSingleThread(tid)
                    done++
                }
                LogCenter.log(LogTag.UNLOCK, "后台扫帖完成", "处理 $done 个帖子")
                Result.success()
            } catch (t: Throwable) {
                LogCenter.fail(LogTag.UNLOCK, "后台扫帖异常", t.message.orEmpty())
                Result.success()
            }
        }
    }

    /** 从导读 + 社区第一页里找带隐藏标记的帖子。 */
    private suspend fun findCandidates(app: MTLuntanApp): List<Long> {
        val tids = linkedSetOf<Long>()
        runCatching {
            app.forum.guide("newthread", 1).filter { it.hasHiddenContent }.forEach { tids += it.threadId }
        }
        runCatching {
            val cats = app.forum.forumIndex()
            cats.firstOrNull()?.forums?.take(3)?.forEach { forum ->
                app.forum.forumThreads(forum.id, 1).filter { it.hasHiddenContent }.forEach { tids += it.threadId }
            }
        }
        // 二次确认：确实还有锁定的隐藏块才算候选
        return tids.filter { tid ->
            runCatching {
                val html = app.forum.threadHtml(tid, 1)
                ThreadExtrasParser.hiddenBlocks(html).any { it.locked }
            }.getOrDefault(false)
        }
    }
}

object AutoReplyScheduler {

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<AutoReplyWorker>(6, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            AutoReplyWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(AutoReplyWorker.WORK_NAME)
    }
}
