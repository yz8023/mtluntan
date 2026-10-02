package io.mtluntan.app.util

/**
 * 加载耗时埋点（Java 版 v3.3 引入的 PerfLog）。
 *
 * 用户说「感觉慢」时，能立刻分清慢在网络、解析还是渲染：
 *  - `Net.get` 里记网络耗时与「本次请求 N 个」
 *  - 解析器记解析耗时
 *  - 界面首次组合完成记渲染耗时
 */
object PerfLog {

    data class Span(
        val name: String,
        val netMs: Long = 0,
        val parseMs: Long = 0,
        val renderMs: Long = 0,
        val extra: String = "",
    ) {
        val totalMs: Long get() = netMs + parseMs + renderMs
    }

    private val spans = ArrayDeque<Span>(60)

    fun record(span: Span) {
        synchronized(this) {
            spans.addLast(span)
            while (spans.size > 60) spans.removeFirst()
        }
        LogCenter.log(
            LogCenter.LogTag.PERF,
            "${span.name} 共 ${span.totalMs}ms",
            buildString {
                append("网络 ${span.netMs}ms · 解析 ${span.parseMs}ms")
                if (span.renderMs > 0) append(" · 渲染 ${span.renderMs}ms")
                if (span.extra.isNotEmpty()) append(" · ${span.extra}")
            },
        )
    }

    fun snapshot(): List<Span> = synchronized(this) { spans.toList() }
}
