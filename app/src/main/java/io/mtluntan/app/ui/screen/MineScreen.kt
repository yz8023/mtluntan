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
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import io.mtluntan.app.domain.model.Account
import io.mtluntan.app.ui.navigation.Routes
import io.mtluntan.app.util.CopyUtil
import io.mtluntan.app.util.Refresh
import kotlinx.coroutines.launch

/** 我的：账号速切 / 签到 / 各种本地入口。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MineScreen(app: MTLuntanApp, nav: NavHostController) {
    var jumpOpen by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val accounts by app.auth.accounts.collectAsStateWithLifecycle(initialValue = emptyList())
    val activeName by app.auth.activeAccount.collectAsStateWithLifecycle(initialValue = null)
    val lastSignRun by app.settings.lastSignRun.collectAsStateWithLifecycle(initialValue = 0L)
    val lastSignSummary by app.settings.lastSignSummary.collectAsStateWithLifecycle(initialValue = "")
    var signing by remember { mutableStateOf(false) }
    var signMsg by remember { mutableStateOf("") }
    var signingAll by remember { mutableStateOf(false) }
    val active = accounts.find { it.username == activeName }

    Scaffold(
        topBar = { TopAppBar(title = { Text("我的") }) },
    ) { pad ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(pad)) {
            item {
                AccountHeader(
                    account = active,
                    guestMode = activeName == null,
                    onLogin = { nav.navigate(Routes.LOGIN) },
                    onManage = { nav.navigate(Routes.ACCOUNTS) },
                    onSearch = { nav.navigate(Routes.SEARCH) },
                )
            }

            // 账号速切
            if (accounts.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Text("快速切换", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.height(6.dp))
                        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(accounts.size) { index ->
                                val acc = accounts[index]
                                FilterChip(
                                    selected = acc.username == activeName,
                                    onClick = {
                                        scope.launch {
                                            app.auth.activate(acc.username)
                                            Refresh.bumpGeneration()
                                            CopyUtil.toast(context, "已切换到 ${acc.nickname.ifBlank { acc.username }}")
                                        }
                                    },
                                    label = { Text(acc.nickname.ifBlank { acc.username }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    leadingIcon = {
                                        AsyncImage(
                                            model = acc.avatarUrl,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp).clip(CircleShape),
                                        )
                                    },
                                )
                            }
                            item {
                                FilterChip(
                                    selected = activeName == null,
                                    onClick = { scope.launch { app.auth.activate(null); Refresh.bumpGeneration() } },
                                    label = { Text("游客") },
                                )
                            }
                        }
                    }
                }
            }

            // 签到
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.EmojiEvents, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("每日签到", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            if (active?.lastCheckInOk == true) {
                                Text("今日已签", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = when {
                                activeName == null -> "登录后才能签到"
                                signMsg.isNotEmpty() -> signMsg
                                active?.lastCheckIn.isNullOrEmpty() -> "还没有签到记录"
                                else -> "上次：${active?.lastCheckIn} ${if (active?.lastCheckInOk == true) "成功" else ""}" +
                                    if (!active?.lastSignReward.isNullOrEmpty()) " · ${active?.lastSignReward}" else ""
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (lastSignRun > 0) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "定时任务：" + java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
                                    .format(java.util.Date(lastSignRun)) + " · " + lastSignSummary,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssistChip(
                                onClick = {
                                    if (activeName == null) { nav.navigate(Routes.LOGIN); return@AssistChip }
                                    signing = true
                                    scope.launch {
                                        val outcome = app.sign.signActive()
                                        signMsg = outcome?.let {
                                            buildString {
                                                append(if (it.ok || it.alreadySigned) "签到成功" else "签到失败")
                                                if (it.reward.isNotEmpty()) append(" · ${it.reward}")
                                                if (it.rank > 0) append(" · 第 ${it.rank} 名")
                                                if (!it.ok && !it.alreadySigned) append("：${it.message}")
                                            }
                                        } ?: "没有可用账号"
                                        signing = false
                                    }
                                },
                                label = { Text(if (signing) "签到中…" else "立即签到") },
                                leadingIcon = { Icon(Icons.Filled.CheckCircle, null, modifier = Modifier.size(16.dp)) },
                            )
                            AssistChip(
                                onClick = {
                                    if (accounts.isEmpty()) { nav.navigate(Routes.LOGIN); return@AssistChip }
                                    signingAll = true
                                    scope.launch {
                                        val summary = app.sign.signAll(notify = true)
                                        signMsg = summary.describe()
                                        signingAll = false
                                    }
                                },
                                label = { Text(if (signingAll) "批量签到中…" else "全部账号签到") },
                                leadingIcon = { Icon(Icons.Filled.Sync, null, modifier = Modifier.size(16.dp)) },
                            )
                            AssistChip(
                                onClick = { nav.navigate(Routes.SIGN_RECORDS) },
                                label = { Text("签到记录") },
                            )
                        }
                    }
                }
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }

            item { EntryRow("账号管理", "多账号、密码托管、切换与排序", Icons.Filled.Person) { nav.navigate(Routes.ACCOUNTS) } }
            item { EntryRow("浏览历史", "看过的帖子，带阅读进度", Icons.Filled.History) { nav.navigate(Routes.HISTORY) } }
            item { EntryRow("我的收藏", "本地与论坛收藏", Icons.Filled.Bookmark) { nav.navigate(Routes.FAVORITES) } }
            item { EntryRow("草稿箱", "没发出去的帖子与回复", Icons.Filled.Description) { nav.navigate(Routes.DRAFTS) } }
            item { EntryRow("离线帖子", "存下来无网也能看", Icons.Filled.CloudOff) { nav.navigate(Routes.OFFLINE) } }
            item { EntryRow("记录中心", "解锁决策 / 加载耗时 / 运行日志", Icons.Filled.AccessTime) { nav.navigate(Routes.RECORDS) } }
            item { EntryRow("黑名单", "被拉黑的人不再显示发言", Icons.Filled.Block) { nav.navigate(Routes.BLACKLIST) } }
            item { EntryRow("AI 助手", "对话、帖子总结、自动回复", Icons.Filled.AutoAwesome) { nav.navigate(Routes.AI_SESSIONS) } }
            item { EntryRow("搜索", "站内帖子搜索", Icons.Filled.Search) { nav.navigate(Routes.SEARCH) } }
            item { EntryRow("快速跳转", "输入 tid / uid 或直接粘链接", Icons.Filled.Tag) { jumpOpen = true } }
            item { EntryRow("设置", "外观、网络、自动化", Icons.Filled.Settings) { nav.navigate(Routes.SETTINGS) } }
            item {
                Column(modifier = Modifier.padding(16.dp)) {
                    HorizontalDivider()
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "MT论坛 第三方客户端 · Kotlin + Compose\n与论坛官方无关，仅供学习交流",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
    if (jumpOpen) {
        QuickJumpDialog(
            onDismiss = { jumpOpen = false },
            onJumpThread = { tid -> nav.navigate(Routes.thread(tid)) },
            onJumpUser = { uid -> nav.navigate(Routes.profile(uid)) },
        )
    }
}

@Composable
private fun AccountHeader(
    account: Account?,
    guestMode: Boolean,
    onLogin: () -> Unit,
    onManage: () -> Unit,
    onSearch: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (account != null) {
                    AsyncImage(
                        model = account.avatarUrl,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp).clip(CircleShape),
                    )
                } else {
                    Icon(Icons.Filled.AccountCircle, null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        account?.let { it.nickname.ifBlank { it.username } } ?: "未登录",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    val subtitle = when {
                        account == null -> "登录后可签到、回复、收藏"
                        account.expired -> "会话可能已失效，去账号管理检查"
                        else -> buildString {
                            append("UID ${account.uid}")
                            if (account.signDays > 0) append(" · 连续签到 ${account.signDays} 天")
                            if (account.creditsText.isNotBlank()) append(" · ${account.creditsText}")
                        }
                    }
                    Text(subtitle, style = MaterialTheme.typography.labelMedium)
                }
                Icon(
                    Icons.Filled.Search,
                    contentDescription = "搜索",
                    modifier = Modifier.size(22.dp).clickable(onClick = onSearch),
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (guestMode) {
                    AssistChip(onClick = onLogin, label = { Text("去登录") }, leadingIcon = { Icon(Icons.Filled.Login, null, modifier = Modifier.size(16.dp)) })
                } else {
                    AssistChip(onClick = onManage, label = { Text("账号管理") }, leadingIcon = { Icon(Icons.Filled.Tune, null, modifier = Modifier.size(16.dp)) })
                }
                AssistChip(onClick = onManage, label = { Text("签到设置") })
            }
        }
    }
}

@Composable
private fun EntryRow(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
    }
    HorizontalDivider(modifier = Modifier.padding(start = 50.dp))
}
