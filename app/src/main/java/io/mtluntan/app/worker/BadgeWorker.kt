package io.mtluntan.app.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.mtluntan.app.MTLuntanApp
import io.mtluntan.app.util.LogCenter
import io.mtluntan.app.util.LogCenter.LogTag
import io.mtluntan.app.util.Notifier
import java.util.concurrent.TimeUnit

/**
 * 未读消息轮询（对应 Java 版 NoticeBadgeManager）。
 *
 * 基线**按账号隔离**存在 DataStore：v3.4 修过的坑是「换号后整屏历史全变新消息」，
 * 根因就是基线跟账号无关。这里每次轮询都把基线写回当前账号自己的 key。
 *
 * 间隔 60 分钟（Java 版从 5 秒改成 60 秒才压住风控，这里保持一致）。
 */
class BadgeWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "mt_badge_poll"
    }

    override suspend fun doWork(): Result {
        val app = applicationContext as MTLuntanApp
        val account = app.auth.activeAccountName() ?: return Result.success()
        if (!app.settings.snapshotNotify()) return Result.success()

        return try {
            val counts = app.forum.badgeCounts()
            val baselineKey = "${counts.notices}:${counts.pms}"
            val previous = app.settings.badgeBaseline(account)
            app.settings.setBadgeBaseline(account, baselineKey)

            if (previous.isNotEmpty() && previous != baselineKey) {
                val prevNotices = previous.substringBefore(":").toIntOrNull() ?: 0
                val prevPms = previous.substringAfter(":").toIntOrNull() ?: 0
                val newNotices = counts.notices - prevNotices
                val newPms = counts.pms - prevPms
                if (newNotices > 0 || newPms > 0) {
                    val text = buildString {
                        if (newNotices > 0) append("$newNotices 条新通知 ")
                        if (newPms > 0) append("$newPms 条新私信")
                    }.trim()
                    Notifier.notify(
                        context = applicationContext,
                        id = 1002,
                        title = "MT论坛新消息",
                        content = text,
                        bigText = "$text\n（$account）",
                    )
                    LogCenter.log(LogTag.RUN, "新消息提醒", text)
                }
            }
            Result.success()
        } catch (t: Throwable) {
            LogCenter.fail(LogTag.RUN, "未读轮询失败", t.message.orEmpty())
            Result.success()
        }
    }
}

object BadgeScheduler {
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<BadgeWorker>(60, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            BadgeWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(BadgeWorker.WORK_NAME)
    }
}
