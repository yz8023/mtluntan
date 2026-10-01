package io.mtluntan.app.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import io.mtluntan.app.MTLuntanApp
import io.mtluntan.app.domain.model.Notice
import kotlinx.coroutines.launch

/** Notifications + PM inbox. */
@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun NoticeScreen(app: MTLuntanApp, nav: NavHostController? = null) {
    var notices by remember { mutableStateOf<List<Notice>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        loading = true
        try {
            val list = app.forum.notices() + app.forum.pms()
            notices = list.sortedByDescending { it.time }.take(100)
            error = ""
        } catch (e: Exception) {
            error = e.message ?: "加载失败"
        }
        loading = false
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("消息") }) },
    ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad)) {
            when {
                loading -> LoadingBox()
                error.isNotEmpty() && notices.isEmpty() -> MessageBox(error)
                notices.isEmpty() -> MessageBox("暂无消息")
                else -> LazyColumn {
                    items(notices, key = { notices.indexOf(it) }) { n ->
                        NoticeRow(n)
                    }
                }
            }
        }
    }
}

@Composable
private fun NoticeRow(n: Notice) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = n.body,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.padding(top = 6.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
            ) {
                if (n.fromAuthor.isNotBlank()) {
                    Text(n.fromAuthor, style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                }
                Text(
                    n.time.ifBlank { if (n.type == 1) "私信" else "通知" },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}