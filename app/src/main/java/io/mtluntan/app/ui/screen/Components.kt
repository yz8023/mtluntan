package io.mtluntan.app.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
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

@Composable
fun ThreadCard(item: ThreadItem, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            if (item.images.isNotEmpty()) {
                AsyncImage(
                    model = item.images.first(),
                    contentDescription = null,
                    modifier = Modifier.width(72.dp).height(72.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f).padding(start = if (item.images.isNotEmpty()) 10.dp else 0.dp),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.summary.isNotBlank()) {
                    Text(
                        text = item.summary,
                        style = MaterialTheme.typography.bodyMedium,
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

@Composable
fun ThreadList(
    items: List<ThreadItem>,
    onOpen: (ThreadItem) -> Unit,
    onLoadMore: () -> Unit,
    loading: Boolean,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(items, key = { "${it.threadId}-${it.title}" }) { item ->
            ThreadCard(item, onClick = { onOpen(item) })
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
