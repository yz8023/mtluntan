package io.mtluntan.app.util

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * 渐变字（Java 版 v3.3 的 8 种预设，两色平滑插值）。
 *
 * 编辑器里选中的文字会被包成 `[color=#RRGGBB]…[/color]` 与
 * `[gradient=RRGGBB,RRGGBB]…[/gradient]`（站点支持的 Comiis 扩展标签），
 * 客户端渲染时按同一个色标画 Brush，保证「编辑器预览 = 发出去的效果 = 别人看到的」。
 */
object GradientText {

    data class Preset(val name: String, val start: Long, val end: Long)

    val presets = listOf(
        Preset("晨曦", 0xFFFF8A65, 0xFFFFD54F),
        Preset("海洋", 0xFF42A5F5, 0xFF26C6DA),
        Preset("紫霞", 0xFFAB47BC, 0xFF7E57C2),
        Preset("森林", 0xFF66BB6A, 0xFF9CCC65),
        Preset("草莓", 0xFFEC407A, 0xFFFF7043),
        Preset("夜空", 0xFF5C6BC0, 0xFF26A69A),
        Preset("黄金", 0xFFFFB300, 0xFFFFF176),
        Preset("彩虹", 0xFFFF0000, 0xFF8E24AA),
    )

    /** 站点侧标签名：`[gradient=FF0000,00FF00]文字[/gradient]` */
    const val TAG = "gradient"

    fun brush(start: Long, end: Long): Brush = Brush.horizontalGradient(listOf(Color(start), Color(end)))

    fun brush(presetIndex: Int): Brush {
        val p = presets[presetIndex.coerceIn(0, presets.size - 1)]
        return brush(p.start, p.end)
    }

    fun hex(color: Long): String = String.format("%06X", color and 0xFFFFFF)

    /** 把 hex 串解析成颜色，失败返回 null。 */
    fun parseHex(hex: String): Long? {
        val clean = hex.trim().removePrefix("#")
        if (clean.length != 6) return null
        return clean.toLongOrNull(16)?.let { 0xFF000000L or it }
    }

    /** 在 BBCode 文本里找出第一段渐变标签，用于渲染。 */
    private val GRADIENT_RE = Regex("\\[gradient=([0-9A-Fa-f]{6}),([0-9A-Fa-f]{6})](.*?)\\[/gradient]", RegexOption.DOT_MATCHES_ALL)

    class Segment(val text: String, val brush: Brush?)

    /** 把一段含渐变标签的文本切成 普通段 / 渐变段，供 Text(AnnotatedString) 渲染。 */
    fun segments(raw: String): List<Segment> {
        val out = mutableListOf<Segment>()
        var cursor = 0
        for (m in GRADIENT_RE.findAll(raw)) {
            if (m.range.first > cursor) out += Segment(raw.substring(cursor, m.range.first), null)
            val start = parseHex(m.groupValues[1]) ?: 0xFF2196F3
            val end = parseHex(m.groupValues[2]) ?: 0xFF9C27B0
            out += Segment(m.groupValues[3], brush(start, end))
            cursor = m.range.last + 1
        }
        if (cursor < raw.length) out += Segment(raw.substring(cursor), null)
        return if (out.isEmpty()) listOf(Segment(raw, null)) else out
    }

    /** 去掉渐变标签，得到纯文本（复制、摘要、AI 上下文用）。 */
    fun strip(raw: String): String = GRADIENT_RE.replace(raw) { it.groupValues[3] }
}
