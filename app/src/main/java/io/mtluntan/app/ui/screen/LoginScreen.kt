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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import io.mtluntan.app.MTLuntanApp
import io.mtluntan.app.data.network.ApiUris
import io.mtluntan.app.domain.model.displayName
import io.mtluntan.app.util.CopyUtil
import io.mtluntan.app.util.Refresh
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 添加账号。
 *
 * 修两个连锁问题：
 *  1. 「点添加账号又进了上一个已登录的界面」——WebView 的 Cookie 是全局共享的，
 *     上次登录留下的会话还在，一打开就是「已登录」状态，于是又去导入了同一个号。
 *     → 现在进页面先把 WebView 的 Cookie 清空，从真正未登录的登录页开始。
 *  2. 「添加第二个账号把第一个覆盖了」——用 `*_auth` 指纹判断这个会话是不是已存在的账号：
 *     是 → 只刷新它的会话，不新增、不覆盖；不是 → 走服务端身份识别，确认真实 uid/用户名后入库。
 *
 * 识别不到身份时不再瞎猜用户名，而是弹出手填名字的对话框（配合「账号管理 → 用 Cookie 添加」）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(app: MTLuntanApp, nav: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accounts by app.auth.accounts.collectAsStateWithLifecycle(initialValue = emptyList())
    var loading by remember { mutableStateOf(true) }
    var ready by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf("正在打开登录页…") }
    var importing by remember { mutableStateOf(false) }
    var handled by remember { mutableStateOf("") }
    // 普通持有者即可：WebView 实例不需要参与重组
    val webHolder = remember { arrayOfNulls<WebView>(1) }
    // 身份识别失败时的兜底：让用户自己填名字
    var manualCookie by remember { mutableStateOf<String?>(null) }
    var manualName by remember { mutableStateOf("") }

    /** 拿到 `*_auth` 之后统一走这里：已存在的账号只刷新会话，新账号才入库。 */
    fun handleCookies(cookies: String) {
        if (importing || cookies.isBlank()) return
        val hasAuth = cookies.split(";").any { part ->
            val name = part.trim().substringBefore("=")
            name.endsWith("_auth") && part.substringAfter("=", "").isNotBlank()
        }
        if (!hasAuth) return
        if (cookies == handled) return
        handled = cookies

        importing = true
        hint = "已登录，正在确认账号信息…"
        scope.launch(Dispatchers.IO) {
            try {
                // ① 是不是已经加过的账号？（同一设备同一账号的 *_auth 稳定不变）
                val known = app.auth.accountForCookie(cookies)
                if (known != null) {
                    app.auth.importStaged(
                        cookieString = cookies,
                        username = known.username,
                        uid = known.uid,
                        nickname = known.nickname,
                        avatar = known.avatarUrl,
                    )
                    app.auth.endImport()
                    withContext(Dispatchers.Main) {
                        CopyUtil.toast(app, "这个账号已在列表里：${known.displayName}（已刷新会话）")
                        nav.popBackStack()
                    }
                    return@launch
                }

                // ② 新账号：临时会话 + 个人页，由服务端告诉我们「这是谁」
                app.auth.beginImport()
                app.auth.stagePendingCookies(cookies)
                val profile = app.forum.identityOf(app.auth.pendingClient())
                val username = profile.username.ifBlank { if (profile.uid > 0) "uid_${profile.uid}" else "" }
                if (username.isBlank()) {
                    // 不猜名字：交给用户填
                    withContext(Dispatchers.Main) {
                        importing = false
                        hint = "拿不到账号信息，请手动填一个名字（仅本地标识用）"
                        manualName = ""
                        manualCookie = cookies
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
                withContext(Dispatchers.Main) {
                    CopyUtil.toast(app, "已添加账号：${profile.username.ifBlank { username }}")
                    nav.popBackStack()
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    importing = false
                    hint = "导入失败：${t.message ?: "网络异常"}，可稍后重试，或改用「账号管理 → 用 Cookie 添加」"
                }
            } finally {
                app.auth.endImport()
            }
        }
    }

    // 先清掉 WebView 里的旧会话：否则一进来就是「已登录」，用户以为添加不了账号
    LaunchedEffect(Unit) {
        val cm = CookieManager.getInstance()
        runCatching {
            cm.setAcceptCookie(true)
            cm.removeAllCookies(null)
        }
        delay(150)
        runCatching { cm.flush() }
        hint = "请在下方页面登录要添加的账号，登录成功后会自动返回"
        ready = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (importing) "正在确认账号信息…" else "添加账号") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                },
                actions = {
                    IconButton(onClick = {
                        // 手动重来：清 Cookie 再打开登录页
                        val cm = CookieManager.getInstance()
                        runCatching { cm.removeAllCookies(null); cm.flush() }
                        handled = ""
                        hint = "已清空网页会话，请重新登录要添加的账号"
                        webHolder[0]?.loadUrl(ApiUris.loginPageRaw())
                    }) { Icon(Icons.Filled.Refresh, "清空网页会话并重新登录") }
                },
            )
        },
    ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad)) {
            if (loading || importing || !ready) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(
                hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
            )
            if (accounts.isNotEmpty()) {
                Text(
                    "已添加 ${accounts.size} 个账号：" + accounts.joinToString("、") { it.displayName },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
            if (!ready) {
                LoadingBox(modifier = Modifier.weight(1f))
            } else {
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
                                    handleCookies(cm.getCookie(ApiUris.SITE).orEmpty())
                                }

                                override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                                    // 登录相关流程继续在 WebView 里走，其他跳转顺手看看有没有拿到会话
                                    if (url.contains("action=login") || url.contains("logging")) {
                                        view.loadUrl(url)
                                    } else {
                                        handleCookies(cm.getCookie(ApiUris.SITE).orEmpty())
                                    }
                                    return true
                                }
                            }
                            loadUrl(ApiUris.loginPageRaw())
                            webHolder[0] = this
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    manualCookie?.let { cookie ->
        AlertDialog(
            onDismissRequest = { manualCookie = null },
            title = { Text("给这个账号起个名字") },
            text = {
                Column {
                    Text(
                        "服务端没返回账号信息（可能是网络或站点防护），内容已经能用了，\n" +
                            "只是需要一个本地标识。名字只在客户端用来区分多账号。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = manualName,
                        onValueChange = { manualName = it },
                        label = { Text("账号名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = manualName.trim().ifBlank { "account_${System.currentTimeMillis() % 100000}" }
                    manualCookie = null
                    scope.launch(Dispatchers.IO) {
                        app.auth.importStaged(cookie, name, 0, name)
                        Refresh.bumpGeneration()
                        withContext(Dispatchers.Main) {
                            CopyUtil.toast(app, "已添加：$name")
                            nav.popBackStack()
                        }
                    }
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { manualCookie = null }) { Text("取消") } },
        )
    }
}
