package io.mtluntan.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.mtluntan.app.MTLuntanApp
import io.mtluntan.app.ui.motion.Motion

/**
 * 主题外壳（多主题引擎 + 玻璃 + 动效三档）。
 *
 * 与设置页一一对应：
 *  - 调色板：[MtPalette] 12 套种子色（含自定义），由 [seedSchemeFor] 生成整套配色
 *  - 浓度：背景/容器着色程度 0..1
 *  - 玻璃：关闭 / 轻玻璃 / 强玻璃（面板半透明 + 描边高光）
 *  - 动效：总开关 + 阻尼滑杆（一个滑杆调全 App 手感）
 *  - 不透明度：底栏 / 侧边栏的悬浮面板
 *  - 字体缩放
 */
data class ThemePreset(val name: String, val light: Long, val dark: Long)

/** 兼容旧的 6 色列表（外部若有引用仍可用）。 */
val ThemePresets = listOf(
    ThemePreset("MT 蓝", 0xFF2F6BFF, 0xFF8AB4FF),
    ThemePreset("森野绿", 0xFF2E7D32, 0xFF80C883),
    ThemePreset("暖阳橙", 0xFFE65100, 0xFFFFB74D),
    ThemePreset("葡萄紫", 0xFF6A1B9A, 0xFFCE93D8),
    ThemePreset("樱花粉", 0xFFC2185B, 0xFFF48FB1),
    ThemePreset("青碧", 0xFF00695C, 0xFF80CBC4),
)

/** 玻璃档位。 */
enum class GlassLevel(val label: String, val surfaceAlpha: Float, val borderAlpha: Float) {
    Off("关闭", 1.0f, 0.0f),
    Soft("轻玻璃", 0.86f, 0.28f),
    Strong("强玻璃", 0.68f, 0.48f),
}

val OpacityLevels = listOf(0.86f, 0.94f, 1f)
val OpacityLabels = listOf("半透明", "标准", "不透明")

val LocalPanelAlpha = staticCompositionLocalOf { 0.94f }
val LocalFontScale = staticCompositionLocalOf { 1f }
val LocalGlass = staticCompositionLocalOf { GlassLevel.Soft }
val LocalPalette = staticCompositionLocalOf { MtPalette.Aurora }

/** 主题附带的大圆角形态（液态玻璃风格偏圆）。 */
private val MtShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
)

@Composable
fun MtLuntanTheme(app: MTLuntanApp, content: @Composable () -> Unit) {
    val darkSetting by app.settings.darkMode.collectAsStateWithLifecycle(initialValue = null)
    val paletteIndex by app.settings.themeColor.collectAsStateWithLifecycle(initialValue = 0)
    val shade by app.settings.themeShade.collectAsStateWithLifecycle(initialValue = 45)
    val customSeed by app.settings.customSeed.collectAsStateWithLifecycle(initialValue = 0L)
    val glassIndex by app.settings.glassLevel.collectAsStateWithLifecycle(initialValue = 1)
    val motionOn by app.settings.motionEnabled.collectAsStateWithLifecycle(initialValue = true)
    val damping by app.settings.motionDamping.collectAsStateWithLifecycle(initialValue = 50)
    val dynamic by app.settings.dynamicColor.collectAsStateWithLifecycle(initialValue = false)
    val opacityIndex by app.settings.opacity.collectAsStateWithLifecycle(initialValue = 1)
    val fontStep by app.settings.fontScale.collectAsStateWithLifecycle(initialValue = 0)

    val dark = darkSetting ?: isSystemInDarkTheme()
    val context = LocalContext.current
    val palette = MtPalette.entries[paletteIndex.coerceIn(0, MtPalette.entries.size - 1)]
    val glass = GlassLevel.entries[glassIndex.coerceIn(0, GlassLevel.entries.size - 1)]

    // 全局动效开关 / 阻尼：写在这里，任何页面都不用各自读设置
    SideEffect {
        Motion.enabled.value = motionOn
        Motion.dampingKnob.floatValue = (damping.coerceIn(0, 100)) / 100f
    }

    val scheme = when {
        dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        else -> schemeFor(
            palette = palette,
            dark = dark,
            shade = shade.coerceIn(0, 100) / 100f,
            customSeed = if (customSeed != 0L) Color(customSeed.toInt()) else null,
        )
    }

    val fontScale = when (fontStep.coerceIn(0, 4)) {
        0 -> 0.92f
        1 -> 1.0f
        2 -> 1.08f
        3 -> 1.18f
        else -> 1.3f
    }

    CompositionLocalProvider(
        LocalPanelAlpha provides OpacityLevels[opacityIndex.coerceIn(0, OpacityLevels.size - 1)],
        LocalFontScale provides fontScale,
        LocalGlass provides glass,
        LocalPalette provides palette,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = scaledTypography(fontScale),
            shapes = MtShapes,
            content = content,
        )
    }
}

private fun scaledTypography(scale: Float): Typography {
    val base = Typography()
    fun TextUnit.s(): TextUnit = (value * scale).sp
    return base.copy(
        bodyLarge = base.bodyLarge.copy(fontSize = base.bodyLarge.fontSize.s(), lineHeight = base.bodyLarge.lineHeight.s()),
        bodyMedium = base.bodyMedium.copy(fontSize = base.bodyMedium.fontSize.s(), lineHeight = base.bodyMedium.lineHeight.s()),
        bodySmall = base.bodySmall.copy(fontSize = base.bodySmall.fontSize.s(), lineHeight = base.bodySmall.lineHeight.s()),
        titleLarge = base.titleLarge.copy(fontSize = base.titleLarge.fontSize.s()),
        titleMedium = base.titleMedium.copy(fontSize = base.titleMedium.fontSize.s()),
        titleSmall = base.titleSmall.copy(fontSize = base.titleSmall.fontSize.s()),
        labelMedium = base.labelMedium.copy(fontSize = base.labelMedium.fontSize.s()),
        labelSmall = base.labelSmall.copy(fontSize = base.labelSmall.fontSize.s()),
    )
}
