package io.mtluntan.app.ui.screen

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavHostController
import io.mtluntan.app.MTLuntanApp
import io.mtluntan.app.data.network.ApiUris
import io.mtluntan.app.util.CopyUtil
import io.mtluntan.app.util.Refresh
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 登录：内嵌 WebView 完成（含 ESA 人机验证），登录成功后**由服务端确认身份**再入库。
 *
 * 修的是这两个问题：
 *  1. 账号管理处读取异常 —— 旧实现直接拿 Cookie 内容当账号，字段常常是空的
 *  2. 添加第二个账号时读到第一个 —— 旧实现从 `xxx_auth` 前缀猜用户名，
 *     两个账号的随机盐前缀撞车后互相覆盖
 *
 * 现在的流程：Cookie → 临时会话 → `home.php?mod=space&do=profile&mobile=2`
 * → 真实 uid/用户名/昵称/头像 → 入库。
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(app: MTLuntanApp, nav: NavHostController) {
    var loading by remember { mutableStateOf(true) }
    var hint by remember { mutableStateOf("请在下方页面登录，登录成功后会自动返回") }
    var importing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (importing) "正在确认账号信息…" else "登录 MT 论坛") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                },
            )
        },
    ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad)) {
            if (loading || importing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(
                hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
            )
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        val cm = CookieManager.getInstance()
                        cm.setAcceptCookie(true)
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                                loading = true
                            }

                            override fun onPageFinished(view: WebView, url: String?) {
                                loading = false
                                maybeImport(app, nav, scope, cm) { message ->
                                    hint = message
                                    importing = true
                                }
                            }

                            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                                if (url.contains("action=login") || url.contains("logging")) {
                                    view.loadUrl(url)
                                } else {
                                    maybeImport(app, nav, scope, cm) { message ->
                                        hint = message
                                        importing = true
                                    }
                                }
                                return true
                            }
                        }
                        loadUrl(ApiUris.loginPageRaw())
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * 只要 WebView 里出现了 `*_auth` Cookie，就同步整串会话并请服务端确认身份。
 * 识别失败不静默吞掉：提示用户改用「Cookie 添加账号」手动补名字。
 */
private fun maybeImport(
    app: MTLuntanApp,
    nav: NavHostController,
    scope: kotlinx.coroutines.CoroutineScope,
    cookieManager: CookieManager,
    onProgress: (String) -> Unit,
) {
    val cookies = runCatching { cookieManager.getCookie(ApiUris.SITE) }.getOrNull().orEmpty()
    if (cookies.isBlank()) return
    val hasAuth = cookies.split(";").any { part ->
        val name = part.trim().substringBefore("=")
        name.endsWith("_auth") && part.substringAfter("=", "").isNotBlank()
    }
    if (!hasAuth) return
    if (app.auth.isImporting()) return

    onProgress("已登录，正在用个人页确认账号信息…")
    scope.launch(Dispatchers.IO) {
        app.auth.beginImport()
        try {
            app.auth.stagePendingCookies(cookies)
            val profile = app.forum.identityOf(app.auth.pendingClient())
            val username = profile.username.ifBlank {
                if (profile.uid > 0) "uid_${profile.uid}" else ""
            }
            if (username.isBlank()) {
                withContextMain {
                    app.auth.endImport()
                    onProgress("拿不到账号信息：请先回到论坛网页确认已登录，或用「账号管理 → 用 Cookie 添加」手动导入")
                }
                return@launch
            }
            app.auth.importStaged(
                cookieString = cookies,
                username = username,
                uid = profile.uid,
                nickname = profile.username,
                avatar = profile.avatarUrl,
            )
            Refresh.bumpGeneration()
            withContextMain {
                CopyUtil.toast(app, "已添加账号：${profile.username.ifBlank { username }}")
                nav.popBackStack()
            }
        } catch (t: Throwable) {
            withContextMain {
                onProgress("导入失败：${t.message ?: "网络异常"}，可稍后重试或手动添加")
            }
        } finally {
            app.auth.endImport()
        }
    }
}

private suspend fun withContextMain(block: () -> Unit) = kotlinx.coroutines.withContext(Dispatchers.Main) { block() }
