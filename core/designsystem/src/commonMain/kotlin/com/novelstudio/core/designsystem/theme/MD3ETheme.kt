package com.novelstudio.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * MD3E 药丸形状：完全圆角（用于 Chip、SegmentedButton）
 */
val MD3EPillShape: Shape = RoundedCornerShape(999.dp)

/**
 * 为中文生产力界面校准的紧凑字号与行高体系
 *
 * 相比默认 M3 Typography：
 * - Display 系列减小 8sp（中文标题无需过大）
 * - Body 系列行高收紧 2sp（提高信息密度）
 * - Label 系列统一 Medium 字重（界面控件可读性）
 */
val MD3ETypography = Typography(
    displayLarge = TextStyle(fontSize = 48.sp, lineHeight = 56.sp, fontWeight = FontWeight.SemiBold),
    displayMedium = TextStyle(fontSize = 40.sp, lineHeight = 48.sp, fontWeight = FontWeight.SemiBold),
    displaySmall = TextStyle(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold),
    headlineLarge = TextStyle(fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.15.sp),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.2.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp),
)

/**
 * MD3E 主题入口：深浅色 scheme + 表现力 Typography
 *
 * 注：CMP 1.12.0 的 MaterialExpressiveTheme 标记为 internal，
 * 暂时使用标准 MaterialTheme + 自定义 Typography + Motion 规格
 */
@Composable
fun MD3ETheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) studioDarkColorScheme() else studioLightColorScheme(),
        shapes = Shapes(),
        typography = MD3ETypography,
        content = content,
    )
}

/**
 * 慢速空间动画规格（向后兼容）
 *
 * 等效于 MD3EMotion.slowSpatial()，用于需要 @Composable 上下文的场景
 */
@Composable
fun <T> expressiveSlowSpatialSpec(): androidx.compose.animation.core.FiniteAnimationSpec<T> =
    com.novelstudio.core.designsystem.motion.MD3EMotion.slowSpatial()

