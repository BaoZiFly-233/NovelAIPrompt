package com.novelstudio.feature.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.novelstudio.core.database.ImageEntity
import com.novelstudio.core.designsystem.theme.MD3EPillShape
import com.novelstudio.core.model.ImageRecord

/**
 * 万级虚拟瀑布流图库（MD3E 交互标准 §4-2）：
 * 自适应多列 StaggeredGrid、Seed/Model 渐变徽标、收藏星标胶囊、灯箱与一键回填。
 */
@Composable
fun GalleryScreen(viewModel: GalleryViewModel, modifier: Modifier = Modifier) {
    val records by viewModel.records.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<ImageEntity?>(null) }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text("万级图库", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(vertical = 16.dp))
        if (records.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "暂无图片记录\n生成或导入 PNG 后自动出现在这里",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        } else {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Adaptive(180.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalItemSpacing = 12.dp,
                modifier = Modifier.fillMaxSize(),
            ) {
                items(records, key = { it.id }) { record ->
                    GalleryCard(
                        record = record,
                        onClick = { selected = record },
                        onToggleStar = { viewModel.toggleLike(record) },
                    )
                }
            }
        }
    }

    selected?.let { record ->
        LightboxDialog(
            record = record,
            onDismiss = { selected = null },
            onFork = {
                viewModel.forkToWorkbench(record)
                selected = null
            },
            onToggleStar = { viewModel.toggleLike(record) },
            onDelete = {
                viewModel.delete(record)
                selected = null
            },
        )
    }
}

/** 图库卡片：统一圆角 16dp，顶部渐变遮罩徽标，右下收藏胶囊 */
@Composable
private fun GalleryCard(record: ImageEntity, onClick: () -> Unit, onToggleStar: () -> Unit) {
    val liked = record.starRating >= ImageRecord.STAR_LIKE
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)),
    ) {
        val model = if (record.filePath.startsWith("pending")) null else "file://${record.filePath}"
        if (model == null) {
            Box(
                Modifier.fillMaxWidth().height(record.height.dp.coerceIn(120.dp, 360.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("待落盘\n${record.prompt.take(40)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            coil3.compose.AsyncImage(
                model = model,
                contentDescription = record.prompt,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(
                    Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)),
                ),
        )
        Row(
            Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Seed ${record.seed}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f))
            Text(
                record.model.removePrefix("nai-diffusion-"),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
            )
        }
        Box(Modifier.align(Alignment.BottomEnd).padding(8.dp)) {
            TextButton(
                onClick = onToggleStar,
                shape = MD3EPillShape,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Text(if (liked) "★ 喜欢" else "☆ 收藏", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** 灯箱：大图 + PNG Info 侧栏 + 「一键回填到工作台」 */
@Composable
private fun LightboxDialog(
    record: ImageEntity,
    onDismiss: () -> Unit,
    onFork: () -> Unit,
    onToggleStar: () -> Unit,
    onDelete: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
        ) {
            Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                val model = if (record.filePath.startsWith("pending")) null else "file://${record.filePath}"
                if (model == null) {
                    Text("原图尚未落盘（生成队列文件管道建设中）", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    AsyncImage(model = model, contentDescription = record.prompt, modifier = Modifier.fillMaxSize())
                }
            }
            Spacer(Modifier.width(24.dp))
            Column(
                Modifier.width(320.dp).fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("PNG Info", style = MaterialTheme.typography.headlineSmall)
                MetadataRow("Prompt", record.prompt)
                MetadataRow("Negative", record.uc)
                MetadataRow("Model", record.model)
                MetadataRow("Seed", record.seed.toString())
                MetadataRow("Steps", record.steps.toString())
                MetadataRow("Scale", record.scale.toString())
                MetadataRow("Sampler", record.sampler)
                MetadataRow("尺寸", "${record.width} × ${record.height}")
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onFork, shape = MD3EPillShape) { Text("回填到工作台") }
                    TextButton(onClick = onToggleStar) { Text("★ 打标") }
                }
                TextButton(onClick = onDelete) {
                    Text("删除记录", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
    }
}
