package io.mtluntan.app.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import io.mtluntan.app.MTLuntanApp
import io.mtluntan.app.ui.components.MtCard
import io.mtluntan.app.domain.model.HistoryRecord
import kotlinx.coroutines.launch

/** Browse history backed by Room. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(app: MTLuntanApp, nav: NavHostController) {
    val scope = rememberCoroutineScope()
    val history by app.local.recentHistory.collectAsStateWithLifecycle(initialValue = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("浏览历史") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "back") }
                },
            )
        },
    ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad)) {
            if (history.isEmpty()) {
                MessageBox("暂无浏览记录")
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(history, key = { it.id }) { record ->
                        HistoryRow(
                            record = record,
                            onClick = { nav.navigate("thread/${record.tid}") },
                            onDelete = { scope.launch { app.local.removeHistory(record.id) } },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(record: HistoryRecord, onClick: () -> Unit, onDelete: () -> Unit) {
    MtCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    record.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    buildString {
                        if (record.boardName.isNotBlank()) append(record.boardName)
                        if (record.authorName.isNotBlank()) {
                            if (isNotEmpty()) append(" · ")
                            append(record.authorName)
                        }
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.DeleteOutline, "删除", tint = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

/** Favorites stored locally. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(app: MTLuntanApp, nav: NavHostController) {
    val scope = rememberCoroutineScope()
    val favorites by app.local.favorites.collectAsStateWithLifecycle(initialValue = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的收藏") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "back") }
                },
            )
        },
    ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad)) {
            if (favorites.isEmpty()) {
                MessageBox("还没有收藏帖子")
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(favorites, key = { it.threadId }) { item ->
                        ThreadCard(item, onClick = { nav.navigate("thread/${item.threadId}") })
                    }
                }
            }
        }
    }
}