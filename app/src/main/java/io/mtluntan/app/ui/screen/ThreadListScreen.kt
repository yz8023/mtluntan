package io.mtluntan.app.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Create
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import io.mtluntan.app.MTLuntanApp
import io.mtluntan.app.domain.model.ThreadItem
import kotlinx.coroutines.launch

/** Thread list of a single board (forumdisplay). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadListScreen(app: MTLuntanApp, nav: NavHostController, fid: Long, boardName: String) {
    var page by remember { mutableIntStateOf(1) }
    val items = remember { mutableStateListOf<ThreadItem>() }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    var exhausted by remember { mutableStateOf(false) }

    suspend fun load(reset: Boolean) {
        loading = true
        if (reset) items.clear()
        try {
            val list = app.forum.forumThreads(fid, page)
            if (reset) { items.clear(); items.addAll(list) } else if (list.isNotEmpty()) items.addAll(list)
            if (list.isEmpty()) exhausted = true
            error = ""
        } catch (e: Exception) { error = e.message ?: "加载失败" }
        loading = false
    }

LaunchedEffect(Unit) { exhausted = false; load(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(boardName) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        nav.navigate(io.mtluntan.app.ui.navigation.Routes.newThread(fid))
                    }) {
                        Icon(Icons.Filled.Create, "发帖")
                    }
                },
            )
        },
    ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad)) {
            when {
                loading && items.isEmpty() -> LoadingBox()
                error.isNotEmpty() && items.isEmpty() -> MessageBox(error)
                else -> ThreadList(
                    items = items.toList(),
                    onOpen = { item -> nav.navigate("thread/${item.threadId}") },
                    onLoadMore = {
                        if (!loading) { page++; scope.launch { load(false) } }
                    },
                    loading = loading,
                    exhausted = exhausted,
                )
            }
        }
    }
}