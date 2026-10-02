package io.mtluntan.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import io.mtluntan.app.ui.theme.MtLuntanTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Root(app: MTLuntanApp) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val darkSetting by app.settings.darkMode.collectAsStateWithLifecycle(initialValue = null)
    val isDark = darkSetting ?: isSystemInDarkTheme()

    // 状态栏 / 导航栏跟着主题走（Java 版 v1.5 起就是这个行为）
    val bar = MaterialTheme.colorScheme.surface.toArgb()
    SideEffect {
        val window = activity?.window ?: return@SideEffect
        @Suppress("DEPRECATION")
        run {
            window.statusBarColor = bar
            window.navigationBarColor = bar
        }
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isDark
            isAppearanceLightNavigationBars = !isDark
        }
    }

    io.mtluntan.app.ui.App(app)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as MTLuntanApp
        // 恢复上次使用的账号
        lifecycleScope.launch {
            val saved = app.settings.activeAccount.first()
            if (saved != null) runCatching { app.auth.activate(saved) }
        }
        setContent {
            MtLuntanTheme(app) {
                Root(app)
            }
        }
    }
}
