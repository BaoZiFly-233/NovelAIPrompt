package com.novelstudio.feature.workbench

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.novelstudio.core.model.GenerationParameters
import com.novelstudio.core.designsystem.components.StudioSection

/** Vibe Transfer 托盘；编码与生成成本提示保持在用户操作附近。 */
@Composable
internal fun VibeTransferTray(
    state: WorkbenchUiState,
    onImagePicked: (PickedVibeImage) -> Unit,
    onStrengthChanged: (String, Float) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pickerError by remember { mutableStateOf<String?>(null) }
    val pickImage = rememberVibeImagePicker(
        onPicked = {
            pickerError = null
            onImagePicked(it)
        },
        onError = { pickerError = it },
    )

    StudioSection("Vibe Transfer", modifier = modifier,
        description = "添加参考图并调整影响强度。") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        "${state.vibeReferences.size}/${GenerationParameters.MAX_VIBE_REFERENCES} 个参考",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(
                    onClick = pickImage,
                    enabled = !state.isEncodingVibe &&
                        state.vibeReferences.size < GenerationParameters.MAX_VIBE_REFERENCES,
                ) {
                    if (state.isEncodingVibe) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("添加参考图")
                    }
                }
            }

            Text(
                "新参考图会在确认后调用编码端点；超过 4 个参考的生成会额外消耗 ImageAnlas。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            state.vibeReferences.forEach { vibe ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(vibe.displayName, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${vibe.model.displayName} · 信息提取 ${formatVibeValue(vibe.informationExtracted)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { onRemove(vibe.id) }) { Text("移除") }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("强度 ${formatVibeValue(vibe.referenceStrength)}")
                        Slider(
                            value = vibe.referenceStrength,
                            onValueChange = { onStrengthChanged(vibe.id, it) },
                            valueRange = 0f..1f,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            if (state.totalVibeStrength > 1f) {
                Text(
                    "参考强度总和为 ${formatVibeValue(state.totalVibeStrength)}；官方建议总和不超过 1.0。",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            state.vibeCompatibilityMessage?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            pickerError?.let { message ->
                Text("读取图片失败：$message", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun formatVibeValue(value: Float): String = ((value * 100).toInt() / 100f).toString()
