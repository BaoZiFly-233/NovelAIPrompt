package com.novelstudio.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

object StudioSpacing {
    val XSmall = 4.dp
    val Small = 8.dp
    val Medium = 12.dp
    val Large = 16.dp
    val XLarge = 24.dp
    val XXLarge = 32.dp
    val PageHorizontal = 24.dp
}

@Composable
fun StudioPageHeader(
    title: String,
    description: String = "",
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(StudioSpacing.Small)) {
            eyebrow?.let { Text(it.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
            Text(title, style = MaterialTheme.typography.headlineSmall)
            if (description.isNotBlank()) {
                Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(StudioSpacing.Small), content = actions)
    }
}

@Composable
fun StudioSection(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    ) {
        Column(
            modifier = Modifier.padding(StudioSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(StudioSpacing.Medium),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    description?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(content = actions)
            }
            content()
        }
    }
}

/** 语义化状态 Chip，通过 [ChipSemantic] 自动选取对应颜色和图标。
 *  旧的 containerColor/contentColor 重载保留以兼容现有调用点。 */
@Composable
fun StudioStatusChip(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color? = null,
    contentColor: Color? = null,
    semantic: ChipSemantic = ChipSemantic.INFO,
    icon: ImageVector? = null,
) {
    val resolvedContainer = containerColor ?: semantic.containerColor()
    val resolvedContent = contentColor ?: semantic.contentColor()
    Surface(
        color = resolvedContainer,
        contentColor = resolvedContent,
        shape = MaterialTheme.shapes.small,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = StudioSpacing.Medium, vertical = StudioSpacing.XSmall),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(StudioSpacing.XSmall),
        ) {
            val resolvedIcon = icon ?: if (containerColor == null) semantic.defaultIcon() else null
            resolvedIcon?.let {
                Icon(it, contentDescription = null, modifier = Modifier.size(14.dp))
            }
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

enum class ChipSemantic {
    INFO, SUCCESS, WARNING, ERROR;

    @Composable
    fun containerColor(): Color = when (this) {
        INFO -> MaterialTheme.colorScheme.primaryContainer
        SUCCESS -> MaterialTheme.colorScheme.secondaryContainer
        WARNING -> MaterialTheme.colorScheme.tertiaryContainer
        ERROR -> MaterialTheme.colorScheme.errorContainer
    }

    @Composable
    fun contentColor(): Color = when (this) {
        INFO -> MaterialTheme.colorScheme.onPrimaryContainer
        SUCCESS -> MaterialTheme.colorScheme.onSecondaryContainer
        WARNING -> MaterialTheme.colorScheme.onTertiaryContainer
        ERROR -> MaterialTheme.colorScheme.onErrorContainer
    }

    fun defaultIcon(): ImageVector? = when (this) {
        INFO -> StudioIcons.Notice
        SUCCESS -> StudioIcons.Success
        WARNING -> StudioIcons.Warning
        ERROR -> StudioIcons.Error
    }
}

@Composable
fun StudioEmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        modifier.fillMaxWidth().padding(StudioSpacing.XXLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(StudioSpacing.Medium),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        actionLabel?.let { Button(onClick = onAction) { Text(it) } }
    }
}

/** 骨架屏占位块，与目标组件形状一致，用于首次加载的视觉稳定性。 */
@Composable
fun StudioSkeleton(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
) {
    Surface(
        modifier = modifier.clip(shape),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
        shape = shape,
    ) {}
}

/** 统一确认对话框，封装主要操作 + 取消的两选一场景。
 *  [confirmIsDestructive] 为 true 时确认按钮使用 error 色。 */
@Composable
fun StudioConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = "取消",
    confirmIsDestructive: Boolean = false,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
        ) {
            Column(
                modifier = Modifier.padding(StudioSpacing.XLarge),
                verticalArrangement = Arrangement.spacedBy(StudioSpacing.Large),
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(StudioSpacing.Small, Alignment.End),
                ) {
                    TextButton(onClick = onDismiss) { Text(dismissLabel) }
                    if (confirmIsDestructive) {
                        Button(
                            onClick = onConfirm,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                        ) { Text(confirmLabel) }
                    } else {
                        Button(onClick = onConfirm) { Text(confirmLabel) }
                    }
                }
            }
        }
    }
}

/** 统一参数 Slider，含左右端点标注和右侧数值 Chip，
 *  替代各界面分散的私有 ParameterSlider 以消除重复。 */
@Composable
fun StudioParameterSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    displayValue: String = "%.2f".format(value),
    minLabel: String? = null,
    maxLabel: String? = null,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            StudioStatusChip(text = displayValue)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth(),
        )
        if (minLabel != null || maxLabel != null) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(minLabel.orEmpty(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(maxLabel.orEmpty(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** 右键上下文菜单容器，在 [content] 的基础上叠加 DropdownMenu。
 *  调用方负责管理 [expanded] 状态和打开触发（鼠标右键/长按）。 */
@Composable
fun StudioContextMenuContainer(
    items: List<StudioContextMenuItem>,
    expanded: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier) {
        content()
        DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = {
                        Text(
                            item.label,
                            color = if (item.isDestructive) MaterialTheme.colorScheme.error else Color.Unspecified,
                        )
                    },
                    leadingIcon = item.icon?.let { icon ->
                        {
                            Icon(
                                icon,
                                contentDescription = null,
                                tint = if (item.isDestructive) MaterialTheme.colorScheme.error
                                       else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    },
                    onClick = { onDismiss(); item.onClick() },
                )
            }
        }
    }
}

data class StudioContextMenuItem(
    val label: String,
    val icon: ImageVector? = null,
    val onClick: () -> Unit,
    val isDestructive: Boolean = false,
)
