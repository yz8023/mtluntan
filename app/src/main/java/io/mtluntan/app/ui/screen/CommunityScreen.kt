package io.mtluntan.app.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import io.mtluntan.app.MTLuntanApp
import io.mtluntan.app.domain.model.Forum
import io.mtluntan.app.domain.model.ForumCategory
import io.mtluntan.app.ui.components.MtButton
import io.mtluntan.app.ui.components.MtCard
import io.mtluntan.app.ui.components.MtSectionHeader
import io.mtluntan.app.ui.navigation.Routes
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Community / forum index. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityScreen(app: MTLuntanApp, nav: NavHostController? = null, onOpenDrawer: () -> Unit = {}) {
    val tabTick by io.mtluntan.app.util.Refresh.tabTick.collectAsStateWithLifecycle(initialValue = 0)
    val generation by io.mtluntan.app.util.Refresh.generation.collectAsStateWithLifecycle(initialValue = 0)
    var categories by remember { mutableStateOf<List<ForumCategory>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        loading = true
        try {
            categories = app.forum.forumIndex()
            error = if (categories.isEmpty()) "板块列表是空的，点右上角刷新试试" else ""
        } catch (e: Exception) {
            error = e.message ?: "加载失败"
        }
        loading = false
    }

    LaunchedEffect(tabTick, generation) { load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("社区") },
                navigationIcon = { IconButton(onClick = onOpenDrawer) { Icon(Icons.Filled.Menu, "菜单") } },
                actions = {
                    IconButton(onClick = { scope.launch { load() } }) { Icon(Icons.Filled.Refresh, "刷新") }
                    IconButton(onClick = { nav?.navigate(Routes.SEARCH) }) { Icon(Icons.Filled.Search, "搜索") } 
                },
            )
        },
    ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad)) {
            when {
                loading && categories.isEmpty() -> LoadingBox()
                error.isNotEmpty() && categories.isEmpty() -> Column {
                    MessageBox(error)
                    MtButton(
                        text = "重新加载",
                        onClick = { scope.launch { load() } },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    // 修闪退：分组 id 在解析结果里常常是 0，用「下标+名字」做 key 才不会撞车
                    // （LazyColumn 的 key 重复直接抛异常 → 底部 Tab 一进就崩）
                    categories.forEachIndexed { catIndex, category ->
                        item(key = "cat-$catIndex-${category.name}") {
                            MtSectionHeader(
                                text = category.name,
                                modifier = Modifier.padding(start = 10.dp, end = 10.dp),
                            )
                        }
                        items(
                            items = category.forums,
                            key = { forum -> "forum-$catIndex-${forum.id}-${forum.name}" },
                        ) { forum ->
                            ForumRow(forum) {
                                nav?.navigate(Routes.forum(forum.id, forum.name))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ForumRow(forum: Forum, onOpen: () -> Unit) {
    MtCard(
        onClick = onOpen,
        padding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            RowText(
                name = forum.name,
                detail = buildString {
                    if (forum.today > 0) append("今日 ${forum.today}")
                    if (forum.threads > 0) { if (isNotEmpty()) append(" · "); append("主题 ${forum.threads}") }
                    if (forum.posts > 0) { if (isNotEmpty()) append(" · "); append("帖子 ${forum.posts}") }
                },
            )
            if (forum.desc.isNotBlank()) {
                Text(
                    text = forum.desc,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun RowText(name: String, detail: String) {
    androidx.compose.foundation.layout.Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(name, style = MaterialTheme.typography.titleMedium)
        Text(detail, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
    }
}