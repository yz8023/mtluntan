package io.mtluntan.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter

/**
 * 帖子 / 评论里的图片。
 *
 * 参考 qcxs/mtbbs_app 的 `BbcodeImage`（`widgets/bbcode/post_html_widget_parts.dart`）实现：
 *  - **不放大**：宽度上限取「原图像素宽」和「可用宽度」的较小值，小图不会被拉成满屏
 *    （用户反馈「图片显示异常」的根因就是旧的 `fillMaxWidth()` 把所有图都撑到满宽 + 硬编码高度变形）；
 *  - **按原始比例取高度**，超过 [maxHeight] 才截断，绝不会变形；
 *  - **三态可见**：加载中占位（转圈）/ 成功 / 失败（破图图标 + 点击重试），
 *    不会出现「一片空白还以为帖子没内容」；
 *  - 点击看大图、长按复制图片链接。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PostImage(
    url: String,
    modifier: Modifier = Modifier,
    maxHeight: Dp = 600.dp,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    // 重试用：URL 后面挂一个不发给服务器的本地片段，改它就能让 Coil 重新取图
    var attempt by remember(url) { mutableIntStateOf(0) }
    val model = if (attempt == 0) url else "$url#mtretry=$attempt"
    val painter = rememberAsyncImagePainter(model = model)
    // Coil 2.5 的 AsyncImagePainter.state 就是普通 State 值（读取即订阅重组），不是 Flow
    val state = painter.state

    val density = LocalDensity.current
    // 原图像素尺寸（加载成功后从 Drawable 拿，最可靠；-1/0 视为未知）
    var naturalW by remember(url) { mutableStateOf(0) }
    var naturalH by remember(url) { mutableStateOf(0) }
    LaunchedEffect(state, url) {
        val ok = state
        if (ok is AsyncImagePainter.State.Success) {
            val w = ok.result.drawable.intrinsicWidth
            val h = ok.result.drawable.intrinsicHeight
            if (w > 0 && h > 0) {
                naturalW = w
                naturalH = h
            }
        }
    }

    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        val w = if (naturalW > 0) with(density) { naturalW.toDp() } else null
        val h = if (naturalH > 0) with(density) { naturalH.toDp() } else null
        when {
            state is AsyncImagePainter.State.Error -> {
                // 失败：明确提示 + 可点击重试（不再是一块空白）
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { attempt += 1 }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Icon(
                        Icons.Filled.BrokenImage,
                        null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.outline,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text("图片加载失败，点这里重试", style = MaterialTheme.typography.labelMedium)
                        Text(
                            url.substringAfterLast('/').take(40),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            state is AsyncImagePainter.State.Success && (w == null || h == null) -> {
                // 拿到了图但读不到原始尺寸（极少数 drawable）：按可用宽度等比显示，不拉伸
                androidx.compose.foundation.Image(
                    painter = painter,
                    contentDescription = "图片",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .combinedClickable(
                            onClick = { onClick?.invoke() },
                            onLongClick = { onLongClick?.invoke() },
                        ),
                )
            }
            w == null || h == null -> {
                // 加载中：固定高度占位（参考项目 placeholder 高度 100）
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    if (state is AsyncImagePainter.State.Loading || state is AsyncImagePainter.State.Empty) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    } else {
                        Icon(
                            Icons.Filled.Image,
                            null,
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }
            else -> {
                // 宽度 = min(原图宽, 可用宽) —— 不放大；高度按比例，超高才截到 maxHeight
                val height = h.coerceAtMost(maxHeight)
                val width = if (height < h) w * (height.value / h.value) else w
                androidx.compose.foundation.Image(
                    painter = painter,
                    contentDescription = "图片",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .widthIn(max = width)
                        .height(height)
                        .clip(RoundedCornerShape(8.dp))
                        .combinedClickable(
                            onClick = { onClick?.invoke() },
                            onLongClick = { onLongClick?.invoke() },
                        ),
                )
            }
        }
    }
}
