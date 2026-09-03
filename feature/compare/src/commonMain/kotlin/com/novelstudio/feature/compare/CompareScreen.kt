package com.novelstudio.feature.compare

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.novelstudio.core.database.ImageEntity
import com.novelstudio.core.designsystem.theme.MD3EPillShape
import kotlin.math.roundToInt

/**
 * 对比实验室（MD3E 交互标准 §4-3）：
 * 基于 Skia clipRect 的卷帘分屏比对，中央悬浮药丸手柄拖拽分割线。
 */
@Composable
fun CompareScreen(viewModel: CompareViewModel, modifier: Modifier = Modifier) {
    val records by viewModel.records.collectAsStateWithLifecycle()
    var left by remember { mutableStateOf<ImageEntity?>(null) }
    var right by remember { mutableStateOf<ImageEntity?>(null) }

    Column(modifier = modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("对比实验室", style = MaterialTheme.typography.headlineMedium)

        if (records.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("图库中还没有图片，先去工作台生成几张吧", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Text("左图", style = MaterialTheme.typography.titleSmall)
            RecordPicker(records = records, selected = left, onSelect = { left = it })
            Text("右图", style = MaterialTheme.typography.titleSmall)
            RecordPicker(records = records, selected = right, onSelect = { right = it })

            if (left != null && right != null) {
                SplitSliderViewer(
                    leftPath = left!!.filePath,
                    rightPath = right!!.filePath,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            } else {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("选择左右两张图片开始卷帘比对", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun RecordPicker(records: List<ImageEntity>, selected: ImageEntity?, onSelect: (ImageEntity) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        records.take(8).forEach { record ->
            FilterChip(
                selected = selected?.id == record.id,
                onClick = { onSelect(record) },
                label = { Text("Seed ${record.seed}", style = MaterialTheme.typography.labelSmall) },
                shape = MD3EPillShape,
            )
        }
    }
}

/** 卷帘分屏视图：左图全幅绘制，右图以 clipRect 裁剪至分割线右侧 */
@Composable
fun SplitSliderViewer(leftPath: String, rightPath: String, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier) {
        var split by remember { mutableStateOf(0.5f) }
        val totalWidth = maxWidth

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val leftModel = if (leftPath.startsWith("pending")) null else "file://$leftPath"
            val rightModel = if (rightPath.startsWith("pending")) null else "file://$rightPath"

            if (leftModel != null) {
                AsyncImage(model = leftModel, contentDescription = "左图", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            } else {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                    Text("左图待落盘")
                }
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        clipRect(right = size.width * split) { this@drawWithContent.drawContent() }
                    },
            ) {
                if (rightModel != null) {
                    AsyncImage(model = rightModel, contentDescription = "右图", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                } else {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                        Text("右图待落盘")
                    }
                }
            }

            // 分割线 + 悬浮药丸手柄
            Box(
                Modifier
                    .offset { IntOffset((maxWidth.toPx() * split).roundToInt() - 12.dp.roundToPx(), 0) }
                    .fillMaxHeight()
                    .width(24.dp)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { change, dragAmount ->
                            change.consume()
                            split = (split + dragAmount / size.width).coerceIn(0.02f, 0.98f)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(Color.White.copy(alpha = 0.9f)),
                )
                Box(
                    Modifier
                        .width(36.dp)
                        .height(24.dp)
                        .background(Color.White, MD3EPillShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("⇔", color = Color.Black, style = MaterialTheme.typography.labelMedium)
                }
            }

            // 分割线百分比标注
            Text(
                text = "Split ${(split * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp)
                    .offset { IntOffset(((split - 0.5f) * totalWidth.toPx() * 0f).roundToInt(), 0) },
            )
        }
    }
}
