package io.mtluntan.app.ui.screen

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import io.mtluntan.app.MTLuntanApp
import io.mtluntan.app.ui.components.MtCard
import io.mtluntan.app.ui.navigation.Routes
import io.mtluntan.app.util.CopyUtil
import io.mtluntan.app.util.LogCenter
import io.mtluntan.app.util.PerfLog
import kotlinx.coroutines.launch

/**
 * 记录中心：解锁决策 / 加载耗时 / 运行日志 / 历史。
 *
 * 「简洁 / 详细」双模式只影响**显示与复制内容**，底层照常记全量，
 * 这样切回详细随时能看到原因（Java 版 v3.7 的做法）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordCenterScreen(app: MTLuntanApp, nav: NavHostController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    val entries by LogCenter.entries.collectAsStateWithLifecycle(initialValue = emptyList())
    val detailMode by app.settings.recordDetailMode.collectAsStateWithLifecycle(initialValue = false)
    val history by app.local.recentHistory.collectAsStateWithLifecycle(initialValue = emptyList())
    val claims by app.local.unlockClaims().collectAsStateWithLifecycle(initialValue = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("记录中心") },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                actions = {
                    TextButton(onClick = { scope.launch { app.settings.setRecordDetailMode(!detailMode) } }) {
                        Text(if (detailMode) "详细" else "简洁")
                    }
                    IconButton(onClick = { CopyUtil.copy(context, LogCenter.dump(detailMode), "日志已复制") }) {
                        Icon(Icons.Filled.ContentCopy, "复制全部")
                    }
                    IconButton(onClick = { LogCenter.clear() }) { Icon(Icons.Filled.DeleteOutline, "清空") }
                },
            )
        },
    ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("日志 ${entries.size}") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("解锁 ${claims.size}") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("耗时 ${PerfLog.snapshot().size}") })
                Tab(selected = tab == 3, onClick = { tab = 3 }, text = { Text("历史 ${history.size}") })
            }
            when (tab) {
                0 -> {
                    if (entries.isEmpty()) MessageBox("还没有日志")
                    else LazyColumn {
                        items(entries.reversed(), key = { it.id }) { entry ->
                            MtCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp)) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        TagDot(entry.tag.name)
                                        Spacer(Modifier.width(6.dp))
                                        Text(entry.timeText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                        Spacer(Modifier.weight(1f))
                                        entry.ok?.let {
                                            Icon(
                                                imageVector = if (it) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                                tint = if (it) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                            )
                                        }
                                        Icon(
                                            Icons.Filled.ContentCopy,
                                            contentDescription = "复制",
                                            modifier = Modifier.size(14.dp).clickable {
                                                CopyUtil.copy(context, entry.render(detailMode), "已复制这条日志")
                                            },
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(entry.title, style = MaterialTheme.typography.bodyMedium)
                                    if (detailMode && entry.detail.isNotBlank()) {
                                        Text(entry.detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    if (claims.isEmpty()) MessageBox("还没有自动解锁记录")
                    else LazyColumn {
                        items(claims, key = { it.tid }) { claim ->
                            ListRow(
                                title = "帖子 ${claim.tid} · ${if (claim.ok) "回复成功" else "待确认"}",
                                subtitle = buildString {
                                    append(java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(claim.at)))
                                    if (claim.replyText.isNotBlank()) append(" · ${claim.replyText}")
                                },
                                onClick = { nav.navigate(Routes.thread(claim.tid)) },
                                action = {
                                    Icon(Icons.Filled.OpenInNew, "打开帖子", modifier = Modifier.size(18.dp))
                                },
                            )
                        }
                    }
                }
                2 -> {
                    val spans = PerfLog.snapshot().reversed()
                    if (spans.isEmpty()) MessageBox("还没有耗时记录")
                    else LazyColumn {
                        items(spans, key = { it.name + it.totalMs + it.netMs }) { span ->
                            ListRow(
                                title = "${span.name} · ${span.totalMs}ms",
                                subtitle = "网络 ${span.netMs}ms · 解析 ${span.parseMs}ms" + if (span.extra.isNotBlank()) " · ${span.extra}" else "",
                            )
                        }
                    }
                }
                else -> {
                    if (history.isEmpty()) MessageBox("还没有浏览历史")
                    else LazyColumn {
                        items(history, key = { it.id }) { record ->
                            ListRow(
                                title = record.title,
                                subtitle = "${record.boardName} · ${record.authorName}",
                                onClick = { nav.navigate(Routes.thread(record.tid)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TagDot(tag: String) {
    val color = when (tag) {
        "UNLOCK" -> MaterialTheme.colorScheme.tertiary
        "PERF" -> MaterialTheme.colorScheme.secondary
        "SIGN" -> MaterialTheme.colorScheme.primary
        "AI" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outline
    }
    Surface(color = color.copy(alpha = 0.18f), shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)) {
        Text(
            tag,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
        )
    }
}

/** 草稿箱。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DraftsScreen(app: MTLuntanApp, nav: NavHostController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val drafts by app.local.drafts.collectAsStateWithLifecycle(initialValue = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("草稿箱（${drafts.size}）") },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
            )
        },
    ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad)) {
            if (drafts.isEmpty()) MessageBox("没有草稿\n编辑器里会自动保存")
            else LazyColumn {
                items(drafts, key = { it.id }) { draft ->
                    ListRow(
                        title = draft.title.ifBlank { "（无标题）" },
                        subtitle = draft.content.take(80),
                        trailing = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(draft.updatedAt)),
                        onClick = {
                            nav.navigate(
                                when (draft.type) {
                                    "newthread" -> Routes.newThread(draft.fid)
                                    else -> Routes.reply(draft.tid)
                                }
                            )
                        },
                        action = {
                            IconButton(onClick = {
                                scope.launch {
                                    app.local.deleteDraft(
                                        when {
                                            draft.type == "newthread" -> "newthread:0:${draft.fid}"
                                            draft.type.startsWith("edit") -> "editreply:${draft.tid}:0"
                                            else -> "reply:${draft.tid}:${draft.fid}"
                                        }
                                    )
                                    CopyUtil.toast(context, "草稿已删除")
                                }
                            }) { Icon(Icons.Filled.DeleteOutline, "删除", modifier = Modifier.size(18.dp)) }
                        },
                    )
                }
            }
        }
    }
}

/** 离线帖子列表。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflinePostsScreen(app: MTLuntanApp, nav: NavHostController) {
    val scope = rememberCoroutineScope()
    val posts by app.local.offlinePosts.collectAsStateWithLifecycle(initialValue = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("离线帖子（${posts.size}）") },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
            )
        },
    ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad)) {
            if (posts.isEmpty()) MessageBox("没有离线帖子\n在帖子页右上角菜单里保存")
            else LazyColumn {
                items(posts, key = { it.tid }) { post ->
                    ListRow(
                        title = post.title.ifBlank { "帖子 ${post.tid}" },
                        subtitle = "${post.boardName} · ${post.authorName} · ${post.html.length / 1024} KB · " +
                            java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(post.savedAt)),
                        onClick = { nav.navigate(Routes.thread(post.tid)) },
                        action = {
                            IconButton(onClick = { scope.launch { app.local.removeOffline(post.tid) } }) {
                                Icon(Icons.Filled.DeleteOutline, "删除", modifier = Modifier.size(18.dp))
                            }
                        },
                    )
                }
            }
        }
    }
}
