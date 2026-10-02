package io.mtluntan.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import io.mtluntan.app.MTLuntanApp
import io.mtluntan.app.data.parser.SocialParser
import io.mtluntan.app.domain.model.ChatMessage
import io.mtluntan.app.domain.model.Notice
import io.mtluntan.app.domain.model.PmSession
import io.mtluntan.app.ui.components.MtCard
import io.mtluntan.app.ui.components.mtNestedSurface
import io.mtluntan.app.ui.motion.pressScale
import io.mtluntan.app.ui.navigation.Routes
import io.mtluntan.app.util.CopyUtil
import io.mtluntan.app.util.Refresh
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Mutex

/**
 * 消息页（重做版，排版对齐参考项目 `fragment_notice.xml` + `NoticeFragment.java`）。
 *
 * 参考项目的结构是「**一张圆角卡片的六个入口**」：
 *   我的消息(私信) / 我的粉丝 / 我的帖子 / 坛友互动 / 系统提醒 / 应用提醒
 * 每行 = 语义色圆底矢量图标 + 标题 + 副标题 + 未读数角标 + 右箭头，
 * 行与行之间是缩进 62dp 的 0.5dp 细分隔线，点哪一行进各自的列表页。
 *
 * 本实现同样只用原生 Compose 列表渲染（不套 WebView）：
 *  - 我的消息 → [PmListScreen]（私信会话）
 *  - 我的粉丝 → [FollowersScreen]
 *  - 其余四类 → [NoticeListScreen]（`home.php?mod=space&do=notice&view=...`）
 *
 * 角标口径：通知类取「服务器标记为未读的条数」，私信取未读条数，
 * 粉丝取「上次查看之后新增的粉丝数」（本地快照比对，参考项目 NoticeBadgeManager 的思路）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoticeScreen(app: MTLuntanApp, nav: NavHostController? = null, onOpenDrawer: () -> Unit = {}) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val activeAccount by app.auth.activeAccount.collectAsStateWithLifecycle(initialValue = null)
    val generation by Refresh.generation.collectAsStateWithLifecycle(initialValue = 0)
    val tabTick by Refresh.tabTick.collectAsStateWithLifecycle(initialValue = 0)

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var pmCount by remember { mutableStateOf(0) }
    var fanCount by remember { mutableStateOf(0) }
    var postCount by remember { mutableStateOf(0) }
    var interactiveCount by remember { mutableStateOf(0) }
    var systemCount by remember { mutableStateOf(0) }
    var appCount by remember { mutableStateOf(0) }
    var pmPreview by remember { mutableStateOf("私信与对话") }
    var fanPreview by remember { mutableStateOf("关注你的人") }
    var postPreview by remember { mutableStateOf("有人回复了你的帖子") }
    var interactivePreview by remember { mutableStateOf("评论、点赞与 @ 提醒") }
    var systemPreview by remember { mutableStateOf("系统通知与审核结果") }
    var appPreview by remember { mutableStateOf("版本更新与公告") }

    suspend fun load() {
        loading = true
        // 启动时账号是异步恢复的：先等它回来，避免「明明登录了却提示需要登录」
        val ready = app.auth.awaitActiveAccount()
        val account = activeAccount ?: ready
        if (account == null) {
            error = "登录后可以查看通知和私信"
            pmCount = 0; fanCount = 0; postCount = 0
            interactiveCount = 0; systemCount = 0; appCount = 0
            loading = false
            return
        }
        error = ""
        io.mtluntan.app.util.UnreadState.clear(account)
        try {
            // 六类并行拉取：串行会明显变慢（参考项目也是 4 线程并发）
            coroutineScope {
                val pmTask = async { runCatching { app.forum.pms() }.getOrDefault(emptyList()) }
                val fanTask = async { runCatching { app.forum.followers() }.getOrDefault(emptyList()) }
                val postTask = async { runCatching { app.forum.notices("mypost") }.getOrDefault(emptyList()) }
                val interactiveTask = async { runCatching { app.forum.notices("interactive") }.getOrDefault(emptyList()) }
                val systemTask = async { runCatching { app.forum.notices("system") }.getOrDefault(emptyList()) }
                val appTask = async { runCatching { app.forum.notices("app") }.getOrDefault(emptyList()) }

                val pms = pmTask.await()
                val fans = fanTask.await()
                val posts = postTask.await()
                val interactive = interactiveTask.await()
                val systems = systemTask.await()
                val apps = appTask.await()

                pmCount = pms.sumOf { it.unread }
                pmPreview = pms.firstOrNull()?.let { "${it.username}：${it.lastMessage.take(18)}" } ?: "私信与对话"
                fanCount = newFanCount(app, account, fans)
                fanPreview = fans.firstOrNull()?.let { "${it.name} 等 ${fans.size} 人" } ?: "关注你的人"
                postCount = posts.count { it.isNew }
                postPreview = posts.firstOrNull()?.body?.take(20) ?: "有人回复了你的帖子"
                interactiveCount = interactive.count { it.isNew }
                interactivePreview = interactive.firstOrNull()?.body?.take(20) ?: "评论、点赞与 @ 提醒"
                systemCount = systems.count { it.isNew }
                systemPreview = systems.firstOrNull()?.body?.take(20) ?: "系统通知与审核结果"
                appCount = apps.count { it.isNew }
                appPreview = apps.firstOrNull()?.body?.take(20) ?: "版本更新与公告"

                // 基线格式：通知数:私信数|fans:<粉丝快照>
                // 写计数时必须**保留** fans 段，否则「我的粉丝」的新增角标会被清掉
                val fansSeg = savedFansSegment(runCatching { app.settings.badgeBaseline(account) }.getOrDefault(""))
                app.settings.setBadgeBaseline(
                    account,
                    "${postCount + interactiveCount + systemCount + appCount}:$pmCount" +
                        if (fansSeg.isNotEmpty()) "|$fansSeg" else "",
                )
                io.mtluntan.app.util.UnreadState.update(
                    account = account,
                    notices = postCount + interactiveCount + systemCount + appCount,
                    pms = pmCount,
                )
            }
        } catch (t: Throwable) {
            error = t.message ?: "加载失败"
        }
        loading = false
    }

    // 首屏自动加载（用户反馈「点消息不自动加载，要手点刷新」）：
    // 之前 key 里带了 activeAccount，账号从 null→名字时会把「等账号就绪」的那次加载**取消**掉，
    // 于是第一屏永远停在异常态。现在拆开：进入必有一次性加载，账号变化只是防抖补一次。
    var loadToken by remember { mutableIntStateOf(0) }
    val loadMutex = remember { Mutex() }
    LaunchedEffect(Unit) { loadMutex.withLock { load() } }
    LaunchedEffect(activeAccount) {
        if (activeAccount != null) {
            kotlinx.coroutines.delay(350)   // 防抖：和首屏那次合并，避免双请求互相打架
            loadMutex.withLock { load() }
        }
    }
    LaunchedEffect(generation) { if (generation > 0) loadMutex.withLock { load() } }
    LaunchedEffect(tabTick) { if (tabTick > 0) loadMutex.withLock { load() } }
    // 首屏异常自动重试一次（网络抖动时用户不用手点刷新）
    LaunchedEffect(error) {
        if (error.isNotEmpty() && error != "登录后可以查看通知和私信" && loadToken == 0) {
            loadToken = 1
            kotlinx.coroutines.delay(900)
            loadMutex.withLock { load() }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("消息") },
                navigationIcon = { IconButton(onClick = onOpenDrawer) { Icon(Icons.Filled.Menu, "菜单") } },
                actions = {
                    IconButton(onClick = { scope.launch { load() } }) { Icon(Icons.Filled.Refresh, "刷新") }
                    TextButton(onClick = {
                        scope.launch {
                            app.forum.markNoticesRead()
                            io.mtluntan.app.util.UnreadState.markAllRead()
                            pmCount = 0; fanCount = 0; postCount = 0
                            interactiveCount = 0; systemCount = 0; appCount = 0
                            CopyUtil.toast(context, "已请求服务端标记已读")
                            load()
                        }
                    }) { Text("全部已读") }
                },
            )
        },
    ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad)) {
            when {
                error.isNotEmpty() -> MessageBox(error)
                loading -> LoadingBox()
                else -> LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
                    item {
                        Spacer(Modifier.height(6.dp))
                        // 一张卡片装六个入口（参考项目就是一个 MaterialCardView 包住 6 行）
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .mtNestedSurface(RoundedCornerShape(14.dp)),
                        ) {
                            val entries = listOf(
                                MsgEntry("pm", "我的消息", pmPreview, Icons.Filled.Chat, NoticeKind.Chat, pmCount),
                                MsgEntry("follower", "我的粉丝", fanPreview, Icons.Filled.Face, NoticeKind.Fan, fanCount),
                                MsgEntry("mypost", "我的帖子", postPreview, Icons.Filled.Forum, NoticeKind.Post, postCount),
                                MsgEntry("interactive", "坛友互动", interactivePreview, Icons.Filled.Notifications, NoticeKind.Interactive, interactiveCount),
                                MsgEntry("system", "系统提醒", systemPreview, Icons.Filled.AccessTime, NoticeKind.System, systemCount),
                                MsgEntry("app", "应用提醒", appPreview, Icons.Filled.Apps, NoticeKind.App, appCount),
                            )
                            entries.forEachIndexed { index, entry ->
                                MessageEntryRow(entry) {
                                    when (entry.key) {
                                        "pm" -> nav?.navigate(Routes.PM_LIST)
                                        "follower" -> nav?.navigate(Routes.FOLLOWERS)
                                        else -> nav?.navigate(Routes.noticeList(entry.key))
                                    }
                                }
                                if (index < entries.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 62.dp),
                                        thickness = 0.5.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "点分类进入列表；「全部已读」会同时通知服务端。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                        Spacer(Modifier.height(96.dp))
                    }
                }
            }
        }
    }
}

/** 六个入口的语义色（参考项目每类一个颜色 + 14% 淡底圆）。 */
private enum class NoticeKind(val color: Color, val icon: ImageVector) {
    Chat(Color(0xFF3B82F6), Icons.Filled.Chat),
    Fan(Color(0xFFEC4899), Icons.Filled.Face),
    Post(Color(0xFF10B981), Icons.Filled.Forum),
    Interactive(Color(0xFFF59E0B), Icons.Filled.Notifications),
    System(Color(0xFF8B5CF6), Icons.Filled.AccessTime),
    App(Color(0xFF0EA5E9), Icons.Filled.Apps),
}

private data class MsgEntry(
    val key: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val kind: NoticeKind,
    val badge: Int,
)

/** 一个入口行：38dp 淡色圆底图标 + 标题/副标题 + 未读角标 + 右箭头 + 按压反馈。 */
@Composable
private fun MessageEntryRow(entry: MsgEntry, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interaction, pressedScale = 0.98f)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(entry.kind.color.copy(alpha = 0.14f)),
        ) {
            Icon(entry.kind.icon, entry.title, tint = entry.kind.color, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
            Text(entry.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(
                entry.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (entry.badge > 0) {
            Badge { Text(if (entry.badge > 99) "99+" else entry.badge.toString()) }
        }
        Spacer(Modifier.width(6.dp))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.outline,
        )
    }
}

/**
 * 粉丝角标 = 上次查看之后新增的粉丝数。
 *
 * 参考项目用 NoticeBadgeManager 存快照，这里复用它同一套思路：
 * 把「当前粉丝 uid 串」和上次的对比，新增了几个就显示几；
 * 打开粉丝列表时会把快照更新掉（所以看过之后角标会归零）。
 */
private fun fansSegmentOf(saved: String): String =
    saved.split("|").firstOrNull { it.startsWith("fans:") }.orEmpty()

private val savedFansSegment = ::fansSegmentOf

private suspend fun newFanCount(app: MTLuntanApp, account: String, fans: List<SocialParser.FriendEntry>): Int {
    if (fans.isEmpty()) return 0
    val savedFanSig = fansSegmentOf(runCatching { app.settings.badgeBaseline(account) }.getOrDefault(""))
        .removePrefix("fans:")
    // 第一次进消息页（还没有快照）不算「新增」，先建立基线
    if (savedFanSig.isBlank()) return 0
    val seen = savedFanSig.split(",").filter { it.isNotBlank() }.toSet()
    return fans.count { fan -> !seen.contains(fan.uid.toString()) }
}

/** 记住已看过的粉丝快照（进粉丝列表时调用）。 */
private suspend fun markFansViewed(app: MTLuntanApp, account: String, fans: List<SocialParser.FriendEntry>) {
    val signature = "fans:" + fans.take(60).joinToString(",") { it.uid.toString() }
    val saved = runCatching { app.settings.badgeBaseline(account) }.getOrDefault("")
    val kept = saved.split("|").filter { it.isNotBlank() && !it.startsWith("fans:") }
    runCatching { app.settings.setBadgeBaseline(account, (kept + signature).joinToString("|")) }
}

// ---------------------------------------------------------------------------
// 私信列表
// ---------------------------------------------------------------------------

/** 私信会话列表（消息页「我的消息」入口，item_message.xml 的排版）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PmListScreen(app: MTLuntanApp, nav: NavHostController) {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    var sessions by remember { mutableStateOf<List<PmSession>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }

    suspend fun load() {
        loading = true
        app.auth.awaitActiveAccount()
        try {
            sessions = app.forum.pms()
            error = ""
        } catch (t: Throwable) {
            error = t.message ?: "加载失败"
        }
        loading = false
    }
    LaunchedEffect(Unit) { load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的消息") },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                actions = { IconButton(onClick = { scope.launch { load() } }) { Icon(Icons.Filled.Refresh, "刷新") } },
            )
        },
    ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad)) {
            when {
                loading -> LoadingBox()
                error.isNotEmpty() -> MessageBox(error)
                sessions.isEmpty() -> MessageBox("暂无私信")
                else -> LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp)) {
                    item { Spacer(Modifier.height(6.dp)) }
                    items(sessions, key = { it.uid }) { session ->
                        PmSessionRow(session) {
                            if (session.uid > 0) nav.navigate(Routes.pm(session.uid, session.username))
                            else CopyUtil.toast(context, "这个会话缺少 uid，无法打开")
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    item { Spacer(Modifier.height(90.dp)) }
                }
            }
        }
    }
}

@Composable
private fun PmSessionRow(session: PmSession, onClick: () -> Unit) {
    var lastMessage by remember(session.uid) {
        mutableStateOf(
            session.lastMessage
                .replace("回复", "回复")
                .replace(Regex("^\\s*[\\d/R<\\-\\s]+"), "")
                .replace(Regex("\\s+"), " ")
                .trim()
        )
    }
    MtCard(
        modifier = Modifier.fillMaxWidth(),
        nested = true,
        shape = RoundedCornerShape(12.dp),
        padding = PaddingValues(12.dp),
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model = session.avatarUrl, contentDescription = null, modifier = Modifier.size(40.dp).clip(CircleShape))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    session.username.ifBlank { "用户 ${session.uid}" },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    lastMessage.ifBlank { "（无内容）" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(session.time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                if (session.unread > 0) {
                    Spacer(Modifier.height(4.dp))
                    Badge { Text(if (session.unread > 99) "99+" else session.unread.toString()) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 通知分类列表
// ---------------------------------------------------------------------------

/** 分类列表标题：与消息页入口对应。 */
private fun noticeTitle(view: String): String = when (view) {
    "mypost" -> "我的帖子"
    "interactive" -> "坛友互动"
    "system" -> "系统提醒"
    "app" -> "应用提醒"
    else -> "提醒"
}

/** 通知分类列表页（我的帖子 / 坛友互动 / 系统提醒 / 应用提醒）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoticeListScreen(app: MTLuntanApp, nav: NavHostController, view: String) {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    var items by remember { mutableStateOf<List<Notice>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    val activeAccount by app.auth.activeAccount.collectAsStateWithLifecycle(initialValue = null)

    suspend fun load() {
        loading = true
        app.auth.awaitActiveAccount()
        try {
            items = app.forum.notices(view)
            error = ""
        } catch (t: Throwable) {
            error = t.message ?: "加载失败"
        }
        loading = false
    }
    // 同消息页：view 变化必加载；账号就绪后再防抖补一次（不再取消首屏那次加载）
    LaunchedEffect(view) { load() }
    LaunchedEffect(view, activeAccount) {
        if (activeAccount != null) {
            kotlinx.coroutines.delay(350)
            load()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(noticeTitle(view)) },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                actions = {
                    IconButton(onClick = { scope.launch { load() } }) { Icon(Icons.Filled.Refresh, "刷新") }
                    IconButton(onClick = {
                        scope.launch {
                            app.forum.markNoticesRead()
                            CopyUtil.toast(context, "已请求标记已读")
                            load()
                        }
                    }) { Icon(Icons.Filled.DoneAll, "全部已读") }
                },
            )
        },
    ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad)) {
            when {
                loading -> LoadingBox()
                error.isNotEmpty() -> MessageBox(error)
                items.isEmpty() -> MessageBox("这里还没有内容")
                else -> LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp)) {
                    item { Spacer(Modifier.height(6.dp)) }
                    itemsIndexed(items, key = { i, n -> "n$i-${n.url.hashCode()}" }) { _, n ->
                        NoticeItemCard(n) {
                            val tid = io.mtluntan.app.util.UrlUtil.tid(n.url)
                            val uid = io.mtluntan.app.util.UrlUtil.uid(n.url)
                            when {
                                tid > 0 -> nav.navigate(Routes.thread(tid))
                                uid > 0 && uid != 0L -> nav.navigate(Routes.profile(uid))
                                else -> CopyUtil.copy(context, n.body, "内容已复制")
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    item { Spacer(Modifier.height(90.dp)) }
                }
            }
        }
    }
}

/** 通知卡片：头像 + 名字 + 时间 + 未读点 + 类型标签 + 正文摘要（item_message.xml 排版）。 */
@Composable
private fun NoticeItemCard(n: Notice, onClick: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val long = n.body.length > 90 || n.body.count { it == '\n' } > 2
    MtCard(
        modifier = Modifier.fillMaxWidth(),
        nested = true,
        shape = RoundedCornerShape(12.dp),
        padding = PaddingValues(12.dp),
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val avatarUid = io.mtluntan.app.util.UrlUtil.uid(n.url)
            AsyncImage(
                model = if (avatarUid > 0) io.mtluntan.app.data.network.Site.avatarUrl(avatarUid) else "",
                contentDescription = null,
                modifier = Modifier.size(40.dp).clip(CircleShape),
            )
            Column(modifier = Modifier.padding(start = 10.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        n.fromAuthor.ifBlank { typeLabel(n.type) },
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(typeLabel(n.type), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                if (n.time.isNotBlank()) {
                    Text(n.time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }
            if (n.isNew) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            n.body.ifBlank { "（无内容）" },
            style = MaterialTheme.typography.bodyMedium,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
            if (long) TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "收起" else "展开") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = {
                CopyUtil.copy(context, buildString {
                    appendLine(n.body)
                    if (n.fromAuthor.isNotBlank()) appendLine("—— ${n.fromAuthor} ${n.time}")
                    if (n.url.isNotBlank()) append(n.url)
                }, "已复制")
            }) { Text("复制") }
            TextButton(onClick = onClick) { Text(if (n.url.isNotBlank()) "打开" else "查看") }
        }
    }
}

private fun typeLabel(type: Int): String = when (type) {
    1 -> "私信"
    2 -> "系统"
    3 -> "关注"
    else -> "回复"
}

// ---------------------------------------------------------------------------
// 我的粉丝
// ---------------------------------------------------------------------------

/** 我的粉丝（参考项目跳 FriendListActivity(mode=followers)）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowersScreen(app: MTLuntanApp, nav: NavHostController) {
    val scope = rememberCoroutineScope()
    val activeAccount by app.auth.activeAccount.collectAsStateWithLifecycle(initialValue = null)
    var fans by remember { mutableStateOf<List<SocialParser.FriendEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }

    suspend fun load() {
        loading = true
        val account = app.auth.awaitActiveAccount()
        try {
            fans = app.forum.followers()
            error = ""
            val name = account ?: app.auth.activeAccountName()
            if (name != null) markFansViewed(app, name, fans)
        } catch (t: Throwable) {
            error = t.message ?: "加载失败"
        }
        loading = false
    }
    LaunchedEffect(Unit) { load() }
    LaunchedEffect(activeAccount) {
        if (activeAccount != null) {
            kotlinx.coroutines.delay(350)
            load()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的粉丝") },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                actions = { IconButton(onClick = { scope.launch { load() } }) { Icon(Icons.Filled.Refresh, "刷新") } },
            )
        },
    ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad)) {
            when {
                loading -> LoadingBox()
                error.isNotEmpty() -> MessageBox(error)
                fans.isEmpty() -> MessageBox("暂时没读到粉丝列表\n（也可能站点模板变了，去空间页看「粉丝」）")
                else -> LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp)) {
                    item { Spacer(Modifier.height(6.dp)) }
                    items(fans, key = { it.uid }) { fan ->
                        MtCard(
                            modifier = Modifier.fillMaxWidth(),
                            nested = true,
                            shape = RoundedCornerShape(12.dp),
                            padding = PaddingValues(12.dp),
                            onClick = { nav.navigate(Routes.profile(fan.uid)) },
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(model = fan.avatarUrl, contentDescription = null, modifier = Modifier.size(40.dp).clip(CircleShape))
                                Spacer(Modifier.width(10.dp))
                                Text(fan.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (fan.online) Text("在线", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.outline,
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    item { Spacer(Modifier.height(90.dp)) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 私信会话
// ---------------------------------------------------------------------------

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
        app.auth.awaitActiveAccount()
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
                            shape = RoundedCornerShape(12.dp),
                            color = if (msg.fromMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
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
