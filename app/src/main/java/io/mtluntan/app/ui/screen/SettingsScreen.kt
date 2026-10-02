package io.mtluntan.app.ui.screen

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import io.mtluntan.app.MTLuntanApp
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
    val themeColor by app.settings.themeColor.collectAsStateWithLifecycle(initialValue = 0)
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

            item { GroupTitle("外观与阅读") }
            item {
                SettingChoice(
                    title = "深色模式",
                    options = listOf("跟随系统", "深色", "浅色"),
                    selected = when (darkMode) { null -> "跟随系统"; true -> "深色"; else -> "浅色" },
                    onSelect = { value ->
                        scope.launch {
                            app.settings.setDarkMode(
                                when (value) { "深色" -> true; "浅色" -> false; else -> null }
                            )
                        }
                    },
                )
            }
            item {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text("主题色", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ThemePresets.forEachIndexed { index, preset ->
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(androidx.compose.ui.graphics.Color(preset.light), CircleShape)
                                    .clickable { scope.launch { app.settings.setThemeColor(index) } },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (index == themeColor) {
                                    Text("✓", color = androidx.compose.ui.graphics.Color.White)
                                }
                            }
                        }
                    }
                }
            }
            item {
                SettingChoice(
                    title = "悬浮面板不透明度",
                    subtitle = "底栏与侧边栏共用这一档",
                    options = OpacityLabels,
                    selected = OpacityLabels.getOrElse(opacity) { "标准" },
                    onSelect = { value -> scope.launch { app.settings.setOpacity(OpacityLabels.indexOf(value)) } },
                )
            }
            item {
                SettingSwitch(
                    title = "动态取色",
                    subtitle = "安卓 12+ 跟随壁纸取色（会覆盖上面的主题色）",
                    checked = dynamicColor,
                    onChange = { scope.launch { app.settings.setDynamicColor(it) } },
                )
            }
            item {
                SettingChoice(
                    title = "字体大小",
                    options = listOf("小", "标准", "大", "特大", "超大"),
                    selected = listOf("小", "标准", "大", "特大", "超大").getOrElse(fontScale) { "标准" },
                    onSelect = { value ->
                        scope.launch {
                            app.settings.setFontScale(listOf("小", "标准", "大", "特大", "超大").indexOf(value))
                        }
                    },
                )
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
                    subtitle = "开启后楼层里直接折叠掉这些人的内容",
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
                }
            }
        }
    }
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
