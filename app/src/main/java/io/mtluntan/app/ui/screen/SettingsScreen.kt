package io.mtluntan.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import io.mtluntan.app.MTLuntanApp
import io.mtluntan.app.ui.components.MtSegmented
import io.mtluntan.app.ui.components.MtButton
import io.mtluntan.app.ui.navigation.Routes
import io.mtluntan.app.ui.theme.OpacityLabels
import io.mtluntan.app.ui.theme.ThemePresets
import io.mtluntan.app.util.CopyUtil
import io.mtluntan.app.util.LogCenter
import kotlinx.coroutines.launch

/** 设置：按「账号与自动化 / 外观与阅读 / 网络与下载 / AI / 记录 / 关于」分组。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(app: MTLuntanApp, nav: NavHostController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    val darkMode by app.settings.darkMode.collectAsStateWithLifecycle(initialValue = null)
    val opacity by app.settings.opacity.collectAsStateWithLifecycle(initialValue = 1)
    val dynamicColor by app.settings.dynamicColor.collectAsStateWithLifecycle(initialValue = false)
    val fontScale by app.settings.fontScale.collectAsStateWithLifecycle(initialValue = 0)
    val bottomAutoHide by app.settings.bottomAutoHide.collectAsStateWithLifecycle(initialValue = true)
    val imageMode by app.settings.imageMode.collectAsStateWithLifecycle(initialValue = "inline")
    val hideBlacklist by app.settings.hideBlacklist.collectAsStateWithLifecycle(initialValue = false)
    val desktopMode by app.settings.desktopMode.collectAsStateWithLifecycle(initialValue = false)
    val downloadMode by app.settings.downloadMode.collectAsStateWithLifecycle(initialValue = "inapp")
    val defaultView by app.settings.defaultView.collectAsStateWithLifecycle(initialValue = "newthread")
    val autoSign by app.settings.autoSign.collectAsStateWithLifecycle(initialValue = true)
    val signHour by app.settings.signHour.collectAsStateWithLifecycle(initialValue = 8)
    val signMinute by app.settings.signMinute.collectAsStateWithLifecycle(initialValue = 30)
    val spacing by app.settings.signSpacing.collectAsStateWithLifecycle(initialValue = 5)
    val notify by app.settings.notifyEnabled.collectAsStateWithLifecycle(initialValue = true)
    val autoReply by app.settings.autoReply.collectAsStateWithLifecycle(initialValue = false)
    val recordDetail by app.settings.recordDetailMode.collectAsStateWithLifecycle(initialValue = false)
    val paletteIndex by app.settings.themeColor.collectAsStateWithLifecycle(initialValue = 0)
    val shade by app.settings.themeShade.collectAsStateWithLifecycle(initialValue = 45)
    val glassIndex by app.settings.glassLevel.collectAsStateWithLifecycle(initialValue = 1)
    val motionEnabled by app.settings.motionEnabled.collectAsStateWithLifecycle(initialValue = true)
    val motionDamping by app.settings.motionDamping.collectAsStateWithLifecycle(initialValue = 50)
    val autoPagination by app.settings.autoPagination.collectAsStateWithLifecycle(initialValue = true)
    val toolbarStyle by app.settings.toolbarStyle.collectAsStateWithLifecycle(initialValue = "icon")
    val toolbarRows by app.settings.toolbarRows.collectAsStateWithLifecycle(initialValue = 2)
    val replyPreview by app.settings.replyPreview.collectAsStateWithLifecycle(initialValue = true)
    var crashLog by remember { mutableStateOf("") }
    var showCrash by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { crashLog = io.mtluntan.app.util.CrashGuard.read(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
            )
        },
    ) { pad ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(pad)) {
            item { GroupTitle("账号与自动化") }
            item {
                SettingSwitch(
                    title = "每日自动签到",
                    subtitle = "到点自动给所有启用账号签到（默认 ${"%02d:%02d".format(signHour, signMinute)}）",
                    checked = autoSign,
                    onChange = { v ->
                        scope.launch {
                            app.settings.setAutoSign(v)
                            if (v) io.mtluntan.app.worker.SignScheduler.schedule(context, signHour, signMinute)
                            else io.mtluntan.app.worker.SignScheduler.cancel(context)
                        }
                    },
                )
            }
            item {
                SettingChoice(
                    title = "签到时间",
                    subtitle = "改成你喜欢的时间，WorkManager 会按这个点排程",
                    options = listOf("06:30", "08:30", "12:00", "20:00", "22:30"),
                    selected = "%02d:%02d".format(signHour, signMinute),
                    onSelect = { value ->
                        val h = value.substringBefore(":").toIntOrNull() ?: 8
                        val m = value.substringAfter(":").toIntOrNull() ?: 30
                        scope.launch {
                            app.settings.setSignTime(h, m)
                            if (autoSign) io.mtluntan.app.worker.SignScheduler.schedule(context, h, m)
                        }
                    },
                )
            }
            item {
                SettingChoice(
                    title = "账号之间签到间隔",
                    subtitle = "间隔太短会被论坛按 IP 限流返回 403",
                    options = listOf("0", "3", "5", "10", "20"),
                    selected = spacing.toString(),
                    onSelect = { scope.launch { app.settings.setSignSpacing(it.toIntOrNull() ?: 5) } },
                    suffix = " 秒",
                )
            }
            item {
                SettingSwitch(
                    title = "后台通知",
                    subtitle = "签到结果汇总、新消息提醒、自动回复失败",
                    checked = notify,
                    onChange = { v ->
                        scope.launch {
                            app.settings.setNotifyEnabled(v)
                            if (v) io.mtluntan.app.worker.BadgeScheduler.schedule(context)
                            else io.mtluntan.app.worker.BadgeScheduler.cancel(context)
                        }
                    },
                )
            }
            item { EntryLine("账号管理", "多账号 / 密码托管 / 排序") { nav.navigate(Routes.ACCOUNTS) } }
            item { EntryLine("签到记录", "每个账号的签到结果") { nav.navigate(Routes.SIGN_RECORDS) } }
            item {
                EntryLine("检测当前会话", "掉线时用托管密码静默重登（v4.2 自动恢复）") {
                    scope.launch {
                        CopyUtil.toast(context, "正在检测会话…")
                        val status = runCatching { app.auth.ensureSession() }.getOrNull()
                        CopyUtil.toast(
                            context,
                            when {
                                status == null -> "检测失败，稍后再试"
                                status.loggedIn && status.repaired -> "会话已自动恢复"
                                status.loggedIn -> "会话正常${if (status.nickname.isNotBlank()) "（${status.nickname}）" else ""}"
                                else -> status.message.ifBlank { "需要重新登录" }
                            },
                        )
                    }
                }
            }
            item { EntryLine("黑名单", "被拉黑的人不再显示发言") { nav.navigate(Routes.BLACKLIST) } }

            item { GroupTitle("主题与外观") }

            // ---- 调色板（12 套种子色，整套配色由引擎生成） ----
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("调色板", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "整套配色（卡片 / 底栏 / 按钮）都由这一颗种子色生成",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    Spacer(Modifier.height(10.dp))
                    // 两行六列，换行布局不用 LazyVerticalGrid，避免嵌套滚动
                    io.mtluntan.app.ui.theme.MtPalette.entries.chunked(6).forEach { rowItems ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                            rowItems.forEach { palette ->
                                val index = io.mtluntan.app.ui.theme.MtPalette.entries.indexOf(palette)
                                val selected = index == paletteIndex
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.clickable {
                                        scope.launch {
                                            app.settings.setThemeColor(index)
                                            if (palette == io.mtluntan.app.ui.theme.MtPalette.Custom) {
                                                // 自定义：先用当前主题色做种子，可再去「自定义种子色」里换
                                                app.settings.setCustomSeed(
                                                    io.mtluntan.app.ui.theme.swatchColor(palette).toArgb().toLong()
                                                )
                                            }
                                        }
                                    },
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .background(io.mtluntan.app.ui.theme.swatchColor(palette), CircleShape)
                                            .then(
                                                if (selected) Modifier.border(2.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                                else Modifier
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (selected) Text("✓", color = Color.White, style = MaterialTheme.typography.labelMedium)
                                    }
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        palette.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ---- 自定义种子色 ----
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Text("自定义种子色", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "拖动色相条，主题色实时跟着变",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            0xFF2F6BFF, 0xFF00A3A3, 0xFF16A06A, 0xFF7CB342, 0xFFC29A16,
                            0xFFE2703A, 0xFFE05B72, 0xFFD46AA5, 0xFF6C5CE7, 0xFF3E4C59,
                        ).forEach { argb ->
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(Color(argb.toInt()), CircleShape)
                                    .clickable {
                                        scope.launch {
                                            app.settings.setCustomSeed(argb.toLong())
                                            app.settings.setThemeColor(
                                                io.mtluntan.app.ui.theme.MtPalette.entries.indexOf(io.mtluntan.app.ui.theme.MtPalette.Custom)
                                            )
                                        }
                                    }
                            )
                        }
                    }
                }
            }

            // ---- 浓度 ----
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Text("背景着色浓度", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "0 = 接近纯白/纯黑，100 = 背景也明显带主题色",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    Slider(
                        value = shade.toFloat(),
                        onValueChange = { value -> scope.launch { app.settings.setThemeShade(value.toInt()) } },
                        valueRange = 0f..100f,
                    )
                }
            }

            // ---- 玻璃档位 ----
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Text("玻璃质感", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "面板半透明 + 描边高光，内容仍在实色卡片上，不影响阅读",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    Spacer(Modifier.height(6.dp))
                    MtSegmented(
                        options = io.mtluntan.app.ui.theme.GlassLevel.entries.map { it.label },
                        selected = io.mtluntan.app.ui.theme.GlassLevel.entries[glassIndex.coerceIn(0, 2)].label,
                        onSelect = { label ->
                            val level = io.mtluntan.app.ui.theme.GlassLevel.entries.first { it.label == label }
                            scope.launch { app.settings.setGlassLevel(level.ordinal) }
                        },
                    )
                }
            }

            item {
                SettingChoice(
                    title = "深色模式",
                    options = listOf("跟随系统", "深色", "浅色"),
                    selected = when (darkMode) { null -> "跟随系统"; true -> "深色"; else -> "浅色" },
                    onSelect = { value ->
                        scope.launch {
                            app.settings.setDarkMode(when (value) { "深色" -> true; "浅色" -> false; else -> null })
                        }
                    },
                )
            }
            item {
                SettingSwitch(
                    title = "动态取色",
                    subtitle = "安卓 12+ 跟随壁纸取色（会覆盖上面的调色板）",
                    checked = dynamicColor,
                    onChange = { scope.launch { app.settings.setDynamicColor(it) } },
                )
            }
            item {
                SettingChoice(
                    title = "面板不透明度",
                    subtitle = "底栏与侧边栏共用这一档",
                    options = OpacityLabels,
                    selected = OpacityLabels.getOrElse(opacity) { "标准" },
                    onSelect = { value -> scope.launch { app.settings.setOpacity(OpacityLabels.indexOf(value)) } },
                )
            }
            item {
                SettingChoice(
                    title = "字体大小",
                    options = listOf("小", "标准", "大", "特大", "超大"),
                    selected = listOf("小", "标准", "大", "特大", "超大").getOrElse(fontScale) { "标准" },
                    onSelect = { value ->
                        scope.launch { app.settings.setFontScale(listOf("小", "标准", "大", "特大", "超大").indexOf(value)) }
                    },
                )
            }

            item { GroupTitle("动效") }

            item {
                SettingSwitch(
                    title = "交互动效",
                    subtitle = "按压缩放、列表交错入场、指示器滑动；关闭后瞬时切换",
                    checked = motionEnabled,
                    onChange = { scope.launch { app.settings.setMotionEnabled(it) } },
                )
            }
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Text("动效阻尼", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "左 = 更弹、更活泼；右 = 更顺、更克制（一个滑杆统一全 App 手感）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    Slider(
                        value = motionDamping.toFloat(),
                        onValueChange = { value -> scope.launch { app.settings.setMotionDamping(value.toInt()) } },
                        valueRange = 0f..100f,
                    )
                }
            }
            item {
                SettingSwitch(
                    title = "底栏滚动自动隐藏",
                    subtitle = "向下滚动收起，向上滚动或停下再出现",
                    checked = bottomAutoHide,
                    onChange = { scope.launch { app.settings.setBottomAutoHide(it) } },
                )
            }
            item {
                SettingSwitch(
                    title = "评论自动下一页",
                    subtitle = "一页没解析到回复时，自动把后面的页接上",
                    checked = autoPagination,
                    onChange = { scope.launch { app.settings.setAutoPagination(it) } },
                )
            }
            item {
                SettingChoice(
                    title = "快捷工具栏形态",
                    subtitle = "发帖 / 回复工具条用「文字」还是「图标」",
                    options = listOf("文字", "图标"),
                    selected = if (toolbarStyle == "text") "文字" else "图标",
                    onSelect = { value -> scope.launch { app.settings.setToolbarStyle(if (value == "文字") "text" else "icon") } },
                )
            }
            item {
                SettingChoice(
                    title = "工具栏显示行数",
                    subtitle = "默认 2 行；行数越多每个按钮越大越好点",
                    options = listOf("1 行", "2 行", "3 行", "4 行"),
                    selected = "${toolbarRows.coerceIn(1, 4)} 行",
                    onSelect = { value ->
                        val n = value.filter { it.isDigit() }.toIntOrNull() ?: 2
                        scope.launch { app.settings.setToolbarRows(n) }
                    },
                )
            }
            item {
                SettingSwitch(
                    title = "回复实时预览",
                    subtitle = "边打字边渲染 BBCode 效果，不用手动点预览",
                    checked = replyPreview,
                    onChange = { scope.launch { app.settings.setReplyPreview(it) } },
                )
            }
            item {
                SettingChoice(
                    title = "正文图片显示方式",
                    options = listOf("原位大图", "汇总图廊"),
                    selected = if (imageMode == "gallery") "汇总图廊" else "原位大图",
                    onSelect = { value -> scope.launch { app.settings.setImageMode(if (value == "汇总图廊") "gallery" else "inline") } },
                )
            }
            item {
                SettingSwitch(
                    title = "隐藏黑名单用户发言",
                    subtitle = "楼层折叠成一行，点一下可恢复",
                    checked = hideBlacklist,
                    onChange = { scope.launch { app.settings.setHideBlacklist(it) } },
                )
            }

            item { GroupTitle("网络与下载") }
            item {
                SettingSwitch(
                    title = "桌面版网页模式",
                    subtitle = "用 PC UA 请求，某些页面结构更完整",
                    checked = desktopMode,
                    onChange = { v ->
                        scope.launch {
                            app.settings.setDesktopMode(v)
                            io.mtluntan.app.data.network.SessionHolder.setDesktopMode(v)
                        }
                    },
                )
            }
            item {
                SettingChoice(
                    title = "文件下载方式",
                    options = listOf("应用内下载", "跳浏览器"),
                    selected = if (downloadMode == "browser") "跳浏览器" else "应用内下载",
                    onSelect = { value -> scope.launch { app.settings.setDownloadMode(if (value == "跳浏览器") "browser" else "inapp") } },
                )
            }
            item {
                SettingChoice(
                    title = "默认导读视图",
                    options = listOf("最新", "新增", "热门", "精华"),
                    selected = when (defaultView) {
                        "new" -> "新增"; "hot" -> "热门"; "digest" -> "精华"; else -> "最新"
                    },
                    onSelect = { value ->
                        scope.launch {
                            app.settings.setDefaultView(
                                when (value) { "新增" -> "new"; "热门" -> "hot"; "精华" -> "digest"; else -> "newthread" }
                            )
                        }
                    },
                )
            }

            item { GroupTitle("AI 与自动化") }
            item {
                SettingSwitch(
                    title = "自动回复 / 进帖解锁",
                    subtitle = "隐藏内容自动回复可见；同一帖子 6 小时内只回一次",
                    checked = autoReply,
                    onChange = { v ->
                        scope.launch {
                            app.settings.setAutoReply(v)
                            if (v) io.mtluntan.app.ai.AutoReplyScheduler.schedule(context)
                            else io.mtluntan.app.ai.AutoReplyScheduler.cancel(context)
                        }
                    },
                )
            }
            item { EntryLine("AI 配置", "接口、Key、模型、提示词、模板") { nav.navigate(Routes.AI_CONFIG) } }
            item { EntryLine("AI 会话", "历史对话与帖子总结") { nav.navigate(Routes.AI_SESSIONS) } }

            item { GroupTitle("记录与调试") }
            item {
                SettingSwitch(
                    title = "记录详细模式",
                    subtitle = "关闭＝简洁一行；打开＝带完整原因，便于排查",
                    checked = recordDetail,
                    onChange = { scope.launch { app.settings.setRecordDetailMode(it) } },
                )
            }
            item { EntryLine("记录中心", "解锁决策 / 耗时 / 运行日志") { nav.navigate(Routes.RECORDS) } }
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            LogCenter.clear()
                            CopyUtil.toast(context, "运行日志已清空")
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Icon(Icons.Filled.DeleteOutline, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text("清空运行日志")
                }
            }

            item { GroupTitle("关于") }
            item {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("MT论坛 客户端", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Kotlin + Jetpack Compose 重写版\n第三方客户端，与论坛官方无关，仅供学习交流。\n" +
                            "自动化的所有开关默认关闭，开启后请控制频率，遵守站点规则。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "耗时埋点：${io.mtluntan.app.util.PerfLog.snapshot().size} 条 · 日志 ${LogCenter.entries.value.size} 条",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MtButton(
                            text = if (crashLog.isNotBlank()) "查看崩溃日志" else "崩溃日志（暂无）",
                            onClick = { showCrash = true },
                            primary = crashLog.isNotBlank(),
                        )
                        if (crashLog.isNotBlank()) {
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = {
                                io.mtluntan.app.util.CrashGuard.clear(context)
                                crashLog = ""
                                CopyUtil.toast(context, "崩溃日志已清空")
                            }) { Text("清空") }
                        }
                    }
                    if (crashLog.isNotBlank()) {
                        Text(
                            "崩溃后会自动记录完整堆栈（含未混淆的类名与行号），反馈问题时把它发来即可定位",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }
        }
    }

    if (showCrash) CrashLogDialog(crashLog) { showCrash = false }
}

@Composable
private fun CrashLogDialog(text: String, onDismiss: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("崩溃日志") },
        text = {
            Column(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(text, style = MaterialTheme.typography.labelSmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                io.mtluntan.app.util.CopyUtil.copy(ctx, text, "崩溃日志已复制")
                onDismiss()
            }) { Text("复制") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
private fun GroupTitle(text: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun SettingSwitch(title: String, subtitle: String = "", checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SettingChoice(
    title: String,
    subtitle: String = "",
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    suffix: String = "",
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (subtitle.isNotEmpty()) {
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(option + suffix, style = MaterialTheme.typography.labelMedium) },
                )
            }
        }
    }
}

@Composable
private fun EntryLine(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle.isNotEmpty()) Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.outline)
    }
}
