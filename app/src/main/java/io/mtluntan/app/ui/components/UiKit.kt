package io.mtluntan.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.mtluntan.app.ui.motion.Motion
import io.mtluntan.app.ui.motion.StaggeredReveal
import io.mtluntan.app.ui.motion.pressScale
import io.mtluntan.app.ui.theme.GlassLevel
import io.mtluntan.app.ui.theme.LocalGlass

/**
 * 项目自己的 UI 组件层。
 *
 * 存在的理由：主题色必须**覆盖全部组件**——之前各处直接用 Material 默认容器色，
 * 换主题时卡片、底栏、分组头颜色各不相同，看着就「不干净」。
 * 现在所有容器都走这里，颜色只有一个来源（colorScheme + 玻璃档位）。
 */

@Composable
fun Modifier.mtGlassSurface(
    shape: Shape = RoundedCornerShape(20.dp),
    tint: Color? = null,
    elevation: Dp = 1.dp,
    glassOverride: GlassLevel? = null,
): Modifier {
    val glass = glassOverride ?: LocalGlass.current
    val base = tint ?: MaterialTheme.colorScheme.surfaceContainer
    val fill = if (glass == GlassLevel.Off) base else base.copy(alpha = glass.surfaceAlpha)
    var mod = this
        .shadow(elevation = if (glass == GlassLevel.Off) elevation else elevation + 2.dp, shape = shape, clip = false)
        .clip(shape)
        .background(fill)
    if (glass != GlassLevel.Off) {
        mod = mod.border(
            width = 1.dp,
            color = Color.White.copy(alpha = glass.borderAlpha * 0.35f),
            shape = shape,
        )
    }
    return mod
}

/** 玻璃卡片（带内边距）。 */
@Composable
fun MtCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(18.dp),
    tint: Color? = null,
    padding: PaddingValues = PaddingValues(14.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    var mod = modifier
        .fillMaxWidth()
        .mtGlassSurface(shape = shape, tint = tint)
        .pressScale(interaction)
    if (onClick != null) mod = mod.clickable(interactionSource = interaction, indication = null, onClick = onClick)
    Column(modifier = mod.padding(padding), content = content)
}

/** 分组标题：换主题时整块跟着变，不再各写各的。 */
@Composable
fun MtSectionHeader(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 6.dp, top = 16.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 3.dp, height = 14.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** 主 / 次按钮，按下有重量 + 高光。 */
@Composable
fun MtButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = true,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val scheme = MaterialTheme.colorScheme
    val container = when {
        !enabled -> scheme.surfaceVariant
        primary -> scheme.primary
        else -> scheme.surfaceContainerHigh
    }
    val contentColor = when {
        !enabled -> scheme.onSurfaceVariant
        primary -> scheme.onPrimary
        else -> scheme.onSurface
    }
    Row(
        modifier = modifier
            .mtGlassSurface(shape = RoundedCornerShape(14.dp), tint = container, elevation = 0.dp)
            .pressScale(interaction, pressedScale = 0.96f, enabled = enabled)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled) { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (loading) {
            androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = contentColor,
            )
            Spacer(Modifier.width(8.dp))
        } else if (icon != null) {
            Icon(icon, null, modifier = Modifier.size(16.dp), tint = contentColor)
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = contentColor)
    }
}

/** 胶囊标签（玻璃质感的 chip）。 */
@Composable
fun MtPill(
    text: String,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val scheme = MaterialTheme.colorScheme
    val tint = if (selected) scheme.primaryContainer else scheme.surfaceContainerHigh
    val contentColor = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant
    var mod = modifier
        .mtGlassSurface(shape = RoundedCornerShape(999.dp), tint = tint, elevation = 0.dp)
        .pressScale(interaction, pressedScale = 0.95f)
    if (onClick != null) mod = mod.clickable(interactionSource = interaction, indication = null, onClick = onClick)
    Row(
        modifier = mod.padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, modifier = Modifier.size(14.dp), tint = contentColor)
            Spacer(Modifier.width(5.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = contentColor)
    }
}

/** 分段控件：选中项有滑动药丸（不是硬切）。 */
@Composable
fun MtSegmented(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .mtGlassSurface(shape = RoundedCornerShape(999.dp), tint = MaterialTheme.colorScheme.surfaceContainerHigh, elevation = 0.dp)
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { option ->
            val active = option == selected
            val weight by animateFloatAsState(if (active) 1f else 0f, Motion.Press, label = "seg")
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f + weight * 0.12f)
                        else Color.Transparent
                    )
                    .clickable(interactionSource = interaction, indication = null) { onSelect(option) }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    option,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 交错入场的列表项外壳。 */
@Composable
fun RevealItem(index: Int, content: @Composable () -> Unit) {
    StaggeredReveal(index = index, modifier = Modifier.fillMaxWidth()) { content() }
}

/**
 * 极光背景：主题色在四角做很淡的径向渐变。
 * 只画在最底层，内容和卡片在上面依然是实色，不影响可读性。
 */
@Composable
fun AuroraBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val glass = LocalGlass.current
    if (glass == GlassLevel.Off) {
        Box(modifier = modifier.background(scheme.background)) { content() }
        return
    }
    val primary = scheme.primary
    val tertiary = scheme.tertiary
    Box(
        modifier = modifier.background(
            Brush.radialGradient(
                colors = listOf(primary.copy(alpha = 0.16f), Color.Transparent),
                center = Offset(0.05f, 0.0f),
                radius = 900f,
            )
        ).background(
            Brush.radialGradient(
                colors = listOf(tertiary.copy(alpha = 0.14f), Color.Transparent),
                center = Offset(1.0f, 0.12f),
                radius = 800f,
            )
        ).background(scheme.background.copy(alpha = 0.86f)),
    ) { content() }
}

/** 带图标的一行设置 / 入口。 */
@Composable
fun MtEntryRow(
    title: String,
    subtitle: String = "",
    icon: ImageVector? = null,
    trailingText: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    var mod = Modifier.fillMaxWidth()
    if (onClick != null) mod = mod.clickable(interactionSource = interaction, indication = null, onClick = onClick)
    Row(
        modifier = mod.pressScale(interaction, pressedScale = 0.985f).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, modifier = Modifier.size(17.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (trailingText != null) {
            Text(trailingText, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
        }
        Text("›", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.outline)
    }
}

/** 页面级容器：统一的背景 + 选中态分隔线。 */
@Composable
fun MtScreen(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(modifier = modifier.fillMaxSize(), color = Color.Transparent) {
        AuroraBackground(modifier = Modifier.fillMaxSize()) { content() }
    }
}
