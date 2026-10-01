package io.mtluntan.app.data.parser

import io.mtluntan.app.domain.model.EditorMeta
import io.mtluntan.app.domain.model.SubmitResult
import io.mtluntan.app.util.UrlUtil

/** Parses editor pages (new thread / reply / edit) and post-submit responses. */
object EditorParser : BaseParser() {

    fun parseEditor(html: String, url: String = ""): EditorMeta {
        val doc = Parsing.doc(html)
        val check = Parsing.checkPageError(doc, html)
        if (check.isError) {
            return EditorMeta(loginRequired = check.loginRequired, errorMessage = check.message)
        }
        val formhash = Parsing.value(doc, "formhash")
        val posttime = Parsing.value(doc, "posttime")
        val fid = Parsing.value(doc, "fid").ifEmpty { doc.select("input[name=forumid]").firstOrNull()?.attr("value") ?: "" }
        val tid = Parsing.value(doc, "tid")
        val pid = Parsing.value(doc, "pid")
        val uploadHash = doc.select("form#imgattachform input[name=hash], form#attachform input[name=hash]")
            .firstOrNull()?.attr("value") ?: ""
        val titleContent = doc.selectFirst("input#subject, input[name=subject]")?.attr("value") ?: ""
        return EditorMeta(
            formhash = formhash,
            posttime = posttime,
            fid = fid,
            tid = tid,
            pid = pid,
            uploadHash = uploadHash,
            success = formhash.isNotEmpty(),
            errorMessage = titleContent.run { "" },
        )
    }

    /**
     * Discuz submits go through a URL like
     * `forum.php?mod=post&action=newthread&fid=X&topicsubmit=yes&...`
     * with a redirect or a return message. Detect both.
     */
    fun parseSubmit(html: String, requestedUrl: String): SubmitResult {
        val doc = Parsing.doc(html)
        // 1) redirect notice with a jump url
        var jump = doc.select("div#messagetext a").firstOrNull()?.attr("href")
            ?: doc.select("div.alert_info a, .notice a").firstOrNull()?.attr("href")
        var message = Parsing.cleanText(doc.select("div#messagetext").firstOrNull())
            ?: Parsing.cleanText(doc.select("div.alert_error, div.alert_info").firstOrNull())
        if (jump == null) {
            // in-ajax or inline: check meta refresh / javascript
        }

        var tid = UrlUtil.tid(jump)
        var pid = UrlUtil.pid(jump)

        // 2) error detection
        if (message.isNotEmpty() && (message.contains("无权") || message.contains("验证") ||
                message.contains("失败") || message.contains("错误") || message.contains("不能"))) {
            return SubmitResult(ok = false, error = message)
        }

        if (tid == 0L && message.isEmpty() && html.contains("location.href") || (jump != null && jump.contains("redirect"))) {
            tid = UrlUtil.tid(jump)
            pid = UrlUtil.pid(jump)
        }

        val ok = tid > 0 || message.isEmpty()
        return if (ok) {
            SubmitResult(ok = true, tid = tid, pid = pid, contentUrl = abs(jump))
        } else {
            SubmitResult(ok = false, error = message.ifEmpty { "提交返回异常" })
        }
    }
}