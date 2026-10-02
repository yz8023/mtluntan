package io.mtluntan.app.ui.screen

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
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
import io.mtluntan.app.domain.model.Account
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
                    IconButton(onClick = { nav.navigate(Routes.LOGIN) }) { Icon(Icons.Filled.Add, "添加账号") }
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
                }
            }
            if (accounts.isEmpty()) {
                item { MessageBox("还没有账号\n点右上角 + 用 WebView 登录导入会话") }
            }
            items(accounts, key = { it.username }) { account ->
                AccountCard(
                    account = account,
                    isActive = account.username == activeName,
                    hasPassword = hasPassword.contains(account.username),
                    onActivate = {
                        scope.launch {
                            app.auth.activate(account.username)
                            Refresh.bumpGeneration()
                            CopyUtil.toast(context, "已切换到 ${account.nickname.ifBlank { account.username }}")
                        }
                    },
                    onToggleEnabled = { enabled ->
                        scope.launch { app.auth.setEnabled(account.username, enabled) }
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
                                append(account.username)
                                append(if (status.loggedIn) "：会话正常" else "：${status.message}")
                                if (status.repaired) append("（已自动重登）")
                            }
                            if (status.loggedIn) app.auth.markExpired(account.username, false)
                        }
                    },
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
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

    deleteTarget?.let { account ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除账号") },
            text = { Text("将从本机删除 ${account.username} 的会话、密码与签到记录，论坛账号不受影响。") },
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
    onSetPassword: () -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
    onSign: () -> Unit,
    onCheckSession: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(model = account.avatarUrl, contentDescription = null, modifier = Modifier.size(44.dp).clip(CircleShape))
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            account.nickname.ifBlank { account.username },
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
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
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
                IconButton(onClick = { onMove(-1) }) { Icon(Icons.Filled.ArrowUpward, "上移", modifier = Modifier.size(16.dp)) }
                IconButton(onClick = { onMove(1) }) { Icon(Icons.Filled.ArrowDownward, "下移", modifier = Modifier.size(16.dp)) }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "删除", modifier = Modifier.size(16.dp)) }
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
