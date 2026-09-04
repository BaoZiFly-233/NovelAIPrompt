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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.novelstudio.core.common.platform.localFileModel
import com.novelstudio.core.designsystem.components.StudioSpacing

private const val MAX_UNDO_STEPS = 20
private val MASK_COLOR = Color(0xAAFF6B6B)   // 半透明红，视觉上与底图有区别
private val ERASE_COLOR = Color.Transparent   // 橡皮擦还原为透明

/** 单条笔画：路径 + 画笔半径 + 是否为橡皮擦 */
private data class Stroke(val path: Path, val radius: Float, val erase: Boolean)

/**
 * Inpaint 涂抹画板（MD3E §4-5 规划项）：
 * - 底图渲染 + Skia Path 遮罩叠层
 * - 支持画笔大小 8–128px、橡皮擦模式、最多 20 步撤销
 * - 完成后通过 [onMaskReady] 回传 PNG 字节供 ImageToolsViewModel 提交
 */
@Composable
fun InpaintCanvas(
    imagePath: String,
    imageWidth: Int,
    imageHeight: Int,
    onMaskReady: (ByteArray) -> Unit,
    modifier: Modifier = Modifier,
) {
    var brushRadius by remember { mutableFloatStateOf(24f) }
    var eraseMode by remember { mutableStateOf(false) }
    val strokes = remember { mutableStateOf(listOf<Stroke>()) }
    var currentPath by remember { mutableStateOf<Path?>(null) }

    fun undo() {
        if (strokes.value.isNotEmpty()) strokes.value = strokes.value.dropLast(1)
    }

    fun clear() {
        strokes.value = emptyList()
        currentPath = null
    }

    Column(modifier = modifier) {
        // 工具栏
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = StudioSpacing.Large, vertical = StudioSpacing.Small),
            horizontalArrangement = Arrangement.spacedBy(StudioSpacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("画笔", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = brushRadius,
                onValueChange = { brushRadius = it },
                valueRange = 8f..128f,
                modifier = Modifier.weight(1f),
            )
            Text("${brushRadius.toInt()}px", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            IconButton(
                onClick = { eraseMode = !eraseMode },
                modifier = Modifier.background(
                    if (eraseMode) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    MaterialTheme.shapes.small,
                ),
            ) {
                Text(if (eraseMode) "橡" else "画", style = MaterialTheme.typography.labelMedium)
            }

            IconButton(onClick = ::undo, enabled = strokes.value.isNotEmpty()) {
                Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = "撤销")
            }
            IconButton(onClick = ::clear) {
                Icon(Icons.Rounded.Delete, contentDescription = "清空遮罩")
            }
        }

        // 画板主体
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(brushRadius, eraseMode) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val newPath = Path().apply { moveTo(offset.x, offset.y) }
                            currentPath = newPath
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            currentPath?.lineTo(change.position.x, change.position.y)
                        },
                        onDragEnd = {
                            currentPath?.let { path ->
                                val committed = strokes.value + Stroke(path, brushRadius, eraseMode)
                                strokes.value = committed.takeLast(MAX_UNDO_STEPS)
                            }
                            currentPath = null
                        },
                        onDragCancel = { currentPath = null },
                    )
                },
        ) {
            // 底图
            AsyncImage(
                model = localFileModel(imagePath),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )

            // 遮罩叠层
            Canvas(Modifier.fillMaxSize()) {
                drawMaskStrokes(strokes.value, currentPath, brushRadius, eraseMode)
            }
        }
    }
}

private fun DrawScope.drawMaskStrokes(
    committed: List<Stroke>,
    active: Path?,
    activeBrushRadius: Float,
    activeErase: Boolean,
) {
    for (stroke in committed) {
        drawPath(
            path = stroke.path,
            color = if (stroke.erase) ERASE_COLOR else MASK_COLOR,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = stroke.radius * 2f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
            blendMode = if (stroke.erase) BlendMode.Clear else BlendMode.SrcOver,
        )
    }
    active?.let { path ->
        drawPath(
            path = path,
            color = if (activeErase) ERASE_COLOR else MASK_COLOR,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = activeBrushRadius * 2f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
            blendMode = if (activeErase) BlendMode.Clear else BlendMode.SrcOver,
        )
    }
}

/** 将遮罩渲染为 PNG 字节（expect/actual 由平台实现，Android 用 Bitmap.compress，Desktop 用 Skia EncodeImage）。 */
internal expect fun renderMaskToPng(
    strokes: List<Stroke>,
    width: Int,
    height: Int,
): ByteArray
