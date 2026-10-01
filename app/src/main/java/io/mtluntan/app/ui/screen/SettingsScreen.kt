package io.mtluntan.app.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import io.mtluntan.app.MTLuntanApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(app: MTLuntanApp, nav: NavHostController) {
    val scope = rememberCoroutineScope()
    val darkMode by app.settings.darkMode.collectAsState(initial = null)
    val dynamicColor by app.settings.dynamicColor.collectAsState(initial = true)
    val desktopMode by app.settings.desktopMode.collectAsState(initial = false)
    val autoSign by app.settings.autoSign.collectAsState(initial = true)
    val notifyEnabled by app.settings.notifyEnabled.collectAsState(initial = true)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "back") }
                },
            )
        },
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState()),
        ) {
            listOf(
                Triple("深色模式", darkMode == true) {
                    scope.launch {
                        val next = darkMode != true
                        // tri-state: off -> on; on -> follow system
                        app.settings.setDarkMode(if (next) true else null)
                    }
                },
                Triple("跟随系统深色", darkMode == null) {
                    scope.launch { app.settings.setDarkMode(null) }
                },
                Triple("动态取色", dynamicColor, {
                    scope.launch { app.settings.setDynamicColor(!dynamicColor) }
                }),
            ).forEach { (label, checked, onChange) ->
                ListItem(
                    headlineContent = { Text(label) },
                    trailingContent = {
                        Switch(checked = checked, onCheckedChange = { onChange() })
                    },
                )
                HorizontalDivider()
            }
            ListItem(
                headlineContent = { Text("桌面版网页模式") },
                supportingContent = {
                    Text("使用桌面 UA 获取完整 PC 页面（详情页推荐）")
                },
                trailingContent = {
                    Switch(checked = desktopMode, onCheckedChange = { v ->
                        scope.launch { app.settings.setDesktopMode(v) }
                    })
                },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("自动签到") },
                trailingContent = {
                    Switch(checked = autoSign, onCheckedChange = { v ->
                        scope.launch { app.settings.setAutoSign(v) }
                    })
                },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("新消息提醒") },
                supportingContent = { Text("后台定期检查通知") },
                trailingContent = {
                    Switch(checked = notifyEnabled, onCheckedChange = { v ->
                        scope.launch { app.settings.setNotifyEnabled(v) }
                    })
                },
            )
        }
    }
}