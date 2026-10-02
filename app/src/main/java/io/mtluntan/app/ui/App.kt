package io.mtluntan.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
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
import io.mtluntan.app.ui.screen.NoticeScreen
import io.mtluntan.app.ui.screen.OfflinePostsScreen
import io.mtluntan.app.ui.screen.PmChatScreen
import io.mtluntan.app.ui.screen.ProfileScreen
import io.mtluntan.app.ui.screen.RecordCenterScreen
import io.mtluntan.app.ui.screen.SearchScreen
import io.mtluntan.app.ui.screen.SettingsScreen
import io.mtluntan.app.ui.screen.SignRecordsScreen
import io.mtluntan.app.ui.screen.ThreadListScreen
import io.mtluntan.app.ui.screen.ThreadScreen
import io.mtluntan.app.ui.theme.LocalPanelAlpha
import io.mtluntan.app.util.Refresh
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
    val onTab = Tabs.any { tab -> currentDestination?.hierarchy?.any { it.route == tab.route } == true }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val activeName by app.auth.activeAccount.collectAsStateWithLifecycle(initialValue = null)
    val accounts by app.auth.accounts.collectAsStateWithLifecycle(initialValue = emptyList())
    val active = accounts.find { it.username == activeName }
    val panelAlpha = LocalPanelAlpha.current

    var barVisible by remember { mutableStateOf(true) }
    val autoHide by app.settings.bottomAutoHide.collectAsStateWithLifecycle(initialValue = true)
    val nestedScroll = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): Offset {
                if (!autoHide) return Offset.Zero
                if (available.y < -6f) barVisible = false
                if (available.y > 6f) barVisible = true
                return Offset.Zero
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = onTab,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier
                    .padding(12.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(androidx.compose.ui.graphics.Color.Transparent),
                drawerContainerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface.copy(alpha = panelAlpha),
            ) {
                DrawerContent(
                    app = app,
                    activeName = activeName,
                    nickname = active?.nickname?.ifBlank { active?.username } ?: "未登录",
                    avatar = active?.avatarUrl.orEmpty(),
                    uid = active?.uid ?: 0,
                    onNavigate = { route ->
                        scope.launch { drawerState.close() }
                        navController.navigate(route)
                    },
                )
            }
        },
    ) {
        Scaffold(
            bottomBar = {
                AnimatedVisibility(
                    visible = onTab && (barVisible || !autoHide),
                    enter = slideInVertically { it },
                    exit = slideOutVertically { it },
                ) {
                    NavigationBar(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface.copy(alpha = panelAlpha)) {
                        Tabs.forEach { tab ->
                            val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    if (selected) {
                                        // 再次点击当前 Tab = 刷新该页（Java 版 v2.4 的行为）
                                        Refresh.bumpTab()
                                        barVisible = true
                                    } else {
                                        navController.navigate(tab.route) {
                                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) },
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding).nestedScroll(nestedScroll)) {
                NavHost(
                    navController = navController,
                    startDestination = Routes.GUIDE,
                    modifier = Modifier.fillMaxSize(),
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
                    composable(Routes.MINE) {
                        MineScreen(app, navController)
                    }

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

@Composable
private fun DrawerContent(
    app: MTLuntanApp,
    activeName: String?,
    nickname: String,
    avatar: String,
    uid: Long,
    onNavigate: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                if (avatar.isNotEmpty()) {
                    AsyncImage(model = avatar, contentDescription = null, modifier = Modifier.size(46.dp).clip(CircleShape))
                } else {
                    Icon(Icons.Filled.Person, null, modifier = Modifier.size(46.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(nickname, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (activeName == null) "游客模式" else "UID $uid · 已登录",
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    )
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
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Icon(icon, null, modifier = Modifier.size(20.dp), tint = androidx.compose.material3.MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Text(label, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
    }
}
