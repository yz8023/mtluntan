package io.mtluntan.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import io.mtluntan.app.data.parser.BbcBlock
import io.mtluntan.app.data.parser.BbcBlocks
import io.mtluntan.app.util.CopyUtil
import io.mtluntan.app.util.GradientText

/** 行内样式：**加粗** / 斜体 / 下划线 / 删除线 / 颜色 / 字号 / 链接 / 渐变。 */
private class InlineSpan(val text: String, val style: SpanStyle)

private val INLINE_RE = Regex(
    "\\[(b|i|u|s|color|size|url|gradient)(?:=([^\\]]*))?\\](.*?)\\[/\\1\\]",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
)

private fun inlines(raw: String, linkColor: Color): List<InlineSpan> {
    val out = mutableListOf<InlineSpan>()
    var cursor = 0
    for (m in INLINE_RE.findAll(raw)) {
        if (m.range.first > cursor) out += InlineSpan(raw.substring(cursor, m.range.first), SpanStyle())
        val tag = m.groupValues[1].lowercase()
        val arg = m.groupValues[2]
        val body = m.groupValues[3]
        val style = when (tag) {
            "b" -> SpanStyle(fontWeight = FontWeight.Bold)
            "i" -> SpanStyle(fontStyle = FontStyle.Italic)
            "u" -> SpanStyle(textDecoration = TextDecoration.Underline)
            "s" -> SpanStyle(textDecoration = TextDecoration.LineThrough)
            "color" -> SpanStyle(color = parseColor(arg) ?: linkColor)
            "size" -> {
                val sp = arg.filter { it.isDigit() }.toIntOrNull()?.coerceIn(10, 30) ?: 15
                SpanStyle(fontSize = sp.sp)
            }
            "url" -> SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
            "gradient" -> {
                val parts = arg.split(",")
                val start = parts.getOrNull(0)?.let { parseColor(it) } ?: Color(0xFF2196F3)
                SpanStyle(color = start)
            }
            else -> SpanStyle()
        }
        out += InlineSpan(BbcBlocks.stripTags(body), style)
        cursor = m.range.last + 1
    }
    if (cursor < raw.length) out += InlineSpan(raw.substring(cursor), SpanStyle())
    return out
}

private fun isHexDigit(c: Char): Boolean =
    (c in '0'..'9') || (c in 'a'..'f') || (c in 'A'..'F')

private fun parseColor(v: String?): Color? {
    val hex = (v ?: "").trim().removePrefix("#")
    return when {
        hex.length == 6 -> {
            if (hex.all { isHexDigit(it) }) Color(0xFF000000L or (hex.toLongOrNull(16) ?: return null)) else null
        }
        hex.length == 3 -> {
            val r = hex[0]; val g = hex[1]; val b = hex[2]
            if (!isHexDigit(r) || !isHexDigit(g) || !isHexDigit(b)) return null
            Color(0xFF000000L or ("$r$r$g$g$b$b".toLong(16)))
        }
        else -> null
    }
}

@Composable
private fun richText(raw: String, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE) {
    val linkColor = MaterialTheme.colorScheme.primary
    val spans = remember(raw, linkColor) { inlines(raw, linkColor) }
    val annotated = buildAnnotatedString {
        spans.forEach { span ->
            withStyle(span.style) { append(span.text) }
        }
    }
    Text(
        text = annotated,
        style = MaterialTheme.typography.bodyLarge,
        maxLines = maxLines,
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * 正文渲染入口。
 *
 * @param onImageClick 点开大图（传出图片列表与当前下标）
 * @param onUnlockClick 「回复可见」时的解锁按钮
 */
@Composable
fun BbcContent(
    bbc: String,
    contentHtml: String = "",
    modifier: Modifier = Modifier,
    onImageClick: ((List<String>, Int) -> Unit)? = null,
    onUnlockClick: (() -> Unit)? = null,
    forceUnlocked: Boolean = false,
) {
    val source = bbc.ifBlank { contentHtml }
    val blocks = remember(source, forceUnlocked) { BbcBlocks.parse(source, forceUnlocked) }
    val allImages = remember(blocks) { blocks.filterIsInstance<BbcBlock.Image>().map { it.url } }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            when (block) {
                is BbcBlock.Code -> CodeBlockCard(block)
                is BbcBlock.Quote -> QuoteBlockCard(block)
                is BbcBlock.Image -> AsyncImage(
                    model = block.url,
                    contentDescription = "图片",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            val idx = allImages.indexOf(block.url).coerceAtLeast(0)
                            onImageClick?.invoke(allImages, idx)
                        },
                )
                is BbcBlock.Hide -> HideBlockCard(block, onUnlockClick)
                is BbcBlock.Attachment -> AttachmentRow(block)
                is BbcBlock.Free -> {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        richText(block.text, Modifier.padding(10.dp))
                    }
                }
                is BbcBlock.Text -> {
                    val clean = block.raw.trim()
                    if (clean.isNotEmpty()) richText(clean)
                }
            }
        }
    }
}

@Composable
private fun CodeBlockCard(block: BbcBlock.Code) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val lineCount = block.code.count { it == '\n' } + 1
    val long = lineCount > 12
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            ) {
                Icon(Icons.Filled.Code, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = buildString {
                        append(block.lang.ifEmpty { "代码" })
                        append(" · $lineCount 行")
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.weight(1f),
                )
                if (long) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "折叠" else "展开",
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { expanded = !expanded },
                        tint = MaterialTheme.colorScheme.outline,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = "复制代码",
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { CopyUtil.copy(context, block.code, "已复制代码") },
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = if (long && !expanded) block.code.lines().take(12).joinToString("\n") else block.code,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.5.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
            if (long && !expanded) {
                Text(
                    text = "点击标题栏展开全部 $lineCount 行",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun QuoteBlockCard(block: BbcBlock.Quote) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val long = block.text.length > 160
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.FormatQuote, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.width(6.dp))
                Text("引用", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline, modifier = Modifier.weight(1f))
                if (long) {
                    TextButton(onClick = { expanded = !expanded }) {
                        Text(if (expanded) "收起" else "展开", style = MaterialTheme.typography.labelMedium)
                    }
                }
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = "复制引用",
                    modifier = Modifier.size(18.dp).clickable { CopyUtil.copy(context, block.text, "已复制引用") },
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = if (long && !expanded) block.text.take(160) + "…" else block.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HideBlockCard(block: BbcBlock.Hide, onUnlockClick: (() -> Unit)?) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (block.locked) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (block.locked) Icons.Filled.Lock else Icons.Filled.Visibility,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (block.locked) "隐藏内容 · 回复可见" else "隐藏内容 · 已解锁",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                if (block.text.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = "复制隐藏内容",
                        modifier = Modifier.size(18.dp).clickable {
                            CopyUtil.copy(context, block.text, "已复制隐藏内容")
                        },
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (block.text.isBlank()) "（服务端未返回明文）" else block.text,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (block.locked && onUnlockClick != null) {
                TextButton(onClick = onUnlockClick) { Text("回复解锁") }
            }
        }
    }
}

@Composable
private fun AttachmentRow(block: BbcBlock.Attachment) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(10.dp),
    ) {
        Icon(Icons.Filled.Image, null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(block.label, style = MaterialTheme.typography.bodyMedium)
    }
}

/** 全屏看图：双指缩放 + 拖动（对应 Java 版的 ZoomableImageView）。 */
@Composable
fun ImageGallery(urls: List<String>, startIndex: Int, onDismiss: () -> Unit) {
    if (urls.isEmpty()) return
    // 下标全部夹紧：urls 为空/变短、startIndex 越界都不会再越界访问（崩溃修复）
    val lastIndex = urls.lastIndex.coerceAtLeast(0)
    var index by remember(urls) { mutableStateOf(startIndex.coerceIn(0, lastIndex)) }
    val safeIndex = index.coerceIn(0, lastIndex)
    Dialog(onDismissRequest = onDismiss) {
        var scale by remember { mutableStateOf(1f) }
        var offsetX by remember { mutableStateOf(0f) }
        var offsetY by remember { mutableStateOf(0f) }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = urls[safeIndex],
                contentDescription = "大图",
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY,
                    )
                    .pointerInput(safeIndex) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            offsetX += pan.x
                            offsetY += pan.y
                        }
                    },
            )
            if (urls.size > 1) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    TextButton(onClick = {
                        index = (safeIndex - 1 + urls.size) % urls.size
                        scale = 1f; offsetX = 0f; offsetY = 0f
                    }) {
                        Text("上一张（${safeIndex + 1}/${urls.size}）", color = Color.White)
                    }
                    TextButton(onClick = {
                        index = (safeIndex + 1) % urls.size
                        scale = 1f; offsetX = 0f; offsetY = 0f
                    }) {
                        Text("下一张", color = Color.White)
                    }
                }
            }
        }
    }
}

/** 渐变字渲染（编辑器预览 / 帖子里用）。 */
@Composable
fun GradientPreview(text: String, modifier: Modifier = Modifier) {
    val segments = remember(text) { GradientText.segments(text) }
    Column(modifier = modifier) {
        segments.forEach { seg ->
            val brush = seg.brush
            if (brush == null) {
                Text(seg.text, style = MaterialTheme.typography.bodyLarge)
            } else {
                Text(
                    text = seg.text,
                    style = MaterialTheme.typography.bodyLarge.copy(brush = brush),
                )
            }
        }
    }
}
