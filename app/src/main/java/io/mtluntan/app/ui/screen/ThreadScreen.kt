package io.mtluntan.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import io.mtluntan.app.MTLuntanApp
import io.mtluntan.app.data.parser.BbcBlocks
import io.mtluntan.app.data.parser.ThreadExtrasParser
import io.mtluntan.app.ui.components.RevealItem
import io.mtluntan.app.domain.model.LikeUser
import io.mtluntan.app.domain.model.Post
import io.mtluntan.app.domain.model.ThreadDetail
import io.mtluntan.app.ui.navigation.Routes
import io.mtluntan.app.util.CopyUtil
import io.mtluntan.app.util.LogCenter
import io.mtluntan.app.util.LogCenter.LogTag
import io.mtluntan.app.util.PerfLog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.mtluntan.app.util.Refresh
import kotlinx.coroutines.launch

/**
 * 帖子详情：分页 + 阅读进度 + 楼层操作 + 进帖自动解锁 + 大图。
 *
 * 与 Java 版的对应关系：
 *  - 阅读进度落 Room（history.lastFloor / totalPages），下次进来接着看
 *  - 「复制」有多档：整楼纯文本 / 整楼 BBCode / 只复制代码 / 带楼层署名
 *  - 自己的楼层才显示编辑与删除；删除走整张表单回提（不猜参数）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadScreen(app: MTLuntanApp, nav: NavHostController, tid: Long) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var detail by remember { mutableStateOf<ThreadDetail?>(null) }
    var pageHtml by remember { mutableStateOf("") }
    var page by remember { mutableIntStateOf(1) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var favorited by remember { mutableStateOf(false) }

    val likedPids = remember { mutableStateListOf<Long>() }
    val editablePids = remember { mutableStateListOf<Long>() }
    val deletablePids = remember { mutableStateListOf<Long>() }
    var menuPost by remember { mutableStateOf<Post?>(null) }
    var likeUsersFor by remember { mutableStateOf<Post?>(null) }
    var likeUsers by remember { mutableStateOf<List<LikeUser>>(emptyList()) }
    var replyText by remember { mutableStateOf("") }
    var quickSending by remember { mutableStateOf(false) }
    var galleryUrls by remember { mutableStateOf<List<String>?>(null) }
    var galleryIndex by remember { mutableIntStateOf(0) }
    var uploading by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var pendingAttachment by remember { mutableStateOf<io.mtluntan.app.domain.model.Attachment?>(null) }
    var downloading by remember { mutableStateOf("") }
    var offlineMode by remember { mutableStateOf(false) }
    // 自动翻页累积的后续页楼层（detail 只保留「当前这一页」的解析结果）
    val extraPosts = remember { mutableStateListOf<Post>() }
    var autoFetching by remember { mutableStateOf(0) }
    val autoPagination by app.settings.autoPagination.collectAsStateWithLifecycle(initialValue = true)
    val hideBlacklist by app.settings.hideBlacklist.collectAsStateWithLifecycle(initialValue = false)
    val blacklist by app.local.blacklist.collectAsStateWithLifecycle(initialValue = emptyList())
    val blacklistUids = remember(blacklist) { blacklist.map { it.uid }.toSet() }

    suspend fun load(targetPage: Int, keepScroll: Boolean = false) {
        loading = true
        val started = System.currentTimeMillis()
        try {
            val html = app.forum.threadHtml(tid, targetPage)
            pageHtml = html
            val parsed = io.mtluntan.app.data.parser.ThreadDetailParser.parse(html, targetPage)
            detail = parsed
            if (targetPage <= 1) extraPosts.clear()
            if (parsed.loginRequired) {
                error = "需要先登录才能查看这个帖子"
            } else if (parsed.errorMessage.isNotEmpty()) {
                error = parsed.errorMessage
            } else {
                error = ""
                app.local.recordVisit(tid, parsed.title, parsed.forumName, parsed.mainPost?.authorName ?: "", targetPage, parsed.totalPages)
                app.local.markRead(tid)
                // 本页一条回复都没解析到、但后面还有页 → 自动把评论接上（用户反馈：评论区要自动下一页）
                if (autoPagination && parsed.posts.isEmpty() &&
                    parsed.currentPage < parsed.totalPages && autoFetching < 6
                ) {
                    autoFetching++
                    load(targetPage + 1, keepScroll = true)
                    autoFetching--
                } else if (targetPage > 1) {
                    extraPosts.addAll(parsed.posts.filter { p -> extraPosts.none { it.pid == p.pid } })
                }
                favorited = app.local.isFavorite(tid)
                likedPids.clear(); likedPids.addAll(if (parsed.likedByCurrent) listOf(parsed.mainPost?.pid ?: 0L) else emptyList())
                editablePids.clear(); editablePids.addAll(ThreadExtrasParser.editablePids(html))
                deletablePids.clear(); deletablePids.addAll(ThreadExtrasParser.deletablePids(html))

                // 进帖自动解锁：只有真的还有锁定隐藏块才会动手
                if (ThreadExtrasParser.hiddenBlocks(html).any { it.locked }) {
                    app.autoReply.tryUnlockOnOpen(tid, parsed, html).onSuccess {
                        // 解锁成功后内容已变，静默重载一次
                        val fresh = app.forum.threadHtml(tid, targetPage)
                        pageHtml = fresh
                        detail = io.mtluntan.app.data.parser.ThreadDetailParser.parse(fresh, targetPage)
                    }
                }
            }
        } catch (e: Exception) {
            // 断网 / 站点抽风时先看有没有离线快照（Java 版 v4.1 的离线阅读）
            val cached = runCatching { app.local.offline(tid) }.getOrNull()
            if (cached != null && cached.html.isNotBlank()) {
                val parsed = io.mtluntan.app.data.parser.ThreadDetailParser.parse(cached.html, targetPage)
                detail = parsed
                pageHtml = cached.html
                offlineMode = true
                error = ""
            } else {
                error = e.message ?: "加载失败"
            }
        }
        loading = false
        PerfLog.record(PerfLog.Span(name = "帖子页 $tid", netMs = System.currentTimeMillis() - started))
        if (!keepScroll) runCatching { listState.scrollToItem(0) }
    }

    val generation by Refresh.generation.collectAsStateWithLifecycle(initialValue = 0)
    LaunchedEffect(tid, generation) { load(1) }

    // 阅读进度：进来先查上次读到第几页/第几楼，给一个「继续阅读」入口
    var resumePage by remember { mutableIntStateOf(0) }
    var lastFloorSeen by remember { mutableIntStateOf(0) }
    var maxPageSeen by remember { mutableIntStateOf(1) }
    LaunchedEffect(tid) {
        val (floor, savedPage) = app.local.progressFor(tid)
        lastFloorSeen = floor
        maxPageSeen = savedPage.coerceAtLeast(1)
        if (savedPage > 1) resumePage = savedPage
    }

    // 记录「最远读到第几页」：回头翻旧页不能把进度改小
    LaunchedEffect(page, detail) {
        // detail 是 remember 委托属性，Kotlin 不能对它做智能转换 → 取局部变量
        val d = detail
        if (d != null && page > maxPageSeen) {
            maxPageSeen = page
            app.local.recordVisit(
                tid,
                d.title,
                d.forumName,
                d.mainPost?.authorName.orEmpty(),
                lastFloorSeen.coerceAtLeast(1),
                d.totalPages,
            )
        }
    }

    // 停在某一楼 0.8 秒就记进度（不新建协程、离开页面也不会丢）
    LaunchedEffect(tid) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collectLatest { index ->
                delay(800)
                val floors = listOfNotNull(detail?.mainPost) + detail?.posts.orEmpty()
                val hit = floors.getOrNull(index) ?: return@collectLatest
                if (hit.floor > 0 && hit.floor != lastFloorSeen) {
                    lastFloorSeen = hit.floor
                    app.local.recordVisit(
                        tid,
                        detail?.title.orEmpty(),
                        detail?.forumName.orEmpty(),
                        detail?.mainPost?.authorName.orEmpty(),
                        hit.floor,
                        detail?.totalPages ?: 1,
                    )
                }
            }
    }

    val current = detail
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(current?.title ?: "帖子", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            val formhash = current?.formhash.orEmpty()
                            if (formhash.isEmpty()) { CopyUtil.toast(context, "缺少 formhash，无法收藏"); return@launch }
                            val ok = app.forum.favorite(tid, formhash, add = !favorited)
                            if (ok) {
                                favorited = !favorited
                                if (favorited) app.local.addFavorite(tid, current?.title ?: "", current?.forumName ?: "", current?.mainPost?.authorName ?: "")
                                else app.local.removeFavorite(tid)
                                CopyUtil.toast(context, if (favorited) "已收藏" else "已取消收藏")
                            } else CopyUtil.toast(context, "操作失败")
                        }
                    }) {
                        Icon(if (favorited) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, "收藏")
                    }
                    IconButton(onClick = { scope.launch { load(page, keepScroll = true) } }) {
                        Icon(Icons.Filled.Refresh, "刷新")
                    }
                    IconButton(onClick = {
                        val text = detail?.let { d ->
                            buildString {
                                appendLine(d.title)
                                appendLine("https://bbs.binmt.cc/thread-$tid-1-1.html")
                                appendLine()
                                d.mainPost?.let { append(BbcBlocks.stripTags(it.contentBbc)) }
                            }
                        }.orEmpty()
                        CopyUtil.copy(context, text, "已复制标题与链接")
                    }) { Icon(Icons.Filled.ContentCopy, "复制") }
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "更多") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("保存到离线列表") },
                                leadingIcon = { Icon(Icons.Filled.Download, null) },
                                onClick = {
                                    menuOpen = false
                                    scope.launch {
                                        val msg = app.local.saveOffline(
                                            tid,
                                            current?.title.orEmpty(),
                                            current?.forumName.orEmpty(),
                                            current?.mainPost?.authorName.orEmpty(),
                                        )
                                        CopyUtil.toast(context, msg)
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("导出 HTML") },
                                leadingIcon = { Icon(Icons.Filled.Download, null) },
                                onClick = {
                                    menuOpen = false
                                    val d = current
                                    if (d == null) { CopyUtil.toast(context, "还没加载完"); return@DropdownMenuItem }
                                    scope.launch {
                                        val name = io.mtluntan.app.util.Export.safeName(d.title, tid, "html")
                                        val result = io.mtluntan.app.util.FileSaver.exportText(
                                            context, name, io.mtluntan.app.util.Export.toHtml(d, tid, pageHtml), "text/html",
                                        )
                                        CopyUtil.toast(context, result.fold({ "已导出：$it" }, { "导出失败：${it.message}" }))
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("导出纯文本") },
                                leadingIcon = { Icon(Icons.Filled.Description, null) },
                                onClick = {
                                    menuOpen = false
                                    val d = current
                                    if (d == null) { CopyUtil.toast(context, "还没加载完"); return@DropdownMenuItem }
                                    scope.launch {
                                        val name = io.mtluntan.app.util.Export.safeName(d.title, tid, "txt")
                                        val result = io.mtluntan.app.util.FileSaver.exportText(
                                            context, name, io.mtluntan.app.util.Export.toText(d, tid), "text/plain",
                                        )
                                        CopyUtil.toast(context, result.fold({ "已导出：$it" }, { "导出失败：${it.message}" }))
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("用浏览器打开") },
                                leadingIcon = { Icon(Icons.Filled.OpenInNew, null) },
                                onClick = {
                                    menuOpen = false
                                    io.mtluntan.app.util.FileSaver.openInBrowser(context, "https://bbs.binmt.cc/thread-$tid-1-1.html")
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("分享链接") },
                                leadingIcon = { Icon(Icons.Filled.Share, null) },
                                onClick = {
                                    menuOpen = false
                                    io.mtluntan.app.util.FileSaver.shareText(
                                        context, "${current?.title.orEmpty()}\nhttps://bbs.binmt.cc/thread-$tid-1-1.html", "分享帖子",
                                    )
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(if (offlineMode) "当前：离线快照" else "复制原始页面链接") },
                                leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
                                onClick = {
                                    menuOpen = false
                                    CopyUtil.copy(context, "https://bbs.binmt.cc/thread-$tid-1-1.html", "链接已复制")
                                },
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            Column {
                if (current != null) {
                    PagerBar(
                        current = current.currentPage,
                        total = current.totalPages,
                        onPrev = { if (page > 1) { page--; scope.launch { load(page) } } },
                        onNext = { if (page < current.totalPages) { page++; scope.launch { load(page) } } },
                        onJump = { p -> page = p.coerceIn(1, current.totalPages); scope.launch { load(page) } },
                    )
                    QuickReplyBar(
                        value = replyText,
                        onChange = { replyText = it },
                        sending = quickSending,
                        loggedIn = current.formhash.isNotEmpty(),
                        onSend = {
                            val text = replyText.trim()
                            if (text.isEmpty()) return@QuickReplyBar
                            quickSending = true
                            scope.launch {
                                val result = app.forum.submitReply(tid, current.formhash, text, current.fid)
                                quickSending = false
                                if (result.ok) {
                                    replyText = ""
                                    CopyUtil.toast(context, "回复成功")
                                    load(page, keepScroll = true)
                                } else {
                                    CopyUtil.toast(context, result.error.ifBlank { "回复失败" })
                                }
                            }
                        },
                        onOpenEditor = { nav.navigate(Routes.reply(tid)) },
                    )
                }
            }
        },
    ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad)) {
            if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            when {
                loading && current == null -> LoadingBox()
                error.isNotEmpty() && current == null -> Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(error, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextButton(onClick = { scope.launch { load(page) } }) { Text("重试") }
                        if (error.contains("登录") || error.contains("会话") || error.contains("权限")) {
                            TextButton(onClick = {
                                scope.launch {
                                    CopyUtil.toast(context, "正在尝试自动重登…")
                                    val status = runCatching { app.auth.ensureSession() }.getOrNull()
                                    if (status?.loggedIn == true) {
                                        CopyUtil.toast(context, "会话已恢复，重新加载")
                                        load(page)
                                    } else {
                                        CopyUtil.toast(context, status?.message?.ifBlank { "自动重登失败，请手动登录" } ?: "自动重登失败")
                                    }
                                }
                            }) { Text("自动重登") }
                        }
                    }
                }
                current == null -> MessageBox("加载失败")
                else -> {
                    val posts = (listOfNotNull(current.mainPost) + current.posts + extraPosts)
                        .distinctBy { it.pid }
                        .let { list -> if (hideBlacklist) list else list }
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        if (offlineMode) {
                            item {
                                Surface(
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.CloudOff, null, modifier = Modifier.size(16.dp).padding(start = 8.dp))
                                        Text(
                                            "离线快照（保存于本地）",
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.weight(1f).padding(start = 6.dp),
                                        )
                                        TextButton(onClick = { offlineMode = false; scope.launch { load(page) } }) { Text("重新联网") }
                                    }
                                }
                            }
                        }
                        if (resumePage > 1 && page == 1) {
                            item {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "上次读到第 $resumePage 页",
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.weight(1f).padding(start = 10.dp),
                                        )
                                        TextButton(onClick = {
                                            page = resumePage
                                            scope.launch { load(resumePage) }
                                        }) { Text("继续阅读") }
                                        TextButton(onClick = { resumePage = 0 }) { Text("从头看") }
                                    }
                                }
                            }
                        }
                        if (current.title.isNotEmpty()) {
                            item {
                                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                    Text(current.title, style = MaterialTheme.typography.titleLarge)
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
                                        if (current.forumName.isNotBlank()) Text(current.forumName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                        Text("第 ${current.currentPage}/${current.totalPages} 页", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                                        if (ThreadExtrasParser.hiddenBlocks(pageHtml).any { it.locked }) {
                                            Text("含隐藏内容", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                    val attachments = ThreadExtrasParser.attachments(pageHtml)
                                    if (attachments.isNotEmpty()) {
                                        Spacer(Modifier.height(6.dp))
                                        attachments.take(5).forEach { att ->
                                            Text(
                                                "📎 ${att.name} ${att.size}" + if (att.cost > 0) "（${att.cost} 金币）" else "",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        if (att.cost > 0) pendingAttachment = att
                                                        else scope.launch {
                                                            CopyUtil.toast(context, "开始下载…")
                                                            val result = io.mtluntan.app.util.FileSaver.download(
                                                                context, att.url, att.name.ifBlank { "attachment" },
                                                            )
                                                            CopyUtil.toast(context, result.fold({ "已保存：$it" }, { "下载失败：${it.message}" }))
                                                        }
                                                    }
                                                    .padding(vertical = 2.dp),
                                            )
                                        }
                                    }
                                    HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                                }
                            }
                        }
                        itemsIndexed(posts, key = { _, post -> post.pid }) { index, post ->
                            if (hideBlacklist && post.uid > 0 && blacklistUids.contains(post.uid)) {
                                RevealItem(index) {
                                    CollapsedFloor(
                                        author = post.authorName,
                                        floor = post.floor,
                                        onShow = { scope.launch { app.local.removeBlacklist(post.uid) } },
                                    )
                                }
                            } else {
                            RevealItem(index) {
                            PostCard(
                                post = post,
                                editable = editablePids.contains(post.pid),
                                deletable = deletablePids.contains(post.pid),
                                liked = likedPids.contains(post.pid),
                                hiddenLocked = ThreadExtrasParser.hiddenBlocks(pageHtml).any { it.locked && (it.pid == 0L || it.pid == post.pid) },
                                onOpenProfile = { uid -> if (post.uid > 0) nav.navigate(Routes.profile(post.uid)) },
                                onImageClick = { urls, index -> galleryUrls = urls; galleryIndex = index },
                                onUnlock = {
                                    scope.launch {
                                        app.autoReply.unlockSingleThread(tid).onSuccess {
                                            CopyUtil.toast(context, "已回复，重新加载中")
                                            load(page, keepScroll = true)
                                        }.onFailure {
                                            CopyUtil.toast(context, "解锁失败：${it.message}")
                                        }
                                    }
                                },
                                onLike = {
                                    scope.launch {
                                        val formhash = current.formhash
                                        if (formhash.isEmpty()) { CopyUtil.toast(context, "请先登录"); return@launch }
                                        val ok = app.forum.like(tid, post.pid, formhash, add = !likedPids.contains(post.pid))
                                        if (ok) {
                                            if (likedPids.contains(post.pid)) likedPids.remove(post.pid) else likedPids.add(post.pid)
                                        } else CopyUtil.toast(context, "操作失败")
                                    }
                                },
                                onReply = {
                                    nav.navigate(Routes.reply(tid))
                                },
                                onMore = { menuPost = post },
                            )
                            }
                            }
                        }
                        if (posts.size > 1 && !offlineMode) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    Text(
                                        "已显示 ${posts.size} 楼 · 第 ${current.currentPage}/${current.totalPages} 页",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                }
                            }
                        }
                        item { Spacer(Modifier.height(12.dp)) }
                    }
                }
            }
        }
    }

    // ---------------- 楼层菜单 ----------------
    menuPost?.let { post ->
        PostMenuDialog(
            post = post,
            editable = editablePids.contains(post.pid),
            deletable = deletablePids.contains(post.pid),
            onDismiss = { menuPost = null },
            onCopyPlain = {
                CopyUtil.copy(context, BbcBlocks.stripTags(post.contentBbc).ifBlank { post.contentHtml }, "已复制正文")
                menuPost = null
            },
            onCopyBbc = {
                CopyUtil.copy(context, post.contentBbc.ifBlank { post.contentHtml }, "已复制 BBCode")
                menuPost = null
            },
            onCopyWithFloor = {
                val head = "第 ${post.floor} 楼 · ${post.authorName}："
                CopyUtil.copy(context, "$head\n${BbcBlocks.stripTags(post.contentBbc)}", "已复制（含楼层）")
                menuPost = null
            },
            onCopyCode = {
                val codes = BbcBlocks.parse(post.contentBbc).filterIsInstance<io.mtluntan.app.data.parser.BbcBlock.Code>()
                if (codes.isEmpty()) CopyUtil.toast(context, "本楼没有代码块")
                else CopyUtil.copy(context, codes.joinToString("\n\n") { it.code }, "已复制 ${codes.size} 段代码")
                menuPost = null
            },
            onLikeUsers = {
                menuPost = null
                scope.launch {
                    likeUsers = app.forum.likeUsers(tid, post.pid)
                    likeUsersFor = post
                }
            },
            onEdit = {
                menuPost = null
                nav.navigate(Routes.editReply(tid, post.pid))
            },
            onDelete = {
                menuPost = null
                scope.launch {
                    val formhash = current?.formhash.orEmpty()
                    val result = app.forum.deletePost(tid, current?.fid ?: 0, post.pid, formhash)
                    CopyUtil.toast(context, if (result.ok) "已删除" else "删除失败：${result.error}")
                    if (result.ok) load(page, keepScroll = true)
                }
            },
            onReport = {
                menuPost = null
                scope.launch {
                    val formhash = current?.formhash.orEmpty()
                    val ok = app.forum.reportPost(tid, post.pid, formhash, "违规内容")
                    CopyUtil.toast(context, if (ok) "已提交举报" else "举报失败")
                }
            },
            onBlacklist = {
                menuPost = null
                if (post.uid <= 0) return@PostMenuDialog
                scope.launch {
                    app.local.addBlacklist(post.uid, post.authorName, "来自帖子 $tid")
                    CopyUtil.toast(context, "已加入黑名单：${post.authorName}")
                    LogCenter.log(LogTag.RUN, "拉黑 ${post.authorName}")
                }
            },
        )
    }

    pendingAttachment?.let { att ->
        AlertDialog(
            onDismissRequest = { pendingAttachment = null },
            title = { Text("购买附件") },
            text = {
                Text("「${att.name}」需要 ${att.cost} 金币，确认后开始下载。\n金币由论坛扣除，客户端不单独计费。")
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = att
                    pendingAttachment = null
                    scope.launch {
                        downloading = target.name
                        CopyUtil.toast(context, "已扣除 ${target.cost} 金币，开始下载…")
                        val result = io.mtluntan.app.util.FileSaver.download(
                            context, target.url, target.name.ifBlank { "attachment" },
                        )
                        CopyUtil.toast(context, result.fold({ "已保存：$it" }, { "下载失败：${it.message}" }))
                        downloading = ""
                    }
                }) { Text("确认购买并下载") }
            },
            dismissButton = { TextButton(onClick = { pendingAttachment = null }) { Text("取消") } },
        )
    }

    likeUsersFor?.let { post ->
        AlertDialog(
            onDismissRequest = { likeUsersFor = null },
            title = { Text("点赞用户（${likeUsers.size}）") },
            text = {
                Column {
                    if (likeUsers.isEmpty()) Text("暂无名单或需要登录后查看")
                    likeUsers.take(30).forEach { u ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                            AsyncImage(model = u.avatarUrl, contentDescription = null, modifier = Modifier.size(28.dp).clip(CircleShape))
                            Spacer(Modifier.width(8.dp))
                            Text(u.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            if (u.time.isNotBlank()) Text(u.time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { likeUsersFor = null }) { Text("关闭") } },
        )
    }

    galleryUrls?.let { urls ->
        ImageGallery(urls, galleryIndex) { galleryUrls = null }
    }
}

@Composable
private fun QuickReplyBar(
    value: String,
    onChange: (String) -> Unit,
    sending: Boolean,
    loggedIn: Boolean,
    onSend: () -> Unit,
    onOpenEditor: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onChange,
                placeholder = { Text(if (loggedIn) "快速回复…" else "登录后可回复") },
                enabled = loggedIn && !sending,
                maxLines = 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                textStyle = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(6.dp))
            IconButton(onClick = onSend, enabled = loggedIn && !sending) {
                Icon(Icons.AutoMirrored.Filled.Send, "发送", tint = MaterialTheme.colorScheme.primary)
            }
            TextButton(onClick = onOpenEditor, enabled = loggedIn) { Text("编辑器") }
        }
    }
}

@Composable
private fun PagerBar(
    current: Int,
    total: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onJump: (Int) -> Unit,
) {
    Surface(tonalElevation = 2.dp) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
        ) {
            Row {
                TextButton(onClick = onPrev, enabled = current > 1) { Text("上一页") }
                TextButton(onClick = { onJump(1) }, enabled = current > 1) { Text("首页") }
            }
            Text("$current / $total 页", style = MaterialTheme.typography.labelMedium)
            Row {
                TextButton(onClick = { onJump(total) }, enabled = current < total) { Text("末页") }
                TextButton(onClick = onNext, enabled = current < total) { Text("下一页") }
            }
        }
    }
}

/** 单个楼层。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostCard(
    post: Post,
    editable: Boolean = false,
    deletable: Boolean = false,
    liked: Boolean = false,
    hiddenLocked: Boolean = false,
    onOpenProfile: ((Long) -> Unit)? = null,
    onImageClick: ((List<String>, Int) -> Unit)? = null,
    onUnlock: (() -> Unit)? = null,
    onLike: (() -> Unit)? = null,
    onReply: (() -> Unit)? = null,
    onMore: (() -> Unit)? = null,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = post.avatarUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .clickable(enabled = post.uid > 0) { onOpenProfile?.invoke(post.uid) },
                )
                Column(modifier = Modifier.padding(start = 8.dp).weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            post.authorName.ifEmpty { "匿名" },
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable(enabled = post.uid > 0) { onOpenProfile?.invoke(post.uid) },
                        )
                        if (post.isMainPost) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "楼主",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 1.dp),
                            )
                        }
                    }
                    Text(
                        buildString {
                            if (post.postTime.isNotBlank()) append(post.postTime)
                            if (post.floor > 0) { if (isNotEmpty()) append(" · "); append("${post.floor} 楼") }
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                if (hiddenLocked) {
                    Text("隐藏未解锁", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
                IconButton(onClick = { onMore?.invoke() }) { Icon(Icons.Filled.MoreVert, "更多") }
            }

            Spacer(Modifier.height(8.dp))
            BbcContent(
                bbc = post.contentBbc,
                contentHtml = post.contentHtml,
                onImageClick = onImageClick,
                onUnlockClick = onUnlock,
            )

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                TextButton(onClick = { onLike?.invoke() }) {
                    Icon(
                        imageVector = if (liked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("支持", style = MaterialTheme.typography.labelMedium)
                }
                TextButton(onClick = { onReply?.invoke() }) {
                    Text("回复", style = MaterialTheme.typography.labelMedium)
                }
                TextButton(onClick = { onMore?.invoke() }) {
                    Text("复制 / 更多", style = MaterialTheme.typography.labelMedium)
                }
                Box(Modifier.weight(1f))
                if (deletable) Icon(Icons.Filled.DeleteOutline, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline)
                if (editable) Icon(Icons.Filled.Edit, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
private fun PostMenuDialog(
    post: Post,
    editable: Boolean,
    deletable: Boolean,
    onDismiss: () -> Unit,
    onCopyPlain: () -> Unit,
    onCopyBbc: () -> Unit,
    onCopyWithFloor: () -> Unit,
    onCopyCode: () -> Unit,
    onLikeUsers: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onReport: () -> Unit,
    onBlacklist: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("第 ${post.floor} 楼 · ${post.authorName}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                MenuRow("复制正文（纯文本）", Icons.Filled.ContentCopy, onCopyPlain)
                MenuRow("复制 BBCode（保留格式）", Icons.Filled.ContentCopy, onCopyBbc)
                MenuRow("复制并带楼层署名", Icons.Filled.ContentCopy, onCopyWithFloor)
                MenuRow("只复制代码块", Icons.Filled.ContentCopy, onCopyCode)
                MenuRow("查看点赞用户", Icons.Filled.ThumbUp, onLikeUsers)
                if (editable) MenuRow("编辑这条回复", Icons.Filled.Edit, onEdit)
                if (deletable) MenuRow("删除这条回复", Icons.Filled.DeleteOutline, onDelete)
                MenuRow("举报", Icons.Filled.Report, onReport)
                MenuRow("拉黑楼主", Icons.Filled.Block, onBlacklist)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun MenuRow(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Icon(icon, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

/** 离线保存入口用（帖子页右上角菜单调用）。 */
@Composable
fun OfflineSaveRow(tid: Long, title: String, app: MTLuntanApp, onDone: (String) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                app.appScope.launch {
                    val result = app.local.saveOffline(tid, title)
                    onDone(result)
                }
            }
            .padding(vertical = 10.dp),
    ) {
        Icon(Icons.Filled.Download, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Text("保存到离线列表")
    }
}


/** 黑名单用户的楼层占位（点一下就能恢复显示，避免「看不了」变成「看不到」）。 */
@Composable
private fun CollapsedFloor(author: String, floor: Int, onShow: () -> Unit) {
    androidx.compose.material3.Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Block, null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.width(8.dp))
            Text(
                (if (floor > 0) "$floor 楼 · " else "") + "$author 已被你拉黑，内容已折叠",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onShow) { Text("不再拉黑", style = MaterialTheme.typography.labelSmall) }
        }
    }
}
