package com.novelstudio.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * MD3E 表现力色彩体系。
 *
 * 静态基线调色板；后续将基于 material-color-utilities 的 HCT
 * 按当前选中图片提取 Dominant Color 动态替换（MD3E_DESIGN_SPEC.md §2）。
 */
object MD3EColors {

    // ---------- 表现力主色（紫罗兰 × 青碧 × 暖沙） ----------
    val Primary = Color(0xFF5B5BD6)
    val OnPrimary = Color(0xFFFFFFFF)
    val PrimaryContainer = Color(0xFFE1E0FF)
    val OnPrimaryContainer = Color(0xFF10105C)

    val Secondary = Color(0xFF6B5EA2)
    val OnSecondary = Color(0xFFFFFFFF)
    val SecondaryContainer = Color(0xFFE7DEFF)
    val OnSecondaryContainer = Color(0xFF231A47)

    val Tertiary = Color(0xFF7E5260)
    val OnTertiary = Color(0xFFFFFFFF)
    val TertiaryContainer = Color(0xFFFFD9E1)
    val OnTertiaryContainer = Color(0xFF31101D)

    val Error = Color(0xFFBA1A1A)
    val OnError = Color(0xFFFFFFFF)
    val ErrorContainer = Color(0xFFFFDAD6)
    val OnErrorContainer = Color(0xFF410002)

    // ---------- 深浅模式配色方案 ----------
    val LightScheme = lightColorScheme(
        primary = Primary,
        onPrimary = OnPrimary,
        primaryContainer = PrimaryContainer,
        onPrimaryContainer = OnPrimaryContainer,
        secondary = Secondary,
        onSecondary = OnSecondary,
        secondaryContainer = SecondaryContainer,
        onSecondaryContainer = OnSecondaryContainer,
        tertiary = Tertiary,
        onTertiary = OnTertiary,
        tertiaryContainer = TertiaryContainer,
        onTertiaryContainer = OnTertiaryContainer,
        error = Error,
        onError = OnError,
        errorContainer = ErrorContainer,
        onErrorContainer = OnErrorContainer,
        background = Color(0xFFFCF8FF),
        onBackground = Color(0xFF1B1B21),
        surface = Color(0xFFFCF8FF),
        onSurface = Color(0xFF1B1B21),
        surfaceVariant = Color(0xFFE5E1EC),
        onSurfaceVariant = Color(0xFF47464F),
        outline = Color(0xFF777680),
        outlineVariant = Color(0xFFC8C5D0),
    )

    val DarkScheme = darkColorScheme(
        primary = Color(0xFFC3C3FF),
        onPrimary = Color(0xFF22226E),
        primaryContainer = Color(0xFF3A3A9C),
        onPrimaryContainer = Color(0xFFE1E0FF),
        secondary = Color(0xFFCBC2EB),
        onSecondary = Color(0xFF332D52),
        secondaryContainer = Color(0xFF4A4469),
        onSecondaryContainer = Color(0xFFE7DEFF),
        tertiary = Color(0xFFEFB8C8),
        onTertiary = Color(0xFF4A2532),
        tertiaryContainer = Color(0xFF633B48),
        onTertiaryContainer = Color(0xFFFFD9E1),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF131318),
        onBackground = Color(0xFFE4E1E9),
        surface = Color(0xFF131318),
        onSurface = Color(0xFFE4E1E9),
        surfaceVariant = Color(0xFF47464F),
        onSurfaceVariant = Color(0xFFC8C5D0),
        outline = Color(0xFF928F99),
        outlineVariant = Color(0xFF47464F),
    )

    // ---------- Opus 电池仪表环语义色 ----------
    val BatteryFull = Color(0xFF4CD97B)   // 绿色：充足
    val BatteryMedium = Color(0xFFFFD23E) // 黄色：中等
    val BatteryLow = Color(0xFFFF7043)    // 红橙色：枯竭
}
