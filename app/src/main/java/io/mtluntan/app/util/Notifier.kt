package io.mtluntan.app.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.mtluntan.app.MainActivity
import io.mtluntan.app.R

/** 统一的通知出口：一个渠道、一个可展开的汇总通知（Java 版 AutoSignInManager 同款行为）。 */
object Notifier {

    private const val CHANNEL_ID = "mtluntan_default"
    private const val CHANNEL_NAME = "账号与签到"
    const val ID_SUMMARY = 1001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "多账号签到结果、消息提醒与后台任务"
            }
        )
    }

    fun canNotify(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    fun notify(
        context: Context,
        id: Int = ID_SUMMARY,
        title: String,
        content: String,
        bigText: String? = null,
        autoCancel: Boolean = true,
    ) {
        if (!canNotify(context)) return
        ensureChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText ?: content))
            .setContentIntent(pending)
            .setAutoCancel(autoCancel)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        try {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        } catch (t: Throwable) {
            LogCenter.fail(LogCenter.LogTag.RUN, "通知发送失败", t.message.orEmpty())
        }
    }

    fun cancel(context: Context, id: Int = ID_SUMMARY) {
        NotificationManagerCompat.from(context).cancel(id)
    }
}
