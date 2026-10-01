package io.mtluntan.app.ui.screen

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavHostController
import io.mtluntan.app.MTLuntanApp
import io.mtluntan.app.data.network.ApiUris
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Login via embedded WebView. After a successful login the site sends us
 * `*_auth` cookies; we extract them from the WebView cookie store and import
 * them into the account session, then pop back.
 *
 * Heuristic: the login is complete as soon as we land on any URL that is NOT
 * the login page AND the *_auth cookie is present.
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(app: MTLuntanApp, nav: NavHostController) {
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("登录 MT 论坛") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "back") }
                },
            )
        },
    ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad)) {
            if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
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
                                checkLogin(app, nav, scope, cm)
                            }
                            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                                if (url.contains("action=login") || url.contains("logging")) {
                                    view.loadUrl(url)
                                } else {
                                    checkLogin(app, nav, scope, cm)
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

private fun checkLogin(
    app: MTLuntanApp,
    nav: NavHostController,
    scope: kotlinx.coroutines.CoroutineScope,
    cookieManager: CookieManager,
) {
    val cookies = cookieManager.getCookie(ApiUris.SITE) ?: ""
    if (cookies.isBlank()) return
    val authCookies = cookies.split(";").map { it.trim() }
        .filter { it.contains("_auth=") }
    if (authCookies.isEmpty()) return
    // Discuz's auth cookie name is like "username_uid_auth"; strip the suffix.
    val authName = authCookies.first().substringBefore("=")
    val username = authName.split("_").dropLast(1).joinToString("_")
    scope.launch(Dispatchers.IO) {
        app.auth.importSession(username, 0, cookies)
        withContext(Dispatchers.Main) {
            nav.popBackStack()
        }
    }
}