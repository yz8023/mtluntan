package io.mtluntan.app.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import io.mtluntan.app.data.network.Net
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 文件落地：附件下载、帖子导出（HTML / 纯文本）。
 *
 * - Android 10+ 走 MediaStore.Downloads，不需要任何存储权限；
 * - Android 9 及以下写 App 专属外部目录（同样不需要权限），路径会 toast 出来。
 *
 * Java 版 v4.1 的「导出 HTML / 导出文本」和附件下载都收在这里，
 * 这样帖子页、记录中心、离线列表可以复用同一套逻辑。
 */
object FileSaver {

    /** 下载二进制附件/图片，返回保存位置的可读描述。 */
    suspend fun download(context: Context, url: String, fileName: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = Net.init(context).getResponse(url, foreground = true)
                if (!response.isSuccessful) error("HTTP ${response.code}")
                val bytes = response.body?.bytes() ?: error("响应为空")
                val safeName = sanitize(fileName.ifBlank { "attachment_${System.currentTimeMillis()}" })
                val where = saveBytes(context, safeName, bytes, "application/octet-stream")
                LogCenter.log(LogCenter.LogTag.RUN, "附件已保存：$safeName（${bytes.size / 1024} KB）")
                where
            }
        }

    /** 导出文本类文件（HTML / TXT / BBCode 备份）。 */
    suspend fun exportText(
        context: Context,
        fileName: String,
        content: String,
        mime: String = "text/plain",
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val safeName = sanitize(fileName)
            saveBytes(context, safeName, content.toByteArray(Charsets.UTF_8), mime)
        }
    }

    fun openInBrowser(context: Context, url: String) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure { CopyUtil.toast(context, "没有可用浏览器") }
    }

    fun shareText(context: Context, text: String, title: String = "分享") {
        runCatching {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, title))
        }
    }

    // ---------------- 内部实现 ----------------

    private fun saveBytes(context: Context, fileName: String, bytes: ByteArray, mime: String): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return legacySave(context, fileName, bytes)
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return "下载目录/$fileName"
        }
        return legacySave(context, fileName, bytes)
    }

    private fun legacySave(context: Context, fileName: String, bytes: ByteArray): String {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, "downloads")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, fileName)
        file.writeBytes(bytes)
        return file.absolutePath
    }

    private fun sanitize(name: String): String {
        val cleaned = name.replace(Regex("[\\\\/:*?\"<>|\\r\\n]"), "_").trim()
        return cleaned.ifBlank { "mtluntan_${System.currentTimeMillis()}" }.take(120)
    }
}
