package com.novelstudio.core.designsystem.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CallSplit
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Queue
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.Warning

/** 语义化图标常量，避免 Icons.Rounded.Star 在多个不同语义场景被滥用。 */
object StudioIcons {
    /** 品牌标识：NavigationRail header、PreviewStage 空态。 */
    val Brand = Icons.Rounded.AutoAwesome

    /** 生成操作：生成按钮、NavigationRail FAB。 */
    val Generate = Icons.Rounded.Bolt

    /** 回填操作：灯箱"回填到工作台"。 */
    val Fork = Icons.Rounded.CallSplit

    /** 通知/消息：Notice 组件信息图标。 */
    val Notice = Icons.Rounded.Info

    /** 警告语义：超出额度、参数越界。 */
    val Warning = Icons.Rounded.Warning

    /** 成功语义：保存成功、操作完成。 */
    val Success = Icons.Rounded.CheckCircle

    /** 错误语义：操作失败、网络错误。 */
    val Error = Icons.Rounded.Error

    /** 生成队列图标：GenerationQueuePanel 空态。 */
    val Queue = Icons.Rounded.Queue

    /** Workbench 一级目的地导航图标（替换重复的 Star）。 */
    val Workbench = Icons.Rounded.Palette

    /** Tags 一级目的地导航图标（替换重复的 Star）。 */
    val Tags = Icons.Rounded.Tag

    /** 拖拽手柄：CompareScreen 分割线手柄（替换语义错误的 MoreVert）。 */
    val DragHandle = Icons.Rounded.DragHandle

    /** 撤销操作：SwipeScreen 撤销上一张。 */
    val Undo = Icons.AutoMirrored.Rounded.Undo
}
