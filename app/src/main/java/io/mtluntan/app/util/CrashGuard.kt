package io.mtluntan.app.util

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩溃记录（排查「评论区点击闪退」这类只在真机上复现的问题）。
 *
 * release 包做了 R8 混淆，第三方库的帧名会被压成 `l0.r.t` 这种看不出所以然的样子。
 * 这里在崩溃瞬间把**完整堆栈 + 线程 + 时间 + 版本**写到 `filesDir/crash.log`，
 * 设置页「崩溃日志」可以直接看/复制；配合发布包里附带的 `mapping.txt`
 * 就能把混淆帧还原成真实的类和方法。
 *
 * 只写文件、不拦截（写完继续交给系统默认处理器，保持系统原生的崩溃提示）。
 */
object CrashGuard {

    private const val FILE_NAME = "crash.log"
    private const val MAX_CHARS = 64 * 1024

    /** 进程里只能装一次。 */
    @Volatile
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { record(app, thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun record(context: Context, thread: Thread, error: Throwable) {
        val when0 = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val sb = StringBuilder()
        sb.append("===== ").append(when0).append(" =====").append('\n')
        sb.append("thread: ").append(thread.name).append('\n')
        sb.append("version: ").append(AppBuildInfo.describe(context)).append('\n')
        sb.append("fatal: ").append(error.javaClass.name).append(": ").append(error.message.orEmpty()).append('\n')
        sb.append(error.stackTraceToString())
        // 常见情况：真正的起因挂在 cause 上
        var cause = error.cause
        var depth = 0
        while (cause != null && depth < 4) {
            sb.append("\ncaused by: ").append(cause.javaClass.name).append(": ").append(cause.message.orEmpty()).append('\n')
            sb.append(cause.stackTraceToString())
            cause = cause.cause
            depth++
        }
        // 运行日志的最后几行：崩溃前在干什么（解锁/签到/切号都能对上）
        runCatching {
            val tail = LogCenter.entries.value.takeLast(20).joinToString("\n") { entry ->
                "#${entry.id} ${entry.tag} ${entry.title}${if (entry.detail.isBlank()) "" else " — ${entry.detail}"}"
            }
            if (tail.isNotBlank()) sb.append("\n--- 运行日志（尾 20 条） ---\n").append(tail)
        }
        sb.append("\n\n")

        val file = File(context.filesDir, FILE_NAME)
        val old = runCatching { if (file.exists()) file.readText() else "" }.getOrDefault("")
        val merged = (sb.toString() + old).take(MAX_CHARS)
        runCatching { file.writeText(merged) }
    }

    /** 读最近一次崩溃记录（没有就返回空串）。 */
    fun read(context: Context): String =
        runCatching { File(context.filesDir, FILE_NAME).takeIf { it.exists() }?.readText().orEmpty() }
            .getOrDefault("")

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE_NAME).delete() }
    }

    fun hasCrash(context: Context): Boolean =
        runCatching { File(context.filesDir, FILE_NAME).let { it.exists() && it.length() > 0 } }.getOrDefault(false)
}

/** 版本信息（崩溃记录里要有，才能对上发布的 mapping）。 */
private object AppBuildInfo {
    fun describe(context: Context): String = runCatching {
        val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
        val code = if (android.os.Build.VERSION.SDK_INT >= 28) pkg.longVersionCode else pkg.versionCode.toLong()
        "${context.packageName} ${pkg.versionName} ($code)"
    }.getOrDefault("unknown")
}
