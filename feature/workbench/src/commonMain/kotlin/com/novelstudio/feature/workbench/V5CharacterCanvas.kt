package com.novelstudio.feature.workbench

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.novelstudio.core.model.V5Character
import com.novelstudio.core.designsystem.components.StudioSection
import kotlin.math.hypot

/** V5 多角色定位画板：API 只保存归一化中心点，画面中的标记尺寸是固定视觉尺寸。 */
@Composable
internal fun V5CharacterCanvas(
    characters: List<V5Character>,
    selectedCharacterId: String?,
    aspectRatio: Float,
    maxCharacters: Int,
    onAdd: () -> Unit,
    onSelect: (String) -> Unit,
    onMove: (String, Float, Float) -> Unit,
    onPromptChange: (String, String) -> Unit,
    onUcChange: (String, String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val currentCharacters by rememberUpdatedState(characters)
    val currentCanvasSize by rememberUpdatedState(canvasSize)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnMove by rememberUpdatedState(onMove)
    val selected = characters.firstOrNull { it.id == selectedCharacterId }
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val pointColor = MaterialTheme.colorScheme.onPrimary

    StudioSection("角色定位", modifier = modifier,
        description = "拖动圆点设置角色中心位置。") {
        if (characters.isEmpty()) {
            Text("还没有角色；点击“添加角色”开始设置。", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (characters.isNotEmpty()) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp)
                    .aspectRatio(aspectRatio.coerceIn(0.35f, 3f))
                    .onSizeChanged { canvasSize = it }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val size = currentCanvasSize
                            if (size.width <= 0 || size.height <= 0) return@awaitEachGesture
                            val hitRadius = 28.dp.toPx()
                            val active = currentCharacters.minByOrNull { candidate ->
                                val x = candidate.centerX * size.width
                                val y = candidate.centerY * size.height
                                hypot(down.position.x - x, down.position.y - y)
                            }?.takeIf { candidate ->
                                val x = candidate.centerX * size.width
                                val y = candidate.centerY * size.height
                                hypot(down.position.x - x, down.position.y - y) <= hitRadius
                            }
                                ?: return@awaitEachGesture

                            currentOnSelect(active.id)
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                if (change.position != change.previousPosition) {
                                    currentOnMove(
                                        active.id,
                                        (change.position.x / size.width).coerceIn(0f, 1f),
                                        (change.position.y / size.height).coerceIn(0f, 1f),
                                    )
                                    change.consume()
                                }
                            }
                        }
                    },
            ) {
                for (index in 1 until 5) {
                    val x = size.width * index / 5f
                    val y = size.height * index / 5f
                    drawLine(gridColor, Offset(x, 0f), Offset(x, size.height))
                    drawLine(gridColor, Offset(0f, y), Offset(size.width, y))
                }
                characters.forEach { character ->
                    val center = Offset(character.centerX * size.width, character.centerY * size.height)
                    val color = if (character.id == selectedCharacterId) primaryColor else secondaryColor
                    drawCircle(color, radius = 16.dp.toPx(), center = center)
                    drawCircle(pointColor, radius = 4.dp.toPx(), center = center)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onAdd, enabled = characters.size < maxCharacters) { Text("添加角色") }
            if (selected != null) TextButton(onClick = { onRemove(selected.id) }) { Text("删除角色") }
            Text("${characters.size}/$maxCharacters", modifier = Modifier.padding(top = 12.dp), style = MaterialTheme.typography.labelMedium)
        }
        if (characters.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(characters, key = { _, character -> character.id }) { index, character ->
                    FilterChip(
                        selected = character.id == selectedCharacterId,
                        onClick = { onSelect(character.id) },
                        label = { Text("角色 ${index + 1}") },
                    )
                }
            }
        }
        if (selected != null) {
            OutlinedTextField(
                value = selected.prompt,
                onValueChange = { onPromptChange(selected.id, it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("角色 ${characters.indexOf(selected) + 1} Prompt") },
                minLines = 2,
            )
            OutlinedTextField(
                value = selected.uc,
                onValueChange = { onUcChange(selected.id, it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("角色 UC") },
                minLines = 2,
            )
        }
    }
}
