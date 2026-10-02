package io.mtluntan.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import io.mtluntan.app.ui.components.MtCard
import io.mtluntan.app.data.parser.SocialParser
import io.mtluntan.app.domain.model.CreditItem
import io.mtluntan.app.domain.model.ThreadItem
import io.mtluntan.app.domain.model.UserProfile
import io.mtluntan.app.ui.navigation.Routes
import io.mtluntan.app.util.CopyUtil
import kotlinx.coroutines.launch

/** 用户空间：资料 + 关注/私信/拉黑 + TA 的主题。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(app: MTLuntanApp, nav: NavHostController, uid: Long) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var profile by remember { mutableStateOf<UserProfile?>(null) }
    val threads = remember { mutableStateListOf<ThreadItem>() }
    var page by remember { mutableIntStateOf(1) }
    var loading by remember { mutableStateOf(true) }
    var following by remember { mutableStateOf<Boolean?>(null) }
    var formhash by remember { mutableStateOf("") }
    val blacklist by app.local.blacklist.collectAsStateWithLifecycle(initialValue = emptyList())
    val isBlacklisted = blacklist.any { it.uid == uid }

    suspend fun load() {
        loading = true
        try {
            profile = app.forum.profile(uid)
            threads.clear()
            threads.addAll(app.forum.myThreads(uid, 1))
            following = app.local.following(uid)
            if (formhash.isEmpty()) {
                formhash = app.forum.signState().second.takeIf { it.length > 6 }.orEmpty()
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
                title = { Text(profile?.username?.ifBlank { "用户 $uid" } ?: "用户空间") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                },
                actions = {
                    IconButton(onClick = { scope.launch { load() } }) { Icon(Icons.Filled.Refresh, "刷新") }
                },
            )
        },
    ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad)) {
            if (loading && profile == null) { LoadingBox(); return@Column }
            val p = profile ?: return@Column
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(
                                    model = p.avatarUrl.ifBlank { app.auth.avatarFor(uid) },
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp).clip(CircleShape),
                                )
                                Spacer(Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(p.username.ifBlank { "用户 $uid" }, style = MaterialTheme.typography.titleLarge)
                                    if (p.groupName.isNotBlank()) Text(p.groupName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    if (p.registerTime.isNotBlank()) Text("注册：${p.registerTime}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                    if (p.lastVisit.isNotBlank()) Text("最近：${p.lastVisit}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatChip("主题", p.threads)
                                StatChip("帖子", p.posts)
                                StatChip("积分", p.credits)
                                StatChip("金币", p.goldCoin)
                            }
                            if (p.signature.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text(p.signature, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AssistChip(
                                    onClick = {
                                        scope.launch {
                                            val target = following != true
                                            val ok = app.forum.follow(uid, formhash, target)
                                            if (ok) {
                                                following = target
                                                app.local.setFollowing(uid, p.username, target)
                                                CopyUtil.toast(context, if (target) "已关注" else "已取消关注")
                                            } else CopyUtil.toast(context, "关注失败（可能需要登录）")
                                        }
                                    },
                                    label = { Text(if (following == true) "已关注（点击取消）" else "加关注") },
                                    leadingIcon = { Icon(Icons.Filled.PersonAdd, null, modifier = Modifier.size(16.dp)) },
                                )
                                AssistChip(
                                    onClick = { nav.navigate(Routes.pm(uid, p.username)) },
                                    label = { Text("发私信") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Send, null, modifier = Modifier.size(16.dp)) },
                                )
                                AssistChip(
                                    onClick = {
                                        scope.launch {
                                            if (isBlacklisted) {
                                                app.local.removeBlacklist(uid)
                                                CopyUtil.toast(context, "已移出黑名单")
                                            } else {
                                                app.local.addBlacklist(uid, p.username, "来自用户页")
                                                CopyUtil.toast(context, "已拉黑")
                                            }
                                        }
                                    },
                                    label = { Text(if (isBlacklisted) "移出黑名单" else "拉黑") },
                                    leadingIcon = { Icon(Icons.Filled.Block, null, modifier = Modifier.size(16.dp)) },
                                )
                                AssistChip(
                                    onClick = { nav.navigate(Routes.friends(uid)) },
                                    label = { Text("好友") },
                                )
                                AssistChip(
                                    onClick = { nav.navigate(Routes.CREDITS) },
                                    label = { Text("积分明细") },
                                )
                            }
                            if (p.uid > 0) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "UID ${p.uid} · 点击复制主页链接",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.clickable {
                                        CopyUtil.copy(context, "https://bbs.binmt.cc/space-uid-${p.uid}.html", "主页链接已复制")
                                    },
                                )
                            }
                        }
                    }
                }
                item {
                    Text(
                        "TA 的主题",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 4.dp),
                    )
                }
                if (threads.isEmpty()) {
                    item { Text("没有公开主题", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(14.dp)) }
                }
                items(threads, key = { "space-${it.threadId}" }) { item ->
                    ThreadCard(item, onClick = { nav.navigate(Routes.thread(item.threadId)) })
                }
                item {
                    TextButton(
                        onClick = {
                            scope.launch {
                                page++
                                val more = app.forum.myThreads(uid, page)
                                threads.addAll(more)
                                if (more.isEmpty()) CopyUtil.toast(context, "没有更多了")
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                    ) { Text("加载更多") }
                }
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: Int) {
    Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$value", style = MaterialTheme.typography.titleSmall)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

/** 好友列表。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(app: MTLuntanApp, nav: NavHostController, uid: Long) {
    val scope = rememberCoroutineScope()
    var friends by remember { mutableStateOf<List<SocialParser.FriendEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(uid) {
        loading = true
        friends = runCatching { app.forum.friends(uid) }.getOrDefault(emptyList())
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("好友 / 关注") },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                actions = {
                    IconButton(onClick = {
                        scope.launch { friends = runCatching { app.forum.friends(uid) }.getOrDefault(emptyList()) }
                    }) { Icon(Icons.Filled.Refresh, "刷新") }
                },
            )
        },
    ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad)) {
            if (loading) LoadingBox()
            else if (friends.isEmpty()) MessageBox("没有公开的好友列表")
            else LazyColumn {
                items(friends, key = { it.uid }) { f ->
                    MtCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable { nav.navigate(Routes.profile(f.uid)) }
                                .padding(12.dp),
                        ) {
                            AsyncImage(model = f.avatarUrl, contentDescription = null, modifier = Modifier.size(40.dp).clip(CircleShape))
                            Spacer(Modifier.width(10.dp))
                            Text(f.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            if (f.online) Text("在线", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

/** 本地黑名单。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlacklistScreen(app: MTLuntanApp, nav: NavHostController) {
    val scope = rememberCoroutineScope()
    val list by app.local.blacklist.collectAsStateWithLifecycle(initialValue = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("黑名单（${list.size}）") },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
            )
        },
    ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad)) {
            if (list.isEmpty()) MessageBox("黑名单是空的\n在帖子或用户页可以把某人拉黑")
            else LazyColumn {
                items(list, key = { it.uid }) { entry ->
                    ListRow(
                        title = entry.username.ifBlank { "UID ${entry.uid}" },
                        subtitle = buildString {
                            append("UID ${entry.uid}")
                            if (entry.reason.isNotBlank()) append(" · ${entry.reason}")
                        },
                        onClick = { nav.navigate(Routes.profile(entry.uid)) },
                        action = {
                            IconButton(onClick = { scope.launch { app.local.removeBlacklist(entry.uid) } }) {
                                Icon(Icons.Filled.DeleteOutline, "移除")
                            }
                        },
                    )
                }
            }
        }
    }
}

/** 积分明细。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditsScreen(app: MTLuntanApp, nav: NavHostController) {
    var items by remember { mutableStateOf<List<CreditItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        loading = true
        items = runCatching { app.forum.credits(0) }.getOrDefault(emptyList())
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("积分明细") },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
            )
        },
    ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad)) {
            if (loading) LoadingBox()
            else if (items.isEmpty()) MessageBox("暂无可显示的积分信息（需要登录）")
            else LazyColumn {
                items(items) { c ->
                    ListRow(
                        title = c.name,
                        subtitle = listOfNotNull(c.delta.takeIf { it.isNotBlank() }, c.time.takeIf { it.isNotBlank() }).joinToString(" · "),
                        trailing = c.value,
                    )
                }
            }
        }
    }
}

/** 搜索：结果按页加载（每页之间留间隔，避免风控）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(app: MTLuntanApp, nav: NavHostController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var keyword by remember { mutableStateOf("") }
    var page by remember { mutableIntStateOf(1) }
    var loading by remember { mutableStateOf(false) }
    val results = remember { mutableStateListOf<ThreadItem>() }
    var error by remember { mutableStateOf("") }

    fun search(reset: Boolean) {
        if (keyword.isBlank()) return
        loading = true
        scope.launch {
            try {
                if (reset) { results.clear(); page = 1 }
                val list = app.forum.search(keyword.trim(), page)
                if (list.isEmpty() && page == 1) error = "没有找到相关帖子"
                else error = ""
                results.addAll(list)
            } catch (t: Throwable) {
                error = t.message ?: "搜索失败"
            }
            loading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("搜索") },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
            )
        },
    ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    placeholder = { Text("搜索帖子关键词") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                IconButton(enabled = !loading, onClick = { search(true) }) { Icon(Icons.Filled.Search, "搜索") }
            }
            if (loading) { LoadingBox(); return@Column }
            if (error.isNotEmpty() && results.isEmpty()) { MessageBox(error); return@Column }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(results, key = { "search-${it.threadId}-${it.title.take(10)}" }) { item ->
                    ThreadCard(item, onClick = { nav.navigate(Routes.thread(item.threadId)) })
                }
                item {
                    TextButton(
                        onClick = { page++; search(false) },
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                    ) { Text("加载下一页") }
                }
            }
        }
    }
}

/** 通用列表行。 */
@Composable
fun ListRow(
    title: String,
    subtitle: String = "",
    trailing: String = "",
    onClick: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (subtitle.isNotBlank()) {
                        Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (trailing.isNotBlank()) Text(trailing, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                action?.invoke()
            }
            HorizontalDivider()
        }
    }
}
