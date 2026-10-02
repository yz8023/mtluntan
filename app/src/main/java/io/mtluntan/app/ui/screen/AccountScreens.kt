package io.mtluntan.app.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.material3.DropdownMenuItem

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

    // 「添加账号」的三种方式：账号密码（推荐）/ 网页登录 / Cookie 导入
    var addDialog by remember { mutableStateOf(false) }
    var loginDialog by remember { mutableStateOf(false) }
    var loginName by remember { mutableStateOf("") }
    var loginPassword by remember { mutableStateOf("") }
    var rememberPassword by remember { mutableStateOf(true) }
    var loginBusy by remember { mutableStateOf(false) }

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
                    // 只保留顶栏这一个「添加」入口：点开选方式（原来页面上那两个白底按钮已移除）
                    IconButton(onClick = { addDialog = true }) { Icon(Icons.Filled.Add, "添加账号") }
                },
            )
        },
    ) { pad ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(pad)) {
            item {
                // 顶部总览卡（1.1.5 重排）：左侧状态摘要，右侧两个主按钮，底部一行小字说明。
                val enabledCount = accounts.count { it.enabled }
                val signedToday = accounts.count { it.lastCheckInOk && it.lastCheckIn.isNotBlank() }
                MtCard(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Filled.ManageAccounts,
                                    null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "共 ${accounts.size} 个账号",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    "启用 $enabledCount · 今日已签 $signedToday" +
                                        (activeName?.let { " · 当前 ${it}" } ?: ""),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = {
                                    if (accounts.isEmpty()) { addDialog = true; return@FilledTonalButton }
                                    busy = true
                                    scope.launch {
                                        // 先看今天签没签过，只签没签的（防重复，避免账号异常）
                                        val summary = app.sign.autoSignOnOpen(force = true)
                                            ?: app.sign.signAll(notify = true)
                                        message = summary.describe()
                                        busy = false
                                    }
                                },
                                enabled = !busy,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Filled.Sync, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (busy) "正在签到…" else "一键签到")
                            }
                            OutlinedButton(
                                onClick = { nav.navigate(Routes.SIGN_RECORDS) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Filled.History, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("签到记录")
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "账号间隔 ${spacing} 秒；同一账号当天已签到会自动跳过。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                        if (message.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }
            if (accounts.isEmpty()) {
                item { MessageBox("还没有账号\n点右上角 + 添加：账号密码登录 / 网页登录 / Cookie 导入") }
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
                                            group = profile.groupName,
                                            credits = profile.creditsText,
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
                // 卡片之间靠间距分隔，不再插横线（排版更干净）
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
                                app.auth.beginImport()
                                app.auth.stagePendingCookies(raw)
                                val profile = app.forum.identityOf(app.auth.pendingClient())
                                val outcome = app.auth.importStaged(
                                    cookieString = raw,
                                    username = profile.username,
                                    uid = profile.uid,
                                    nickname = profile.username,
                                    avatar = profile.avatarUrl,
                                    group = profile.groupName,
                                    credits = profile.creditsText,
                                )
                                if (outcome is io.mtluntan.app.data.repo.AuthRepository.ImportOutcome.Ok) {
                                    CopyUtil.toast(
                                        context,
                                        if (outcome.isNew) "已添加：${outcome.username} (uid ${profile.uid})"
                                        else "这个账号已在列表里：${outcome.username}（已刷新会话）",
                                    )
                                    cookieDialog = false
                                    cookieInput = ""
                                    nameFallback = ""
                                } else if (nameFallback.isBlank()) {
                                    CopyUtil.toast(context, "Cookie 没换到身份信息，请填一个名字后再点一次")
                                } else {
                                    val forced = app.auth.importStaged(raw, nameFallback, 0, nameFallback)
                                    if (forced is io.mtluntan.app.data.repo.AuthRepository.ImportOutcome.Ok) {
                                        CopyUtil.toast(context, "已添加：${forced.username}")
                                        cookieDialog = false
                                        cookieInput = ""
                                        nameFallback = ""
                                    } else {
                                        CopyUtil.toast(context, "名字不能是「论坛 / 登录 / 纯数字」这类字样")
                                    }
                                }
                            } catch (t: Throwable) {
                                CopyUtil.toast(context, "导入失败：${t.message}")
                            } finally {
                                app.auth.endImport()
                                cookieBusy = false
                            }
                        }
                    },
                ) { Text(if (cookieBusy) "识别中…" else "导入") }
            },
            dismissButton = { TextButton(onClick = { cookieDialog = false }) { Text("取消") } },
        )
    }

    // ---------------- 添加账号：选方式 ----------------
    if (addDialog) {
        AlertDialog(
            onDismissRequest = { addDialog = false },
            title = { Text("添加账号") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AddMethodRow(
                        title = "账号密码登录",
                        subtitle = "推荐：直接在 App 里登录，可记住密码，掉线自动重登",
                        icon = Icons.Filled.Person,
                    ) {
                        addDialog = false
                        loginName = ""
                        loginPassword = ""
                        rememberPassword = true
                        loginDialog = true
                    }
                    AddMethodRow(
                        title = "网页登录",
                        subtitle = "用内置网页登录一次，再回到 App 导入会话（支持扫码/人机校验）",
                        icon = Icons.Filled.Language,
                    ) {
                        addDialog = false
                        nav.navigate(Routes.LOGIN)
                    }
                    AddMethodRow(
                        title = "Cookie 导入",
                        subtitle = "已经在别处登录过：粘贴 Cookie 字符串直接导入",
                        icon = Icons.Filled.Key,
                    ) {
                        addDialog = false
                        cookieDialog = true
                    }
                }
            },
            confirmButton = { TextButton(onClick = { addDialog = false }) { Text("取消") } },
        )
    }

    // ---------------- 账号密码登录 ----------------
    if (loginDialog) {
        AlertDialog(
            onDismissRequest = { if (!loginBusy) loginDialog = false },
            title = { Text("账号密码登录") },
            text = {
                Column {
                    OutlinedTextField(
                        value = loginName,
                        onValueChange = { loginName = it },
                        label = { Text("用户名 / 邮箱") },
                        singleLine = true,
                        enabled = !loginBusy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = loginPassword,
                        onValueChange = { loginPassword = it },
                        label = { Text("密码") },
                        singleLine = true,
                        enabled = !loginBusy,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = rememberPassword, onCheckedChange = { rememberPassword = it })
                        Text("记住密码（本机 KeyStore 加密保存）", style = MaterialTheme.typography.labelMedium)
                    }
                    Text(
                        "登录时会自动处理站点的 acw_sc__v2 人机校验；如果站点弹出图形验证码，请改用「网页登录」。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !loginBusy,
                    onClick = {
                        if (loginName.isBlank() || loginPassword.isEmpty()) {
                            CopyUtil.toast(context, "请输入账号和密码")
                            return@TextButton
                        }
                        loginBusy = true
                        scope.launch {
                            val (ok, msg) = app.auth.loginWithPassword(loginName, loginPassword, rememberPassword)
                            loginBusy = false
                            message = msg
                            CopyUtil.toast(context, msg)
                            if (ok) {
                                loginDialog = false
                                loginPassword = ""
                                Refresh.bumpGeneration()
                            }
                        }
                    },
                ) { Text(if (loginBusy) "登录中…" else "登录并添加") }
            },
            dismissButton = {
                TextButton(enabled = !loginBusy, onClick = { loginDialog = false }) { Text("取消") }
            },
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

/** 「添加账号」里的一行方式。 */
@Composable
private fun AddMethodRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(10.dp),
    ) {
        Icon(icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
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
    var menu by remember { mutableStateOf(false) }
    var infoExpanded by remember { mutableStateOf(false) }

    MtCard(
        tint = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.06f) else null,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // ---- 头部：头像 + 名字/徽章 + 开关 & 更多菜单 ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(if (isActive) 52.dp else 48.dp)
                        .clip(CircleShape)
                        .background(if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                        .padding(if (isActive) 2.dp else 0.dp),
                ) {
                    AsyncImage(
                        model = account.avatarUrl.ifBlank { io.mtluntan.app.data.network.Site.avatarUrl(account.uid) },
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        account.displayName.ifBlank { account.username },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isActive) {
                            AccountTag("当前使用", MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(4.dp))
                        }
                        if (!account.enabled) {
                            AccountTag("已停用", MaterialTheme.colorScheme.outline)
                            Spacer(Modifier.width(4.dp))
                        }
                        if (account.expired) {
                            AccountTag("可能掉线", MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(4.dp))
                        }
                        if (hasPassword) {
                            AccountTag("已托管密码", MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.width(4.dp))
                        }
                        if (account.lastCheckInOk && account.lastCheckIn.isNotBlank()) {
                            AccountTag("今日已签", MaterialTheme.colorScheme.tertiary)
                        }
                    }
                }
                Switch(checked = account.enabled, onCheckedChange = onToggleEnabled)
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Filled.MoreVert, "更多操作", modifier = Modifier.size(20.dp))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("编辑资料") },
                            leadingIcon = { Icon(Icons.Filled.Edit, null, modifier = Modifier.size(18.dp)) },
                            onClick = { menu = false; onEdit() },
                        )
                        DropdownMenuItem(
                            text = { Text(if (hasPassword) "修改密码" else "托管密码") },
                            leadingIcon = { Icon(Icons.Filled.Lock, null, modifier = Modifier.size(18.dp)) },
                            onClick = { menu = false; onSetPassword() },
                        )
                        DropdownMenuItem(
                            text = { Text("上移") },
                            leadingIcon = { Icon(Icons.Filled.ArrowUpward, null, modifier = Modifier.size(18.dp)) },
                            onClick = { menu = false; onMove(-1) },
                        )
                        DropdownMenuItem(
                            text = { Text("下移") },
                            leadingIcon = { Icon(Icons.Filled.ArrowDownward, null, modifier = Modifier.size(18.dp)) },
                            onClick = { menu = false; onMove(1) },
                        )
                        DropdownMenuItem(
                            text = { Text("删除账号", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(Icons.Filled.Delete, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                            },
                            onClick = { menu = false; onDelete() },
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            // ---- 信息区：两列网格，不再一长串灰色小字 ----
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                AccountInfoCell(
                    "账号",
                    account.username.ifBlank { "—" },
                    Modifier.weight(1f),
                )
                AccountInfoCell(
                    "UID / 用户组",
                    listOfNotNull(
                        account.uid.takeIf { it > 0 }?.toString(),
                        account.groupName.takeIf { it.isNotBlank() },
                    ).joinToString(" · ").ifBlank { "未补全" },
                    Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                AccountInfoCell(
                    "签到状态",
                    buildString {
                        if (account.lastCheckIn.isNotBlank()) {
                            append(account.lastCheckIn)
                            append(if (account.lastCheckInOk) " ✓" else " ✗")
                        } else append("无记录")
                    },
                    Modifier.weight(1f),
                )
                AccountInfoCell(
                    "连续 / 排名",
                    buildString {
                        if (account.signDays > 0) append("${account.signDays} 天") else append("—")
                        if (account.lastSignRank > 0) append(" · 第 ${account.lastSignRank} 名")
                    },
                    Modifier.weight(1f),
                )
            }
            if (account.creditsText.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .clickable { infoExpanded = !infoExpanded }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        account.creditsText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (infoExpanded) 8 else 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            // ---- 操作区：只留两个高频按钮，其余的进「更多」菜单 ----
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!isActive) {
                    FilledTonalButton(onClick = onActivate, modifier = Modifier.weight(1f)) { Text("切换为当前") }
                } else {
                    OutlinedButton(onClick = onCheckSession, modifier = Modifier.weight(1f)) { Text("检测会话") }
                }
                OutlinedButton(onClick = onSign, modifier = Modifier.weight(1f)) { Text("立即签到") }
                if (!isActive) {
                    OutlinedButton(onClick = onCheckSession, modifier = Modifier.weight(1f)) { Text("检测会话") }
                }
            }
        }
    }
}

/** 账号卡里的一个信息格：小标题 + 值。 */
@Composable
private fun AccountInfoCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 账号卡上的小标签（当前 / 已停用 / 掉线 / 已托管密码 / 今日已签）。 */
@Composable
private fun AccountTag(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
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
