package io.mtluntan.app.util

/** Text cleaning helpers for parsing forum HTML/BBCode. */
object TextUtil {

    private val zeroWidthPattern by lazy {
        Regex("[\u200b-\u200f\u2060-\u2064\ufeff]")
    }
    private val controlPattern by lazy {
        Regex("[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]")
    }
    private val iconPattern by lazy { Regex("[\ue000-\uf8ff]") }

    /** Removes invisible characters, control chars and icon font glyphs. */
    fun sanitize(text: String?): String {
        if (text.isNullOrEmpty()) return ""
        var t = text
        t = t.replace("\u00a0", " ")
        t = zeroWidthPattern.replace(t, "")
        t = controlPattern.replace(t, "")
        t = iconPattern.replace(t, "")
        t = t.replace(Regex("[ \t]+"), " ")
        return t.trim()
    }

    /** Collapses all whitespace runs to a single space. */
    fun collapse(t: String): String = Regex("\\s+").replace(t, " ")

    fun intOf(text: String?): Int {
        if (text.isNullOrEmpty()) return 0
        val digits = Regex("-?\\d+").findAll(text)
        val sb = StringBuilder()
        for (d in digits) sb.append(d.value)
        return if (sb.isEmpty()) 0 else sb.toString().toIntOrNull() ?: 0
    }

    fun longOf(text: String?): Long {
        if (text.isNullOrEmpty()) return 0L
        val digits = Regex("\\d+").findAll(text).map { it.value }.joinToString("")
        return if (digits.isEmpty()) 0L else digits.toLongOrNull() ?: 0L
    }

    /** Clips text to max n chars, appends … when truncated. */
    fun clip(text: String, max: Int): String =
        if (text.length <= max) text else text.take(max) + "…"

    /** "2026-10-01 12:33" style Discuz time → relative display. */
    fun formatTime(t: String): String = t.trim()

    fun htmlUnescape(text: String): String {
        var out = text.replace("&nbsp;", " ")
        out = out.replace("&lt;", "<").replace("&gt;", ">")
        out = out.replace("&quot;", "\"").replace("&amp;", "&")
        out = out.replace("&#39;", "'")
        // numeric entities
        out = Regex("&#(\\d+);").replace(out) { m ->
            m.groupValues[1].toIntOrNull()?.let { Char(it).toString() } ?: m.value
        }
        return out
    }
}