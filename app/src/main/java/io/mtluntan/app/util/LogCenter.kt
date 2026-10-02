package io.mtluntan.app.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 记录中心的内存日志总线（对应 Java 版 RecordCenter / PerfLog / UnlockLog）。
 *
 * 三类记录共用一条时间线，用 [LogTag] 区分，界面按标签着色：
 *  - [LogTag.UNLOCK] 进帖自动解锁的每一次决策（含跳过原因，这是 Java 版调试时最有用的东西）
 *  - [LogTag.PERF]   加载耗时（帖子 / 用户页 / 列表）
 *  - [LogTag.RUN]    运行日志（签到、隧道、AI、异常）
 *
 * 只保留最近 [CAPACITY] 条在内存里，界面刷新是同步的；需要落盘的场景（签到记录）
 * 走 Room，不在这里堆。
 */
object LogCenter {

    enum class LogTag(val label: String) {
        UNLOCK("解锁"),
        PERF("耗时"),
        RUN("运行"),
        SIGN("签到"),
        AI("AI"),
    }

    data class Entry(
        val id: Long,
        val at: Long,
        val tag: LogTag,
        val title: String,
        val detail: String,
        val ok: Boolean? = null,
    ) {
        val timeText: String
            get() = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(at))

        /** 简洁模式一行；详细模式标题 + 正文。 */
        fun render(detailMode: Boolean): String =
            if (detailMode) "$timeText [${tag.label}] $title\n$detail" else "$timeText [${tag.label}] $title"
    }

    private const val CAPACITY = 400

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    private var seq = 0L
    private var lastTag: LogTag? = null
    private var lastTitle: String? = null
    private var lastAt: Long = 0L

    fun log(
        tag: LogTag,
        title: String,
        detail: String = "",
        ok: Boolean? = null,
    ) {
        val now = System.currentTimeMillis()
        synchronized(this) {
            // 去重：3 秒内完全相同的记录不再堆（轮询类逻辑很容易刷屏）
            if (tag == lastTag && title == lastTitle && now - lastAt < 3000) return
            lastTag = tag; lastTitle = title; lastAt = now
            val entry = Entry(++seq, now, tag, title, detail, ok)
            _entries.value = (_entries.value + entry).takeLast(CAPACITY)
        }
    }

    fun ok(tag: LogTag, title: String, detail: String = "") = log(tag, title, detail, true)

    fun fail(tag: LogTag, title: String, detail: String = "") = log(tag, title, detail, false)

    fun skip(kind: String, detail: String) = log(LogTag.UNLOCK, "跳过：$kind", detail)

    fun clear() {
        synchronized(this) {
            _entries.value = emptyList()
            lastTag = null; lastTitle = null
        }
    }

    /** 供「复制全部日志」使用。 */
    fun dump(detailMode: Boolean = true): String =
        _entries.value.joinToString("\n") { it.render(detailMode) }
}
