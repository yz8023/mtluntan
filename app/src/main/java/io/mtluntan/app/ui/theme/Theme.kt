package io.mtluntan.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.mtluntan.app.MTLuntanApp

/**
 * 主题三件套（Java 版 ThemeManager）：
 *  - 深色模式：跟随系统 / 强制深色 / 强制浅色
 *  - 主题色：6 套预设（或动态取色，安卓 12+）
 *  - 不透明度：底栏与侧边栏的悬浮面板透明度（0 ~ 2 档）
 *
 * 额外还有字体缩放档位，直接作用在 Typography 上，正文和列表一起变，
 * 不会出现「只有某一页字变大」的割裂感。
 */
data class ThemePreset(val name: String, val light: Long, val dark: Long)

val ThemePresets = listOf(
    ThemePreset("MT 蓝", 0xFF2F6BFF, 0xFF8AB4FF),
    ThemePreset("森野绿", 0xFF2E7D32, 0xFF80C883),
    ThemePreset("暖阳橙", 0xFFE65100, 0xFFFFB74D),
    ThemePreset("葡萄紫", 0xFF6A1B9A, 0xFFCE93D8),
    ThemePreset("樱花粉", 0xFFC2185B, 0xFFF48FB1),
    ThemePreset("青碧", 0xFF00695C, 0xFF80CBC4),
)

/** 悬浮面板（底栏 / 侧边栏）不透明度档位。 */
val OpacityLevels = listOf(0.86f, 0.94f, 1f)
val OpacityLabels = listOf("半透明", "标准", "不透明")

val LocalPanelAlpha = staticCompositionLocalOf { 0.94f }
val LocalFontScale = staticCompositionLocalOf { 1f }

@Composable
fun MtLuntanTheme(app: MTLuntanApp, content: @Composable () -> Unit) {
    val darkSetting by app.settings.darkMode.collectAsStateWithLifecycle(initialValue = null)
    val colorIndex by app.settings.themeColor.collectAsStateWithLifecycle(initialValue = 0)
    val dynamic by app.settings.dynamicColor.collectAsStateWithLifecycle(initialValue = false)
    val opacityIndex by app.settings.opacity.collectAsStateWithLifecycle(initialValue = 1)
    val fontStep by app.settings.fontScale.collectAsStateWithLifecycle(initialValue = 0)

    val dark = darkSetting ?: isSystemInDarkTheme()
    val preset = ThemePresets[colorIndex.coerceIn(0, ThemePresets.size - 1)]
    val context = LocalContext.current

    val scheme = when {
        dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme(
            primary = Color(preset.dark),
            secondary = Color(preset.dark).copy(alpha = 0.85f),
            tertiary = Color(preset.light),
        )
        else -> lightColorScheme(
            primary = Color(preset.light),
            secondary = Color(preset.light).copy(alpha = 0.85f),
            tertiary = Color(preset.dark),
        )
    }

    val scale = when (fontStep.coerceIn(0, 4)) {
        0 -> 0.92f
        1 -> 1.0f
        2 -> 1.08f
        3 -> 1.18f
        else -> 1.3f
    }

    CompositionLocalProvider(
        LocalPanelAlpha provides OpacityLevels[opacityIndex.coerceIn(0, OpacityLevels.size - 1)],
        LocalFontScale provides scale,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = scaledTypography(scale),
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
