package io.mtluntan.app.data.parser

import io.mtluntan.app.util.TextUtil
import io.mtluntan.app.util.UrlUtil
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * HTML → BBCode-ish converter for post bodies.
 * Normalizes the messy Discuz/Comiis post HTML into a compact BBCode that
 * the UI renderer understands (see ui/components/BbcRenderer).
 */
object BbcToHtml {

    private val IMG_RE = Regex("""(?:src|data-src)=["']([^"']+)["']""")

    /** Collects image urls from raw post html. */
    fun collectImageUrls(htmlOrText: String): List<String> =
        IMG_RE.findAll(htmlOrText)
            .map { UrlUtil.absolute("https://bbs.binmt.cc", it.groupValues[1]) }
            .filter { it.isNotEmpty() }
            .toList()

    /** Converts the message element's HTML into BBCode text. */
    fun htmlToBbc(msgEl: Element): String {
        val sb = StringBuilder()
        convertChildren(msgEl, sb)
        return sb.toString().trim()
    }

    private fun convertChildren(parent: Element, sb: StringBuilder) {
        for (node in parent.childNodes()) {
            when (node) {
                is TextNode -> {
                    val t = node.text()
                    if (t.isNotBlank()) sb.append(t)
                }
                is Element -> {
                    val tag = node.tagName().lowercase()
                    when (tag) {
                        "br" -> sb.append("\n")
                        "p" -> { convertChildren(node, sb); sb.append("\n") }
                        "div" -> { convertChildren(node, sb); sb.append("\n") }
                        "li" -> { sb.append("• "); convertChildren(node, sb); sb.append("\n") }
                        "ul" -> sb.append("\n")
                        "ol" -> sb.append("\n")
                        "img" -> {
                            val src = node.attr("src").ifBlank { node.attr("data-src") }
                            if (src.isNotEmpty()) {
                                sb.append("[img]").append(UrlUtil.absolute("https://bbs.binmt.cc", src)).append("[/img]")
                            }
                        }
                        "a" -> {
                            val href = node.attr("href")
                            val text = node.text().ifBlank { href }
                            if (href.contains("mod=image") || href.contains("attachment")) {
                                convertChildren(node, sb)
                            } else {
                                sb.append("[url=").append(href).append("]").append(text).append("[/url]")
                            }
                        }
                        "b", "strong" -> { sb.append("[b]"); convertChildren(node, sb); sb.append("[/b]") }
                        "i", "em" -> { sb.append("[i]"); convertChildren(node, sb); sb.append("[/i]") }
                        "u" -> { sb.append("[u]"); convertChildren(node, sb); sb.append("[/u]") }
                        "s", "strike" -> { sb.append("[s]"); convertChildren(node, sb); sb.append("[/s]") }
                        "font" -> {
                            val color = node.attr("color")
                            val size = node.attr("size")
                            if (color.isNotEmpty() && TextUtil.sanitize(color).isNotEmpty()) {
                                sb.append("[color=").append(color).append("]")
                                convertChildren(node, sb)
                                sb.append("[/color]")
                            } else if (size.isNotEmpty()) {
                                sb.append("[size=").append(size).append("]")
                                convertChildren(node, sb)
                                sb.append("[/size]")
                            } else convertChildren(node, sb)
                        }
                        "span" -> {
                            val color = node.attr("style")
                                .let { Regex("color:\\s*([^;]+)").find(it)?.groupValues?.getOrNull(1)?.trim() }
                            if (color != null && color.startsWith("#")) {
                                sb.append("[color=").append(color).append("]")
                                convertChildren(node, sb)
                                sb.append("[/color]")
                            } else convertChildren(node, sb)
                        }
                        "blockquote", "cite" -> {
                            sb.append("[quote]")
                            convertChildren(node, sb)
                            sb.append("[/quote]")
                        }
                        "pre" -> {
                            sb.append("[code]")
                            convertChildren(node, sb)
                            sb.append("[/code]")
                        }
                        "code" -> {
                            sb.append("[code]")
                            convertChildren(node, sb)
                            sb.append("[/code]")
                        }
                        "table" -> {
                            sb.append("[table]")
                            convertChildren(node, sb)
                            sb.append("[/table]")
                        }
                        "tr" -> { convertChildren(node, sb); sb.append("\n") }
                        "td", "th" -> {
                            sb.append("[td]")
                            convertChildren(node, sb)
                            sb.append("[/td]")
                        }
                        "h1", "h2", "h3", "h4" -> {
                            sb.append("[size=5]")
                            val text = node.text().trim()
                            sb.append(text)
                            sb.append("[/size]\n")
                        }
                        "ignore_js_op" -> {
                            // attachment block; use inner image if any
                            val img = node.selectFirst("img")
                            if (img != null) {
                                val src = img.attr("src").ifBlank { img.attr("data-src") }
                                if (src.isNotEmpty()) {
                                    sb.append("[img]").append(UrlUtil.absolute("https://bbs.binmt.cc", src)).append("[/img]")
                                }
                            } else {
                                val title = node.select("dl, .attnm, a[title]").firstOrNull()?.attr("title") ?: ""
                                if (title.isNotEmpty()) sb.append("[attach]").append(title).append("[/attach]")
                            }
                        }
                        else -> convertChildren(node, sb)
                    }
                }
            }
        }
    }
}