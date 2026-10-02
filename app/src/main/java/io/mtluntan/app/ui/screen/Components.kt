package io.mtluntan.app.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material3.Icon
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.mtluntan.app.ui.components.MtCard
import io.mtluntan.app.ui.components.MtDivider
import io.mtluntan.app.domain.model.ThreadItem

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun MessageBox(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.outline)
    }
}

/**
 * 帖子列表项。
 *
 * 三条硬要求（用户反馈「标题要对齐 / 纯文字帖和带图帖错位」）：
 *  1. 缩略图**固定占位**：带图的显示图，纯文字帖显示统一的占位块，
 *     所以每张卡片的标题都从同一个 x 开始，上下滑动不会参差不齐；
 *  2. 卡片不打底色（只在按下时缩放反馈），列表用分隔线分条；
 *  3. 摘要最多两行，元信息一行放不下就省略，卡片高度稳定。
 */
@Composable
fun ThreadCard(item: ThreadItem, onClick: () -> Unit) {
    MtCard(
        onClick = onClick,
        padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        modifier = Modifier.padding(horizontal = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            ThreadThumb(item)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.summary.isNotBlank()) {
                    Text(
                        text = item.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 6.dp),
                ) {
                    if (item.authorName.isNotBlank()) {
                        Text(item.authorName, style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    if (item.boardName.isNotBlank()) {
                        Text(item.boardName, style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary)
                    }
                    if (item.replies > 0) {
                        Text("${item.replies} 回复", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.outline)
                    }
                    Text(item.postTime, style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

/**
 * 固定尺寸缩略图。
 *
 * 没有图的时候不留空白、也不让标题跑到左边去——统一大小 + 中性底色，
 * 这样带图帖和纯文字帖混在一起排，标题也是齐的。
 */
@Composable
private fun ThreadThumb(item: ThreadItem) {
    val size = 76.dp
    val hasImage = item.images.isNotEmpty() && item.images.first().isNotBlank()
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center,
    ) {
        if (hasImage) {
            AsyncImage(
                model = item.images.first(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                Icons.Outlined.Article,
                contentDescription = "文字帖",
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(24.dp),
            )
        }
        if (item.images.size > 1) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            ) {
                Text(
                    "图 ${item.images.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
fun ThreadList(
    items: List<ThreadItem>,
    onOpen: (ThreadItem) -> Unit,
    onLoadMore: () -> Unit,
    loading: Boolean,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        itemsIndexed(items, key = { _, it -> "${it.threadId}-${it.title}" }) { index, item ->
            ThreadCard(item, onClick = { onOpen(item) })
            if (index < items.lastIndex) MtDivider()
        }
        if (loading) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
fun DetailTopBar(title: String) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}
/**
 * 帖子 / 用户快速跳转（Java 版 v4.0 的 UID / TID 直达）。
 *
 * 直接粘一串链接也能用：链接里的 tid= / uid= 会被自动识别出来，
 * 所以从浏览器复制过来的地址不用手动裁。
 */
@Composable
fun QuickJumpDialog(
    onDismiss: () -> Unit,
    onJumpThread: (Long) -> Unit,
    onJumpUser: (Long) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    val tid = remember(input) { io.mtluntan.app.util.UrlUtil.tid(input).takeIf { it > 0 } ?: input.filter { it.isDigit() }.toLongOrNull() ?: 0L }
    val uid = remember(input) { io.mtluntan.app.util.UrlUtil.uid(input).takeIf { it > 0 } ?: 0L }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("快速跳转") },
        text = {
            Column {
                Text(
                    "输入帖子 tid、用户 uid，或直接粘一整条链接。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = input,
                    onValueChange = { input = it; message = "" },
                    label = { Text("tid / uid / 链接") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (message.isNotEmpty()) {
                    androidx.compose.foundation.layout.Spacer(Modifier.height(6.dp))
                    Text(message, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                }
                if (tid > 0 || uid > 0) {
                    androidx.compose.foundation.layout.Spacer(Modifier.height(6.dp))
                    Text(
                        buildString {
                            if (tid > 0) append("识别为帖子 $tid ")
                            if (uid > 0) append("用户 $uid")
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                when {
                    tid > 0 -> { onJumpThread(tid); onDismiss() }
                    uid > 0 -> { onJumpUser(uid); onDismiss() }
                    else -> message = "没识别出 tid / uid，检查一下输入"
                }
            }) { Text(if (uid > 0 && tid == 0L) "打开用户" else "打开帖子") }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
