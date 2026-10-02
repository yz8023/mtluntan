package io.mtluntan.app.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import io.mtluntan.app.MTLuntanApp
import io.mtluntan.app.ui.components.MtDivider
import io.mtluntan.app.ui.components.MtButton
import io.mtluntan.app.ui.components.MtCard
import io.mtluntan.app.domain.model.Account
import io.mtluntan.app.domain.model.displayName
import io.mtluntan.app.ui.navigation.Routes
import io.mtluntan.app.util.CopyUtil
import io.mtluntan.app.util.Refresh
import kotlinx.coroutines.launch

/**
 * 账号管理：多账号入库、切换、排序、启用、密码托管（KeyStore 加密）、批量签到。
 *
 * 这里体现 Java 版 v2.1 的核心能力，重点是「不打扰前台账号」：
 * 批量签到走隔离会话，切换才动前台。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountManagerScreen(app: MTLuntanApp, nav: NavHostController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val accounts by app.auth.accounts.collectAsStateWithLifecycle(initialValue = emptyList())
    val activeName by app.auth.activeAccount.collectAsStateWithLifecycle(initialValue = null)
    val spacing by app.settings.signSpacing.collectAsStateWithLifecycle(initialValue = 5)
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var passwordFor by remember { mutableStateOf<Account?>(null) }
    var passwordInput by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<Account?>(null) }
    var hasPassword by remember { mutableStateOf<Set<String>>(emptySet()) }
    var cookieDialog by remember { mutableStateOf(false) }
    var cookieInput by remember { mutableStateOf("") }
    var cookieBusy by remember { mutableStateOf(false) }
    var nameFallback by remember { mutableStateOf("") }
    // 编辑账号（昵称 / UID / 头像）——之前只有「删除」，用户反馈缺管理
    var editTarget by remember { mutableStateOf<Account?>(null) }
    var editName by remember { mutableStateOf("") }
    var editUid by remember { mutableStateOf("") }
    var editAvatar by remember { mutableStateOf("") }

    LaunchedEffect(accounts) {
        hasPassword = accounts.filter { app.auth.hasPassword(it.username) }.map { it.username }.toSet()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("账号管理（${accounts.size}）") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                },
                actions = {
                    IconButton(onClick = { nav.navigate(Routes.LOGIN) }) { Icon(Icons.Filled.Add, "添加账号（网页登录）") }
                    IconButton(onClick = { cookieDialog = true }) { Icon(Icons.Filled.Key, "用 Cookie 添加") }
                },
            )
        },
    ) { pad ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(pad)) {
            item {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(
                            onClick = {
                                if (accounts.isEmpty()) { nav.navigate(Routes.LOGIN); return@AssistChip }
                                busy = true
                                scope.launch {
                                    val summary = app.sign.signAll(notify = true)
                                    message = summary.describe()
                                    busy = false
                                }
                            },
                            label = { Text(if (busy) "正在签到…" else "一键全部签到") },
                            leadingIcon = { Icon(Icons.Filled.Sync, null, modifier = Modifier.size(16.dp)) },
                        )
                        AssistChip(
                            onClick = { nav.navigate(Routes.SIGN_RECORDS) },
                            label = { Text("签到记录") },
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "账号之间会间隔 ${spacing} 秒签到（可在设置里调整），避免论坛按 IP 限流返回 403。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    if (message.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MtButton(
                            text = "添加账号",
                            icon = Icons.Filled.Add,
                            onClick = { nav.navigate(Routes.LOGIN) },
                            modifier = Modifier.weight(1f),
                        )
                        MtButton(
                            text = "用 Cookie 添加",
                            icon = Icons.Filled.Key,
                            primary = false,
                            onClick = { cookieDialog = true },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "打开添加页时会先清空网页里的旧会话，所以不会又进到上一个账号；\n" +
                            "每个账号的 Cookie 独立保存，加新账号不会影响已有账号。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (accounts.isEmpty()) {
                item { MessageBox("还没有账号\n点右上角 + 用 WebView 登录导入会话") }
            }
            itemsIndexed(accounts, key = { _, it -> it.username }) { index, account ->
                AccountCard(
                    account = account,
                    isActive = account.username == activeName,
                    hasPassword = hasPassword.contains(account.username),
                    onActivate = {
                        scope.launch {
                            app.auth.activate(account.username)
                            Refresh.bumpGeneration()
                            CopyUtil.toast(context, "已切换到 ${account.displayName}")
                        }
                    },
                    onToggleEnabled = { enabled ->
                        scope.launch { app.auth.setEnabled(account.username, enabled) }
                    },
                    onEdit = {
                        editTarget = account
                        editName = account.displayName
                        editUid = account.uid.takeIf { it > 0 }?.toString().orEmpty()
                        editAvatar = account.avatarUrl
                    },
                    onSetPassword = { passwordFor = account; passwordInput = "" },
                    onMove = { delta -> scope.launch { app.auth.move(account.username, delta) } },
                    onDelete = { deleteTarget = account },
                    onSign = {
                        scope.launch {
                            busy = true
                            val outcome = app.sign.signOne(
                                app.db.accountDao().byUsername(account.username)
                                    ?: return@launch
                            )
                            message = "${account.username}：" + (if (outcome.ok || outcome.alreadySigned) "签到成功" else "失败 ${outcome.message}")
                            busy = false
                        }
                    },
                    onCheckSession = {
                        scope.launch {
                            val password = app.auth.passwordFor(account.username)
                            val status = app.guard.verify(account.username, password, repair = password.isNotEmpty())
                            message = buildString {
                                append(account.displayName)
                                append(if (status.loggedIn) "：会话正常" else "：${status.message}")
                                if (status.repaired) append("（已自动重登）")
                            }
                            // 顺便把身份信息补全（旧版本存下来的账号 uid / 昵称可能是空的）
                            if (status.loggedIn) {
                                runCatching {
                                    val profile = app.forum.identityOf(
                                        io.mtluntan.app.data.network.IsolatedClient(app.net.cookieRepo, account.username)
                                    )
                                    if (profile.uid > 0 || profile.username.isNotBlank()) {
                                        app.auth.updateInfo(
                                            username = account.username,
                                            uid = profile.uid,
                                            nickname = profile.username,
                                            avatar = profile.avatarUrl,
                                        )
                                        app.auth.refreshCookieSnapshot(account.username)
                                        message += " · 已补全为 ${profile.username.ifBlank { "uid_" + profile.uid }}"
                                    }
                                }
                            }
                            if (status.loggedIn) app.auth.markExpired(account.username, false)
                        }
                    },
                )
                if (index < accounts.lastIndex) MtDivider(startIndent = 20.dp)
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (cookieDialog) {
        AlertDialog(
            onDismissRequest = { if (!cookieBusy) cookieDialog = false },
            title = { Text("用 Cookie 添加账号") },
            text = {
                Column {
                    Text(
                        "浏览器 F12 → Network → 复制 Cookie 整串（要包含 xxx_auth 与 xxx_saltkey），粘到下面即可。\n" +
                            "客户端会请求个人页确认真实身份，不会用 Cookie 名猜账号。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = cookieInput,
                        onValueChange = { cookieInput = it },
                        label = { Text("Cookie 字符串") },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (nameFallback.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "身份识别失败，请手填一个名字（仅本地标识用）：",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        OutlinedTextField(
                            value = nameFallback,
                            onValueChange = { nameFallback = it },
                            label = { Text("备注名") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !cookieBusy,
                    onClick = {
                        val raw = cookieInput.trim()
                        if (raw.isBlank()) { CopyUtil.toast(context, "先粘贴 Cookie"); return@TextButton }
                        cookieBusy = true
                        scope.launch {
                            try {
                                app.auth.stagePendingCookies(raw)
                                val profile = app.forum.identityOf(app.auth.pendingClient())
                                val username = profile.username.ifBlank { "uid_${profile.uid}" }
                                if (profile.uid <= 0 && profile.username.isBlank()) {
                                    if (nameFallback.isBlank()) {
                                        nameFallback = "account_${System.currentTimeMillis() % 100000}"
                                        CopyUtil.toast(context, "Cookie 没换到身份信息，确认名字后再点一次")
                                        return@launch
                                    }
                                    app.auth.importStaged(raw, nameFallback, 0, nameFallback)
                                } else {
                                    app.auth.importStaged(
                                        cookieString = raw,
                                        username = username,
                                        uid = profile.uid,
                                        nickname = profile.username,
                                        avatar = profile.avatarUrl,
                                    )
                                }
                                CopyUtil.toast(context, "已添加：$username")
                                cookieDialog = false
                                cookieInput = ""
                                nameFallback = ""
                            } catch (t: Throwable) {
                                CopyUtil.toast(context, "导入失败：${t.message}")
                            } finally {
                                cookieBusy = false
                            }
                        }
                    },
                ) { Text(if (cookieBusy) "识别中…" else "导入") }
            },
            dismissButton = { TextButton(onClick = { cookieDialog = false }) { Text("取消") } },
        )
    }

    passwordFor?.let { account ->
        AlertDialog(
            onDismissRequest = { passwordFor = null },
            title = { Text("为 ${account.username} 设置密码") },
            text = {
                Column {
                    Text(
                        "密码经 Android KeyStore（AES-GCM）加密后只存本机，用于 Cookie 失效时静默重登与自动签到。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text("密码") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        app.auth.setPassword(account.username, passwordInput)
                        hasPassword = accounts.filter { app.auth.hasPassword(it.username) }.map { it.username }.toSet()
                        CopyUtil.toast(context, if (passwordInput.isEmpty()) "已清除密码" else "已加密保存")
                        passwordFor = null
                    }
                }) { Text("保存") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        scope.launch {
                            app.auth.setPassword(account.username, "")
                            hasPassword = accounts.filter { app.auth.hasPassword(it.username) }.map { it.username }.toSet()
                            passwordFor = null
                        }
                    }) { Text("清除") }
                    TextButton(onClick = { passwordFor = null }) { Text("取消") }
                }
            },
        )
    }

    editTarget?.let { account ->
        AlertDialog(
            onDismissRequest = { editTarget = null },
            title = { Text("编辑账号") },
            text = {
                Column {
                    Text(
                        "只改本地显示信息（登录名 ${account.username} 不会变），用于多账号时一眼分清是哪个号。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("昵称（显示名）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editUid,
                        onValueChange = { editUid = it.filter { ch -> ch.isDigit() } },
                        label = { Text("UID（可留空）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editAvatar,
                        onValueChange = { editAvatar = it },
                        label = { Text("头像地址（可留空）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = account
                    scope.launch {
                        app.auth.updateProfile(
                            username = target.username,
                            uid = editUid.toLongOrNull() ?: 0L,
                            nickname = editName,
                            avatar = editAvatar,
                        )
                        Refresh.bumpGeneration()
                        CopyUtil.toast(context, "已更新 ${editName.ifBlank { target.username }}")
                        editTarget = null
                    }
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { editTarget = null }) { Text("取消") } },
        )
    }

    deleteTarget?.let { account ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除账号") },
            text = { Text("将从本机删除「${account.displayName}」的会话、密码与签到记录。\n其他账号不受影响，论坛侧账号也不会被注销。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        app.auth.removeAccount(account.username)
                        Refresh.bumpGeneration()
                        deleteTarget = null
                    }
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun AccountCard(
    account: Account,
    isActive: Boolean,
    hasPassword: Boolean,
    onActivate: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onSetPassword: () -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
    onSign: () -> Unit,
    onCheckSession: () -> Unit,
) {
    MtCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = account.avatarUrl.ifBlank { io.mtluntan.app.data.network.Site.avatarUrl(account.uid) },
                    contentDescription = null,
                    modifier = Modifier.size(44.dp).clip(CircleShape),
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            account.displayName,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        if (isActive) {
                            Spacer(Modifier.width(6.dp))
                            Text("当前", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                        if (account.expired) {
                            Spacer(Modifier.width(6.dp))
                            Text("可能掉线", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        }
                        if (hasPassword) {
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Filled.Key, null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.secondary)
                        }
                    }
                    Text(
                        buildString {
                            append(account.username)
                            if (account.uid > 0) append(" · UID ${account.uid}")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    Text(
                        buildString {
                            if (account.lastCheckIn.isNotBlank()) {
                                append("签到 ${account.lastCheckIn} ")
                                append(if (account.lastCheckInOk) "✓" else "✗")
                            } else append("还没有签到记录")
                            if (account.signDays > 0) append(" · 连续 ${account.signDays} 天")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                Switch(checked = account.enabled, onCheckedChange = onToggleEnabled)
            }
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                if (!isActive) {
                    AssistChip(onClick = onActivate, label = { Text("切换") })
                }
                AssistChip(onClick = onSign, label = { Text("签到") })
                AssistChip(onClick = onCheckSession, label = { Text("检测会话") })
                AssistChip(
                    onClick = onSetPassword,
                    label = { Text(if (hasPassword) "改密码" else "存密码") },
                    leadingIcon = { Icon(Icons.Filled.Lock, null, modifier = Modifier.size(14.dp)) },
                )
                AssistChip(
                    onClick = onEdit,
                    label = { Text("编辑") },
                    leadingIcon = { Icon(Icons.Filled.Edit, null, modifier = Modifier.size(14.dp)) },
                )
                AssistChip(
                    onClick = onDelete,
                    label = { Text("删除") },
                    leadingIcon = {
                        Icon(Icons.Filled.Delete, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                    },
                )
                IconButton(onClick = { onMove(-1) }) { Icon(Icons.Filled.ArrowUpward, "上移", modifier = Modifier.size(16.dp)) }
                IconButton(onClick = { onMove(1) }) { Icon(Icons.Filled.ArrowDownward, "下移", modifier = Modifier.size(16.dp)) }
            }
        }
    }
}

/** 签到记录：按账号与日期展示状态 / 排名 / 奖励。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignRecordsScreen(app: MTLuntanApp, nav: NavHostController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val records by app.auth.signRecords.collectAsStateWithLifecycle(initialValue = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("签到记录（${records.size}）") },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            app.auth.clearSignRecords()
                            CopyUtil.toast(context, "已清空签到记录")
                        }
                    }) { Icon(Icons.Filled.Delete, "清空") }
                },
            )
        },
    ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad)) {
            if (records.isEmpty()) MessageBox("还没有签到记录")
            else LazyColumn {
                items(records, key = { it.id }) { r ->
                    ListRow(
                        title = "${r.account} · ${if (r.ok) "成功" else "失败"}",
                        subtitle = buildString {
                            append(r.date)
                            if (r.alreadySigned) append(" · 当时已签")
                            if (r.rank > 0) append(" · 第 ${r.rank} 名")
                            if (r.reward.isNotBlank()) append(" · ${r.reward}")
                            if (r.message.isNotBlank()) append(" · ${r.message.take(40)}")
                        },
                        trailing = r.date.takeLast(5),
                        onClick = {
                            CopyUtil.copy(
                                context,
                                "${r.date} ${r.account} ${if (r.ok) "成功" else "失败"} ${r.reward} ${r.rank}".trim(),
                                "已复制这条记录",
                            )
                        },
                    )
                }
            }
        }
    }
}
