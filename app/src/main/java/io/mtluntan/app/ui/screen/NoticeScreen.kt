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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import io.mtluntan.app.MTLuntanApp
import io.mtluntan.app.domain.model.ChatMessage
import io.mtluntan.app.domain.model.Notice
import io.mtluntan.app.domain.model.PmSession
import io.mtluntan.app.ui.navigation.Routes
import io.mtluntan.app.util.CopyUtil
import io.mtluntan.app.util.Refresh
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Menu

/**
 * 消息页：通知 + 私信两个 Tab。
 *
 * 角标基线按账号隔离（Java 版 v3.4 修过的「切号后全屏历史变新消息」），
 * 「全部已读」按钮直接打服务端的已读接口，避免只清了本地角标、服务端还是未读。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoticeScreen(app: MTLuntanApp, nav: NavHostController? = null, onOpenDrawer: () -> Unit = {}) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    var notices by remember { mutableStateOf<List<Notice>>(emptyList()) }
    var pms by remember { mutableStateOf<List<PmSession>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    val generation by Refresh.generation.collectAsStateWithLifecycle(initialValue = 0)
    val activeAccount by app.auth.activeAccount.collectAsStateWithLifecycle(initialValue = null)

    suspend fun load() {
        loading = true
        // 委托属性不能智能转换；顺便保证「这一轮加载」用的是同一个账号
        val account = activeAccount
        // 先清空：切号后如果这一秒还在等网络，界面不能继续显示上一个账号的消息
        notices = emptyList()
        pms = emptyList()
        io.mtluntan.app.util.UnreadState.clear(account)
        try {
            if (account == null) {
                error = "登录后可以查看通知和私信"
                loading = false
                return
            }
            notices = app.forum.notices()
            pms = app.forum.pms()
            error = ""
            // 记录基线（按账号）—— 切号不会把历史消息全标成新消息
            app.settings.setBadgeBaseline(
                account,
                "${notices.count { it.isNew }}:${pms.sumOf { it.unread }}",
            )
            io.mtluntan.app.util.UnreadState.update(
                account = account,
                notices = notices.count { it.isNew },
                pms = pms.sumOf { it.unread },
            )
        } catch (t: Throwable) {
            error = t.message ?: "加载失败"
        }
        loading = false
    }

    // 账号变了要重载；点当前 Tab 刷新也要重载
    LaunchedEffect(generation, activeAccount) { load() }

    val tabTick by Refresh.tabTick.collectAsStateWithLifecycle(initialValue = 0)
    LaunchedEffect(tabTick) { if (tabTick > 0) load() }

    // 切号时立刻把角标归零，避免「A 账号的红点留在 B 账号上」
    LaunchedEffect(activeAccount) { io.mtluntan.app.util.UnreadState.clear(activeAccount) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("消息") },
                navigationIcon = { IconButton(onClick = onOpenDrawer) { Icon(Icons.Filled.Menu, "菜单") } },
                actions = {
                    IconButton(onClick = { scope.launch { load() } }) { Icon(Icons.Filled.Refresh, "刷新") }
                    IconButton(onClick = {
                        scope.launch {
                            app.forum.markNoticesRead()
                            io.mtluntan.app.util.UnreadState.markAllRead()
                            CopyUtil.toast(context, "已请求服务端标记已读")
                            load()
                        }
                    }) { Icon(Icons.Filled.DoneAll, "全部已读") }
                },
            )
        },
    ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("通知 ${if (notices.count { it.isNew } > 0) "·${notices.count { it.isNew }}" else ""}") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("私信 ${if (pms.sumOf { it.unread } > 0) "·${pms.sumOf { it.unread }}" else ""}") })
            }
            when {
                activeAccount == null -> MessageBox("当前是游客模式\n登录后这里会显示通知与私信")
                loading && notices.isEmpty() && pms.isEmpty() -> LoadingBox()
                error.isNotEmpty() && notices.isEmpty() && pms.isEmpty() -> MessageBox(error)
                tab == 0 -> {
                    if (notices.isEmpty()) MessageBox("暂无通知")
                    else LazyColumn {
                        items(notices, key = { "${it.type}-${it.url}-${it.body.take(20)}" }) { n ->
                            NoticeRow(n, onAction = {
                                if (n.url.isNotBlank()) {
                                    val tid = io.mtluntan.app.util.UrlUtil.tid(n.url)
                                    if (tid > 0) {
                                        nav?.navigate(Routes.thread(tid))
                                        return@NoticeRow
                                    }
                                    val uid = io.mtluntan.app.util.UrlUtil.uid(n.url)
                                    if (uid > 0) {
                                        nav?.navigate(Routes.profile(uid))
                                        return@NoticeRow
                                    }
                                }
                                CopyUtil.copy(context, n.body, "通知内容已复制")
                            })
                        }
                    }
                }
                else -> {
                    if (pms.isEmpty()) MessageBox("暂无私信（或需要登录）")
                    else LazyColumn {
                        items(pms, key = { it.uid }) { session ->
                            PmRow(session) { nav?.navigate(Routes.pm(session.uid, session.username)) }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 通知卡片：默认折叠 3 行，点「展开」看全文（对应 Java 版 NoticeActivity 的详情），
 * 长通知不再被截断——这是 v2.2 用户反馈最多的一条。
 */
@Composable
private fun NoticeRow(n: Notice, onAction: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val long = n.body.length > 80 || n.body.count { it == '\n' } > 2
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (n.isNew) {
                    BadgedBox(badge = { Badge { Text("新") } }) {
                        Text(typeLabel(n.type), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    Text(typeLabel(n.type), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                }
                Spacer(Modifier.weight(1f))
                Text(n.time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                n.body.ifBlank { "（无内容）" },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { expanded = !expanded },
            )
            if (n.fromAuthor.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(n.fromAuthor, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (long) {
                    TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "收起" else "展开全文") }
                }
                Spacer(Modifier.weight(1f))
                val url = n.url
                val copyText = buildString {
                    appendLine(n.body)
                    if (n.fromAuthor.isNotBlank()) appendLine("—— ${n.fromAuthor} ${n.time}")
                    if (url.isNotBlank()) append(url)
                }
                TextButton(onClick = { CopyUtil.copy(context, copyText, "通知内容已复制") }) { Text("复制") }
                TextButton(onClick = onAction) { Text(if (url.isNotBlank()) "打开" else "查看") }
            }
        }
    }
}

private fun typeLabel(type: Int): String = when (type) {
    1 -> "私信"
    2 -> "系统"
    3 -> "好友"
    else -> "回复"
}

@Composable
private fun PmRow(session: PmSession, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable(onClick = onClick).padding(12.dp),
        ) {
            AsyncImage(model = session.avatarUrl, contentDescription = null, modifier = Modifier.size(40.dp).clip(CircleShape))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(session.username.ifBlank { "用户 ${session.uid}" }, style = MaterialTheme.typography.titleSmall)
                Text(session.lastMessage, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(session.time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                if (session.unread > 0) {
                    Spacer(Modifier.height(4.dp))
                    Badge { Text(session.unread.toString()) }
                }
            }
        }
    }
}

/** 私信会话：气泡 + 输入框。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PmChatScreen(app: MTLuntanApp, nav: NavHostController, uid: Long, name: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var input by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var sending by remember { mutableStateOf(false) }
    var formhash by remember { mutableStateOf("") }

    suspend fun load() {
        loading = true
        try {
            val myName = app.auth.activeAccountName().orEmpty()
            messages = app.forum.chatMessages(uid, myName)
            if (formhash.isEmpty()) {
                runCatching { formhash = app.forum.signState().second.takeIf { it.length > 6 } ?: "" }
            }
        } catch (t: Throwable) {
            CopyUtil.toast(context, t.message ?: "加载失败")
        }
        loading = false
    }

    LaunchedEffect(uid) { load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(name.ifBlank { "私信" }) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                },
                actions = {
                    IconButton(onClick = { scope.launch { load() } }) { Icon(Icons.Filled.Refresh, "刷新") }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text("发消息…") },
                        maxLines = 3,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(6.dp))
                    IconButton(
                        enabled = !sending && input.isNotBlank(),
                        onClick = {
                            val text = input.trim()
                            sending = true
                            scope.launch {
                                val ok = app.forum.sendPm(uid, text, formhash)
                                sending = false
                                if (ok) {
                                    input = ""
                                    load()
                                } else CopyUtil.toast(context, "发送失败（可能需要重新登录）")
                            }
                        },
                    ) { Icon(Icons.AutoMirrored.Filled.Send, "发送") }
                }
            }
        },
    ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad)) {
            if (loading && messages.isEmpty()) LoadingBox()
            else if (messages.isEmpty()) MessageBox("还没有聊天记录，发条消息试试")
            else LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                items(messages, key = { it.id }) { msg ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        horizontalArrangement = if (msg.fromMe) Arrangement.End else Arrangement.Start,
                    ) {
                        Surface(
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                            color = if (msg.fromMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth(0.82f),
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(msg.body, style = MaterialTheme.typography.bodyMedium)
                                if (msg.time.isNotBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(msg.time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
