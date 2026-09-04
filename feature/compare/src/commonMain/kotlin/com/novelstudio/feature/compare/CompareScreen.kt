package com.novelstudio.feature.compare

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
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
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.novelstudio.core.common.platform.localFileModel
import com.novelstudio.core.designsystem.theme.expressiveSlowSpatialSpec
import com.novelstudio.core.designsystem.components.StudioEmptyState
import com.novelstudio.core.designsystem.components.StudioIcons
import com.novelstudio.core.designsystem.components.StudioPageHeader
import com.novelstudio.core.designsystem.components.StudioSection
import com.novelstudio.core.designsystem.components.StudioStatusChip
import com.novelstudio.core.designsystem.motion.MD3EMotion
import com.novelstudio.core.designsystem.theme.MD3EPillShape
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 对比实验室（MD3E 交互标准 §4-3）：
 * 基于 Skia clipRect 的卷帘分屏比对，中央悬浮药丸手柄拖拽分割线。
 */
@Composable
fun CompareScreen(viewModel: CompareViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val left = state.records.getOrNull(0)
    val right = state.records.getOrNull(1)
    var sideBySide by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        StudioPageHeader(
            title = "对比实验室",
            description = "并排或卷帘查看两张作品的构图、细节与风格差异。",
            actions = {
            if (state.requestedIds.isNotEmpty()) {
                TextButton(onClick = viewModel::clearSelection) {
                    androidx.compose.material3.Icon(Icons.Rounded.Clear, contentDescription = null)
                    androidx.compose.foundation.layout.Spacer(Modifier.width(6.dp))
                    Text("清空选择")
                }
            }
            },
        )

        if (state.requestedIds.isEmpty()) {
            StudioEmptyState(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                icon = StudioIcons.CompareArrows,
                title = "还没有对比素材",
                description = "请在图库选择 1～2 张图片并加入对比。",
            )
        } else {
            if (state.missingIds.isNotEmpty()) {
                StudioStatusChip(
                    text = "${state.missingIds.size} 张记录已不存在",
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                )
            }

            if (left != null && right != null) {
                StudioSection(title = "查看方式", description = "拖动卷帘分割线，或同步平移两张图片。") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !sideBySide, onClick = { sideBySide = false }, label = { Text("卷帘") })
                        FilterChip(selected = sideBySide, onClick = { sideBySide = true }, label = { Text("并排联动") })
                    }
                }
                StudioSection(title = "对比画布", modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (sideBySide) SideBySideViewer(left.filePath, right.filePath, Modifier.fillMaxSize())
                    else SplitSliderViewer(left.filePath, right.filePath, Modifier.fillMaxSize())
                }
            } else if (left != null) {
                Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    val model = left.filePath
                        .takeIf { it.isNotBlank() && !it.startsWith("pending") }
                        ?.let(::localFileModel)
                    if (model == null) {
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text("原图文件待落盘或不可用", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        AsyncImage(
                            model = model,
                            contentDescription = left.prompt,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentScale = ContentScale.Fit,
                        )
                    }
                    Text("已加入 1 张；再从图库选择两张可开始卷帘对比", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("所选图片不可用，请返回图库重新选择", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** 卷帘分屏视图：左图全幅绘制，右图以 clipRect 裁剪至分割线右侧 */
@Composable
fun SplitSliderViewer(leftPath: String, rightPath: String, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier) {
        var split by remember(leftPath, rightPath) { mutableStateOf(0.5f) }
        val totalWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val scope = rememberCoroutineScope()
        val splitAnimatable = remember(leftPath, rightPath) { androidx.compose.animation.core.Animatable(0.5f) }
        // 同步 animatable 到 split，用于键盘驱动动画
        LaunchedEffect(splitAnimatable.value) { split = splitAnimatable.value }

        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    if (event.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        androidx.compose.ui.input.key.Key.DirectionLeft -> {
                            scope.launch { splitAnimatable.animateTo((split - 0.02f).coerceIn(0.02f, 0.98f), MD3EMotion.GentleSpring) }
                            true
                        }
                        androidx.compose.ui.input.key.Key.DirectionRight -> {
                            scope.launch { splitAnimatable.animateTo((split + 0.02f).coerceIn(0.02f, 0.98f), MD3EMotion.GentleSpring) }
                            true
                        }
                        else -> false
                    }
                }
                .focusable(),
        ) {
            val leftModel = leftPath.takeIf { it.isNotBlank() && !it.startsWith("pending") }?.let(::localFileModel)
            val rightModel = rightPath.takeIf { it.isNotBlank() && !it.startsWith("pending") }?.let(::localFileModel)

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
                        clipRect(left = size.width * split) { this@drawWithContent.drawContent() }
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
                    .offset { IntOffset((totalWidthPx * split).roundToInt() - 12.dp.roundToPx(), 0) }
                    .fillMaxHeight()
                    .width(24.dp)
                    .pointerInput(totalWidthPx) {
                        detectHorizontalDragGestures { change, dragAmount ->
                            change.consume()
                            split = splitFromDrag(split, dragAmount, totalWidthPx)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
                )
                Box(
                    Modifier
                        .width(36.dp)
                        .height(24.dp)
                        .background(MaterialTheme.colorScheme.surface, MD3EPillShape),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.Icon(
                        StudioIcons.DragHandle,
                        contentDescription = "拖动分割线",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            // 分割线百分比标注
            Text(
                text = "Split ${(split * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp),
            )
        }
    }
}

/** 并排对比：两张图共享同一缩放/平移手势，双击在 1x 与 2.5x 间切换。 */
@Composable
fun SideBySideViewer(leftPath: String, rightPath: String, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier.clipToBounds()) {
        var scale by remember(leftPath, rightPath) { mutableFloatStateOf(1f) }
        var offsetX by remember(leftPath, rightPath) { mutableFloatStateOf(0f) }
        var offsetY by remember(leftPath, rightPath) { mutableFloatStateOf(0f) }
        var settleJob by remember(leftPath, rightPath) { mutableStateOf<Job?>(null) }
        val scope = rememberCoroutineScope()
        val settleSpec: AnimationSpec<Float> = expressiveSlowSpatialSpec()
        val paneWidthPx = (constraints.maxWidth / 2f).coerceAtLeast(1f)
        val heightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)

        fun settle(targetScale: Float) {
            val safeTarget = clampCompareScale(targetScale)
            val startScale = scale
            val startX = offsetX
            val startY = offsetY
            val targetX = clampCompareOffset(startX, safeTarget, paneWidthPx)
            val targetY = clampCompareOffset(startY, safeTarget, heightPx)
            settleJob?.cancel()
            settleJob = scope.launch {
                animate(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = settleSpec,
                ) { progress, _ ->
                    scale = clampCompareScale(startScale + (safeTarget - startScale) * progress)
                    offsetX = clampCompareOffset(startX + (targetX - startX) * progress, scale, paneWidthPx)
                    offsetY = clampCompareOffset(startY + (targetY - startY) * progress, scale, heightPx)
                }
                scale = safeTarget
                offsetX = targetX
                offsetY = targetY
            }
        }

        LaunchedEffect(paneWidthPx, heightPx) {
            offsetX = clampCompareOffset(offsetX, scale, paneWidthPx)
            offsetY = clampCompareOffset(offsetY, scale, heightPx)
        }
        val transformState = rememberTransformableState { _, zoomChange, panChange, _ ->
            if (zoomChange.isFinite() && zoomChange > 0f) {
                settleJob?.cancel()
                val nextScale = clampCompareScale(scale * zoomChange)
                val ratio = nextScale / scale
                scale = nextScale
                // 两图共享同一 pane 坐标；边界按各自 pane，而不是整行宽度计算。
                offsetX = clampCompareOffset(offsetX * ratio + panChange.x, nextScale, paneWidthPx)
                offsetY = clampCompareOffset(offsetY * ratio + panChange.y, nextScale, heightPx)
            }
        }
        val gesture = Modifier
            .pointerInput(paneWidthPx, heightPx) {
                detectTapGestures(onDoubleTap = { settle(nextCompareDoubleTapScale(scale)) })
            }
            .transformable(transformState)
        Row(gesture.fillMaxSize()) {
            CompareImage(leftPath, "左图", Modifier.weight(1f).fillMaxHeight(), scale, offsetX, offsetY)
            CompareImage(rightPath, "右图", Modifier.weight(1f).fillMaxHeight(), scale, offsetX, offsetY)
        }
    }
}

@Composable
private fun CompareImage(path: String, description: String, modifier: Modifier, scale: Float, x: Float, y: Float) {
    val model = path.takeIf { it.isNotBlank() && !it.startsWith("pending") }?.let(::localFileModel)
    AsyncImage(model = model, contentDescription = description, contentScale = ContentScale.Fit,
        modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale; translationX = x; translationY = y })
}

internal fun clampCompareScale(value: Float): Float =
    if (value.isFinite()) value.coerceIn(1f, 5f) else 1f

internal fun clampCompareOffset(value: Float, scale: Float, viewport: Float): Float {
    if (!value.isFinite() || !viewport.isFinite() || viewport <= 0f) return 0f
    val maxOffset = (clampCompareScale(scale) - 1f) * viewport / 2f
    return value.coerceIn(-maxOffset, maxOffset)
}

internal fun splitFromDrag(split: Float, dragPx: Float, widthPx: Float): Float =
    if (!split.isFinite() || !dragPx.isFinite() || !widthPx.isFinite() || widthPx <= 0f) {
        0.5f
    } else {
        (split + dragPx / widthPx).coerceIn(0.02f, 0.98f)
    }

internal fun nextCompareDoubleTapScale(currentScale: Float): Float =
    if (clampCompareScale(currentScale) > 1.1f) 1f else 2.5f
