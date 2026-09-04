package com.novelstudio.feature.workbench

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novelstudio.core.designsystem.components.StudioPageHeader
import com.novelstudio.core.designsystem.components.StudioSection
import com.novelstudio.core.designsystem.components.StudioSpacing
import com.novelstudio.core.designsystem.components.StudioStatusChip
import com.novelstudio.core.model.OpusBatteryState

/**
 * 生成工作台（MD3E 交互标准 §4-1）：
 * 提示词编辑区、参数面板（模型/比例胶囊/步数/CFG）、Opus 电池仪表环、透明背景开关。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkbenchScreen(viewModel: WorkbenchViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var promptValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(state.freePrompt, TextRange(state.freePrompt.length)))
    }
    var showParameterSheet by rememberSaveable { mutableStateOf(false) }
    val parameterSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    LaunchedEffect(state.freePrompt) {
        if (promptValue.text != state.freePrompt) {
            promptValue = TextFieldValue(state.freePrompt, TextRange(state.freePrompt.length))
        }
    }

    val activePromptToken = promptTokenAtCursor(promptValue.text, promptValue.selection.end)
    LaunchedEffect(activePromptToken?.query, state.model) {
        viewModel.requestTagSuggestions(activePromptToken?.query.orEmpty())
    }
    val parameterPanel: @Composable () -> Unit = {
        GenerationParameterPanel(
            state = state,
            onModelSelected = viewModel::selectModel,
            onAspectSelected = viewModel::selectAspect,
            onStepsChanged = viewModel::updateSteps,
            onScaleChanged = viewModel::updateScale,
            onCfgRescaleChanged = viewModel::updateCfgRescale,
            onSamplerSelected = viewModel::selectSampler,
            onNoiseScheduleSelected = viewModel::selectNoiseSchedule,
            onSamplesChanged = viewModel::updateSamples,
            onQualityTagsChanged = viewModel::toggleQualityTags,
            onSeedChanged = viewModel::updateSeed,
            onTransparencyChanged = viewModel::toggleTransparent,
            onSmeaChanged = viewModel::toggleSmea,
            onSmeaDynChanged = viewModel::toggleSmeaDyn,
            onVarietyPlusChanged = viewModel::toggleVarietyPlus,
            onDecrisperChanged = viewModel::toggleDecrisper,
        )
    }

    val promptComposer: @Composable () -> Unit = {
        StudioSection(
            title = "提示词",
            description = "正向描述决定画面，负向描述用于排除不需要的内容。",
        ) {
            AssetComposer(
                state = state,
                onArtistSelected = viewModel::selectArtist,
                onPromptSelected = viewModel::selectPromptAsset,
                onTagToggled = viewModel::toggleTag,
                onMoveTag = viewModel::moveTag,
            )
            PromptEditor(
                value = promptValue,
                onValueChange = { nextValue ->
                    promptValue = nextValue
                    viewModel.updateFreePrompt(nextValue.text)
                },
                suggestions = state.tagSuggestions,
                onSuggestionAccepted = viewModel::acceptTagSuggestion,
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.suggestionsOffline) {
                Text("正在使用离线 Tag 建议缓存", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedTextField(
                value = state.negativePrompt,
                onValueChange = viewModel::updateNegativePrompt,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("负向提示词") },
                minLines = 2,
                shape = MaterialTheme.shapes.medium,
            )
            FinalPromptPreview(state.finalPositive, state.finalNegative)
        }
    }
    val previewStage: @Composable () -> Unit = {
        PreviewStage(
            previewBitmap = state.previewBitmap,
            isGenerating = state.isGenerating,
            resolutionLabel = "${state.width} × ${state.height}",
        )
    }
    val generationAction: @Composable () -> Unit = {
        GenerationActionPanel(
            state = state,
            onGenerate = viewModel::generate,
            onModeSelected = viewModel::selectGenerationMode,
            onRejectAndNext = viewModel::rejectAndGenerateNext,
            onFinishDraw = viewModel::finishDrawMode,
        )
    }
    val supportingTools: @Composable () -> Unit = {
        VibeTransferTray(
            state = state,
            onImagePicked = { picked -> viewModel.requestVibeEncoding(picked.displayName, picked.bytes) },
            onStrengthChanged = viewModel::updateVibeStrength,
            onRemove = viewModel::removeVibe,
        )
        V5CharacterCanvas(
            characters = state.characterPrompts,
            selectedCharacterId = state.selectedCharacterId,
            aspectRatio = state.width.toFloat() / state.height.toFloat(),
            maxCharacters = state.model.maxCharacterPrompts,
            onAdd = viewModel::addCharacter,
            onSelect = viewModel::selectCharacter,
            onMove = viewModel::moveCharacter,
            onPromptChange = viewModel::updateCharacterPrompt,
            onUcChange = viewModel::updateCharacterUc,
            onRemove = viewModel::removeCharacter,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val compact = maxWidth < 600.dp
        val expanded = maxWidth >= 1000.dp
        val pagePadding = if (compact) StudioSpacing.Large else StudioSpacing.XLarge
        LaunchedEffect(compact) {
            if (!compact) showParameterSheet = false
        }

        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            StudioPageHeader(
                title = "智能工作台",
                description = "让提示词、参考图和参数围绕预览组织，而不是堆成一张设置表。",
                eyebrow = "NOVELAI STUDIO",
                modifier = Modifier.padding(start = pagePadding, end = pagePadding, top = StudioSpacing.Large),
                actions = {
                    BatteryRing(state.battery)
                    Spacer(Modifier.size(StudioSpacing.Small))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(state.batteryLabel, style = MaterialTheme.typography.labelMedium)
                        TextButton(onClick = viewModel::refreshBattery, enabled = !state.isBatteryLoading) {
                            Text("刷新用量")
                        }
                    }
                },
            )

            if (expanded) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = pagePadding, vertical = StudioSpacing.Large),
                    horizontalArrangement = Arrangement.spacedBy(StudioSpacing.Large),
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1.15f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(StudioSpacing.Large),
                    ) {
                        promptComposer()
                        supportingTools()
                        Spacer(Modifier.height(StudioSpacing.Small))
                    }
                    Column(
                        modifier = Modifier
                            .weight(0.85f)
                            .widthIn(min = 360.dp, max = 480.dp)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(StudioSpacing.Large),
                    ) {
                        previewStage()
                        generationAction()
                        parameterPanel()
                        GenerationQueuePanel(state = state, onCancel = viewModel::cancelQueueTask)
                        Spacer(Modifier.height(StudioSpacing.Small))
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = pagePadding, vertical = StudioSpacing.Large),
                    verticalArrangement = Arrangement.spacedBy(StudioSpacing.Large),
                ) {
                    promptComposer()
                    previewStage()
                    if (compact) {
                        CompactParameterSummary(state = state, onOpen = { showParameterSheet = true })
                    } else {
                        parameterPanel()
                    }
                    generationAction()
                    supportingTools()
                    GenerationQueuePanel(state = state, onCancel = viewModel::cancelQueueTask)
                    Spacer(Modifier.height(StudioSpacing.Small))
                }
            }
        }

        if (compact && showParameterSheet) {
            ModalBottomSheet(
                onDismissRequest = { showParameterSheet = false },
                sheetState = parameterSheetState,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    parameterPanel()
                    Button(
                        onClick = { showParameterSheet = false },
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("完成")
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }

        if (state.needsAnlasConfirmation) {
            AlertDialog(
            onDismissRequest = viewModel::dismissAnlasConfirmation,
            title = { Text("确认使用 ImageAnlas") },
            text = {
                Text(
                    state.anlasConfirmationMessage
                        ?: "当前请求不在可用的 Opus 免费/用量额度内。继续可能扣除 ImageAnlas。",
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmAnlas) { Text("确认扣 Anlas") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissAnlasConfirmation) { Text("取消") }
            },
            )
        }

        if (state.needsVibeEncodingConfirmation) {
            AlertDialog(
            onDismissRequest = viewModel::dismissVibeEncodingConfirmation,
            title = { Text("确认编码 Vibe") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "“${state.pendingVibeDisplayName}”需要调用 NovelAI 编码端点。" +
                            "官方说明 V4 及以上模型每次新编码消耗 2 ImageAnlas，是否继续？",
                    )
                    Text("信息提取 ${formatDialogValue(state.pendingVibeInformationExtracted)}")
                    Slider(
                        value = state.pendingVibeInformationExtracted,
                        onValueChange = viewModel::updatePendingVibeInformation,
                        valueRange = 0f..1f,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmVibeEncoding) { Text("确认编码（2 ImageAnlas）") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissVibeEncodingConfirmation) { Text("取消") }
            },
            )
        }
    }
}

@Composable
private fun AssetComposer(
    state: WorkbenchUiState,
    onArtistSelected: (String?) -> Unit,
    onPromptSelected: (String?) -> Unit,
    onTagToggled: (String) -> Unit,
    onMoveTag: (Int, Int) -> Unit,
) {
    Text("画师串", style = MaterialTheme.typography.titleSmall)
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(StudioSpacing.Small),
    ) {
        FilterChip(
            selected = state.selectedArtistStringId == null,
            onClick = { onArtistSelected(null) },
            label = { Text("不使用") },
        )
        state.artistStrings.forEach { asset ->
            FilterChip(
                selected = state.selectedArtistStringId == asset.id,
                onClick = { onArtistSelected(asset.id) },
                label = { Text(asset.name) },
            )
        }
    }
    Text("主 Prompt", style = MaterialTheme.typography.titleSmall)
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(StudioSpacing.Small),
    ) {
        FilterChip(
            selected = state.selectedPromptAssetId == null,
            onClick = { onPromptSelected(null) },
            label = { Text("不使用") },
        )
        state.promptAssets.forEach { asset ->
            FilterChip(
                selected = state.selectedPromptAssetId == asset.id,
                onClick = { onPromptSelected(asset.id) },
                label = { Text(asset.name) },
            )
        }
    }
    Text("排序 Tag", style = MaterialTheme.typography.titleSmall)
    if (state.availableTags.isEmpty()) {
        Text("Tag 库为空；可从 Tag 页面建立个人库，或接受官方建议。", style = MaterialTheme.typography.bodySmall)
    } else {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(StudioSpacing.Small),
        ) {
            state.availableTags.forEach { tag ->
                FilterChip(
                    selected = tag.normalizedValue in state.orderedTags,
                    onClick = { onTagToggled(tag.normalizedValue) },
                    label = { Text(tag.displayValue) },
                )
            }
        }
    }
    if (state.orderedTags.isNotEmpty()) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(StudioSpacing.XSmall),
        ) {
            state.orderedTags.forEachIndexed { index, tag ->
                AssistChip(
                    onClick = { onTagToggled(tag) },
                    label = { Text("${index + 1}. $tag ×") },
                    leadingIcon = {
                        if (index > 0) TextButton(onClick = { onMoveTag(index, index - 1) }) { Text("←") }
                    },
                    trailingIcon = {
                        if (index < state.orderedTags.lastIndex) TextButton(onClick = { onMoveTag(index, index + 1) }) { Text("→") }
                    },
                )
            }
        }
    }
    Text("自由补充", style = MaterialTheme.typography.titleSmall)
}

@Composable
private fun FinalPromptPreview(positive: String, negative: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(StudioSpacing.Medium), verticalArrangement = Arrangement.spacedBy(StudioSpacing.Small)) {
            Text("最终发送文本", style = MaterialTheme.typography.titleSmall)
            Text("Positive", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(positive.ifBlank { "尚未组合正向文本" }, style = MaterialTheme.typography.bodyMedium)
            Text("Negative", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
            Text(negative.ifBlank { "无" }, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun CompactParameterSummary(
    state: WorkbenchUiState,
    onOpen: () -> Unit,
) {
    StudioSection(
        title = "生成参数",
        description = "常用配置保持可见，完整参数在底部面板中调整。",
        actions = {
            TextButton(onClick = onOpen) {
                Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(StudioSpacing.XSmall))
                Text("调整")
            }
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(StudioSpacing.Small),
        ) {
            StudioStatusChip(state.model.displayName)
            StudioStatusChip("${state.width} × ${state.height}")
            StudioStatusChip("${state.steps} 步")
            StudioStatusChip("${state.nSamples} 张")
        }
    }
}

@Composable
private fun GenerationActionPanel(
    state: WorkbenchUiState,
    onGenerate: () -> Unit,
    onModeSelected: (GenerationMode) -> Unit,
    onRejectAndNext: () -> Unit,
    onFinishDraw: () -> Unit,
) {
    val ready = state.canGenerate
    StudioSection(
        title = "生成",
        description = "${state.model.displayName} · ${state.width} × ${state.height} · ${if (state.generationMode == GenerationMode.BATCH_REVIEW) state.nSamples else 1} 张",
        actions = {
            StudioStatusChip(
                text = if (ready) "已就绪" else "需要检查",
                containerColor = if (ready) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.errorContainer
                },
                contentColor = if (ready) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                },
            )
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(StudioSpacing.Small),
        ) {
            GenerationMode.entries.forEach { mode ->
                FilterChip(
                    selected = state.generationMode == mode,
                    onClick = { onModeSelected(mode) },
                    label = { Text(mode.label) },
                )
            }
        }
        Text(
            text = when (val preflight = state.lastPreflight) {
                null -> "提交前会再次检查模型能力、订阅与计费边界。"
                com.novelstudio.core.model.GenerationPreflight.Free -> "上一次请求通过免费额度检查。"
                is com.novelstudio.core.model.GenerationPreflight.RequiresConfirmation -> preflight.summary
                is com.novelstudio.core.model.GenerationPreflight.Blocked -> preflight.reason
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onGenerate,
            enabled = ready,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) {
            if (state.isGenerating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(19.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.size(StudioSpacing.Small))
                Text("加入下一任务")
            } else {
                Icon(Icons.Rounded.Star, contentDescription = null, modifier = Modifier.size(19.dp))
                Spacer(Modifier.size(StudioSpacing.Small))
                Text("加入生成队列")
            }
        }
        if (state.generationMode == GenerationMode.DRAW_UNTIL_LIKED && state.previewBitmap != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(StudioSpacing.Small)) {
                Button(onClick = onRejectAndNext, modifier = Modifier.weight(1f)) { Text("不喜欢并生成下一张") }
                TextButton(onClick = onFinishDraw, modifier = Modifier.weight(1f)) { Text("喜欢，结束") }
            }
            Text(
                "每次“生成下一张”都是一次新的人工操作，不会在后台自动循环。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.message?.let { StudioStatusChip(it) }
        state.errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        state.batteryErrorMessage?.let {
            Text(
                "V5 用量读取失败：$it",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** Opus 电池仪表环：绿（充足）/ 黄（中等）/ 红橙（枯竭） */
@Composable
private fun BatteryRing(battery: OpusBatteryState?) {
    val percent = battery?.batteryPercent
    val isOpus = battery?.isOpus == true
    val animated by animateFloatAsState(
        targetValue = if (isOpus && percent != null) percent.coerceIn(0, 100) / 100f else 0f,
        label = "batterySweep",
    )
    val color = when {
        !isOpus -> MaterialTheme.colorScheme.outlineVariant
        battery.isUsageUnavailable -> MaterialTheme.colorScheme.error
        percent == null -> MaterialTheme.colorScheme.outlineVariant
        percent > 30 -> MaterialTheme.colorScheme.tertiary
        percent > 10 -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.error
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

private fun formatDialogValue(value: Float): String = ((value * 100).toInt() / 100f).toString()
