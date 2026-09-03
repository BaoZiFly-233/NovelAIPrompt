package com.novelstudio.feature.inpaint

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.novelstudio.core.designsystem.theme.MD3EPillShape
import com.novelstudio.feature.inpaint.InpaintViewModel

/**
 * Skia 原生局部重绘涂抹画板（对应 feature:inpaint）：
 * 在选中图片上方叠加半透明遮罩笔迹，笔刷大小可调，遮罩可清空/导出（导出落盘待 Task 2.4 管道）。
 */
@Composable
fun InpaintScreen(viewModel: InpaintViewModel, modifier: Modifier = Modifier) {
    val records by viewModel.records.collectAsStateWithLifecycle()
    val target = records.firstOrNull()
    var strokes by remember { mutableStateOf(listOf<List<Offset>>()) }
    var brushWidth by remember { mutableStateOf(24f) }
    var message by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("局部重绘", style = MaterialTheme.typography.headlineMedium)

        if (target == null) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("没有可编辑的图片，先在工作台生成或收藏一张", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            val model = if (target.filePath.startsWith("pending")) null else "file://${target.filePath}"
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)),
            ) {
                if (model == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("原图待落盘，暂时只能练习笔刷手感", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    AsyncImage(model = model, contentDescription = "重绘底图", modifier = Modifier.fillMaxSize())
                }

                Canvas(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(brushWidth) {
                            detectDragGestures(
                                onDragStart = { start ->
                                    strokes = strokes + listOf(listOf(start))
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val last = strokes.lastOrNull() ?: return@detectDragGestures
                                    strokes = strokes.dropLast(1) + listOf(last + change.position)
                                },
                            )
                        },
                ) {
                    strokes.forEach { points ->
                        if (points.size == 1) {
                            drawCircle(
                                color = Color(0xFF29B6F6).copy(alpha = 0.55f),
                                radius = brushWidth / 2,
                                center = points.first(),
                            )
                        } else {
                            val path = Path().apply {
                                moveTo(points.first().x, points.first().y)
                                points.drop(1).forEach { lineTo(it.x, it.y) }
                            }
                            drawPath(
                                path = path,
                                color = Color(0xFF29B6F6).copy(alpha = 0.55f),
                                style = Stroke(width = brushWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                            )
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("笔刷 ${brushWidth.toInt()}px", style = MaterialTheme.typography.labelLarge)
                Slider(value = brushWidth, onValueChange = { brushWidth = it }, valueRange = 8f..96f, modifier = Modifier.weight(1f))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { strokes = emptyList() }, shape = MD3EPillShape) { Text("清空遮罩") }
                Button(
                    onClick = {
                        message = if (strokes.isEmpty()) {
                            "遮罩为空"
                        } else {
                            "已暂存 ${strokes.size} 笔遮罩（导出落盘管道将在 Task 2.4 接入）"
                        }
                    },
                    shape = MD3EPillShape,
                ) { Text("导出遮罩") }
                Text(message ?: "", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 12.dp))
            }
        }
    }
}
