package com.novelstudio.feature.inpaint

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import com.novelstudio.core.designsystem.components.StudioConfirmDialog
import com.novelstudio.core.designsystem.components.StudioPageHeader
import com.novelstudio.core.designsystem.components.StudioParameterSlider
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.novelstudio.core.common.platform.localFileModel
import com.novelstudio.core.designsystem.components.StudioIcons
import com.novelstudio.core.designsystem.components.StudioSection
import com.novelstudio.core.model.DirectorTool
import kotlin.math.roundToInt

@Composable
fun ImageToolsScreen(
    imageId: String,
    viewModel: ImageToolsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(imageId) { viewModel.load(imageId) }
    val pickMask = rememberMaskImagePicker(
        onPicked = viewModel::requestInpaint,
        onError = viewModel::showPickerError,
    )

    // 退出保护：处理中点返回时弹出确认对话框
    var showExitConfirm by remember { mutableStateOf(false) }
    val safeBack: () -> Unit = {
        if (state.running) showExitConfirm = true else onBack()
    }

    if (showExitConfirm) {
        StudioConfirmDialog(
            title = "确认退出",
            body = "正在处理请求，退出后无法确认处理结果。确认退出？",
            confirmLabel = "退出",
            confirmIsDestructive = true,
            onConfirm = { showExitConfirm = false; onBack() },
            onDismiss = { showExitConfirm = false },
        )
    }

    if (state.confirmation != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissConfirmation,
            title = { Text("确认提交图像工具请求") },
            text = { Text(state.confirmation.orEmpty()) },
            confirmButton = { Button(onClick = viewModel::confirm) { Text("确认并提交一次") } },
            dismissButton = { TextButton(onClick = viewModel::dismissConfirmation) { Text("取消") } },
        )
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = safeBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回作品详情") }
            StudioPageHeader(
                eyebrow = "DERIVE",
                title = "图像工具",
                modifier = Modifier.weight(1f),
            )
        }

        when {
            state.loading -> Box(Modifier.fillMaxWidth().heightIn(min = 280.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.parent == null -> Text(state.error ?: "作品不可用", color = MaterialTheme.colorScheme.error)
            else -> {
                AsyncImage(
                    model = localFileModel(state.parent!!.filePath),
                    contentDescription = state.parent!!.prompt,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                )

                state.message?.let { Notice(it, false, viewModel::clearNotice) }
                state.error?.let { Notice(it, true, viewModel::clearNotice) }

                StudioSection(title = "创作派生") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = state.prompt,
                            onValueChange = viewModel::updatePrompt,
                            label = { Text("Prompt") },
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = state.negativePrompt,
                            onValueChange = viewModel::updateNegativePrompt,
                            label = { Text("Negative") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        StudioParameterSlider(
                            label = "Strength",
                            value = state.strength,
                            onValueChange = viewModel::updateStrength,
                            valueRange = 0f..1f,
                            displayValue = displayDecimal(state.strength),
                            minLabel = "0",
                            maxLabel = "1",
                        )
                        StudioParameterSlider(
                            label = "Noise",
                            value = state.noise,
                            onValueChange = viewModel::updateNoise,
                            valueRange = 0f..1f,
                            displayValue = displayDecimal(state.noise),
                            minLabel = "0",
                            maxLabel = "1",
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = viewModel::requestImg2Img, enabled = !state.running) { Text("Img2Img") }
                            Button(onClick = pickMask, enabled = !state.running) { Text("Inpaint") }
                        }
                        StudioParameterSlider(
                            label = "Enhance 输出缩放",
                            value = (state.enhanceScale - 1f) / 3f,
                            onValueChange = { viewModel.updateEnhanceScale(1f + it * 3f) },
                            valueRange = 0f..1f,
                            displayValue = "${displayDecimal(state.enhanceScale)}×",
                            minLabel = "1×",
                            maxLabel = "4×",
                        )
                        Button(onClick = viewModel::requestEnhance, enabled = !state.running) { Text("Enhance") }
                    }
                }

                StudioSection(title = "Upscale") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Declared blur sigma")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0f, 0.30f, 0.35f, 0.40f, 0.45f, 0.50f).forEach { sigma ->
                                FilterChip(
                                    selected = state.blurSigma == sigma,
                                    onClick = { viewModel.updateBlurSigma(sigma) },
                                    label = { Text(sigma.toString()) },
                                )
                            }
                        }
                        Button(onClick = viewModel::requestUpscale, enabled = !state.running) { Text("Upscale") }
                    }
                }

                StudioSection(title = "Director Tools") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = state.directorPrompt,
                            onValueChange = viewModel::updateDirectorPrompt,
                            label = { Text("仅 Colorize / Emotion 使用的 Prompt") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        StudioParameterSlider(
                            label = "Defry",
                            value = state.defry / 5f,
                            onValueChange = { viewModel.updateDefry((it * 5).roundToInt()) },
                            valueRange = 0f..1f,
                            displayValue = state.defry.toString(),
                            minLabel = "0",
                            maxLabel = "5",
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            DirectorTool.entries.forEach { tool ->
                                // 主要操作（Remove BG 三种变体、Colorize、Emotion）保持实心按钮，其余降级为 OutlinedButton
                                val isPrimary = tool in setOf(
                                    DirectorTool.REMOVE_BACKGROUND,
                                    DirectorTool.REMOVE_BACKGROUND_GENERATED,
                                    DirectorTool.REMOVE_BACKGROUND_BLENDED,
                                    DirectorTool.COLORIZE,
                                    DirectorTool.EMOTION,
                                )
                                if (isPrimary) {
                                    Button(onClick = { viewModel.requestDirector(tool) }, enabled = !state.running) {
                                        Text(tool.label())
                                    }
                                } else {
                                    OutlinedButton(onClick = { viewModel.requestDirector(tool) }, enabled = !state.running) {
                                        Text(tool.label())
                                    }
                                }
                            }
                        }
                    }
                }
                if (state.running) Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(22.dp))
                    Text("正在处理，请勿重复提交…", modifier = Modifier.padding(start = 10.dp))
                }
            }
        }
    }
}

@Composable
private fun Notice(message: String, error: Boolean, onDismiss: () -> Unit) {
    Surface(
        color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (error) StudioIcons.Error else StudioIcons.Notice,
                contentDescription = null,
            )
            Text(message, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    }
}

private fun DirectorTool.label(): String = when (this) {
    DirectorTool.REMOVE_BACKGROUND -> "Remove BG"
    DirectorTool.REMOVE_BACKGROUND_GENERATED -> "Remove BG (Generated)"
    DirectorTool.REMOVE_BACKGROUND_BLENDED -> "Remove BG (Blend)"
    DirectorTool.LINE_ART -> "Line Art"
    DirectorTool.SKETCH -> "Sketch"
    DirectorTool.COLORIZE -> "Colorize"
    DirectorTool.EMOTION -> "Emotion"
    DirectorTool.DECLUTTER -> "Declutter"
}

private fun displayDecimal(value: Float): String = "%.2f".format(value)
