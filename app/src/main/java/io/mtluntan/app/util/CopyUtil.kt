package io.mtluntan.app.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast

/** 剪贴板 + 轻提示的统一封装。 */
object CopyUtil {

    fun copy(context: Context, text: String, toast: String = "已复制") {
        if (text.isEmpty()) {
            Toast.makeText(context, "没有可复制的内容", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("mtluntan", text))
            Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
        } catch (t: Throwable) {
            Toast.makeText(context, "复制失败", Toast.LENGTH_SHORT).show()
        }
    }

    fun toast(context: Context, text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }
}
