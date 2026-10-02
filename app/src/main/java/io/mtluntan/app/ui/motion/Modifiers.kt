package io.mtluntan.app.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

/**
 * 可复用的动效修饰符（参考 `yz8023/ui` 的「修饰符.kt / 交互高亮.kt」）。
 *
 * 四个手感原语，可以叠加使用：
 *  - [pressScale]      按下去有重量（替代干巴巴的 ripple）
 *  - [impactShake]     一次性冲击抖动（失败 / 校验不过时用）
 *  - [idleBreathing]   闲置呼吸（正在进行的动作让界面「活着」）
 *  - [staggeredReveal] 入场交错
 *  - [mtSpecular]      跟手指的高光（玻璃面板的交互反馈）
 */

/**
 * ⚠️ 为什么这里不再用 `Modifier.composed`：
 *
 * pressScale / impactShake / mtSpecular 以前都是 `Modifier.composed { … }`。
 * `composed` 把 `remember` / `LaunchedEffect` 放进一个延迟到布局阶段才执行的小组合里；
 * 这个 Modifier 挂在会被**复用**的列表项上（我们每个卡片、每个楼层都挂了）时，
 * 组合的「组结构」可能错位，表现就是社区里很典型的那条崩溃：
 *
 *     FATAL EXCEPTION: main
 *     java.lang.ArrayIndexOutOfBoundsException: length=0; index=-5
 *       at androidx.compose.runtime.ComposerImpl.t(…)      ← 混淆后就是 `l0.r.t`
 *       （mapping 还原：ComposerImpl.end / SlotWriter.getParent / SlotTableKt.key…）
 *
 * 现在改成普通的 **@Composable Modifier 工厂函数**：效果一样，
 * 但走正常组合流程，没有延迟小组合，也就没有这个崩溃面。
 */

/** 按压缩放：0.94 是「有重量但不过火」的默认值。 */
@Composable
fun Modifier.pressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.94f,
    enabled: Boolean = true,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val target = if (pressed && enabled && Motion.enabled.value) pressedScale else 1f
    val scale by animateFloatAsState(
        targetValue = target,
        animationSpec = Motion.Press,
        label = "pressScale",
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * 冲击抖动：两轴不同频率 + 线性衰减包络，才是「冲击」而不是「糊成一团」。
 * 传 null 不触发。
 */
@Composable
fun Modifier.impactShake(
    trigger: Any?,
    amplitude: Float = 9f,
    durationMs: Int = 300,
): Modifier {
    val progress = remember { Animatable(1f) }
    LaunchedEffect(trigger) {
        if (trigger == null || !Motion.enabled.value) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMs, easing = LinearEasing))
    }
    return this.graphicsLayer {
        val p = progress.value
        val envelope = (1f - p).coerceAtLeast(0f)
        val seconds = p * durationMs / 1000f
        translationX = sin(seconds * 47f) * amplitude * envelope
        translationY = sin(seconds * 31f + 1.3f) * amplitude * envelope
    }
}

/** 闲置呼吸：默认幅度很小，只用来表达「正在进行」。 */
@Composable
fun Modifier.idleBreathing(
    enabled: Boolean = true,
    periodMs: Int = 5600,
    amplitudeX: Float = 0.012f,
    amplitudeY: Float = 0.020f,
): Modifier {
    if (!enabled || !Motion.enabled.value) return this
    val transition = rememberInfiniteTransition(label = "idle")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(periodMs, easing = LinearEasing)),
        label = "idlePhase",
    )
    return this.graphicsLayer {
        scaleX = 1f + sin(phase) * amplitudeX
        scaleY = 1f + sin(phase * 0.84f + 0.7f) * amplitudeY
    }
}

/**
 * 入场交错。**动效关闭时直接是完成态**，避免出现「内容永远不显示」。
 */
@Composable
fun StaggeredReveal(
    index: Int,
    modifier: Modifier = Modifier,
    staggerMs: Int = Motion.Stagger.StandardList,
    startDelayMs: Int = 40,
    translateYDp: Int = 16,
    content: @Composable () -> Unit,
) {
    val active = Motion.enabled.value
    var visible by remember { mutableStateOf(!active) }
    LaunchedEffect(active, index) {
        if (active) {
            delay((startDelayMs + index.coerceAtMost(12) * staggerMs).toLong())
            visible = true
        }
    }
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = Motion.Reveal,
        label = "staggerReveal",
    )
    val offsetPx = with(LocalDensity.current) { translateYDp.dp.toPx() }
    Box(
        modifier = modifier.graphicsLayer {
            alpha = progress
            translationY = (1f - progress) * offsetPx
        }
    ) { content() }
}

/**
 * 跟着手指的高光（交互高亮简化版）。
 *
 * 有两层：铺满的白色薄雾 + 以手指为圆心的径向「镜头光」，
 * 都按按压进度淡入淡出，所以它不会「啪」地出现。
 */
@Composable
fun Modifier.mtSpecular(interactionSource: InteractionSource, radiusScale: Float = 0.72f): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val progress by animateFloatAsState(
        targetValue = if (pressed && Motion.enabled.value) 1f else 0f,
        animationSpec = Motion.standard(0.001f),
        label = "specular",
    )
    var center by remember { mutableStateOf(Offset.Zero) }
    val pointer = Modifier.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                event.changes.lastOrNull()?.let { center = it.position }
            }
        }
    }
    return pointer.drawWithContent {
        if (progress > 0f) {
            drawRect(Color.White.copy(alpha = 0.07f * progress), blendMode = BlendMode.Plus)
            drawCircle(
                color = Color.White.copy(alpha = 0.13f * progress),
                radius = size.minDimension * radiusScale,
                center = Offset(center.x.coerceIn(0f, size.width), center.y.coerceIn(0f, size.height)),
                blendMode = BlendMode.Plus,
            )
        }
        drawContent()
    }
}

/** 一次性「弹一下」，用于点赞 / 收藏成功的即时反馈。 */
@Composable
fun bounceOn(trigger: Any?, scale: Float = 1.18f): Float {
    val anim = remember { Animatable(1f) }
    LaunchedEffect(trigger) {
        if (trigger == null || !Motion.enabled.value) return@LaunchedEffect
        anim.snapTo(scale)
        anim.animateTo(1f, Motion.bouncy(0.001f))
    }
    return anim.value
}
