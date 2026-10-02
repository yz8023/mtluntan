package io.mtluntan.app.data.parser

/**
 * BBCode → 结构化块。纯 Kotlin，不依赖 Android，方便 JVM 单测。
 *
 * 站点正文里会混着这几类东西，界面必须区别对待：
 *  - `[code]` 代码块：要能折叠 + 一键复制（Java 版最早就是正则抽出来的，
 *    还需要「显示与复制解耦」，行号不能进剪贴板）
 *  - `[quote]` 引用块：折叠 + 复制，「只复制引用」是常用操作
 *  - `[img]` 图片：原位显示，可点开大图
 *  - `[hide]` 隐藏块：锁定态显示「回复可见」，解锁后直接显示明文
 *  - `[attach]` / `[free]` 附件
 */
sealed class BbcBlock {
    /** 普通文本（内含行内 BBCode，交给 UI 层做 AnnotatedString）。 */
    data class Text(val raw: String) : BbcBlock()

    data class Code(val lang: String, val code: String) : BbcBlock()

    data class Quote(val text: String, val raw: String) : BbcBlock()

    data class Image(val url: String) : BbcBlock()

    data class Hide(val text: String, val locked: Boolean) : BbcBlock()

    data class Attachment(val label: String, val url: String) : BbcBlock()

    data class Free(val text: String) : BbcBlock()
}

object BbcBlocks {

    private val OPENERS = listOf(
        "code" to "[/code]",
        "quote" to "[/quote]",
        "hide" to "[/hide]",
        "free" to "[/free]",
        "img" to "[/img]",
        "attach" to "[/attach]",
        "media" to "[/media]",
    )

    /** 门控文案：只有这些才算「还没解锁」。 */
    private val LOCK_HINTS = listOf("如果您要查看", "隐藏内容请", "回复可见", "需要回复", "回复才可")

    fun parse(raw: String, forceUnlocked: Boolean = false): List<BbcBlock> {
        if (raw.isBlank()) return emptyList()
        val out = mutableListOf<BbcBlock>()
        var i = 0
        val sb = StringBuilder()

        fun flushText() {
            if (sb.isNotEmpty()) {
                out += BbcBlock.Text(sb.toString())
                sb.clear()
            }
        }

        while (i < raw.length) {
            // 找出最早出现的开标签
            var bestTag: String? = null
            var bestIdx = -1
            for ((tag, _) in OPENERS) {
                val idx = indexOfTag(raw, tag, i)
                if (idx >= 0 && (bestIdx < 0 || idx < bestIdx)) {
                    bestIdx = idx
                    bestTag = tag
                }
            }
            if (bestTag == null || bestIdx < 0) {
                sb.append(raw, i, raw.length)
                break
            }
            if (bestIdx > i) sb.append(raw, i, bestIdx)

            val closer = OPENERS.first { it.first == bestTag }.second
            val openEnd = raw.indexOf(']', bestIdx)
            if (openEnd < 0) {
                sb.append(raw, bestIdx, raw.length)
                break
            }
            val header = raw.substring(bestIdx + 1, openEnd)      // 例如 code=java
            val bodyStart = openEnd + 1
            val bodyEnd = raw.indexOf(closer, bodyStart, ignoreCase = true)

            val body = if (bodyEnd >= 0) raw.substring(bodyStart, bodyEnd) else raw.substring(bodyStart)
            i = if (bodyEnd >= 0) bodyEnd + closer.length else raw.length

            when (bestTag) {
                "code" -> {
                    flushText()
                    val lang = header.substringAfter('=', "").trim()
                    out += BbcBlock.Code(lang = lang, code = cleanCode(body))
                }
                "quote" -> {
                    flushText()
                    out += BbcBlock.Quote(text = stripTags(body).trim(), raw = body.trim())
                }
                "hide" -> {
                    flushText()
                    val text = stripTags(body).trim()
                    val locked = !forceUnlocked && isLockedText(text)
                    out += BbcBlock.Hide(text = text, locked = locked)
                }
                "free" -> {
                    flushText()
                    out += BbcBlock.Free(text = stripTags(body).trim())
                }
                "img" -> {
                    val url = body.trim()
                    if (url.startsWith("http") || url.startsWith("/")) {
                        flushText()
                        out += BbcBlock.Image(absUrl(url))
                    } else {
                        sb.append(body)
                    }
                }
                "attach" -> {
                    flushText()
                    val id = body.trim()
                    out += BbcBlock.Attachment(label = "附件 $id", url = "")
                }
                "media" -> {
                    flushText()
                    out += BbcBlock.Text("🎬 " + body.trim())
                }
            }
        }
        flushText()
        return out
    }

    /** 隐藏块是否仍是锁定态（与 ThreadExtrasParser 同一套判定）。 */
    fun isLockedText(text: String): Boolean {
        if (text.isBlank()) return false
        return LOCK_HINTS.any { text.contains(it) }
    }

    /** 代码块：清掉行号（站点会把行号拼进正文，复制时就带出去了 —— v2.5 修过的坑）。 */
    private fun cleanCode(body: String): String {
        var code = body.replace("\r\n", "\n").replace("\r", "\n")
        code = code.replace(Regex("^\\s*\\d+[.、]?\\s?\\n", RegexOption.MULTILINE), { "\n" }).trim('\n')
        // 去掉行首形如 "1 " / "12 " 的行号
        val lines = code.split('\n')
        val numbered = lines.count { it.trimStart().matches(Regex("^\\d+[.、]?\\s+.*")) || it.trim().matches(Regex("^\\d+$")) }
        if (lines.size > 2 && numbered >= lines.size - 1) {
            code = lines.joinToString("\n") { it.replaceFirst(Regex("^\\s*\\d+[.、]?\\s?"), "") }
        }
        return code.trim('\n')
    }

    /** 找开标签位置，跳过 `[/xx]` 这种闭标签。 */
    private fun indexOfTag(raw: String, tag: String, from: Int): Int {
        var idx = raw.indexOf("[$tag", from, ignoreCase = true)
        while (idx >= 0) {
            if (idx == 0 || raw[idx - 1] != '[') return idx
            idx = raw.indexOf("[$tag", idx + 1, ignoreCase = true)
        }
        return -1
    }

    /** 去掉所有 BBCode 标签，得到纯文本（复制 / AI 上下文用）。 */
    fun stripTags(raw: String): String =
        raw.replace(Regex("\\[/?[a-zA-Z0-9=,#_\\-\\*]{1,20}\\]"), "")
            .replace(Regex("\\[\\*\\]"), "· ")

    /** 提取所有图片地址（含 [img] 与裸 HTML img）。 */
    fun imageUrls(raw: String): List<String> {
        val out = mutableListOf<String>()
        Regex("\\[img\\]([^\\[]+)\\[/img\\]", RegexOption.IGNORE_CASE).findAll(raw).forEach {
            val u = it.groupValues[1].trim()
            if (u.isNotEmpty()) out += absUrl(u)
        }
        // 表情不是「正文配图」：列表缩略图和图廊都不该收
        return contentImages(out)
    }

    /**
     * 论坛自带表情（smiley）的地址特征。
     *
     * 用户反馈「表情包被当成图片，点开还打开了错误的图」——Discuz 的表情是
     * `static/image/smiley/...` 下面的小图，跟正文配图混在同一个 [img]/<img> 里。
     * 这里统一识别，渲染时当行内表情、不进图廊、不参与「点开大图」。
     */
    private val SMILEY_HINTS = listOf(
        "static/image/smiley", "/smiley/", "smiley/", "images/smiley",
        "/face/", "editor/face", "static/image/face",
    )

    /** 占位 / 图标的文件名：既不是表情也不是配图，直接忽略。 */
    private val PLACEHOLDER_FILE = Regex("^(none|lazy|loading|blank|spacer|grey|trans)[._-]?[a-z0-9]*\\.(gif|png|jpg|jpeg)$", RegexOption.IGNORE_CASE)

    /** 这条地址是不是论坛自带表情。 */
    fun isSmiley(url: String): Boolean {
        val u = url.lowercase()
        if (PLACEHOLDER_FILE.matches(u.substringAfterLast('/'))) return false
        if (SMILEY_HINTS.any { u.contains(it) }) return true
        // 纯文件名的表情（sad.gif / biggrin.png 之类），且不是附件/相册链接
        val name = u.substringAfterLast('/')
        return SMILEY_FILE.matches(name) && !u.contains("/forum.php") && !u.contains("/attachment")
    }

    private val SMILEY_FILE = Regex("^[a-z0-9_!]{2,20}\\.(gif|png|jpg|jpeg)$", RegexOption.IGNORE_CASE)

    /** 正文配图：过滤掉表情、头像、站点图标、data: 占位图。 */
    fun contentImages(urls: List<String>): List<String> = urls.filter { u ->
        u.isNotBlank() &&
            !u.startsWith("data:") &&
            !isSmiley(u) &&
            !u.contains("avatar.php") &&
            !u.contains("/uc_server/")
    }

    fun absUrl(url: String): String = when {
        url.startsWith("http") -> url
        url.startsWith("//") -> "https:$url"
        url.startsWith("/") -> "https://bbs.binmt.cc$url"
        else -> "https://bbs.binmt.cc/$url"
    }

    /** 摘要：取第一段普通文本，去掉代码与标签。 */
    fun summary(raw: String, limit: Int = 80): String {
        val blocks = parse(raw)
        val text = blocks.filterIsInstance<BbcBlock.Text>().joinToString(" ") { stripTags(it.raw) }
            .ifBlank { blocks.filterIsInstance<BbcBlock.Quote>().joinToString(" ") { it.text } }
        return text.replace(Regex("\\s+"), " ").trim().take(limit)
    }
}
