package com.novelstudio.feature.workbench

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.horizontalScroll
import com.novelstudio.core.designsystem.motion.MD3EMotion
import com.novelstudio.core.designsystem.components.StudioParameterSlider
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.novelstudio.core.designsystem.theme.MD3EPillShape
import com.novelstudio.core.designsystem.components.StudioSection
import com.novelstudio.core.designsystem.components.StudioStatusChip
import com.novelstudio.core.model.AspectPreset
import com.novelstudio.core.model.NaiModel
import com.novelstudio.core.model.NoiseSchedule
import com.novelstudio.core.model.Sampler
import com.novelstudio.core.network.OpusFreeCalculator

/** 工作台参数面板：常用项直接可见，高级项按需展开，所有值由 ViewModel 单向驱动。 */
@Composable
internal fun GenerationParameterPanel(
    state: WorkbenchUiState,
    onModelSelected: (NaiModel) -> Unit,
    onAspectSelected: (AspectPreset) -> Unit,
    onStepsChanged: (Int) -> Unit,
    onScaleChanged: (Float) -> Unit,
    onCfgRescaleChanged: (Float) -> Unit,
    onSamplerSelected: (Sampler) -> Unit,
    onNoiseScheduleSelected: (NoiseSchedule) -> Unit,
    onSamplesChanged: (Int) -> Unit,
    onQualityTagsChanged: (Boolean) -> Unit,
    onSeedChanged: (String) -> Unit,
    onTransparencyChanged: (Boolean) -> Unit,
    onSmeaChanged: (Boolean) -> Unit,
    onSmeaDynChanged: (Boolean) -> Unit,
    onVarietyPlusChanged: (Boolean) -> Unit,
    onDecrisperChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var advancedExpanded by rememberSaveable { mutableStateOf(false) }
    val withinNormalAllowance = OpusFreeCalculator.isFreeGeneration(state.width, state.height, state.steps) &&
        state.nSamples == 1 && state.vibeReferences.size <= 4

    StudioSection("生成参数", modifier = modifier,
        description = "常用参数直接调整，高级选项按需展开。") {
        Column(
            modifier = Modifier,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("模型", style = MaterialTheme.typography.titleSmall)
            ScrollablePillRow {
                NaiModel.entries.forEach { model ->
                    SelectablePill(
                        selected = state.model == model,
                        onClick = { onModelSelected(model) },
                        label = model.displayName,
                    )
                }
            }

            Text("画面比例", style = MaterialTheme.typography.titleSmall)
            ScrollablePillRow {
                AspectPreset.entries.forEach { preset ->
                    SelectablePill(
                        selected = state.aspect == preset,
                        onClick = { onAspectSelected(preset) },
                        label = preset.label,
                    )
                }
            }
            Text(
                "${state.width} × ${state.height}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            StudioParameterSlider(
                label = "步数",
                displayValue = state.steps.toString(),
                value = state.steps.toFloat(),
                onValueChange = { onStepsChanged(it.toInt()) },
                valueRange = 1f..50f,
                steps = 48,
                minLabel = "1",
                maxLabel = "50",
            )
            StudioParameterSlider(
                label = "提示词引导",
                displayValue = formatOneDecimal(state.scale),
                value = state.scale,
                onValueChange = onScaleChanged,
                valueRange = 0f..10f,
                minLabel = "0",
                maxLabel = "10",
            )

            Text("单次请求张数", style = MaterialTheme.typography.titleSmall)
            ScrollablePillRow {
                (1..6).forEach { count ->
                    SelectablePill(
                        selected = state.nSamples == count,
                        onClick = { onSamplesChanged(count) },
                        label = "$count 张",
                    )
                }
            }
            Text(
                "多图仍只提交一次生成请求；实际张数上限还取决于分辨率。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LabeledSwitch(
                    label = "V5 透明背景",
                    checked = state.transparentBackground,
                    enabled = state.model.supportsTransparency,
                    onCheckedChange = onTransparencyChanged,
                )
            }

            StudioStatusChip(
                text = if (withinNormalAllowance) "符合 Opus 常规额度" else "提交前需要额度确认",
                containerColor = if (withinNormalAllowance) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.errorContainer
                },
                contentColor = if (withinNormalAllowance) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                },
            )

            TextButton(onClick = { advancedExpanded = !advancedExpanded }) {
                Text(if (advancedExpanded) "收起高级参数" else "展开高级参数")
            }

            AnimatedVisibility(
                visible = advancedExpanded,
                enter = expandVertically(animationSpec = MD3EMotion.ExpandSpring) + fadeIn(MD3EMotion.StandardEasing),
                exit = shrinkVertically(animationSpec = MD3EMotion.ExpandSpring) + fadeOut(MD3EMotion.StandardEasing),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("采样器", style = MaterialTheme.typography.titleSmall)
                    ScrollablePillRow {
                        Sampler.entries.forEach { sampler ->
                            SelectablePill(
                                selected = state.sampler == sampler,
                                onClick = { onSamplerSelected(sampler) },
                                label = sampler.displayLabel,
                            )
                        }
                    }

                    Text("噪声调度", style = MaterialTheme.typography.titleSmall)
                    ScrollablePillRow {
                        NoiseSchedule.entries.forEach { schedule ->
                            SelectablePill(
                                selected = state.noiseSchedule == schedule,
                                onClick = { onNoiseScheduleSelected(schedule) },
                                label = schedule.id,
                            )
                        }
                    }

                    StudioParameterSlider(
                        label = "引导重缩放",
                        displayValue = formatOneDecimal(state.cfgRescale),
                        value = state.cfgRescale,
                        onValueChange = onCfgRescaleChanged,
                        valueRange = 0f..1f,
                        minLabel = "0",
                        maxLabel = "1",
                    )
                    OutlinedTextField(
                        value = state.seedText,
                        onValueChange = onSeedChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Seed（留空则随机）") },
                        singleLine = true,
                        isError = state.seedError != null,
                        supportingText = state.seedError?.let { message -> { Text(message) } },
                    )

                    LabeledSwitch(
                        label = "自动添加质量标签",
                        checked = state.qualityToggle,
                        onCheckedChange = onQualityTagsChanged,
                    )

                    LabeledSwitch(
                        label = "SMEA（大尺寸图像质量增强）",
                        checked = state.smea,
                        onCheckedChange = onSmeaChanged,
                    )
                    LabeledSwitch(
                        label = "SMEA DYN（动态增强，需开启 SMEA）",
                        checked = state.smeaDyn,
                        enabled = state.smea,
                        onCheckedChange = onSmeaDynChanged,
                    )
                    LabeledSwitch(
                        label = "Variety+（多样性增强）",
                        checked = state.varietyPlus,
                        onCheckedChange = onVarietyPlusChanged,
                    )
                    LabeledSwitch(
                        label = "Decrisper（减少高引导强度过曝）",
                        checked = state.decrisper,
                        onCheckedChange = onDecrisperChanged,
                    )
                }
            }
        }
    }
}

@Composable
private fun LabeledSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun ScrollablePillRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun SelectablePill(selected: Boolean, onClick: () -> Unit, label: String) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        ),
        shape = MD3EPillShape,
    )
}

private val Sampler.displayLabel: String
    get() = when (this) {
        Sampler.K_EULER -> "Euler"
        Sampler.K_EULER_ANCESTRAL -> "Euler A"
        Sampler.K_DPMPP_2S_ANCESTRAL -> "DPM++ 2S A"
        Sampler.K_DPMPP_2M -> "DPM++ 2M"
        Sampler.K_DPMPP_SDE -> "DPM++ SDE"
        Sampler.K_DPMPP_3M_SDE -> "DPM++ 3M SDE"
    }

private fun formatOneDecimal(value: Float): String =
    ((value * 10).toInt() / 10f).toString()
