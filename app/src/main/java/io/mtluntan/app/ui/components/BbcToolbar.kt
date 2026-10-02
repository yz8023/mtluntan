package io.mtluntan.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.mtluntan.app.util.GradientText

/**
 * BBCode 工具条：**发帖编辑器与帖子页回复面板共用同一套**。
 *
 * 之前只有编辑器里有工具条，快速回复只能发纯文本；现在抽成公共组件，
 * 回复面板也能加粗 / 上色 / 插图，行为完全一致（少维护一份代码）。
 */
@Composable
fun BbcToolbar(
    onWrap: (String, String) -> Unit,
    modifier: Modifier = Modifier,
    onImage: (() -> Unit)? = null,
    uploading: Boolean = false,
    onOpenFullEditor: (() -> Unit)? = null,
) {
    var colorPicker by remember { mutableStateOf(false) }
    var gradientPicker by remember { mutableStateOf(false) }
    var tagPicker by remember { mutableStateOf(false) }

    Surface(tonalElevation = 2.dp, modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolButton(Icons.Filled.TextFields, "B", "加粗") { onWrap("[b]", "[/b]") }
            ToolButton(Icons.Filled.TextFields, "I", "斜体") { onWrap("[i]", "[/i]") }
            ToolButton(Icons.Filled.TextFields, "U", "下划线") { onWrap("[u]", "[/u]") }
            ToolButton(Icons.Filled.Code, null, "代码") { onWrap("[code]", "[/code]") }
            ToolButton(Icons.Filled.Tag, null, "引用") { onWrap("[quote]", "[/quote]") }
            ToolButton(Icons.Filled.Tag, null, "隐藏") { onWrap("[hide]", "[/hide]") }
            ToolButton(Icons.Filled.Palette, null, "颜色") { colorPicker = true }
            ToolButton(Icons.Filled.ColorLens, null, "渐变字") { gradientPicker = true }
            ToolButton(Icons.Filled.Send, null, "链接") { onWrap("[url]", "[/url]") }
            if (onImage != null) {
                ToolButton(Icons.Filled.Image, null, if (uploading) "上传中" else "图片") { onImage() }
            }
            ToolButton(Icons.Filled.Slideshow, null, "更多标签") { tagPicker = true }
            if (onOpenFullEditor != null) {
                ToolButton(Icons.Filled.TextFields, "完整编辑器", "完整编辑器") { onOpenFullEditor() }
            }
        }
    }

    if (colorPicker) {
        ColorPickerDialog(onDismiss = { colorPicker = false }) { hex ->
            onWrap("[color=#$hex]", "[/color]")
            colorPicker = false
        }
    }
    if (gradientPicker) {
        GradientPickerDialog(onDismiss = { gradientPicker = false }) { start, end ->
            onWrap("[gradient=${GradientText.hex(start)},${GradientText.hex(end)}]", "[/gradient]")
            gradientPicker = false
        }
    }
    if (tagPicker) {
        TagPickerDialog(onDismiss = { tagPicker = false }) { open, close ->
            onWrap(open, close)
            tagPicker = false
        }
    }
}

@Composable
private fun ToolButton(
    icon: ImageVector,
    label: String?,
    desc: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Icon(icon, contentDescription = desc, modifier = Modifier.size(16.dp))
            if (label != null) {
                Spacer(Modifier.width(4.dp))
                Text(label, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun ColorPickerDialog(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var r by remember { mutableStateOf(0.2f) }
    var g by remember { mutableStateOf(0.5f) }
    var b by remember { mutableStateOf(0.9f) }
    val color = Color(r, g, b)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择颜色") },
        text = {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(color),
                )
                Spacer(Modifier.height(8.dp))
                ChannelSlider("R", r) { r = it }
                ChannelSlider("G", g) { g = it }
                ChannelSlider("B", b) { b = it }
                Text(
                    "#" + listOf(r, g, b).joinToString("") { channel ->
                        ((channel * 255).toInt()).toString(16).padStart(2, '0')
                    }.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val hex = listOf(r, g, b).joinToString("") { ch ->
                    ((ch * 255).toInt()).toString(16).padStart(2, '0')
                }.uppercase()
                onPick(hex)
            }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ChannelSlider(name: String, value: Float, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(18.dp))
        Slider(value = value, onValueChange = onChange, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun GradientPickerDialog(onDismiss: () -> Unit, onPick: (Long, Long) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("渐变字（8 种预设）") },
        text = {
            Column {
                GradientText.presets.forEach { preset ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(preset.start, preset.end) }
                            .padding(vertical = 8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(GradientText.brush(preset.start, preset.end)),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(preset.name, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text(
                    "选中后会把「选中的文字」包成 [gradient=..] 标签",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
private fun TagPickerDialog(onDismiss: () -> Unit, onPick: (String, String) -> Unit) {
    val tags = listOf(
        "[b]" to "[/b]", "[i]" to "[/i]", "[u]" to "[/u]", "[s]" to "[/s]",
        "[color=#FF5722]" to "[/color]", "[size=18]" to "[/size]",
        "[url]" to "[/url]", "[email]" to "[/email]",
        "[quote]" to "[/quote]", "[code]" to "[/code]", "[hide]" to "[/hide]",
        "[free]" to "[/free]", "[img]" to "[/img]", "[attach]" to "[/attach]",
        "[float]" to "[/float]", "[table]" to "[/table]", "[tr]" to "[/tr]", "[td]" to "[/td]",
        "[audio]" to "[/audio]", "[media]" to "[/media]", "[flash]" to "[/flash]",
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("BBCode 标签（${tags.size} 个）") },
        text = {
            Column(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                tags.forEach { (open, close) ->
                    Text(
                        open.replace("[", "").replace("]", ""),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(open, close) }
                            .padding(vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}
