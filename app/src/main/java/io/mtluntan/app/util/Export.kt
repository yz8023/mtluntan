package io.mtluntan.app.util

import io.mtluntan.app.data.parser.BbcBlock
import io.mtluntan.app.data.parser.BbcBlocks
import io.mtluntan.app.domain.model.ThreadDetail

/**
 * 帖子导出（Java 版 v4.1 的「导出 HTML / 导出文本」）。
 *
 * 导出的 HTML 是自包含的：图片仍然走网络地址，但正文、楼层、时间、署名都在，
 * 用浏览器 / 微信 / 任意阅读器打开都能看，不依赖论坛前台样式。
 */
object Export {

    fun safeName(title: String, tid: Long, ext: String): String {
        val base = title.ifBlank { "thread_$tid" }
        val cleaned = base.replace(Regex("[\\\\/:*?\"<>|\\r\\n]"), "_").trim().take(60)
        return "${cleaned}_$tid.$ext"
    }

    fun toHtml(detail: ThreadDetail, tid: Long, pageHtml: String = ""): String {
        val sb = StringBuilder()
        sb.append("<!DOCTYPE html>\n<html lang=\"zh-CN\">\n<head>\n")
        sb.append("<meta charset=\"utf-8\">\n")
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
        sb.append("<title>").append(escape(detail.title)).append("</title>\n")
        sb.append(
            """
            <style>
            body{font-family:system-ui,-apple-system,"PingFang SC","Microsoft YaHei",sans-serif;
                 margin:0;padding:16px;line-height:1.7;background:#fff;color:#222;max-width:760px;}
            h1{font-size:20px;margin:0 0 6px;}
            .meta{color:#888;font-size:12px;margin-bottom:14px;}
            .post{border-bottom:1px solid #eee;padding:12px 0;}
            .post .head{font-size:13px;color:#666;margin-bottom:6px;}
            .post .head b{color:#111;}
            img{max-width:100%;height:auto;border-radius:8px;margin:6px 0;}
            pre{background:#f6f8fa;padding:10px;border-radius:8px;overflow:auto;font-size:13px;}
            blockquote{margin:8px 0;padding:8px 12px;border-left:3px solid #d0d7de;background:#fafbfc;color:#555;}
            code{background:#f6f8fa;padding:1px 4px;border-radius:4px;}
            .hide{background:#fff8e1;border-left:3px solid #ffb300;padding:8px 12px;color:#8d6e63;}
            .foot{margin-top:20px;color:#aaa;font-size:12px;}
            </style>
            """.trimIndent()
        )
        sb.append("\n</head>\n<body>\n")
        sb.append("<h1>").append(escape(detail.title)).append("</h1>\n")
        sb.append("<div class=\"meta\">")
        sb.append(escape(detail.forumName)).append(" · 第 ").append(detail.currentPage).append("/").append(detail.totalPages).append(" 页")
        sb.append(" · tid ").append(tid)
        sb.append(" · 导出时间 ").append(java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date()))
        sb.append("</div>\n")

        val posts = listOfNotNull(detail.mainPost) + detail.posts
        for (post in posts) {
            sb.append("<div class=\"post\">\n")
            sb.append("<div class=\"head\"><b>").append(escape(post.authorName)).append("</b>")
            if (post.postTime.isNotBlank()) sb.append(" · ").append(escape(post.postTime))
            if (post.floor > 0) sb.append(" · ").append(post.floor).append(" 楼")
            if (post.likes > 0) sb.append(" · 赞 ").append(post.likes)
            sb.append("</div>\n")
            sb.append(renderBody(post.contentBbc, post.contentHtml))
            sb.append("</div>\n")
        }
        sb.append("<div class=\"foot\">由 MT论坛 客户端导出 · 内容版权归原作者所有</div>\n")
        if (pageHtml.isNotBlank()) {
            // 保留原始页面片段，方便以后排查解析问题（不参与渲染）
            sb.append("<!-- raw page snapshot: ").append(pageHtml.length).append(" chars -->\n")
        }
        sb.append("</body>\n</html>\n")
        return sb.toString()
    }

    fun toText(detail: ThreadDetail, tid: Long): String {
        val sb = StringBuilder()
        sb.append(detail.title).append('\n')
        sb.append(detail.forumName).append(" · tid ").append(tid).append(" · 第 ")
            .append(detail.currentPage).append('/').append(detail.totalPages).append(" 页\n")
        sb.append("====================\n\n")
        val posts = listOfNotNull(detail.mainPost) + detail.posts
        for (post in posts) {
            sb.append("【").append(post.authorName).append("】")
            if (post.floor > 0) sb.append(" ").append(post.floor).append(" 楼")
            if (post.postTime.isNotBlank()) sb.append(" ").append(post.postTime)
            sb.append('\n')
            voteBody(post.contentBbc, post.contentHtml, sb)
            sb.append("\n\n--------------------\n\n")
        }
        return sb.toString()
    }

    // ---------------- 内部 ----------------

    private fun renderBody(bbc: String, html: String): String {
        val source = bbc.ifBlank { html }
        if (source.isBlank()) return ""
        // 用已经写好的块解析器，保证导出内容与 App 内看到的一致
        val blocks = BbcBlocks.parse(source)
        val sb = StringBuilder()
        for (block in blocks) {
            when (block) {
                is BbcBlock.Code ->
                    sb.append("<pre><code>").append(escape(block.code)).append("</code></pre>\n")
                is BbcBlock.Quote ->
                    sb.append("<blockquote>").append(escape(block.text)).append("</blockquote>\n")
                is BbcBlock.Image ->
                    sb.append("<img src=\"").append(escape(block.url)).append("\" alt=\"图片\">\n")
                is BbcBlock.Hide ->
                    sb.append("<div class=\"hide\">").append(escape(block.text))
                        .append(if (block.locked) "（隐藏内容，需在手机上回复可见）" else "").append("</div>\n")
                is BbcBlock.Attachment ->
                    sb.append("<p>附件：<a href=\"").append(escape(block.url)).append("\">")
                        .append(escape(block.label)).append("</a></p>\n")
                is BbcBlock.Free ->
                    sb.append("<p>").append(escape(block.text).replace("\n", "<br>")).append("</p>\n")
                is BbcBlock.Text ->
                    sb.append("<p>").append(escape(block.raw).replace("\n", "<br>")).append("</p>\n")
            }
        }
        return sb.toString()
    }

    private fun voteBody(bbc: String, html: String, sb: StringBuilder) {
        val source = bbc.ifBlank { html }
        if (source.isBlank()) return
        BbcBlocks.parse(source).forEach { block ->
            when (block) {
                is BbcBlock.Code -> sb.append("```\n").append(block.code).append("\n```\n")
                is BbcBlock.Quote -> sb.append("> ").append(block.text.replace("\n", "\n> ")).append('\n')
                is BbcBlock.Image -> sb.append("[图片] ").append(block.url).append('\n')
                is BbcBlock.Hide -> sb.append("[隐藏内容]").append(if (block.locked) "（需回复可见）" else "").append('\n')
                is BbcBlock.Attachment -> sb.append("[附件] ").append(block.label).append(' ').append(block.url).append('\n')
                is BbcBlock.Free -> sb.append(block.text).append('\n')
                is BbcBlock.Text -> sb.append(BbcBlocks.stripTags(block.raw)).append('\n')
            }
        }
    }

    private fun escape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
