package com.novelstudio.core.designsystem.motion

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

/**
 * MD3E 弹簧物理与手势动效参数（MD3E_DESIGN_SPEC.md §3）。
 *
 * 禁止使用生硬的线性（Linear）或普通贝塞尔动画，
 * 所有位移与缩放全部绑定 Spring Physics。
 */
object MD3EMotion {

    /** 弹簧跟手动效（用于卡片拖拽、卷帘比对） */
    val SnappySpring = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy, // 0.75f
        stiffness = Spring.StiffnessMediumLow,          // 400f
    )

    /** 柔和过渡动效（用于页面切换、抽屉展开） */
    val GentleSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,     // 1.0f
        stiffness = Spring.StiffnessLow,                // 200f
    )

    /** 卡片飞出抛掷动效（Swipe Deck） */
    val ThrowSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,    // 0.6f
        stiffness = Spring.StiffnessMedium,             // 800f
    )
}
