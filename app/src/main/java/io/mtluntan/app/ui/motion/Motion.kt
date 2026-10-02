package io.mtluntan.app.ui.motion

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.util.lerp

/**
 * 运动令牌（Motion tokens）。
 *
 * 设计语言参考 `yz8023/ui`（Liquid Motion UI）的 `运动.kt`：
 * 所有动画只从这一处取值，调用点不再临时写 `spring(...)`，
 * 这样「一个滑杆调全 App 手感」才有可能。
 *
 * 两档全局开关：
 *  - [enabled] 总开关（设置里可关，关掉后所有动效退化为瞬时/静态）
 *  - [dampingKnob] 阻尼滑杆 0..1，0 = 更有弹性，1 = 更顺滑
 */
object Motion {

    /** 动效总开关。关掉后仍保证内容可见（不会出现「永远不出现」的元素）。 */
    val enabled = mutableStateOf(true)

    /** 全局阻尼比例基准 0..1（0.5 为设计默认）。 */
    val dampingKnob = mutableFloatStateOf(0.5f)

    private fun damped(base: Float): Float {
        val d = dampingKnob.floatValue.coerceIn(0f, 1f)
        return (base * lerp(0.72f, 1.28f, d)).coerceIn(0.25f, 1.25f)
    }

    /** 动效关闭时把时长压到 0，让动画直接落位。 */
    fun tweenMs(ms: Int): Int = if (enabled.value) ms else 0

    object Duration {
        const val Instant = 90
        const val Fast = 180
        const val Standard = 320
        const val Medium = 460
        const val Slow = 700
    }

    val EaseOutExpo: Easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
    val EaseInOutQuart: Easing = CubicBezierEasing(0.76f, 0f, 0.24f, 1f)
    val EaseOutBack: Easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
    val EaseSharp: Easing = CubicBezierEasing(0.65f, 0f, 0.45f, 1f)

    fun <T> gentle(visibilityThreshold: T? = null): FiniteAnimationSpec<T> =
        spring(damped(0.90f), 240f, visibilityThreshold)

    fun <T> standard(visibilityThreshold: T? = null): FiniteAnimationSpec<T> =
        spring(damped(0.82f), 520f, visibilityThreshold)

    fun <T> snappy(visibilityThreshold: T? = null): FiniteAnimationSpec<T> =
        spring(damped(0.76f), 900f, visibilityThreshold)

    fun <T> bouncy(visibilityThreshold: T? = null): FiniteAnimationSpec<T> =
        spring(damped(0.46f), 420f, visibilityThreshold)

    val Press: FiniteAnimationSpec<Float> = snappy(0.001f)
    val TabSlide: FiniteAnimationSpec<Float> = snappy(0.001f)
    val PanelExpand: FiniteAnimationSpec<Float> = standard(0.01f)
    val Reveal: FiniteAnimationSpec<Float> = gentle(0.01f)
    val OffsetSnap: FiniteAnimationSpec<IntOffset> = snappy(IntOffset(1, 1))

    /** 列表交错出现的间隔（毫秒）。 */
    object Stagger {
        const val TightGrid = 30
        const val StandardList = 55
        const val DramaticCascade = 110
    }

    /** 临界阻尼，零过冲。 */
    const val CriticalDamping = Spring.DampingRatioNoBouncy
}
