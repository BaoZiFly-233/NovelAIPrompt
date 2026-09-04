package com.novelstudio.feature.workbench

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.novelstudio.core.data.GenerationQueueItem
import com.novelstudio.core.model.TaskStatus
import com.novelstudio.core.designsystem.components.StudioEmptyState
import com.novelstudio.core.designsystem.components.StudioSection
import com.novelstudio.core.designsystem.components.StudioIcons

/** 持久化串行队列概览；运行中取消只停止本地等待，不代表服务端撤销。 */
@Composable
internal fun GenerationQueuePanel(
    state: WorkbenchUiState,
    onCancel: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    StudioSection("生成队列", modifier = modifier,
        description = "任务会按加入顺序逐个提交。") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "待运行 ${state.queuedTaskCount} · 待确认 ${state.waitingAnlasCount}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.queueItems.isEmpty()) {
                StudioEmptyState(
                    icon = StudioIcons.Queue,
                    title = "队列为空",
                    description = "可连续加入多个参数快照，队列会逐个提交。",
                )
            } else {
                state.queueItems.asReversed().take(MAX_VISIBLE_TASKS).forEachIndexed { index, item ->
                    if (index > 0) HorizontalDivider()
                    QueueRow(item = item, onCancel = onCancel)
                }
            }

            if (state.queueItems.any { it.status == TaskStatus.FAILED_UNKNOWN }) {
                Text(
                    "“结果未知”任务不会自动重试，请先到 NovelAI 账户或本地图库核对。",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun QueueRow(item: GenerationQueueItem, onCancel: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (item.isSnapshotCorrupted) {
                    "任务参数快照损坏"
                } else {
                    item.parameters.prompt.ifBlank { "（空提示词）" }.take(PROMPT_PREVIEW_LENGTH)
                },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
            )
            if (!item.isSnapshotCorrupted) {
                Text(
                    "${item.parameters.model.displayName} · ${item.parameters.width}×${item.parameters.height}" +
                        " · ${item.parameters.nSamples} 张",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item.errorMessage?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                item.status.displayLabel,
                style = MaterialTheme.typography.labelLarge,
                color = if (item.status in ERROR_STATUSES) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
            if (item.status in CANCELLABLE_STATUSES) {
                TextButton(onClick = { onCancel(item.id) }) {
                    Text(if (item.status == TaskStatus.RUNNING) "停止等待" else "取消")
                }
            }
        }
    }
}

private val TaskStatus.displayLabel: String
    get() = when (this) {
        TaskStatus.QUEUED -> "排队中"
        TaskStatus.WAITING_ANLAS_CONFIRMATION -> "等待 Anlas 确认"
        TaskStatus.RUNNING -> "生成中"
        TaskStatus.SUCCEEDED -> "已完成"
        TaskStatus.FAILED -> "失败"
        TaskStatus.FAILED_UNKNOWN -> "结果未知"
        TaskStatus.CANCELLED -> "已取消"
    }

private val CANCELLABLE_STATUSES = setOf(
    TaskStatus.QUEUED,
    TaskStatus.WAITING_ANLAS_CONFIRMATION,
    TaskStatus.RUNNING,
)
private val ERROR_STATUSES = setOf(TaskStatus.FAILED, TaskStatus.FAILED_UNKNOWN)
private const val MAX_VISIBLE_TASKS = 6
private const val PROMPT_PREVIEW_LENGTH = 48
