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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
import io.mtluntan.app.ui.components.MtCard
import io.mtluntan.app.ui.components.MtButton
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
 * 帖子列表项（用户要的排版）：
 *
 *   标题（大号加粗，突出重点）
 *   发布人 · 等级 · 时间 · 回复/查看   ← 小号字
 *   正文摘要（中号字，最多三行）
 *   图片缩略图 2~3 张（**没有图就完全不占位**，不放空白块）
 *
 * 所以整张卡是上下结构、左对齐：标题永远从左边同一条线开始，
 * 带图帖和纯文字帖混排也不会错位。
 */
@Composable
fun ThreadCard(item: ThreadItem, onClick: () -> Unit) {
    MtCard(
        onClick = onClick,
        padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        modifier = Modifier.padding(horizontal = 6.dp),
    ) {
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        // 发布人 / 等级 / 时间
        val meta = buildList {
            if (item.authorName.isNotBlank()) add(item.authorName)
            if (item.authorLevel.isNotBlank()) add(item.authorLevel)
            if (item.postTime.isNotBlank()) add(item.postTime)
            if (item.replies > 0) add("${item.replies} 回复")
            else if (item.views > 0) add("${item.views} 查看")
        }
        if (meta.isNotEmpty()) {
            Text(
                text = meta.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        if (item.summary.isNotBlank()) {
            Text(
                text = item.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        // 缩略图放到正文下面；没有图就什么都不画（不留占位、不留空白）
        val thumbs = item.images.filter { it.isNotBlank() }.take(3)
        if (thumbs.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                thumbs.forEach { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .weight(1f)
                            .height(if (thumbs.size == 1) 150.dp else 92.dp)
                            .clip(RoundedCornerShape(10.dp)),
                    )
                }
            }
        }
        // 板块 / 状态标签
        val tags = buildList {
            if (item.boardName.isNotBlank()) add(item.boardName)
            if (item.isDigest) add("精")
            if (item.isSticky) add("顶")
            if (item.hasHiddenContent) add("回复可见")
            if (item.typeName.isNotBlank()) add(item.typeName)
        }
        if (tags.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 6.dp),
            ) {
                tags.take(4).forEach { tag ->
                    Text(
                        tag,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }
        }
    }
}

/**
 * 帖子列表 + **滚到底部自动拼接下一页**。
 *
 * 用户反馈「翻到底不能自动加载」——这里用 `LazyListState` 监听最后一个可见项，
 * 距离底部还剩 2 条时就自动请求下一页，结果追加在列表末尾（不会跳页/换页）。
 * 自动加载失败或还未触发时，底部也留一个「加载更多」按钮兜底。
 */
@Composable
fun ThreadList(
    items: List<ThreadItem>,
    onOpen: (ThreadItem) -> Unit,
    onLoadMore: () -> Unit,
    loading: Boolean,
    modifier: Modifier = Modifier,
    exhausted: Boolean = false,
) {
    val state = rememberLazyListState()
    LaunchedEffect(state, items.size, exhausted, loading) {
        if (exhausted) return@LaunchedEffect
        snapshotFlow { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collect { lastVisible ->
                if (!loading && items.isNotEmpty() && lastVisible >= items.size - 2) onLoadMore()
            }
    }
    LazyColumn(state = state, modifier = modifier.fillMaxSize()) {
        itemsIndexed(items, key = { _, it -> "${it.threadId}-${it.title}" }) { index, item ->
            ThreadCard(item, onClick = { onOpen(item) })
            if (index < items.lastIndex) MtDivider(startIndent = 16.dp)
        }
        item {
            when {
                loading -> Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp))
                }
                exhausted -> Box(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("没有更多了", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
                items.isNotEmpty() -> Box(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    MtButton(text = "加载更多", onClick = onLoadMore, primary = false)
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
