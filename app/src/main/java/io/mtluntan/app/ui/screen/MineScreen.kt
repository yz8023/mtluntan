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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import coil.compose.AsyncImage
import io.mtluntan.app.MTLuntanApp
import io.mtluntan.app.domain.model.Account
import io.mtluntan.app.ui.navigation.Routes
import kotlinx.coroutines.launch

/** 我的: account switch, check-in, history, favorites, settings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MineScreen(app: MTLuntanApp, nav: NavHostController) {
    val scope = rememberCoroutineScope()
    val accounts by app.auth.accounts.collectAsStateWithLifecycle(initialValue = emptyList())
    val activeName by app.auth.activeAccount.collectAsStateWithLifecycle(initialValue = null)
    var signing by remember { mutableStateOf(false) }
    var signMsg by remember { mutableStateOf("") }

    Scaffold(
        topBar = { TopAppBar(title = { Text("我的") }) },
    ) { pad ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(pad)) {
            item {
                AccountHeader(
                    name = activeName,
                    account = accounts.find { it.username == activeName },
                    onClick = {
                        if (activeName == null) nav.navigate(Routes.LOGIN)
                        else nav.navigate("settings")
                    },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("每日签到") },
                    supportingContent = {
                        Text(signMsg.ifEmpty { if (activeName != null) "点击签到领取金币" else "请先登录" })
                    },
                    trailingContent = {
                        if (activeName != null) {
                            Icon(
                                imageVector = if (signing) Icons.Filled.CheckCircle else Icons.Filled.AddCircle,
                                contentDescription = "签到",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = activeName != null && !signing) {
                            val name = activeName
                            signing = true
                            scope.launch {
                                val (ok, msg) = app.forum.doSignIn()
                                signMsg = msg
                                if (ok && name != null) app.auth.recordSign(name, true)
                                signing = false
                            }
                        },
                )
            }
            item { HorizontalDivider() }
            item {
                ListItem(
                    leadingContent = { Icon(Icons.Filled.History, null) },
                    headlineContent = { Text("浏览历史") },
                    modifier = Modifier.fillMaxWidth().clickable { nav.navigate("history") },
                )
            }
            item {
                ListItem(
                    leadingContent = { Icon(Icons.Filled.Star, null) },
                    headlineContent = { Text("我的收藏") },
                    modifier = Modifier.fillMaxWidth().clickable { nav.navigate("favorites") },
                )
            }
            item {
                ListItem(
                    leadingContent = { Icon(Icons.Filled.Settings, null) },
                    headlineContent = { Text("设置") },
                    modifier = Modifier.fillMaxWidth().clickable { nav.navigate("settings") },
                )
            }
            if (activeName != null) {
                item {
                    ListItem(
                        leadingContent = { Icon(Icons.AutoMirrored.Filled.Logout, null) },
                        headlineContent = { Text("退出登录") },
                        modifier = Modifier.fillMaxWidth().clickable {
                            scope.launch { app.auth.activate(null) }
                        },
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun AccountHeader(name: String?, account: Account?, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp),
        ) {
            if (name == null) {
                Icon(Icons.Filled.AccountCircle, null, modifier = Modifier.size(56.dp))
                Text(
                    "未登录",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 12.dp),
                )
            } else {
                AsyncImage(
                    model = account?.avatarUrl ?: "",
                    contentDescription = "头像",
                    modifier = Modifier.size(56.dp),
                )
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(name, style = MaterialTheme.typography.titleMedium)
                    if (account?.uid != null && account.uid > 0) {
                        Text(
                            "UID ${account.uid}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }
        }
    }
}