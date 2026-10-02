package io.mtluntan.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.compose.AsyncImage
import io.mtluntan.app.MTLuntanApp
import io.mtluntan.app.ai.AiChatScreen
import io.mtluntan.app.ai.AiConfigScreen
import io.mtluntan.app.ai.AiSessionsScreen
import io.mtluntan.app.ui.components.AuroraBackground
import io.mtluntan.app.ui.motion.Motion
import io.mtluntan.app.ui.motion.idleBreathing
import io.mtluntan.app.ui.motion.pressScale
import io.mtluntan.app.ui.navigation.Routes
import io.mtluntan.app.ui.screen.AccountManagerScreen
import io.mtluntan.app.ui.screen.BlacklistScreen
import io.mtluntan.app.ui.screen.CommunityScreen
import io.mtluntan.app.ui.screen.CreditsScreen
import io.mtluntan.app.ui.screen.DraftsScreen
import io.mtluntan.app.ui.screen.EditorScreen
import io.mtluntan.app.ui.screen.FavoritesScreen
import io.mtluntan.app.ui.screen.FriendsScreen
import io.mtluntan.app.ui.screen.GuideScreen
import io.mtluntan.app.ui.screen.HistoryScreen
import io.mtluntan.app.ui.screen.LoginScreen
import io.mtluntan.app.ui.screen.MineScreen
import io.mtluntan.app.ui.screen.FollowersScreen
import io.mtluntan.app.ui.screen.NoticeListScreen
import io.mtluntan.app.ui.screen.NoticeScreen
import io.mtluntan.app.ui.screen.PmListScreen
import io.mtluntan.app.ui.screen.OfflinePostsScreen
import io.mtluntan.app.ui.screen.PmChatScreen
import io.mtluntan.app.ui.screen.ProfileScreen
import io.mtluntan.app.ui.screen.RecordCenterScreen
import io.mtluntan.app.ui.screen.SearchScreen
import io.mtluntan.app.ui.screen.SettingsScreen
import io.mtluntan.app.ui.screen.SignRecordsScreen
import io.mtluntan.app.ui.screen.ThreadListScreen
import io.mtluntan.app.ui.screen.ThreadScreen
import io.mtluntan.app.ui.theme.LocalGlass
import io.mtluntan.app.ui.theme.LocalPanelAlpha
import io.mtluntan.app.ui.theme.GlassLevel
import io.mtluntan.app.util.Refresh
import io.mtluntan.app.util.UnreadState
import kotlinx.coroutines.launch

private data class BottomTab(val route: String, val label: String, val icon: ImageVector)

private val Tabs = listOf(
    BottomTab(Routes.GUIDE, "导读", Icons.Filled.Home),
    BottomTab(Routes.COMMUNITY, "社区", Icons.Filled.Dashboard),
    BottomTab(Routes.NOTICE, "消息", Icons.Filled.Notifications),
    BottomTab(Routes.MINE, "我的", Icons.Filled.Person),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(app: MTLuntanApp) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentRoute = currentDestination?.route
    val onTab = Tabs.any { tab -> currentDestination?.hierarchy?.any { it.route == tab.route } == true }
    val activeIndex = Tabs.indexOfFirst { tab -> currentDestination?.hierarchy?.any { it.route == tab.route } == true }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val activeName by app.auth.activeAccount.collectAsStateWithLifecycle(initialValue = null)
    val accounts by app.auth.accounts.collectAsStateWithLifecycle(initialValue = emptyList())
    val active = accounts.find { it.username == activeName }
    val panelAlpha = LocalPanelAlpha.current
    val glass = LocalGlass.current
    val unread by UnreadState.flow.collectAsStateWithLifecycle(initialValue = UnreadState.Snapshot())

    var barVisible by remember { mutableStateOf(true) }
    val autoHide by app.settings.bottomAutoHide.collectAsStateWithLifecycle(initialValue = true)
    val nestedScroll = remember(autoHide) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): Offset {
                if (!autoHide) return Offset.Zero
                if (available.y < -6f) barVisible = false
                if (available.y > 6f) barVisible = true
                return Offset.Zero
            }
        }
    }

    // 切号时收尾：角标归零 + 底栏回到可见
    LaunchedEffect(activeName) {
        UnreadState.clear(activeName)
        barVisible = true
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = onTab,
        drawerContent = {
            // 抽屉：不加外层 padding —— 之前 padding(10.dp) 让抽屉收回后仍有一条边露在屏幕外
            // （用户反馈「没用的时候显示太明显，直接突出来了」）。宽度也收一点，视觉更像悬浮面板。
            ModalDrawerSheet(
                modifier = Modifier.widthIn(max = 300.dp),
                drawerShape = RoundedCornerShape(topEnd = 22.dp, bottomEnd = 22.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surfaceContainer.copy(
                    alpha = if (glass == GlassLevel.Off) 1f else glass.surfaceAlpha
                ),
            ) {
                DrawerContent(
                    activeName = activeName,
                    nickname = active?.let { if (it.nickname.isNotBlank()) it.nickname else it.username } ?: "未登录",
                    avatar = active?.avatarUrl.orEmpty(),
                    uid = active?.uid ?: 0,
                    unread = unread.total,
                    onNavigate = { route ->
                        scope.launch { drawerState.close() }
                        navController.navigate(route)
                    },
                )
            }
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AuroraBackground(modifier = Modifier.fillMaxSize()) {
                Scaffold(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    bottomBar = {
                        AnimatedVisibility(
                            visible = onTab && (barVisible || !autoHide),
                            enter = slideInVertically { it },
                            exit = slideOutVertically { it },
                        ) {
                            LiquidBottomBar(
                                tabs = Tabs,
                                selectedIndex = activeIndex,
                                unread = unread.total,
                                alpha = panelAlpha,
                                onSelect = { index, alreadySelected ->
                                    if (alreadySelected) {
                                        Refresh.bumpTab()
                                        barVisible = true
                                    } else {
                                        navController.navigate(Tabs[index].route) {
                                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                            )
                        }
                    },
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = Routes.GUIDE,
                        modifier = Modifier.fillMaxSize().padding(innerPadding).nestedScroll(nestedScroll),
                    ) {
                        composable(Routes.GUIDE) {
                            GuideScreen(app, navController, onOpenDrawer = { scope.launch { drawerState.open() } })
                        }
                        composable(Routes.COMMUNITY) {
                            CommunityScreen(app, navController, onOpenDrawer = { scope.launch { drawerState.open() } })
                        }
                        composable(Routes.NOTICE) {
                            NoticeScreen(app, navController, onOpenDrawer = { scope.launch { drawerState.open() } })
                        }
                        composable(Routes.MINE) { MineScreen(app, navController) }

                        composable(
                            Routes.FORUM,
                            arguments = listOf(
                                navArgument("fid") { type = NavType.LongType },
                                navArgument("name") { type = NavType.StringType },
                            ),
                        ) { back ->
                            ThreadListScreen(
                                app = app,
                                nav = navController,
                                fid = back.arguments?.getLong("fid") ?: 0L,
                                boardName = Routes.dec(back.arguments?.getString("name")),
                            )
                        }
                        composable(
                            Routes.THREAD,
                            arguments = listOf(navArgument("tid") { type = NavType.LongType }),
                        ) { back ->
                            ThreadScreen(app, navController, back.arguments?.getLong("tid") ?: 0L)
                        }
                        composable(
                            Routes.REPLY,
                            arguments = listOf(navArgument("tid") { type = NavType.LongType }),
                        ) { back ->
                            EditorScreen(app, navController, tid = back.arguments?.getLong("tid") ?: 0L)
                        }
                        composable(
                            Routes.EDIT_REPLY,
                            arguments = listOf(
                                navArgument("tid") { type = NavType.LongType },
                                navArgument("pid") { type = NavType.LongType },
                            ),
                        ) { back ->
                            EditorScreen(
                                app = app,
                                nav = navController,
                                tid = back.arguments?.getLong("tid") ?: 0L,
                                pid = back.arguments?.getLong("pid") ?: 0L,
                            )
                        }
                        composable(
                            Routes.NEW_THREAD,
                            arguments = listOf(navArgument("fid") { type = NavType.LongType }),
                        ) { back ->
                            EditorScreen(app, navController, fid = back.arguments?.getLong("fid") ?: 0L)
                        }

                        composable(Routes.PM_LIST) { PmListScreen(app, navController) }
                        composable(Routes.FOLLOWERS) { FollowersScreen(app, navController) }
                        composable(
                            Routes.NOTICE_LIST,
                            arguments = listOf(navArgument("view") { type = NavType.StringType }),
                        ) { back ->
                            NoticeListScreen(
                                app = app,
                                nav = navController,
                                view = back.arguments?.getString("view").orEmpty().ifBlank { "mypost" },
                            )
                        }

                        composable(Routes.SEARCH) { SearchScreen(app, navController) }
                        composable(Routes.SETTINGS) { SettingsScreen(app, navController) }
                        composable(Routes.HISTORY) { HistoryScreen(app, navController) }
                        composable(Routes.FAVORITES) { FavoritesScreen(app, navController) }
                        composable(Routes.DRAFTS) { DraftsScreen(app, navController) }
                        composable(Routes.RECORDS) { RecordCenterScreen(app, navController) }
                        composable(Routes.OFFLINE) { OfflinePostsScreen(app, navController) }
                        composable(Routes.LOGIN) { LoginScreen(app, navController) }
                        composable(Routes.ACCOUNTS) { AccountManagerScreen(app, navController) }
                        composable(Routes.SIGN_RECORDS) { SignRecordsScreen(app, navController) }
                        composable(Routes.BLACKLIST) { BlacklistScreen(app, navController) }
                        composable(Routes.CREDITS) { CreditsScreen(app, navController) }

                        composable(
                            Routes.PROFILE,
                            arguments = listOf(navArgument("uid") { type = NavType.LongType }),
                        ) { back ->
                            ProfileScreen(app, navController, back.arguments?.getLong("uid") ?: 0L)
                        }
                        composable(
                            Routes.PM,
                            arguments = listOf(
                                navArgument("uid") { type = NavType.LongType },
                                navArgument("name") { type = NavType.StringType },
                            ),
                        ) { back ->
                            PmChatScreen(
                                app = app,
                                nav = navController,
                                uid = back.arguments?.getLong("uid") ?: 0L,
                                name = Routes.dec(back.arguments?.getString("name")),
                            )
                        }
                        composable(
                            Routes.FRIENDS,
                            arguments = listOf(navArgument("uid") { type = NavType.LongType }),
                        ) { back ->
                            FriendsScreen(app, navController, back.arguments?.getLong("uid") ?: 0L)
                        }

                        composable(Routes.AI_SESSIONS) { AiSessionsScreen(app, navController) }
                        composable(
                            Routes.AI_CHAT,
                            arguments = listOf(navArgument("sessionId") { type = NavType.LongType }),
                        ) { back ->
                            AiChatScreen(app, navController, back.arguments?.getLong("sessionId") ?: 0L)
                        }
                        composable(Routes.AI_CONFIG) { AiConfigScreen(app, navController) }
                    }
                }
            }
        }
    }
}

/**
 * 液态玻璃底栏。
 *
 * 与参考实现（`yz8023/ui` 的液态玻璃底部标签栏）一致的三件事：
 *  1. 胶囊容器 + 滑动指示器（不是每一项各自变色）
 *  2. 按下有重量、图标在有未读时轻轻呼吸
 *  3. 未读角标跟着**当前账号**走
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LiquidBottomBar(
    tabs: List<BottomTab>,
    selectedIndex: Int,
    unread: Int,
    alpha: Float,
    onSelect: (Int, Boolean) -> Unit,
) {
    val glass = LocalGlass.current
    val container = MaterialTheme.colorScheme.surfaceContainer
    val fill = container.copy(alpha = if (glass == GlassLevel.Off) alpha else alpha * glass.surfaceAlpha)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(fill),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { index, tab ->
                val selected = index == selectedIndex
                val interaction = remember { MutableInteractionSource() }
                val weight = animateFloatAsState(if (selected) 1f else 0f, Motion.TabSlide, label = "tabWeight")
                val showBadge = tab.route == Routes.NOTICE && unread > 0
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .then(
                            if (selected) Modifier.background(
                                Brush.verticalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.20f * weight.value + 0.04f),
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.06f * weight.value),
                                    )
                                )
                            ) else Modifier
                        )
                        .pressScale(interaction, pressedScale = 0.93f)
                        .clickable(interactionSource = interaction, indication = null) {
                            onSelect(index, selected)
                        }
                        .padding(vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(contentAlignment = Alignment.TopEnd) {
                        Icon(
                            tab.icon,
                            contentDescription = tab.label,
                            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(23.dp)
                                .idleBreathing(enabled = showBadge && !selected, amplitudeX = 0.05f, amplitudeY = 0.05f),
                        )
                        if (showBadge) {
                            Box(
                                modifier = Modifier
                                    .size(if (unread > 9) 16.dp else 14.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.error),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    if (unread > 99) "99+" else unread.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onError,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                    Text(
                        tab.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        // 滑动指示器：一条小药丸跟着当前 Tab（用权重动画，不依赖像素测量）
        val leftWeight by animateFloatAsState(selectedIndex.coerceAtLeast(0).toFloat(), Motion.TabSlide, label = "indLeft")
        val rightWeight by animateFloatAsState((tabs.size - selectedIndex.coerceAtLeast(0) - 1).toFloat(), Motion.TabSlide, label = "indRight")
        Row(modifier = Modifier.fillMaxWidth()) {
            if (leftWeight > 0.001f) Spacer(Modifier.weight(leftWeight))
            Box(
                modifier = Modifier
                    .weight(0.6f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.9f))
            )
            if (rightWeight > 0.001f) Spacer(Modifier.weight(rightWeight))
        }
    }
}

@Composable
private fun DrawerContent(
    activeName: String?,
    nickname: String,
    avatar: String,
    uid: Long,
    unread: Int,
    onNavigate: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f),
                        )
                    )
                )
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (avatar.isNotEmpty()) {
                    AsyncImage(model = avatar, contentDescription = null, modifier = Modifier.size(46.dp).clip(CircleShape))
                } else {
                    Icon(Icons.Filled.Person, null, modifier = Modifier.size(46.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        nickname,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (activeName == null) "游客模式 · 登录后可用签到与私信" else "UID $uid · 已登录",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                if (unread > 0) {
                    Box(
                        modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.error).padding(horizontal = 7.dp, vertical = 2.dp),
                    ) {
                        Text("$unread", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onError)
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        if (activeName == null) {
            DrawerEntry("登录 / 添加账号", Icons.Filled.Login) { onNavigate(Routes.LOGIN) }
        } else {
            DrawerEntry("账号管理", Icons.Filled.Tune) { onNavigate(Routes.ACCOUNTS) }
            DrawerEntry("签到记录", Icons.Filled.AccessTime) { onNavigate(Routes.SIGN_RECORDS) }
        }
        DrawerEntry("搜索", Icons.Filled.Search) { onNavigate(Routes.SEARCH) }
        DrawerEntry("浏览历史", Icons.Filled.History) { onNavigate(Routes.HISTORY) }
        DrawerEntry("我的收藏", Icons.Filled.Bookmark) { onNavigate(Routes.FAVORITES) }
        DrawerEntry("草稿箱", Icons.Filled.Description) { onNavigate(Routes.DRAFTS) }
        DrawerEntry("离线帖子", Icons.Filled.CloudOff) { onNavigate(Routes.OFFLINE) }
        DrawerEntry("AI 助手", Icons.Filled.AutoAwesome) { onNavigate(Routes.AI_SESSIONS) }
        DrawerEntry("记录中心", Icons.Filled.AccessTime) { onNavigate(Routes.RECORDS) }
        DrawerEntry("黑名单", Icons.Filled.Block) { onNavigate(Routes.BLACKLIST) }
        DrawerEntry("设置", Icons.Filled.Settings) { onNavigate(Routes.SETTINGS) }
    }
}

@Composable
private fun DrawerEntry(label: String, icon: ImageVector, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(14.dp))
            .pressScale(interaction, pressedScale = 0.985f)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
    ) {
        Icon(icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
