package io.mtluntan.app.ai

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import io.mtluntan.app.MTLuntanApp
import io.mtluntan.app.ui.navigation.Routes
import io.mtluntan.app.ui.screen.LoadingBox
import io.mtluntan.app.ui.screen.MessageBox
import io.mtluntan.app.util.CopyUtil
import kotlinx.coroutines.launch

/** AI 会话列表。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSessionsScreen(app: MTLuntanApp, nav: NavHostController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val sessions by app.ai.sessions.collectAsStateWithLifecycle(initialValue = emptyList())
    val configReady by app.settings.aiApiKey.collectAsStateWithLifecycle(initialValue = "")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 助手") },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                actions = {
                    IconButton(onClick = { nav.navigate(Routes.AI_CONFIG) }) { Icon(Icons.Filled.Settings, "配置") }
                    IconButton(onClick = {
                        scope.launch { nav.navigate(Routes.aiChat(app.ai.newSession("新对话"))) }
                    }) { Icon(Icons.Filled.Add, "新对话") }
                },
            )
        },
    ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad)) {
            if (configReady.isBlank()) {
                Surface(color = MaterialTheme.colorScheme.errorContainer) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                        Text("还没配置 API Key", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "点右上角齿轮填入 baseUrl / Key / 模型（任何 OpenAI 兼容服务都行：OpenAI、DeepSeek、通义、Kimi、本地 Ollama…）",
                            style = MaterialTheme.typography.labelSmall,
                        )
                        TextButton(onClick = { nav.navigate(Routes.AI_CONFIG) }) { Text("去配置") }
                    }
                }
            }
            if (sessions.isEmpty()) MessageBox("还没有会话\n点右上角 + 开始对话")
            else LazyColumn {
                items(sessions, key = { it.id }) { session ->
                    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable { nav.navigate(Routes.aiChat(session.id)) }
                                .padding(12.dp),
                        ) {
                            Icon(Icons.Filled.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(session.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(session.updatedAt))
                                        + if (session.tid > 0) " · 帖子 ${session.tid}" else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }
                            IconButton(onClick = { scope.launch { app.ai.deleteSession(session.id) } }) {
                                Icon(Icons.Filled.DeleteOutline, "删除", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** AI 对话页。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(app: MTLuntanApp, nav: NavHostController, sessionId: Long) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val messages by app.ai.messages(sessionId).collectAsStateWithLifecycle(initialValue = emptyList())
    var input by remember { mutableStateOf("") }
    var thinking by remember { mutableStateOf(false) }
    var summaryTid by remember { mutableStateOf("") }
    var showSummaryDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 对话") },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                actions = {
                    IconButton(onClick = { showSummaryDialog = true }) { Icon(Icons.Filled.Summarize, "总结帖子") }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column {
                    if (thinking) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            placeholder = { Text("问点什么…") },
                            maxLines = 4,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(6.dp))
                        IconButton(
                            enabled = !thinking && input.isNotBlank(),
                            onClick = {
                                val text = input.trim()
                                input = ""
                                thinking = true
                                scope.launch {
                                    app.ai.ask(sessionId, text)
                                        .onFailure { CopyUtil.toast(context, it.message ?: "请求失败") }
                                    thinking = false
                                }
                            },
                        ) { Icon(Icons.AutoMirrored.Filled.Send, "发送") }
                    }
                }
            }
        },
    ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad)) {
            if (messages.isEmpty()) MessageBox("开始提问吧\n也可以点右上角「总结帖子」，把 tid 丢进来让它读全文")
            else LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(messages, key = { it.id }) { msg ->
                    val fromMe = msg.role == "user"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (fromMe) Arrangement.End else Arrangement.Start,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (fromMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth(0.9f),
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(msg.content, style = MaterialTheme.typography.bodyMedium)
                                Row(modifier = Modifier.padding(top = 4.dp)) {
                                    TextButton(onClick = { CopyUtil.copy(context, msg.content, "已复制") }) {
                                        Icon(Icons.Filled.ContentCopy, null, modifier = Modifier.size(13.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("复制", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSummaryDialog) {
        AlertDialog(
            onDismissRequest = { showSummaryDialog = false },
            title = { Text("总结帖子") },
            text = {
                Column {
                    Text("输入帖子 tid，AI 会抓取全文并总结（不消耗论坛积分）。", style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = summaryTid,
                        onValueChange = { summaryTid = it.filter { ch -> ch.isDigit() } },
                        label = { Text("tid") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val tid = summaryTid.toLongOrNull()
                    showSummaryDialog = false
                    if (tid == null || tid <= 0) return@TextButton
                    thinking = true
                    scope.launch {
                        runCatching {
                            val html = app.forum.threadHtml(tid, 1)
                            val detail = io.mtluntan.app.data.parser.ThreadDetailParser.parse(html, 1)
                            app.ai.ask(sessionId, "请总结这个帖子：${detail.title}\n\n" + (detail.mainPost?.contentBbc ?: "").take(4000))
                        }.onFailure { CopyUtil.toast(context, it.message ?: "总结失败") }
                        thinking = false
                    }
                }) { Text("开始总结") }
            },
            dismissButton = { TextButton(onClick = { showSummaryDialog = false }) { Text("取消") } },
        )
    }
}

/** AI 配置：baseUrl / key / 模型 / 温度 / 系统提示词 / 自动回复策略。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiConfigScreen(app: MTLuntanApp, nav: NavHostController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val baseUrl by app.settings.aiBaseUrl.collectAsStateWithLifecycle(initialValue = "")
    val apiKey by app.settings.aiApiKey.collectAsStateWithLifecycle(initialValue = "")
    val model by app.settings.aiModel.collectAsStateWithLifecycle(initialValue = "")
    val system by app.settings.aiSystemPrompt.collectAsStateWithLifecycle(initialValue = "")
    val temperature by app.settings.aiTemperature.collectAsStateWithLifecycle(initialValue = "0.7")
    val maxTokens by app.settings.aiMaxTokens.collectAsStateWithLifecycle(initialValue = 2048)
    val dryRun by app.settings.aiDryRun.collectAsStateWithLifecycle(initialValue = false)
    val autoReply by app.settings.autoReply.collectAsStateWithLifecycle(initialValue = false)
    val autoReplyOnView by app.settings.autoReplyOnView.collectAsStateWithLifecycle(initialValue = false)
    val autoReplyAi by app.settings.autoReplyAi.collectAsStateWithLifecycle(initialValue = false)
    val autoReplyTemplate by app.settings.autoReplyTemplate.collectAsStateWithLifecycle(initialValue = "")
    val autoReplyPrompt by app.settings.autoReplyPrompt.collectAsStateWithLifecycle(initialValue = "")
    val autoReplyDaily by app.settings.autoReplyDaily.collectAsStateWithLifecycle(initialValue = 10)
    val aiSummaryButton by app.settings.aiSummaryButton.collectAsStateWithLifecycle(initialValue = true)

    var baseInput by remember { mutableStateOf(baseUrl) }
    var keyInput by remember { mutableStateOf(apiKey) }
    var modelInput by remember { mutableStateOf(model) }
    var systemInput by remember { mutableStateOf(system) }
    var templateInput by remember { mutableStateOf(autoReplyTemplate) }
    var promptInput by remember { mutableStateOf(autoReplyPrompt) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf("") }

    LaunchedEffect(baseUrl, apiKey, model, system, autoReplyTemplate, autoReplyPrompt) {
        baseInput = baseUrl; keyInput = apiKey; modelInput = model
        systemInput = system; templateInput = autoReplyTemplate; promptInput = autoReplyPrompt
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 与自动回复") },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                actions = {
                    TextButton(onClick = {
                        scope.launch {
                            app.settings.setAiBaseUrl(baseInput)
                            app.settings.setAiApiKey(keyInput)
                            app.settings.setAiModel(modelInput)
                            app.settings.setAiSystemPrompt(systemInput)
                            app.settings.setAutoReplyTemplate(templateInput)
                            app.settings.setAutoReplyPrompt(promptInput)
                            CopyUtil.toast(context, "已保存")
                        }
                    }) { Text("保存") }
                },
            )
        },
    ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())) {
            ConfigField("接口地址 baseUrl", baseInput, { baseInput = it }, "https://api.openai.com/v1")
            ConfigField("API Key", keyInput, { keyInput = it }, "sk-…", password = true)
            ConfigField("模型", modelInput, { modelInput = it }, "gpt-4o-mini / deepseek-chat")
            ConfigField("系统提示词", systemInput, { systemInput = it }, "", lines = 3)
            ConfigField("温度（0~2）", temperature, { scope.launch { app.settings.setAiTemperature(it) } }, "0.7")
            ConfigField("最大回复 token", maxTokens.toString(), {
                it.toIntOrNull()?.let { v -> scope.launch { app.settings.setAiMaxTokens(v) } }
            }, "2048")

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                Switch(checked = aiSummaryButton, onCheckedChange = { scope.launch { app.settings.setAiSummaryButton(it) } })
                Spacer(Modifier.width(10.dp))
                Text("在帖子页显示 AI 总结按钮", style = MaterialTheme.typography.bodyMedium)
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                Switch(checked = dryRun, onCheckedChange = { scope.launch { app.settings.setAiDryRun(it) } })
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("演练模式", style = MaterialTheme.typography.bodyMedium)
                    Text("只生成回复内容不发送，用来确认文案", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }

            TextButton(
                enabled = !testing,
                onClick = {
                    testing = true
                    testResult = "测试中…"
                    scope.launch {
                        app.settings.setAiBaseUrl(baseInput)
                        app.settings.setAiApiKey(keyInput)
                        app.settings.setAiModel(modelInput)
                        val cfg = app.aiConfig.snapshot()
                        val result = AiClient().chat(
                            listOf(
                                AiClient.Msg("system", "你是连通性测试助手，只回四个字：连接正常"),
                                AiClient.Msg("user", "测试"),
                            ),
                            cfg,
                        )
                        testResult = result.getOrElse { "失败：${it.message}" }
                        testing = false
                    }
                },
                modifier = Modifier.padding(horizontal = 8.dp),
            ) { Text("测试连接") }
            if (testResult.isNotEmpty()) {
                Text(testResult, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
            }

            Spacer(Modifier.height(12.dp))
            Text("解锁回复的内容", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 14.dp))
            Text(
                "开关不在这里：请到「设置 → 自动回复解锁」。这里只调回复内容与频率。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                Switch(checked = autoReplyAi, onCheckedChange = { scope.launch { app.settings.setAutoReplyAi(it) } })
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("用 AI 生成回复内容（可选）", style = MaterialTheme.typography.bodyMedium)
                    Text("关掉就用下面的固定模板 —— 不配 AI Key 也能解锁", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }
            ConfigField("回复模板", templateInput, { templateInput = it }, "感谢分享，正好需要", lines = 2)
            ConfigField("AI 额外要求（可选）", promptInput, { promptInput = it }, "例如：不要用感叹号", lines = 2)
            ConfigField("每日最多自动回复次数", autoReplyDaily.toString(), {
                it.toIntOrNull()?.let { v -> scope.launch { app.settings.setAutoReplyDaily(v) } }
            }, "10")
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ConfigField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    hint: String = "",
    lines: Int = 1,
    password: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = if (hint.isNotEmpty()) ({ Text(hint) }) else null,
        singleLine = lines == 1,
        maxLines = lines,
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}
