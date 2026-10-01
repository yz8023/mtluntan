package io.mtluntan.app.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import io.mtluntan.app.MTLuntanApp
import io.mtluntan.app.data.network.ApiUris
import io.mtluntan.app.domain.model.ThreadItem
import io.mtluntan.app.ui.navigation.Routes
import kotlinx.coroutines.launch

/** 导读: newest threads across all boards, with view switching. */
@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun GuideScreen(app: MTLuntanApp, nav: NavHostController? = null) {
    var view by remember { mutableStateOf("newthread") }
    var page by remember { mutableIntStateOf(1) }
    val items = remember { mutableStateListOf<ThreadItem>() }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun load(reset: Boolean) {
        scope.launch {
            loading = true
            if (reset) { items.clear() }
            try {
                val list = app.forum.guide(view, page)
                if (reset) items.addAll(list) else if (list.isNotEmpty()) items.addAll(list)
                error = ""
            } catch (e: Exception) {
                error = e.message ?: "加载失败"
            }
            loading = false
        }
    }

    LaunchedEffect(view) {
        page = 1
        load(true)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MT论坛") },
                actions = {
                    IconButton(onClick = { /* search from notice? leave nav */ }) { Icon(Icons.Filled.Search, "$ icon") }
                },
            )
        },
    ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad)) {
            GuideViewTabs(selected = view, onSelect = { view = it })
            when {
                loading && items.isEmpty() -> LoadingBox()
                error.isNotEmpty() && items.isEmpty() -> MessageBox(error)
                else -> ThreadList(
                    items = items.toList(),
                    onOpen = { item -> nav?.navigate(Routes.thread(item.threadId)) },
                    onLoadMore = { if (!loading) { page++; load(false) } },
                    loading = loading,
                )
            }
        }
    }
}

@Composable
private fun GuideViewTabs(selected: String, onSelect: (String) -> Unit) {
    val views = listOf("newthread" to "最新", "new" to "新增", "hot" to "热门", "digest" to "精华")
    androidx.compose.material3.TabRow(selectedTabIndex = views.indexOfFirst { it.first == selected }.coerceAtLeast(0)) {
        views.forEachIndexed { i, (name, label) ->
            androidx.compose.material3.Tab(
                selected = selected == name,
                onClick = { onSelect(name) },
                text = { Text(label) },
            )
        }
    }
}