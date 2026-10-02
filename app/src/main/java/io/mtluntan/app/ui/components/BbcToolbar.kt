package io.mtluntan.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.mtluntan.app.util.GradientText

/**
 * BBCode 工具条：**发帖编辑器与帖子页回复面板共用同一套**。
 *
 * 用户要求的两点可配置项（设置 → 外观 / 回复）：
 *  - `style`：`text` 文字形态 / `icon` 图标形态；
 *  - `rows`：显示几行，**默认 2 行**，按钮按行等分（不滚动、不挤成一条）。
 *
 * 默认 2 行：第一行是高频的加粗/斜体/下划线/代码/引用/隐藏，
 * 第二行是颜色/渐变/链接/图片/标签/编辑器。行数变了自动重新均分。
 */
@Composable
fun BbcToolbar(
    onWrap: (String, String) -> Unit,
    modifier: Modifier = Modifier,
    onImage: (() -> Unit)? = null,
    uploading: Boolean = false,
    onOpenFullEditor: (() -> Unit)? = null,
    style: String = "icon",
    rows: Int = 2,
) {
    var colorPicker by remember { mutableStateOf(false) }
    var gradientPicker by remember { mutableStateOf(false) }
    var tagPicker by remember { mutableStateOf(false) }
    val textStyle = style == "text"
    val rowCount = rows.coerceIn(1, 4)

    // 动作集中成一个列表，按行数等分；图标 / 文字只影响呈现方式
    val actions: List<BbcAction> = buildList {
        add(BbcAction("加粗", "B", Icons.Filled.TextFields) { onWrap("[b]", "[/b]") })
        add(BbcAction("斜体", "I", Icons.Filled.TextFields) { onWrap("[i]", "[/i]") })
        add(BbcAction("下划线", "U", Icons.Filled.TextFields) { onWrap("[u]", "[/u]") })
        add(BbcAction("代码", "代码", Icons.Filled.Code) { onWrap("[code]", "[/code]") })
        add(BbcAction("引用", "引用", Icons.Filled.Tag) { onWrap("[quote]", "[/quote]") })
        add(BbcAction("隐藏", "隐藏", Icons.Filled.Tag) { onWrap("[hide]", "[/hide]") })
        add(BbcAction("颜色", "颜色", Icons.Filled.Palette) { colorPicker = true })
        add(BbcAction("渐变字", "渐变", Icons.Filled.ColorLens) { gradientPicker = true })
        add(BbcAction("链接", "链接", Icons.Filled.Send) { onWrap("[url]", "[/url]") })
        if (onImage != null) {
            add(BbcAction(if (uploading) "上传中" else "图片", "图片", Icons.Filled.Image) { onImage() })
        }
        add(BbcAction("更多标签", "标签", Icons.Filled.Slideshow) { tagPicker = true })
        if (onOpenFullEditor != null) {
            add(BbcAction("完整编辑器", "编辑器", Icons.Filled.TextFields) { onOpenFullEditor() })
        }
    }
    val perRow = ((actions.size + rowCount - 1) / rowCount).coerceAtLeast(1)
    val chunks = actions.chunked(perRow)

    Surface(tonalElevation = 2.dp, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            chunks.forEach { chunk ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    chunk.forEach { action ->
                        BbcToolButton(action = action, textStyle = textStyle, modifier = Modifier.weight(1f))
                    }
                    // 最后一行不满时补空位：每行按钮宽度一致，不会忽宽忽窄
                    repeat(perRow - chunk.size) { Spacer(Modifier.weight(1f)) }
                }
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

/** 工具条上的一个动作。 */
private class BbcAction(
    val desc: String,
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

/** 单个按钮：文字形态显示文字，图标形态显示图标（都带无障碍描述）。 */
@Composable
private fun BbcToolButton(action: BbcAction, textStyle: Boolean, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.heightIn(min = 30.dp).clickable(onClick = action.onClick),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (textStyle) {
                Text(
                    action.label,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Icon(action.icon, contentDescription = action.desc, modifier = Modifier.size(17.dp))
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
