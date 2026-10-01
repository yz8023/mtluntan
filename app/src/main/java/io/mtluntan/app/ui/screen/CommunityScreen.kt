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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import io.mtluntan.app.MTLuntanApp
import io.mtluntan.app.domain.model.Forum
import io.mtluntan.app.domain.model.ForumCategory
import io.mtluntan.app.ui.navigation.Routes
import kotlinx.coroutines.launch

/** Community / forum index. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityScreen(app: MTLuntanApp, nav: NavHostController? = null) {
    var categories by remember { mutableStateOf<List<ForumCategory>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        loading = true
        try {
            categories = app.forum.forumIndex()
            error = ""
        } catch (e: Exception) {
            error = e.message ?: "加载失败"
        }
        loading = false
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("社区") }) },
    ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad)) {
            when {
                loading -> LoadingBox()
                error.isNotEmpty() && categories.isEmpty() -> MessageBox(error)
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    categories.forEach { category ->
                        item(key = "cat-${category.id}") {
                            Text(
                                text = category.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
                                textAlign = TextAlign.Start,
                            )
                        }
                        items(category.forums, key = { "cat-${category.id}-${it.id}" }) { forum ->
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
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp),
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