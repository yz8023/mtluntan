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
    var previewing by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }
    var showGradientPicker by remember { mutableStateOf(false) }
    var showTagPicker by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }
    val quickReplies by app.settings.quickReplies.collectAsStateWithLifecycle(initialValue = "")
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
                    IconButton(onClick = { previewing = !previewing }) {
                        Icon(Icons.Filled.Slideshow, "预览")
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

            if (!previewing) {
                OutlinedTextField(
                    value = field,
                    onValueChange = { field = it },
                    label = { Text("正文（支持 BBCode）") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 10.dp),
                )
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 10.dp),
                ) {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(10.dp)) {
                        Text("预览", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.height(6.dp))
                        BbcContent(bbc = field.text)
                        if (GradientText.segments(field.text).any { it.brush != null }) {
                            Spacer(Modifier.height(8.dp))
                            Text("渐变字预览", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                            GradientPreview(field.text)
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

            // 工具条
            Horizontaltoolbar(
                onInsertTag = { open, close -> wrapSelection(field, open, close) { field = it } },
                onPickTag = { showTagPicker = true },
                onColor = { showColorPicker = true },
                onGradient = { showGradientPicker = true },
                onImage = { imagePicker.launch("image/*") },
                uploading = uploading,
                onCode = { wrapSelection(field, "[code]", "[/code]") { field = it } },
                onQuote = { wrapSelection(field, "[quote]", "[/quote]") { field = it } },
                onHide = { wrapSelection(field, "[hide]", "[/hide]") { field = it } },
            )
        }
    }

    if (showColorPicker) {
        ColorPickerDialog(onDismiss = { showColorPicker = false }) { hex ->
            wrapSelection(field, "[color=#$hex]", "[/color]") { field = it }
            showColorPicker = false
        }
    }

    if (showGradientPicker) {
        GradientPickerDialog(onDismiss = { showGradientPicker = false }) { start, end ->
            wrapSelection(field, "[gradient=${GradientText.hex(start)},${GradientText.hex(end)}]", "[/gradient]") { field = it }
            showGradientPicker = false
        }
    }

    if (showTagPicker) {
        TagPickerDialog(onDismiss = { showTagPicker = false }) { open, close ->
            wrapSelection(field, open, close) { field = it }
            showTagPicker = false
        }
    }
}

@Composable
private fun Horizontaltoolbar(
    onInsertTag: (String, String) -> Unit,
    onPickTag: () -> Unit,
    onColor: () -> Unit,
    onGradient: () -> Unit,
    onImage: () -> Unit,
    uploading: Boolean,
    onCode: () -> Unit,
    onQuote: () -> Unit,
    onHide: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolButton(Icons.Filled.TextFields, "B", "加粗") { onInsertTag("[b]", "[/b]") }
            ToolButton(Icons.Filled.TextFields, "I", "斜体") { onInsertTag("[i]", "[/i]") }
            ToolButton(Icons.Filled.TextFields, "U", "下划线") { onInsertTag("[u]", "[/u]") }
            ToolButton(Icons.Filled.Code, null, "代码") { onCode() }
            ToolButton(Icons.Filled.Tag, null, "引用") { onQuote() }
            ToolButton(Icons.Filled.Tag, null, "隐藏") { onHide() }
            ToolButton(Icons.Filled.Palette, null, "颜色") { onColor() }
            ToolButton(Icons.Filled.ColorLens, null, "渐变字") { onGradient() }
            ToolButton(Icons.Filled.Image, null, if (uploading) "上传中" else "图片") { onImage() }
            ToolButton(Icons.Filled.Slideshow, null, "更多标签") { onPickTag() }
            ToolButton(Icons.Filled.Send, null, "链接") { onInsertTag("[url]", "[/url]") }
        }
    }
}

@Composable
private fun ToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String?,
    desc: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Icon(icon, contentDescription = desc, modifier = Modifier.size(16.dp))
            if (label != null) {
                Spacer(Modifier.width(4.dp))
                Text(label, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun ColorPickerDialog(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var r by remember { mutableStateOf(0.2f) }
    var g by remember { mutableStateOf(0.5f) }
    var b by remember { mutableStateOf(0.9f) }
    val color = Color(r, g, b)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择颜色") },
        text = {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(color),
                )
                Spacer(Modifier.height(8.dp))
                ChannelSlider("R", r) { r = it }
                ChannelSlider("G", g) { g = it }
                ChannelSlider("B", b) { b = it }
                Text(
                    "#" + listOf(r, g, b).joinToString("") { channel ->
                        ((channel * 255).toInt()).toString(16).padStart(2, '0')
                    }.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val hex = listOf(r, g, b).joinToString("") { ch ->
                    ((ch * 255).toInt()).toString(16).padStart(2, '0')
                }.uppercase()
                onPick(hex)
            }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ChannelSlider(name: String, value: Float, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(18.dp))
        Slider(value = value, onValueChange = onChange, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun GradientPickerDialog(onDismiss: () -> Unit, onPick: (Long, Long) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("渐变字（8 种预设）") },
        text = {
            Column {
                GradientText.presets.forEach { preset ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(preset.start, preset.end) }
                            .padding(vertical = 8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(GradientText.brush(preset.start, preset.end)),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(preset.name, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text("选中后会把「选中的文字」包成 [gradient=..] 标签", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
private fun TagPickerDialog(onDismiss: () -> Unit, onPick: (String, String) -> Unit) {
    val tags = listOf(
        "[b]" to "[/b]", "[i]" to "[/i]", "[u]" to "[/u]", "[s]" to "[/s]",
        "[color=#FF5722]" to "[/color]", "[size=18]" to "[/size]",
        "[url]" to "[/url]", "[email]" to "[/email]",
        "[quote]" to "[/quote]", "[code]" to "[/code]", "[hide]" to "[/hide]",
        "[free]" to "[/free]", "[img]" to "[/img]", "[attach]" to "[/attach]",
        "[float]" to "[/float]", "[table]" to "[/table]", "[tr]" to "[/tr]", "[td]" to "[/td]",
        "[audio]" to "[/audio]", "[media]" to "[/media]", "[flash]" to "[/flash]",
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("BBCode 标签（${tags.size} 个）") },
        text = {
            Column(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                tags.forEach { (open, close) ->
                    Text(
                        open.replace("[", "").replace("]", ""),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(open, close) }
                            .padding(vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
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
