package com.novelstudio.feature.swipe

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.novelstudio.core.common.platform.localFileModel
import com.novelstudio.core.model.ImageRecord
import com.novelstudio.core.designsystem.components.StudioEmptyState
import com.novelstudio.core.designsystem.components.StudioIcons
import com.novelstudio.core.designsystem.components.StudioPageHeader
import com.novelstudio.core.designsystem.components.StudioSection
import com.novelstudio.core.designsystem.components.StudioStatusChip
import com.novelstudio.core.designsystem.motion.MD3EMotion
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.sin

/**
 * 滑动喜欢/不喜欢卡片流（MD3E 交互标准 §4-4）：
 * 左滑 -15° 红色遮罩 = 不喜欢；右滑 +15° 青绿遮罩 = 喜欢；
 * PC 端快捷键 ← 不喜欢，→ 喜欢。
 */
@Composable
fun SwipeScreen(viewModel: SwipeViewModel, modifier: Modifier = Modifier) {
    val deck by viewModel.deck.collectAsStateWithLifecycle()
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val top = deck.top
    val scope = rememberCoroutineScope()

    // 顶层 offsetX 供底部按钮触发飞出动画使用
    val topCardOffsetX = remember(top?.id) { Animatable(0f) }
    var topCardFlying by remember(top?.id) { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        StudioPageHeader(
            title = "滑动筛选",
            description = "快速整理生成结果：右滑保留，左滑略过。桌面端可使用 ← / → 快捷键。",
            actions = {
                if (canUndo) {
                    IconButton(onClick = { viewModel.undoLast() }) {
                        Icon(StudioIcons.Undo, contentDescription = "撤销上一张")
                    }
                }
                StudioStatusChip(text = "待处理 ${deck.remainingCount} 张")
            },
        )

        StudioSection(
            title = "当前卡组",
            description = "拖动卡片或使用键盘完成判断",
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            BoxWithConstraints(
                Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                if (top == null) {
                    StudioEmptyState(
                        icon = StudioIcons.Brand,
                        title = "卡组已清空",
                        description = "没有待筛选的图片了，回到工作台继续生成。",
                    )
                } else {
                    // 叠牌预览：下方最多两张背景卡
                    if (deck.remainingCount >= 3) {
                        Box(
                            Modifier
                                .fillMaxSize(0.9f)
                                .offset(y = 16.dp)
                                .graphicsLayer { scaleX = 0.90f; scaleY = 0.90f; alpha = 0.5f }
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.shapes.large)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large),
                        )
                    }
                    if (deck.remainingCount >= 2) {
                        Box(
                            Modifier
                                .fillMaxSize(0.9f)
                                .offset(y = 8.dp)
                                .graphicsLayer { scaleX = 0.95f; scaleY = 0.95f; alpha = 0.7f }
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.shapes.large)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large),
                        )
                    }
                    SwipeCard(
                        record = top,
                        externalOffsetX = topCardOffsetX,
                        externalFlying = topCardFlying,
                        onFlyingChanged = { topCardFlying = it },
                        modifier = Modifier.fillMaxSize(0.9f),
                        onSwipedRight = { viewModel.swipeLike(top) },
                        onSwipedLeft = { viewModel.swipeDislike(top) },
                    )
                }
            }
        }
        if (top != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FilledTonalButton(
                    onClick = {
                        if (topCardFlying) return@FilledTonalButton
                        topCardFlying = true
                        scope.launch {
                            topCardOffsetX.animateTo(-2400f, MD3EMotion.ThrowSpring)
                            viewModel.swipeDislike(top)
                            topCardOffsetX.snapTo(0f)
                            topCardFlying = false
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("不喜欢 · 移入垃圾箱")
                }
                Button(
                    onClick = {
                        if (topCardFlying) return@Button
                        topCardFlying = true
                        scope.launch {
                            topCardOffsetX.animateTo(2400f, MD3EMotion.ThrowSpring)
                            viewModel.swipeLike(top)
                            topCardOffsetX.snapTo(0f)
                            topCardFlying = false
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("喜欢 · 归档")
                }
            }
        }
    }
}

@Composable
private fun SwipeCard(
    record: ImageRecord,
    modifier: Modifier = Modifier,
    externalOffsetX: Animatable<Float, *>? = null,
    externalFlying: Boolean = false,
    onFlyingChanged: ((Boolean) -> Unit)? = null,
    onSwipedRight: () -> Unit,
    onSwipedLeft: () -> Unit,
) {
    val internalOffsetX = remember(record.id) { Animatable(0f) }
    val offsetX = externalOffsetX ?: internalOffsetX
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val throwSpec = MD3EMotion.ThrowSpring
    val snappySpec = MD3EMotion.SnappySpring
    val particleColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f)
    var internalFlying by remember(record.id) { mutableStateOf(false) }
    val flying = externalFlying || internalFlying
    var dragOffset by remember(record.id) { mutableStateOf(0f) }

    fun setFlying(value: Boolean) {
        internalFlying = value
        onFlyingChanged?.invoke(value)
    }

    // PC 快捷键：← 不喜欢，→ 喜欢
    LaunchedEffect(record.id) { focusRequester.requestFocus() }

    Box(
        modifier = modifier
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || flying) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionRight -> {
                        setFlying(true)
                        scope.launch {
                            offsetX.animateTo(2400f, throwSpec)
                            onSwipedRight()
                            offsetX.snapTo(0f)
                            setFlying(false)
                        }
                        true
                    }
                    Key.DirectionLeft -> {
                        setFlying(true)
                        scope.launch {
                            offsetX.animateTo(-2400f, throwSpec)
                            onSwipedLeft()
                            offsetX.snapTo(0f)
                            setFlying(false)
                        }
                        true
                    }
                    else -> false
                }
            }
            .focusRequester(focusRequester)
            .focusable()
            .graphicsLayer {
                translationX = offsetX.value + dragOffset
                rotationZ = ((offsetX.value + dragOffset) / 1200f) * 15f
            }
            .pointerInput(record.id) {
                detectDragGestures(
                    onDragEnd = {
                        if (flying) return@detectDragGestures
                        val dragged = offsetX.value + dragOffset
                        dragOffset = 0f
                        if (swipeDecision(dragged, size.width.toFloat()) == SwipeDecision.NONE) {
                            scope.launch { offsetX.animateTo(0f, snappySpec) }
                            return@detectDragGestures
                        }
                        setFlying(true)
                        scope.launch {
                            offsetX.animateTo(sign(dragged) * 2400f, throwSpec)
                            if (dragged > 0) onSwipedRight() else onSwipedLeft()
                            offsetX.snapTo(0f)
                            setFlying(false)
                        }
                    },
                ) { change, dragAmount ->
                    change.consume()
                    if (!flying) dragOffset += dragAmount.x
                }            }
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.shapes.large)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large),
        contentAlignment = Alignment.Center,
    ) {
        val model = record.filePath.takeIf { it.isNotBlank() && !it.startsWith("pending") }?.let(::localFileModel)
        if (model == null) {
            Text("待落盘 ${record.seed}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            AsyncImage(model = model, contentDescription = record.prompt, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }

        val ratio = swipeProgress(offsetX.value + dragOffset, 600f)
        Canvas(Modifier.fillMaxSize()) {
            if (flying) repeat(8) { index ->
                drawCircle(
                    particleColor, 5.dp.toPx(),
                    androidx.compose.ui.geometry.Offset(
                        size.width / 2f + sign(offsetX.value) * (size.width * 0.28f + index * 9f),
                        size.height * 0.45f + sin(index * 0.78f) * 70f,
                    ),
                )
            }
        }
        if (ratio < 0f) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.error.copy(alpha = -ratio * 0.35f), MaterialTheme.shapes.large))
        } else if (ratio > 0f) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.tertiary.copy(alpha = ratio * 0.35f), MaterialTheme.shapes.large))
        }

        Text(
            text = if (ratio < -0.3f) "不喜欢" else if (ratio > 0.3f) "喜欢" else record.prompt.take(60),
            style = MaterialTheme.typography.titleMedium,
            color = if (abs(ratio) > 0.3f) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
        )
    }
}
