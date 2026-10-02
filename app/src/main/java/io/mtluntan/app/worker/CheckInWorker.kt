package io.mtluntan.app.worker

import android.content.Context
import android.util.Log
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
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * 每日定时签到（全部启用账号）。
 *
 * WorkManager 的周期任务最小间隔是 15 分钟，没法「每天 08:30 准点」；
 * 标准做法是 **24 小时周期 + 首次 initialDelay 对齐到目标时刻**，
 * 之后每次执行完自动顺延 24h，长期偏差在分钟级以内。
 */
class CheckInWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "mt_daily_sign"
        private const val TAG = "CheckInWorker"
    }

    override suspend fun doWork(): Result {
        val app = applicationContext as MTLuntanApp
        if (!app.settings.snapshotAutoSign()) {
            LogCenter.log(LogTag.SIGN, "定时签到已关闭，跳过")
            return Result.success()
        }
        return try {
            val summary = app.sign.signAll(notify = true)
            Log.d(TAG, summary.describe())
            Result.success()
        } catch (t: Throwable) {
            LogCenter.fail(LogTag.SIGN, "定时签到异常", t.message.orEmpty())
            Result.retry()
        }
    }
}

/** 定时签到的排程与立即执行。 */
object SignScheduler {

    /** 按设置里的时刻排定每日签到（默认 08:30）。 */
    fun schedule(context: Context, hour: Int, minute: Int) {
        val delayMs = millisUntilNext(hour, minute)
        val request = PeriodicWorkRequestBuilder<CheckInWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            CheckInWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
        LogCenter.log(
            LogTag.SIGN,
            "已排定每日签到 ${"%02d".format(hour)}:${"%02d".format(minute)}",
            "距首次执行约 ${delayMs / 60000} 分钟",
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(CheckInWorker.WORK_NAME)
        LogCenter.log(LogTag.SIGN, "已取消定时签到")
    }

    /** 距离下一个 hh:mm 还有多少毫秒。 */
    fun millisUntilNext(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, minute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (target.timeInMillis <= now.timeInMillis) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }
        return target.timeInMillis - now.timeInMillis
    }
}
