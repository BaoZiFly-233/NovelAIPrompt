package com.novelstudio.feature.workbench

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novelstudio.core.designsystem.theme.MD3EColors
import com.novelstudio.core.designsystem.theme.MD3EPillShape
import com.novelstudio.core.model.AspectPreset
import com.novelstudio.core.model.NaiModel

/**
 * 生成工作台（MD3E 交互标准 §4-1）：
 * 提示词编辑区、参数面板（模型/比例胶囊/步数/CFG）、Opus 电池仪表环、透明背景开关。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkbenchScreen(viewModel: WorkbenchViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("智能工作台", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.weight(1f))
            BatteryRing(percent = state.battery.batteryPercent, isOpus = state.battery.isOpus)
            Spacer(Modifier.size(8.dp))
            Text(state.batteryLabel, style = MaterialTheme.typography.labelMedium)
        }

        OutlinedTextField(
            value = state.prompt,
            onValueChange = viewModel::updatePrompt,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("提示词 Prompt") },
            placeholder = { Text("masterpiece, best quality, 1girl, solo ...") },
            minLines = 3,
            shape = RoundedCornerShape(16.dp),
        )

        OutlinedTextField(
            value = state.negativePrompt,
            onValueChange = viewModel::updateNegativePrompt,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("负面提示词 UC") },
            minLines = 2,
            shape = RoundedCornerShape(16.dp),
        )

        Text("模型", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NaiModel.entries.forEach { model ->
                SelectablePill(
                    selected = state.model == model,
                    onClick = { viewModel.selectModel(model) },
                    label = model.displayName,
                )
            }
        }

        Text("画面比例（Opus 免费钳位）", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AspectPreset.entries.forEach { preset ->
                SelectablePill(
                    selected = state.aspect == preset,
                    onClick = { viewModel.selectAspect(preset) },
                    label = preset.label,
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("步数 ${state.steps}", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = state.steps.toFloat(),
                onValueChange = { viewModel.updateSteps(it.toInt()) },
                valueRange = 1f..50f,
                steps = 49,
                modifier = Modifier.weight(1f),
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("CFG ${state.scale}", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = state.scale,
                onValueChange = viewModel::updateScale,
                valueRange = 0f..10f,
                modifier = Modifier.weight(1f),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = state.transparentBackground, onCheckedChange = viewModel::toggleTransparent)
                Text("V5 透明背景", style = MaterialTheme.typography.labelLarge)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = state.explorationMode, onCheckedChange = viewModel::toggleExploration)
                Text("探索抽卡模式", style = MaterialTheme.typography.labelLarge)
            }
        }

        Text(
            text = buildString {
                append("分辨率 ${state.width} × ${state.height}")
                append("　·　决策：${state.lastDecision?.description ?: "待生成"}")
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Button(
            onClick = viewModel::generate,
            enabled = !state.isGenerating && state.prompt.isNotBlank(),
            shape = MD3EPillShape,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            if (state.isGenerating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.size(12.dp))
                Text("正在生成…")
            } else {
                Text("生成图像", style = MaterialTheme.typography.titleMedium)
            }
        }

        state.message?.let {
            Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
        }
        state.errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        state.previewBitmap?.let { bitmap ->
            Text("生成预览", style = MaterialTheme.typography.titleMedium)
            Image(
                bitmap = bitmap,
                contentDescription = "生成预览",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)),
            )
        }

        Spacer(Modifier.height(24.dp))
    }

    if (state.needsAnlasConfirmation) {
        AlertDialog(
            onDismissRequest = viewModel::dismissAnlasConfirmation,
            title = { Text("V5 电池不足") },
            text = { Text("继续生成将扣除 Anlas 余额，是否确认？\n（可开启「探索抽卡模式」自动切至 V4.5 无限池）") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmAnlas) { Text("确认扣 Anlas") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissAnlasConfirmation) { Text("取消") }
            },
        )
    }
}

/** 模型/比例选择的药丸型胶囊（Filled Tonal 高亮选中态） */
@Composable
private fun SelectablePill(selected: Boolean, onClick: () -> Unit, label: String) {
    val container by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        label = "pillContainer",
    )
    val content by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "pillContent",
    )
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, color = content) },
        colors = FilterChipDefaults.filterChipColors(containerColor = container),
        shape = MD3EPillShape,
    )
}

/** Opus 电池仪表环：绿（充足）/ 黄（中等）/ 红橙（枯竭） */
@Composable
private fun BatteryRing(percent: Float, isOpus: Boolean) {
    val animated by animateFloatAsState(
        targetValue = if (isOpus) percent.coerceIn(0f, 100f) / 100f else 0f,
        label = "batterySweep",
    )
    val color = when {
        !isOpus -> MaterialTheme.colorScheme.outlineVariant
        percent > 30f -> MD3EColors.BatteryFull
        percent > 10f -> MD3EColors.BatteryMedium
        else -> MD3EColors.BatteryLow
    }
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier = Modifier.size(28.dp)) {
        val stroke = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
        drawArc(
            color = trackColor,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            style = stroke,
            topLeft = Offset.Zero,
            size = Size(size.width, size.height),
        )
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = 360f * animated,
            useCenter = false,
            style = stroke,
        )
    }
}
