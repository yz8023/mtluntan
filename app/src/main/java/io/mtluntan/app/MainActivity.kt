package io.mtluntan.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.lifecycle.lifecycleScope
import io.mtluntan.app.ui.theme.MtLuntanTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Root(app: MTLuntanApp) {
    io.mtluntan.app.ui.App(app)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as MTLuntanApp
        // restore the previously active account
        lifecycleScope.launch {
            val saved = app.settings.activeAccount.first()
            if (saved != null) app.auth.activate(saved)
        }
        setContent {
            val app = application as MTLuntanApp
            MtLuntanTheme {
                Root(app)
            }
        }
    }
}