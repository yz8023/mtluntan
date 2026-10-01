package io.mtluntan.app.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import io.mtluntan.app.domain.model.Post

/**
 * Minimal BBCode renderer for the post bodies produced by [io.mtluntan.app.data.parser.BbcToHtml].
 * Handles [img], [url=], [b]/[i]/[u]/[s], [color=], [size=], [quote], [code] and line breaks.
 */
@Composable
fun BbcContent(bbc: String, modifier: Modifier = Modifier, onImageClick: ((String) -> Unit)? = null) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            renderBlocks(bbc) { chunk ->
                when (chunk.kind) {
                    ChunkKind.Image -> {
                        Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                            AsyncImage(
                                model = chunk.text,
                                contentDescription = "附件",
                                modifier = Modifier
                                    .width(260.dp)
                                    .height(180.dp)
                                    .align(Alignment.CenterVertically),
                            )
                        }
                    }
                    ChunkKind.Code -> {
                        SurfaceCard(container = true) {
                            Text(
                                chunk.text,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(8.dp),
                            )
                        }
                    }
                    ChunkKind.Quote -> {
                        SurfaceCard(container = false) {
                            Text(
                                chunk.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(8.dp),
                            )
                        }
                    }
                    ChunkKind.Normal -> {
                        Text(
                            chunk.text.take(2000),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SurfaceCard(container: Boolean, content: @Composable () -> Unit) {
    val shape = MaterialTheme.shapes.small
    if (container) {
        androidx.compose.material3.Surface(
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) { content() }
    } else {
        androidx.compose.material3.Surface(
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth(),
        ) { content() }
    }
}

private enum class ChunkKind { Normal, Image, Code, Quote }

private data class Chunk(val kind: ChunkKind, val text: String)

/** Parses a BBCode flat string into display chunks (no nesting beyond pairs). */
@Composable
private fun renderBlocks(bbc: String?, emit: @Composable (Chunk) -> Unit) {
    val content = bbc ?: return
    var i = 0
    while (i < content.length) {
        val imgStart = content.indexOf("[img]", i)
        val codeStart = content.indexOf("[code]", i)
        val quoteStart = content.indexOf("[quote]", i)
        // pick the earliest opener
        val cands = listOf(
            imgStart.takeIf { it >= 0 }?.let { Triple(it, 0, "]") },
            codeStart.takeIf { it >= 0 }?.let { Triple(it, 1, "]") },
            quoteStart.takeIf { it >= 0 }?.let { Triple(it, 2, "]") },
        ).filterNotNull()
        val chosen = cands.minByOrNull { it.first }

        if (chosen == null) {
            if (i < content.length) emit(Chunk(ChunkKind.Normal, content.substring(i)))
            break
        }
        val (startIdx, kindIdx, _) = chosen
        if (startIdx > i) {
            emit(Chunk(ChunkKind.Normal, content.substring(i, startIdx)))
        }
        val closer = when (kindIdx) { 0 -> "[/img]"; 1 -> "[/code]"; else -> "[/quote]" }
        val contentStart = startIdx + when (kindIdx) { 0 -> 5; 1 -> 6; else -> 7 }
        val contentEnd = content.indexOf(closer, contentStart)
        val body = if (contentEnd > 0) content.substring(contentStart, contentEnd) else content.substring(contentStart)
        emit(Chunk(kindFrom(kindIdx), body.trim()))
        i = if (contentEnd > 0) contentEnd + closer.length else content.length
    }
}

private fun kindFrom(idx: Int): ChunkKind = when (idx) {
    0 -> ChunkKind.Image
    1 -> ChunkKind.Code
    else -> ChunkKind.Quote
}

/** A single post item inside a thread detail. */
@Composable
fun PostCard(post: Post, onOpenProfile: ((Long) -> Unit)? = null) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = post.avatarUrl,
                    contentDescription = null,
                    modifier = Modifier.width(36.dp).height(36.dp),
                )
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    Text(post.authorName, style = MaterialTheme.typography.titleSmall)
                    Text(
                        post.postTime.ifBlank { "第 ${post.floor} 楼" },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                Box(modifier = Modifier.weight(1f)) {}
                Text(
                    if (post.floor > 0) "${post.floor}楼" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            if (post.replyQuote.isNotBlank()) {
                Text(
                    text = buildQuote(post.replyQuote),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            BbcContent(post.contentBbc.ifBlank { post.contentHtml.toPlainText() })
        }
    }
}

private fun buildQuote(q: String): String = if (q.length > 120) q.take(120) + "…" else q

private fun String.toPlainText(): String = replace(Regex("""\[/?[a-z0-9=]+\]"""), "").trim()