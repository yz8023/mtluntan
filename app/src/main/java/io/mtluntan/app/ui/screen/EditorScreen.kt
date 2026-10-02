package io.mtluntan.app.ui.screen

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import io.mtluntan.app.MTLuntanApp
import io.mtluntan.app.data.network.ApiUris
import io.mtluntan.app.domain.model.Draft
import io.mtluntan.app.domain.model.EditorMeta
import io.mtluntan.app.util.CopyUtil
import io.mtluntan.app.ui.components.BbcToolbar
import io.mtluntan.app.util.GradientText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 编辑器（发帖 / 回复 / 编辑共用）。
 *
 * 这是 Java 版打磨最多的一页，Kotlin 版对齐这些能力：
 *  - BBCode 快捷工具条：21 个常用标签 + 颜色/渐变 + 图片 + 代码
 *  - 300ms 防抖实时预览（「所见即所得」但仍是纯文本，发出去就是原文）
 *  - 快捷回复短语（一行一条，点一下插入）
 *  - 图片上传（自动压到论坛 1MB 限制内）
 *  - 草稿自动保存，退出不丢
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    app: MTLuntanApp,
    nav: NavHostController,
    fid: Long = 0L,
    tid: Long = 0L,
    pid: Long = 0L,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isNew = tid == 0L
    val isEdit = pid > 0L
    val draftKey = when {
        isEdit -> "editreply:$tid:$pid"
        isNew -> "newthread:0:$fid"
        else -> "reply:$tid:$fid"
    }

    var meta by remember { mutableStateOf<EditorMeta?>(null) }
    var title by remember { mutableStateOf("") }
    var field by remember { mutableStateOf(TextFieldValue("")) }
    var submitting by remember { mutableStateOf(false) }
    var livePreview by remember { mutableStateOf(true) }
    var uploading by remember { mutableStateOf(false) }
    val quickReplies by app.settings.quickReplies.collectAsStateWithLifecycle(initialValue = "")
    // 工具条形态（文字 / 图标）与行数（默认 2 行），以及「实时预览」开关
    val toolbarStyle by app.settings.toolbarStyle.collectAsStateWithLifecycle(initialValue = "icon")
    val toolbarRows by app.settings.toolbarRows.collectAsStateWithLifecycle(initialValue = 2)
    val livePreviewSetting by app.settings.replyPreview.collectAsStateWithLifecycle(initialValue = true)

    // 设置里改了「实时预览」→ 跟着切（用户不用手动点开）
    LaunchedEffect(livePreviewSetting) { livePreview = livePreviewSetting }
    var lastSavedAt by remember { mutableStateOf(0L) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        uploading = true
        scope.launch {
            val (bytes, mime) = withContext(Dispatchers.IO) { readImage(context, uri) } ?: run {
                uploading = false
                return@launch
            }
            val (ok, urlOrError) = app.forum.uploadImage(
                bytes = bytes,
                fileName = "upload_${System.currentTimeMillis()}.jpg",
                mime = mime,
                fid = fid.coerceAtLeast(meta?.fid?.toLongOrNull() ?: 2L),
                uploadHash = meta?.uploadHash.orEmpty(),
            )
            uploading = false
            if (ok) insertAtCursor(field, "[img]$urlOrError[/img]") { field = it }
            else CopyUtil.toast(context, "上传失败：$urlOrError")
        }
    }

    // 载入编辑器（编辑模式先取原帖内容）
    LaunchedEffect(Unit) {
        try {
            val url = when {
                isEdit -> ApiUris.editPost(2, tid, pid)
                isNew -> ApiUris.newThread(fid)
                else -> ApiUris.reply(tid)
            }
            val html = if (isEdit) app.forum.editPageHtml(fid.coerceAtLeast(2L), tid, pid) else ""
            meta = app.forum.fetchEditor(url)
            if (isEdit && html.isNotEmpty()) {
                val form = io.mtluntan.app.data.parser.ThreadExtrasParser.editForm(html, pid)
                if (form != null) {
                    field = TextFieldValue(form.textareaValue)
                    title = form.fields["subject"].orEmpty()
                }
            }
            val draft = app.local.draftFor(draftKey)
            if (draft != null && draft.content.isNotBlank() && field.text.isBlank()) {
                field = TextFieldValue(draft.content)
                if (title.isBlank()) title = draft.title
                CopyUtil.toast(context, "已恢复上次未提交的草稿")
            }
        } catch (t: Throwable) {
            CopyUtil.toast(context, "打开编辑器失败：${t.message}")
        }
    }

    // 草稿自动保存（2.5 秒防抖）
    LaunchedEffect(field.text, title) {
        if (field.text.isBlank() && title.isBlank()) return@LaunchedEffect
        kotlinx.coroutines.delay(2500)
        val now = System.currentTimeMillis()
        if (now - lastSavedAt < 2000) return@LaunchedEffect
        lastSavedAt = now
        app.local.saveDraft(
            Draft(
                tid = tid,
                fid = fid,
                type = when {
                    isEdit -> "editpost"
                    isNew -> "newthread"
                    else -> "reply"
                },
                title = title,
                content = field.text,
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEdit) "编辑回复" else if (isNew) "发布主题" else "回复主题") },
                navigationIcon = {
                    IconButton(onClick = {
                        // 退出前兜底存一次草稿
                        scope.launch {
                            if (field.text.isNotBlank()) {
                                app.local.saveDraft(Draft(tid = tid, fid = fid, type = "reply", title = title, content = field.text))
                            }
                            nav.popBackStack()
                        }
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                },
                actions = {
                    IconButton(onClick = { livePreview = !livePreview }) {
                        Icon(Icons.Filled.Slideshow, if (livePreview) "收起实时预览" else "打开实时预览")
                    }
                    IconButton(onClick = {
                        scope.launch {
                            app.local.saveDraft(Draft(tid = tid, fid = fid, type = "manual", title = title, content = field.text))
                            CopyUtil.toast(context, "草稿已保存")
                        }
                    }) { Icon(Icons.Filled.Save, "保存草稿") }
                    TextButton(enabled = !submitting && meta != null, onClick = {
                        val content = field.text
                        if (content.isBlank() && !isNew) { CopyUtil.toast(context, "内容不能为空"); return@TextButton }
                        if (isNew && title.isBlank()) { CopyUtil.toast(context, "标题不能为空"); return@TextButton }
                        submitting = true
                        scope.launch {
                            val m = meta
                            val result = when {
                                m == null -> io.mtluntan.app.domain.model.SubmitResult(false, "编辑器未就绪")
                                isEdit -> app.forum.submitEdit(m.fid.toLongOrNull() ?: fid, tid, pid, m.formhash, title, content)
                                isNew -> app.forum.submitThread(fid, m.formhash, title, content)
                                else -> app.forum.submitReply(tid, m.formhash, content, fid = m.fid.toLongOrNull() ?: fid)
                            }
                            submitting = false
                            CopyUtil.toast(context, if (result.ok) "发布成功" else result.error.ifBlank { "提交失败" })
                            if (result.ok) {
                                app.local.deleteDraft(draftKey)
                                nav.popBackStack()
                            }
                        }
                    }) { Text(if (submitting) "提交中…" else "发布") }
                },
            )
        },
    ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad)) {
            if (isNew) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("标题") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }

            OutlinedTextField(
                value = field,
                onValueChange = { field = it },
                label = { Text("正文（支持 BBCode）") },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 10.dp),
            )

            // 实时预览：边打字边渲染（BbcContent 是同步渲染，不需要手动触发）
            if (livePreview) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 190.dp)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(10.dp)) {
                        Text(
                            "实时预览",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.outline,
                        )
                        Spacer(Modifier.height(6.dp))
                        if (field.text.isBlank()) {
                            Text(
                                "输入内容后这里实时显示效果",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        } else {
                            BbcContent(bbc = field.text)
                            if (GradientText.segments(field.text).any { it.brush != null }) {
                                Spacer(Modifier.height(8.dp))
                                Text("渐变字预览", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                                GradientPreview(field.text)
                            }
                        }
                    }
                }
            }

            // 快捷短语
            val phrases = quickReplies.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            if (phrases.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    phrases.forEach { phrase ->
                        AssistChip(
                            onClick = { insertAtCursor(field, phrase) { field = it } },
                            label = { Text(phrase, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            }

            // 工具条（与帖子页回复面板共用 ui/components/BbcToolbar.kt）
            BbcToolbar(
                onWrap = { open, close -> wrapSelection(field, open, close) { field = it } },
                onImage = { imagePicker.launch("image/*") },
                uploading = uploading,
                style = toolbarStyle,
                rows = toolbarRows,
            )
        }
    }



}

// ---------------- 文本操作 ----------------

private fun insertAtCursor(
    current: TextFieldValue,
    insert: String,
    apply: (TextFieldValue) -> Unit,
) {
    val start = current.selection.start.coerceIn(0, current.text.length)
    val end = current.selection.end.coerceIn(start, current.text.length)
    val newText = current.text.substring(0, start) + insert + current.text.substring(end)
    apply(TextFieldValue(newText, TextRange(start + insert.length)))
}

private fun wrapSelection(
    current: TextFieldValue,
    open: String,
    close: String,
    apply: (TextFieldValue) -> Unit,
) {
    val start = current.selection.min.coerceIn(0, current.text.length)
    val end = current.selection.max.coerceIn(start, current.text.length)
    val selected = current.text.substring(start, end)
    val newText = current.text.substring(0, start) + open + selected + close + current.text.substring(end)
    val cursor = if (selected.isEmpty()) start + open.length else start + open.length + selected.length
    apply(TextFieldValue(newText, TextRange(cursor)))
}

/** 读取图片：超过论坛 1MB 限制时先降质量再降分辨率（Java 版 v3.9 的策略）。 */
private fun readImage(context: android.content.Context, uri: Uri): Pair<ByteArray, String>? {
    return try {
        val raw = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: return raw to "image/jpeg"
        var quality = 92
        var width = bitmap.width
        var bytes: ByteArray
        while (true) {
            val scaled = if (width >= bitmap.width) bitmap else android.graphics.Bitmap.createScaledBitmap(
                bitmap, width, (bitmap.height * (width.toFloat() / bitmap.width)).toInt(), true
            )
            val out = java.io.ByteArrayOutputStream()
            scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, out)
            bytes = out.toByteArray()
            if (bytes.size <= 900 * 1024) break
            if (quality > 60) quality -= 12
            else if (width > 800) width = (width * 0.8f).toInt()
            else break
        }
        bytes to "image/jpeg"
    } catch (t: Throwable) {
        null
    }
}
