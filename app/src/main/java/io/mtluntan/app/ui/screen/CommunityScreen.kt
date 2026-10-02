package io.mtluntan.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import io.mtluntan.app.MTLuntanApp
import io.mtluntan.app.domain.model.Forum
import io.mtluntan.app.domain.model.ForumCategory
import io.mtluntan.app.ui.components.MtButton
import io.mtluntan.app.ui.components.MtCard
import io.mtluntan.app.ui.components.MtDivider
import io.mtluntan.app.ui.components.MtSectionHeader
import io.mtluntan.app.ui.navigation.Routes
import kotlinx.coroutines.launch

/**
 * Community / forum index.
 *
 * 1.1.5：版块列表支持用户自定义 ——
 *  ① 显示项目：可隐藏不看的版块；
 *  ② 排版方式：文字纵列 / 图标横排 / 图标纵排（每行 3 个）；
 *  ③ 排序：上移/下移调整顺序；
 *  以上选择全部落到 DataStore（AppSettings.community*），下次进来原样恢复。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityScreen(app: MTLuntanApp, nav: NavHostController? = null, onOpenDrawer: () -> Unit = {}) {
    val tabTick by io.mtluntan.app.util.Refresh.tabTick.collectAsStateWithLifecycle(initialValue = 0)
    val generation by io.mtluntan.app.util.Refresh.generation.collectAsStateWithLifecycle(initialValue = 0)
    var categories by remember { mutableStateOf<List<ForumCategory>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var showCustomize by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // ---- 用户自定义：排版 / 显示 / 排序（全部持久化） ----
    val layoutMode by app.settings.communityLayout.collectAsStateWithLifecycle(initialValue = 0)
    val hiddenIdsRaw by app.settings.communityHidden.collectAsStateWithLifecycle(initialValue = "")
    val orderRaw by app.settings.communityOrder.collectAsStateWithLifecycle(initialValue = "")
    val hiddenIds = remember(hiddenIdsRaw) { hiddenIdsRaw.split(',').filter { it.isNotBlank() }.toSet() }
    val orderIds = remember(orderRaw) { orderRaw.split(',').filter { it.isNotBlank() } }

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

    val allForums = remember(categories) { categories.flatMap { it.forums } }

    /** 先按自定义顺序排，再过滤掉隐藏的；分类标题按剩下版块所属分类保留。 */
    fun orderedVisible(): List<Pair<ForumCategory, List<Forum>>> {
        val index = allForums.associateBy { it.id }
        val head = orderIds.mapNotNull { index[it.toLongOrNull()] }
        val tail = allForums.filter { f -> orderIds.none { it == f.id.toString() } }
        val ordered = head + tail
        return categories.mapNotNull { cat ->
            val forums = ordered.filter { f ->
                f.id.toString() !in hiddenIds && cat.forums.any { it.id == f.id }
            }
            if (forums.isEmpty()) null else cat to forums
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("社区") },
                navigationIcon = { IconButton(onClick = onOpenDrawer) { Icon(Icons.Filled.Menu, "菜单") } },
                actions = {
                    IconButton(onClick = { showCustomize = true }) { Icon(Icons.Filled.Tune, "自定义社区") }
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
                else -> {
                    val groups = orderedVisible()
                    if (groups.isEmpty()) {
                        MessageBox("版块都被你隐藏啦，点右上角「自定义」恢复显示")
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            groups.forEachIndexed { catIndex, (category, forums) ->
                                item(key = "cat-$catIndex-${category.name}") {
                                    MtSectionHeader(
                                        text = category.name,
                                        modifier = Modifier.padding(start = 10.dp, end = 10.dp),
                                    )
                                }
                                when (layoutMode) {
                                    // 图标·纵排：每行 3 个，图标在上、名字在下
                                    // key 用「行号」而不是版块 id：解析出来的版块 id 常是 0，
                                    // 用 id 拼 key 会撞车 → LazyColumn 直接抛异常
                                    2 -> itemsIndexed(
                                        items = forums.chunked(3),
                                        key = { rowIndex, _ -> "gridrow-$catIndex-$rowIndex" },
                                    ) { _, row ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp, vertical = 3.dp),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            row.forEach { forum ->
                                                ForumTile(forum, Modifier.weight(1f)) {
                                                    nav?.navigate(Routes.forum(forum.id, forum.name))
                                                }
                                            }
                                            // 补齐空位，保证每格宽度一致
                                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                                        }
                                    }
                                    // 图标·横排：图标在左、名字与数据在右
                                    1 -> itemsIndexed(
                                        items = forums,
                                        key = { _, forum -> "forum-$catIndex-${forum.id}-${forum.name}" },
                                    ) { index, forum ->
                                        ForumIconRow(forum) {
                                            nav?.navigate(Routes.forum(forum.id, forum.name))
                                        }
                                        if (index < forums.lastIndex) MtDivider(startIndent = 62.dp)
                                    }
                                    // 文字纵列：纯文字，信息密度最高
                                    else -> itemsIndexed(
                                        items = forums,
                                        key = { _, forum -> "forum-$catIndex-${forum.id}-${forum.name}" },
                                    ) { index, forum ->
                                        ForumRow(forum) {
                                            nav?.navigate(Routes.forum(forum.id, forum.name))
                                        }
                                        if (index < forums.lastIndex) MtDivider(startIndent = 20.dp)
                                    }
                                }
                            }
                            item { Spacer(Modifier.height(24.dp)) }
                        }
                    }
                }
            }
        }
    }

    if (showCustomize) {
        CommunityCustomizeSheet(
            allForums = allForums,
            layoutMode = layoutMode,
            hiddenIds = hiddenIds,
            orderIds = orderIds,
            onLayout = { mode -> scope.launch { app.settings.setCommunityLayout(mode) } },
            onToggleVisible = { forum ->
                val next = if (forum.id.toString() in hiddenIds) hiddenIds - forum.id.toString()
                else hiddenIds + forum.id.toString()
                scope.launch { app.settings.setCommunityHidden(next.joinToString(",")) }
            },
            onMove = { forum, delta ->
                val cur = orderIds.ifEmpty { allForums.map { it.id.toString() } }.toMutableList()
                val from = cur.indexOf(forum.id.toString())
                if (from >= 0) {
                    val to = (from + delta).coerceIn(0, cur.lastIndex)
                    if (to != from) {
                        val item = cur.removeAt(from)
                        cur.add(to, item)
                        scope.launch { app.settings.setCommunityOrder(cur.joinToString(",")) }
                    }
                }
            },
            onReset = {
                scope.launch {
                    app.settings.setCommunityHidden("")
                    app.settings.setCommunityOrder("")
                }
            },
            onDismiss = { showCustomize = false },
        )
    }
}

/** 版块自定义面板：排版方式 + 显示项目 + 排序。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommunityCustomizeSheet(
    allForums: List<Forum>,
    layoutMode: Int,
    hiddenIds: Set<String>,
    orderIds: List<String>,
    onLayout: (Int) -> Unit,
    onToggleVisible: (Forum) -> Unit,
    onMove: (Forum, Int) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        val ordered = remember(allForums, orderIds) {
            val known = orderIds.mapNotNull { id -> allForums.firstOrNull { it.id.toString() == id } }
            known + allForums.filter { f -> orderIds.none { it == f.id.toString() } }
        }
        // 面板内部顺序也要随点随变，用可变副本，避免「点了箭头没反应」的观感问题
        val list = remember(ordered) { mutableStateListOf<Forum>().also { it.addAll(ordered) } }
        var localLayout by remember(layoutMode) { mutableIntStateOf(layoutMode) }

        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.GridView, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("自定义社区", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text(
                    "恢复默认",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .clickable { onReset() },
                )
            }
            Spacer(Modifier.height(12.dp))
            Text("排版方式", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("文字纵列" to 0, "图标横排" to 1, "图标纵排" to 2).forEach { (label, mode) ->
                    FilterChip(
                        selected = localLayout == mode,
                        onClick = {
                            localLayout = mode
                            onLayout(mode)
                        },
                        label = { Text(label) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            MtDivider()
            Text(
                "显示项目与顺序（眼睛 = 显示/隐藏，箭头 = 排序）",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (list.isEmpty()) {
                    Text("还没加载到版块，先刷新一次社区页", style = MaterialTheme.typography.bodySmall)
                }
                list.forEachIndexed { index, forum ->
                    val visible = forum.id.toString() !in hiddenIds
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    ) {
                        Text(
                            forum.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (visible) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.outline,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { onToggleVisible(forum) },
                            modifier = Modifier.size(34.dp),
                        ) {
                            Icon(
                                if (visible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                if (visible) "隐藏" else "显示",
                                modifier = Modifier.size(18.dp),
                                tint = if (visible) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline,
                            )
                        }
                        IconButton(
                            onClick = {
                                if (index > 0) {
                                    val f = list.removeAt(index)
                                    list.add(index - 1, f)
                                    onMove(forum, -1)
                                }
                            },
                            enabled = index > 0,
                            modifier = Modifier.size(34.dp),
                        ) { Icon(Icons.Filled.ArrowUpward, "上移", modifier = Modifier.size(18.dp)) }
                        IconButton(
                            onClick = {
                                if (index < list.lastIndex) {
                                    val f = list.removeAt(index)
                                    list.add(index + 1, f)
                                    onMove(forum, 1)
                                }
                            },
                            enabled = index < list.lastIndex,
                            modifier = Modifier.size(34.dp),
                        ) { Icon(Icons.Filled.ArrowDownward, "下移", modifier = Modifier.size(18.dp)) }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ForumRow(forum: Forum, onOpen: () -> Unit) {
    MtCard(
        onClick = onOpen,
        padding = PaddingValues(0.dp),
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            RowText(name = forum.name, detail = statsOf(forum))
            if (forum.desc.isNotBlank()) {
                Text(
                    text = forum.desc,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 图标横排：左侧圆角图标 + 名称 + 数据。 */
@Composable
private fun ForumIconRow(forum: Forum, onOpen: () -> Unit) {
    MtCard(
        onClick = onOpen,
        padding = PaddingValues(0.dp),
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            ForumAvatar(forum, size = 40)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    forum.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (forum.desc.isNotBlank()) {
                    Text(
                        forum.desc,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                statsOf(forum),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.End,
            )
        }
    }
}

/** 图标纵排（网格）：图标在上、名称在下。 */
@Composable
private fun ForumTile(forum: Forum, modifier: Modifier = Modifier, onOpen: () -> Unit) {
    MtCard(
        onClick = onOpen,
        padding = PaddingValues(0.dp),
        modifier = modifier,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 4.dp),
        ) {
            ForumAvatar(forum, size = 42)
            Spacer(Modifier.height(6.dp))
            Text(
                forum.name,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            val s = statsOf(forum)
            if (s.isNotEmpty()) {
                Text(
                    s,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** 版块图标：有 iconUrl 用图，没有就用名字首字 + 稳定配色，避免一片空白。 */
@Composable
private fun ForumAvatar(forum: Forum, size: Int) {
    val palette = listOf(
        Color(0xFF3B82F6), Color(0xFF10B981), Color(0xFFF59E0B), Color(0xFFEC4899),
        Color(0xFF8B5CF6), Color(0xFF0EA5E9), Color(0xFFEF4444), Color(0xFF14B8A6),
    )
    val tint = palette[(forum.name.hashCode().let { if (it < 0) -it else it }) % palette.size]
    if (forum.iconUrl.isNotBlank()) {
        coil.compose.AsyncImage(
            model = forum.iconUrl,
            contentDescription = forum.name,
            modifier = Modifier
                .size(size.dp)
                .clip(RoundedCornerShape(12.dp)),
        )
    } else {
        Box(
            modifier = Modifier
                .size(size.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                forum.name.take(1),
                style = MaterialTheme.typography.titleMedium,
                color = tint,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private fun statsOf(forum: Forum): String = buildString {
    if (forum.today > 0) append("今日 ${forum.today}")
    if (forum.threads > 0) {
        if (isNotEmpty()) append(" · ")
        append("主题 ${forum.threads}")
    }
    if (forum.posts > 0) {
        if (isNotEmpty()) append(" · ")
        append("帖子 ${forum.posts}")
    }
}

@Composable
private fun RowText(name: String, detail: String) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(name, style = MaterialTheme.typography.titleMedium)
        Text(detail, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
    }
}
