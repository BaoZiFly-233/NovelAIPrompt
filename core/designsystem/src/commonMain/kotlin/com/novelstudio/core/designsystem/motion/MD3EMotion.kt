package com.novelstudio.core.designsystem.motion

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Material 3 Expressive 动效规范
 *
 * 基于 M3E 运动令牌，提供统一的动画规格。
 * 注：等待 CMP 正式支持 MotionScheme 后，这些值将从 MaterialTheme.motionScheme 读取。
 *
 * 当前使用硬编码规格，基于 Material Design 3 Expressive Motion 指南：
 * - Fast Spatial: 300ms spring (位置/尺寸)
 * - Slow Spatial: 500ms spring (布局/页面)
 * - Fast Effects: 150ms tween (颜色/透明度)
 * - Slow Effects: 300ms tween (渐变/阴影)
 */
object MD3EMotion {
    /**
     * 快速空间变换：位置、尺寸变化（300ms spring）
     *
     * 适用场景：
     * - 按钮按下缩放
     * - 卡片拖拽跟手
     * - Slider 滑动
     * - 小范围位移
     */
    fun <T> fastSpatial(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.8f,
        stiffness = 380f
    )

    /**
     * 慢速空间变换：大范围位移、复杂布局（500ms spring）
     *
     * 适用场景：
     * - 页面切换
     * - 抽屉展开/折叠
     * - 灯箱出现
     * - AnimatedVisibility expandVertically
     */
    fun <T> slowSpatial(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 1.0f,
        stiffness = 200f
    )

    /**
     * 快速效果：颜色、透明度（150ms tween）
     *
     * 适用场景：
     * - Hover 状态变化
     * - 颜色切换
     * - 快速淡入淡出
     */
    fun <T> fastEffects(): FiniteAnimationSpec<T> = tween(durationMillis = 150)

    /**
     * 慢速效果：渐变、阴影（300ms tween）
     *
     * 适用场景：
     * - 卡片阴影变化
     * - 背景渐变
     * - 长距离淡入淡出
     */
    fun <T> slowEffects(): FiniteAnimationSpec<T> = tween(durationMillis = 300)

    /**
     * 默认效果：通用场景（250ms tween）
     *
     * 适用场景：
     * - 大多数状态切换
     * - 不确定用哪个时的默认选择
     */
    fun <T> defaultEffects(): FiniteAnimationSpec<T> = tween(durationMillis = 250)

    // 向后兼容的别名
    val ExpandSpring: FiniteAnimationSpec<Float> = slowSpatial()
    val ItemEnterSpring: FiniteAnimationSpec<Float> = fastSpatial()
    val StandardEasing: FiniteAnimationSpec<Float> = defaultEffects()
    val EmphasizedEasing: FiniteAnimationSpec<Float> = slowEffects()

    // 特定场景的 Spring 规格
    /** 温和弹簧：平滑过渡（用于键盘控制等精确操作） */
    val GentleSpring: AnimationSpec<Float> = spring(dampingRatio = 1.0f, stiffness = 300f)

    /** 快速弹簧：跟手响应（用于拖拽、快速交互） */
    val SnappySpring: AnimationSpec<Float> = spring(dampingRatio = 0.7f, stiffness = 450f)

    /** 投掷弹簧：飞出动画（用于 Swipe 卡片飞出） */
    val ThrowSpring: AnimationSpec<Float> = spring(dampingRatio = 0.6f, stiffness = 500f)
}
