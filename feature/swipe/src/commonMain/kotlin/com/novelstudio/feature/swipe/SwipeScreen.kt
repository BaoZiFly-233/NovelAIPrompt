package com.novelstudio.feature.swipe

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.novelstudio.core.model.ImageRecord
import com.novelstudio.core.designsystem.motion.MD3EMotion
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/**
 * 滑动喜欢/不喜欢卡片流（MD3E 交互标准 §4-4）：
 * 左滑 -15° 红色遮罩 = 不喜欢；右滑 +15° 青绿遮罩 = 喜欢；
 * PC 端快捷键 ← 不喜欢，→ 喜欢。
 */
@Composable
fun SwipeScreen(viewModel: SwipeViewModel, modifier: Modifier = Modifier) {
    val deck by viewModel.deck.collectAsStateWithLifecycle()
    val top = deck.firstOrNull()

    Column(modifier = modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("滑动筛选", style = MaterialTheme.typography.headlineMedium)
        Text(
            "剩余 ${deck.size} 张待筛选 · 左滑不喜欢 / 右滑喜欢（PC 可用 ←/→）",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            if (top == null) {
                Text("卡组已清空，回到工作台继续生成", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                SwipeCard(
                    record = top,
                    modifier = Modifier.fillMaxSize(0.9f),
                    onSwipedRight = { viewModel.swipeLike(top) },
                    onSwipedLeft = { viewModel.swipeDislike(top) },
                )
            }
        }
    }
}

@Composable
private fun SwipeCard(
    record: ImageRecord,
    modifier: Modifier = Modifier,
    onSwipedRight: () -> Unit,
    onSwipedLeft: () -> Unit,
) {
    val offsetX = remember(record.id) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val throwSpec: AnimationSpec<Float> = MD3EMotion.ThrowSpring
    val snappySpec: AnimationSpec<Float> = MD3EMotion.SnappySpring

    // PC 快捷键：← 不喜欢，→ 喜欢
    LaunchedEffect(record.id) {
        // 空实现占位：键盘事件由 onPreviewKeyEvent 处理
    }

    Box(
        modifier = modifier
            .onPreviewKeyEvent { event ->
                when (event.key) {
                    Key.DirectionRight -> {
                        scope.launch {
                            offsetX.animateTo(2400f, throwSpec)
                            onSwipedRight()
                            offsetX.snapTo(0f)
                        }
                        true
                    }
                    Key.DirectionLeft -> {
                        scope.launch {
                            offsetX.animateTo(-2400f, throwSpec)
                            onSwipedLeft()
                            offsetX.snapTo(0f)
                        }
                        true
                    }
                    else -> false
                }
            }
            .graphicsLayer {
                translationX = offsetX.value
                rotationZ = (offsetX.value / 1200f) * 15f
            }
            .pointerInput(record.id) {
                detectDragGestures(
                    onDragEnd = {
                        val dragged = offsetX.value
                        val threshold = size.width * 0.35f
                        scope.launch {
                            if (abs(dragged) > threshold) {
                                offsetX.animateTo(sign(dragged) * 2400f, throwSpec)
                                if (dragged > 0) onSwipedRight() else onSwipedLeft()
                                offsetX.snapTo(0f)
                            } else {
                                offsetX.animateTo(0f, snappySpec)
                            }
                        }
                    },
                ) { change, dragAmount ->
                    change.consume()
                    scope.launch { offsetX.snapTo(offsetX.value + dragAmount.x) }
                }
            }
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) {
        val model = if (record.filePath.startsWith("pending")) null else "file://${record.filePath}"
        if (model == null) {
            Text("待落盘 ${record.seed}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            AsyncImage(model = model, contentDescription = record.prompt, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }

        val ratio = (offsetX.value / 600f).coerceIn(-1f, 1f)
        if (ratio < 0f) {
            Box(Modifier.fillMaxSize().background(Color(0xFFB3261E).copy(alpha = -ratio * 0.35f), RoundedCornerShape(16.dp)))
        } else if (ratio > 0f) {
            Box(Modifier.fillMaxSize().background(Color(0xFF00BFA5).copy(alpha = ratio * 0.35f), RoundedCornerShape(16.dp)))
        }

        Text(
            text = if (ratio < -0.3f) "不喜欢" else if (ratio > 0.3f) "喜欢 ★" else record.prompt.take(60),
            style = MaterialTheme.typography.titleMedium,
            color = if (abs(ratio) > 0.3f) Color.White else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
        )
    }
}
