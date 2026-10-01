package io.mtluntan.app.ui.screen

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.navigation.NavHostController
import io.mtluntan.app.MTLuntanApp
import io.mtluntan.app.data.network.ApiUris
import io.mtluntan.app.domain.model.EditorMeta
import kotlinx.coroutines.launch

/**
 * Compose editor used for both new threads (mode=new, fid given) and replies
 * (mode=reply, tid given). Meta (formhash) is fetched from the server editor page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    app: MTLuntanApp,
    nav: NavHostController,
    fid: Long = 0L,
    tid: Long = 0L,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var meta by remember { mutableStateOf<EditorMeta?>(null) }
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    val isNew = tid == 0L

    LaunchedEffect(Unit) {
        try {
            val url = if (isNew) ApiUris.newThread(fid) else ApiUris.reply(tid)
            meta = app.forum.fetchEditor(url)
        } catch (e: Exception) {
            Toast.makeText(context, "打开编辑器失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "发布主题" else "回复主题") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "back") }
                },
                actions = {
                    TextButton(enabled = !submitting && meta != null, onClick = {
                        if (content.isBlank()) {
                            Toast.makeText(context, "内容不能为空", Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }
                        submitting = true
                        scope.launch {
                            val result = if (isNew) {
                                app.forum.submitThread(fid, meta!!.formhash, title, content)
                            } else {
                                app.forum.submitReply(tid, meta!!.formhash, content)
                            }
                            submitting = false
                            Toast.makeText(
                                context,
                                if (result.ok) "发布成功" else result.error,
                                Toast.LENGTH_SHORT,
                            ).show()
                            if (result.ok) {
                                nav.popBackStack()
                            }
                        }
                    }) {
                        if (submitting) Text("发布中…") else Text("发布")
                    }
                },
            )
        },
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            if (meta?.loginRequired == true) {
                Text("需要先登录才能发帖", color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
            }
            if (isNew) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("标题") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 1,
                )
                Spacer(Modifier.height(12.dp))
            }
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("内容 (支持 BBCode)") },
                modifier = Modifier.fillMaxWidth().height(280.dp),
            )
            Spacer(Modifier.height(8.dp))
            if (meta?.errorMessage?.isNotBlank() == true) {
                Text(meta!!.errorMessage, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}