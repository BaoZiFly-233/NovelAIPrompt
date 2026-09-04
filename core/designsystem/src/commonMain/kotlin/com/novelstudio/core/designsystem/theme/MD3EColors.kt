package com.novelstudio.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// ─── 灰蓝中性梯度（OKLCH 对齐，亮度均匀步进）────────────────────────────────
private val Neutral00 = Color(0xFF0E1117)  // 近黑，off-black 背景
private val Neutral05 = Color(0xFF181C24)
private val Neutral10 = Color(0xFF1E2330)
private val Neutral15 = Color(0xFF252B3A)
private val Neutral20 = Color(0xFF2C3344)
private val Neutral30 = Color(0xFF3A4257)
private val Neutral40 = Color(0xFF4E5870)
private val Neutral50 = Color(0xFF6B758E)
private val Neutral60 = Color(0xFF8B96AE)
private val Neutral70 = Color(0xFFAAB4CA)
private val Neutral80 = Color(0xFFC8D0E4)
private val Neutral90 = Color(0xFFE4E8F4)
private val Neutral95 = Color(0xFFF0F2FA)
private val Neutral99 = Color(0xFFF7F8FC)  // 近白，off-white

// ─── 电光靛蓝主调 ──────────────────────────────────────────────────────────
private val Indigo10 = Color(0xFF0A0F3D)
private val Indigo20 = Color(0xFF141D6B)
private val Indigo30 = Color(0xFF1E2E99)
private val Indigo40 = Color(0xFF2B41CC)  // 主强调色 (Light)
private val Indigo50 = Color(0xFF4A5FE8)
private val Indigo60 = Color(0xFF6B7FF2)
private val Indigo70 = Color(0xFF8E9FF8)
private val Indigo80 = Color(0xFFB3BFFB)  // 主强调色容器文字 (Dark)
private val Indigo90 = Color(0xFFD8DEFF)
private val Indigo95 = Color(0xFFECEFFF)

// ─── 次级语义：青色（成功/激活）────────────────────────────────────────────
private val Teal40 = Color(0xFF00766A)
private val Teal80 = Color(0xFF7FDAD1)
private val Teal90 = Color(0xFF9EF2E8)

// ─── 次级语义：暖金（警告）────────────────────────────────────────────────
private val Amber40 = Color(0xFF785A00)
private val Amber80 = Color(0xFFE9C46A)
private val Amber90 = Color(0xFFFAE18A)

// ─── 错误色 ───────────────────────────────────────────────────────────────
private val Error40 = Color(0xFFBA1A1A)
private val Error90 = Color(0xFFFFDAD6)
private val ErrorContainer = Color(0xFFFFDAD6)
private val OnErrorContainer = Color(0xFF410002)

/** 亮色 ColorScheme：灰蓝工作台背景 + 电光靛蓝主调 */
fun studioLightColorScheme() = lightColorScheme(
    primary = Indigo40,
    onPrimary = Color.White,
    primaryContainer = Indigo90,
    onPrimaryContainer = Indigo10,
    secondary = Teal40,
    onSecondary = Color.White,
    secondaryContainer = Teal90,
    onSecondaryContainer = Color(0xFF002019),
    tertiary = Amber40,
    onTertiary = Color.White,
    tertiaryContainer = Amber90,
    onTertiaryContainer = Color(0xFF251A00),
    error = Error40,
    onError = Color.White,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,
    background = Neutral99,
    onBackground = Neutral10,
    surface = Neutral99,
    onSurface = Neutral10,
    surfaceVariant = Neutral90,
    onSurfaceVariant = Neutral30,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Neutral95,
    surfaceContainer = Neutral90,
    surfaceContainerHigh = Neutral80,
    surfaceContainerHighest = Neutral70,
    outline = Neutral50,
    outlineVariant = Neutral70,
    scrim = Neutral00,
    inverseSurface = Neutral20,
    inverseOnSurface = Neutral90,
    inversePrimary = Indigo80,
)

/** 暗色 ColorScheme：深灰蓝底 + 柔和靛蓝主调（chroma 降低、lightness 提高以保持可读性）*/
fun studioDarkColorScheme() = darkColorScheme(
    primary = Indigo80,
    onPrimary = Indigo20,
    primaryContainer = Indigo30,
    onPrimaryContainer = Indigo90,
    secondary = Teal80,
    onSecondary = Color(0xFF003731),
    secondaryContainer = Color(0xFF005047),
    onSecondaryContainer = Teal90,
    tertiary = Amber80,
    onTertiary = Color(0xFF3F2E00),
    tertiaryContainer = Color(0xFF5B4300),
    onTertiaryContainer = Amber90,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Neutral05,
    onBackground = Neutral90,
    surface = Neutral05,
    onSurface = Neutral90,
    surfaceVariant = Neutral20,
    onSurfaceVariant = Neutral70,
    surfaceContainerLowest = Neutral00,
    surfaceContainerLow = Neutral10,
    surfaceContainer = Neutral15,
    surfaceContainerHigh = Neutral20,
    surfaceContainerHighest = Neutral30,
    outline = Neutral50,
    outlineVariant = Neutral30,
    scrim = Neutral00,
    inverseSurface = Neutral90,
    inverseOnSurface = Neutral20,
    inversePrimary = Indigo40,
)
