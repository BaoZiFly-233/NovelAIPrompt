package com.novelstudio.core.designsystem.motion

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/** 设计系统动效预设：三档弹簧 + 两档 tween，业务模块直接引用，避免散落的魔法数字。 */
object MD3EMotion {
    /** 卡片拖拽、卷帘比对——跟手优先，欠阻尼略有弹性。 */
    val SnappySpring = spring<Float>(dampingRatio = 0.75f, stiffness = 400f)

    /** 页面切换、抽屉展开——临界阻尼，无过冲，柔和落定。 */
    val GentleSpring = spring<Float>(dampingRatio = 1.0f, stiffness = 200f)

    /** Swipe 卡片飞出抛掷——高刚度欠阻尼，快速加速离屏。 */
    val ThrowSpring = spring<Float>(dampingRatio = 0.6f, stiffness = 800f)

    /** 常规状态切换：180ms，标准减速曲线。 */
    val StandardEasing = tween<Float>(durationMillis = 180, easing = FastOutSlowInEasing)

    /** 强调性进入动效：250ms，线性减速曲线，用于从手势触发的展开。 */
    val EmphasizedEasing = tween<Float>(durationMillis = 250, easing = LinearOutSlowInEasing)

    /** 折叠/展开区块动效：GentleSpring 弹性，用于 AnimatedVisibility expandVertically。 */
    val ExpandSpring = spring<Float>(dampingRatio = 1.0f, stiffness = 200f)

    /** 列表项进入动效：轻微欠阻尼，营造跟随感。 */
    val ItemEnterSpring = spring<Float>(dampingRatio = 0.85f, stiffness = 300f)
}
