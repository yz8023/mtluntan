package io.mtluntan.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.mtluntan.app.MTLuntanApp
import io.mtluntan.app.ui.navigation.Routes
import io.mtluntan.app.ui.screen.CommunityScreen
import io.mtluntan.app.ui.screen.EditorScreen
import io.mtluntan.app.ui.screen.FavoritesScreen
import io.mtluntan.app.ui.screen.GuideScreen
import io.mtluntan.app.ui.screen.HistoryScreen
import io.mtluntan.app.ui.screen.LoginScreen
import io.mtluntan.app.ui.screen.MineScreen
import io.mtluntan.app.ui.screen.NoticeScreen
import io.mtluntan.app.ui.screen.SettingsScreen
import io.mtluntan.app.ui.screen.ThreadListScreen
import io.mtluntan.app.ui.screen.ThreadScreen
import java.net.URLDecoder

private data class BottomTab(val route: String, val label: String, val icon: ImageVector)

private val Tabs = listOf(
    BottomTab(Routes.GUIDE, "导读", Icons.Filled.Home),
    BottomTab(Routes.COMMUNITY, "社区", Icons.Filled.Dashboard),
    BottomTab(Routes.NOTICE, "消息", Icons.Filled.Notifications),
    BottomTab(Routes.MINE, "我的", Icons.Filled.Person),
)

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun App(app: MTLuntanApp) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val onTab = Tabs.any { tab ->
        currentDestination?.hierarchy?.any { it.route == tab.route } == true
    }

    Scaffold(
        bottomBar = {
            if (onTab) {
                NavigationBar {
                    Tabs.forEach { tab ->
                        val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
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
        NavHost(
            navController = navController,
            startDestination = Routes.GUIDE,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.GUIDE) { GuideScreen(app, navController) }
            composable(Routes.COMMUNITY) { CommunityScreen(app, navController) }
            composable(Routes.NOTICE) { NoticeScreen(app, navController) }
            composable(Routes.MINE) { MineScreen(app, navController) }

            composable(
                Routes.FORUM,
                arguments = listOf(
                    navArgument("fid") { type = NavType.LongType },
                    navArgument("name") { type = NavType.StringType },
                ),
            ) { back ->
                val fid = back.arguments?.getLong("fid") ?: 0L
                val name = URLDecoder.decode(back.arguments?.getString("name") ?: "", "UTF-8")
                ThreadListScreen(app, navController, fid, name)
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
                Routes.NEW_THREAD,
                arguments = listOf(navArgument("fid") { type = NavType.LongType }),
            ) { back ->
                EditorScreen(app, navController, fid = back.arguments?.getLong("fid") ?: 0L)
            }
            composable(Routes.SETTINGS) { SettingsScreen(app, navController) }
            composable(Routes.HISTORY) { HistoryScreen(app, navController) }
            composable(Routes.FAVORITES) { FavoritesScreen(app, navController) }
            composable(Routes.LOGIN) { LoginScreen(app, navController) }
        }
    }
}