package io.mtluntan.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb

/**
 * 多主题引擎。
 *
 * 设计思路参考 `yz8023/ui` 的液态玻璃主题（`theme/Theme.kt`）：
 *  - 一套「种子色 → 完整配色方案」的生成器，而不是每套主题手写 30 个颜色
 *  - 种子只提供 primary，secondary / tertiary 用色相偏移推导，保证同色系和谐
 *  - 深浅两档 + 一个「浓度」滑杆（shade）控制背景/容器的着色程度
 *
 * 与参考实现的一致原则：文本对比度由亮度推算（luminance > 0.5 用深色字），
 * 不依赖手挑的 on* 配色，所以新增主题不会出现「字看不清」。
 */
enum class MtPalette(val label: String, val light: Color, val dark: Color) {
    Aurora("冰蓝", Color(0xFF3E8FE0), Color(0xFF9CCAFF)),
    Mint("薄荷", Color(0xFF16A06A), Color(0xFF6FDCA8)),
    Sunset("落日", Color(0xFFE2703A), Color(0xFFFFB48A)),
    Coral("珊瑚", Color(0xFFE05B72), Color(0xFFFFA8B5)),
    Sakura("樱花", Color(0xFFD46AA5), Color(0xFFFFAEDC)),
    Galaxy("星夜", Color(0xFF6C5CE7), Color(0xFFC3BBFF)),
    Forest("竹青", Color(0xFF4C8C4A), Color(0xFF9FD79B)),
    Sand("暖沙", Color(0xFFB08442), Color(0xFFE8C98F)),
    Mono("素灰", Color(0xFF5A6472), Color(0xFFBCC6D4)),
    Gold("鎏金", Color(0xFFC29A16), Color(0xFFF2D675)),
    Ocean("深海", Color(0xFF1E7A9E), Color(0xFF7ED0EE)),
    Slate("石墨", Color(0xFF3E4C59), Color(0xFFAFBCC8)),
    Custom("自定义", Color(0xFF3E8FE0), Color(0xFF9CCAFF)),
}

/** 背景 / 容器着色浓度档位。 */
object Shade {
    const val Subtle = 0.0f
    const val Standard = 0.45f
    const val Bold = 1.0f
}

private val Ink = Color(0xFF1A1C1E)
private val Paper = Color(0xFFFDFCFF)
private val Night = Color(0xFF111318)
private val NightText = Color(0xFFE3E6EB)

/**
 * 由种子色生成完整 Material3 配色方案。
 *
 * @param seed  primary（主色）
 * @param dark  深色模式
 * @param shade 0..1，越大背景/容器越「带色」
 */
fun seedSchemeFor(seed: Color, dark: Boolean, shade: Float): ColorScheme {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(seed.toArgb(), hsv)
    val hue = hsv[0]
    val saturation = hsv[1]
    val s = shade.coerceIn(0f, 1f)

    fun tinted(deg: Float, satScale: Float = 1f, valueScale: Float = 1f): Color {
        val h = (hue + deg + 360f) % 360f
        val sat = (saturation * satScale).coerceIn(0f, 1f)
        val v = (hsv[2] * valueScale).coerceIn(0f, 1f)
        return Color(android.graphics.Color.HSVToColor(floatArrayOf(h, sat, v)))
    }

    val primary = seed
    val secondary = tinted(38f, 1.05f, 0.92f)
    val tertiary = tinted(-42f, 0.9f, 1.06f)

    val background = if (dark) mix(Night, primary, 0.10f + s * 0.14f)
    else mix(Paper, primary, 0.02f + s * 0.06f)
    val onBackground = if (dark) NightText else Ink
    val container = if (dark) mix(Night, primary, 0.16f + s * 0.10f)
    else mix(Color(0xFFF2F4F8), primary, 0.08f + s * 0.12f)

    val onPrimary = if (primary.luminance() > 0.5f) Color(0xFF101418) else Color.White
    val primaryContainer = if (dark) mix(primary, Color.White, 0.18f) else mix(primary, Color.White, 0.78f)
    val onPrimaryContainer = if (dark) mix(primary, Color.White, 0.75f) else mix(primary, Color.Black, 0.72f)

    return if (dark) {
        darkColorScheme(
            primary = primary, onPrimary = onPrimary,
            primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer,
            secondary = secondary, tertiary = tertiary,
            background = background, onBackground = onBackground,
            surface = background, onSurface = onBackground,
            surfaceVariant = mix(container, Color.White, 0.12f), onSurfaceVariant = Color(0xFFC1C7CE),
            surfaceContainer = container,
            surfaceContainerLow = background,
            surfaceContainerLowest = mix(background, Color.Black, 0.25f),
            surfaceContainerHigh = mix(container, Color.White, 0.05f),
            surfaceContainerHighest = mix(container, Color.White, 0.09f),
            outline = Color(0xFF8B929A),
            outlineVariant = mix(container, Color.White, 0.14f),
            error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
        )
    } else {
        lightColorScheme(
            primary = primary, onPrimary = onPrimary,
            primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer,
            secondary = secondary, tertiary = tertiary,
            background = background, onBackground = onBackground,
            surface = background, onSurface = onBackground,
            surfaceVariant = mix(container, Color.White, 0.45f), onSurfaceVariant = Color(0xFF45494E),
            surfaceContainer = container,
            surfaceContainerLow = mix(background, Color.White, 0.4f),
            surfaceContainerLowest = Color.White,
            surfaceContainerHigh = mix(container, Color.Black, 0.04f),
            surfaceContainerHighest = mix(container, Color.Black, 0.08f),
            outline = Color(0xFF767B82),
            outlineVariant = mix(container, Color.Black, 0.10f),
            error = Color(0xFFB3261E), onError = Color.White,
        )
    }
}

/** 取主题预设（带自定义种子色）。 */
/**
 * 两个颜色按比例混合。
 *
 * 不直接用 Compose 的 `Color.lerp` 扩展（版本间 API 形态不一致，编译期已踩坑），
 * 这里自己按 sRGB 分量插值，行为可控、任何 Compose 版本都能编。
 */
private fun mix(start: Color, stop: Color, fraction: Float): Color {
    val t = fraction.coerceIn(0f, 1f)
    return Color(
        red = start.red + (stop.red - start.red) * t,
        green = start.green + (stop.green - start.green) * t,
        blue = start.blue + (stop.blue - start.blue) * t,
        alpha = start.alpha + (stop.alpha - start.alpha) * t,
    )
}

fun schemeFor(palette: MtPalette, dark: Boolean, shade: Float, customSeed: Color?): ColorScheme {
    val seed = if (palette == MtPalette.Custom) (customSeed ?: MtPalette.Custom.light) else if (dark) palette.dark else palette.light
    return seedSchemeFor(seed, dark, shade)
}

/** 设置页的色板预览（浅色版的 primary）。 */
fun swatchColor(palette: MtPalette): Color = palette.light
