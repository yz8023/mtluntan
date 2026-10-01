package io.mtluntan.app.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import io.mtluntan.app.MTLuntanApp
import io.mtluntan.app.domain.model.ThreadDetail
import kotlinx.coroutines.launch

/** Thread detail: main post + replies with pagination. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadScreen(app: MTLuntanApp, nav: NavHostController, tid: Long) {
    var detail by remember { mutableStateOf<ThreadDetail?>(null) }
    var page by remember { mutableIntStateOf(1) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    suspend fun load() {
        loading = true
        try {
            val d = app.forum.threadDetail(tid, page)
            detail = d
            if (d.loginRequired) {
                error = "需要先登录"
            } else if (d.errorMessage.isNotEmpty()) {
                error = d.errorMessage
            } else {
                error = ""
                app.local.markRead(tid)
                app.local.recordVisit(tid, d.title, d.forumName, d.mainPost?.authorName ?: "")
            }
        } catch (e: Exception) {
            error = e.message ?: "加载失败"
        }
        loading = false
    }

    LaunchedEffect(tid, page) { load() }

    val current = detail
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        current?.title ?: "帖子",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "back") }
                },
                actions = {
                    if (current != null && current.formhash.isNotEmpty()) {
                        IconButton(onClick = {
                            nav.navigate(io.mtluntan.app.ui.navigation.Routes.reply(tid))
                        }) { Icon(Icons.Filled.Mode, "回复") }
                    }
                },
            )
        },
        bottomBar = {
            if (current != null) {
                PagerBar(
                    current = current.currentPage,
                    total = current.totalPages,
                    onPrev = { if (page > 1) { page-- ; scope.launch { listState.scrollToItem(0) } } },
                    onNext = { if (page < current.totalPages) { page++ ; scope.launch { listState.scrollToItem(0) } } },
                )
            }
        },
    ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad)) {
            if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            when {
                loading && current == null -> LoadingBox()
                error.isNotEmpty() -> MessageBox(error)
                current == null -> MessageBox("加载失败")
                else -> {
                    val posts = listOfNotNull(current.mainPost) + current.posts
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        items(posts, key = { it.pid }) { post ->
                            PostCard(post)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PagerBar(
    current: Int,
    total: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    androidx.compose.material3.Surface(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            TextButton(onClick = onPrev, enabled = current > 1) { Text("上一页") }
            Text("$current / $total", style = MaterialTheme.typography.labelMedium)
            TextButton(onClick = onNext, enabled = current < total) { Text("下一页") }
        }
    }
}