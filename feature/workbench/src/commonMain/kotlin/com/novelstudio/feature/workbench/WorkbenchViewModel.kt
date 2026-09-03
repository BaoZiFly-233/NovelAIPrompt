package com.novelstudio.feature.workbench

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelstudio.core.data.GenerationOutcome
import com.novelstudio.core.data.GenerationRepository
import com.novelstudio.core.model.AspectPreset
import com.novelstudio.core.model.GenerationParameters
import com.novelstudio.core.model.NaiModel
import com.novelstudio.core.model.OpusBatteryState
import com.novelstudio.core.model.PromptDraftStore
import com.novelstudio.core.network.OpusFreeCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * 生成工作台 ViewModel（只依赖 GenerationRepository 抽象，SRP/DIP 达成）：
 * 提示词/参数编辑 → 仓储双轨调度、落盘、入库管道 → UI 状态回写。
 */
class WorkbenchViewModel(
    private val generationRepository: GenerationRepository,
    private val draftStore: PromptDraftStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkbenchUiState())
    val uiState: StateFlow<WorkbenchUiState> = _uiState.asStateFlow()

    /** Anlas 确认后继续执行的挂起参数 */
    private var pendingParameters: GenerationParameters? = null

    init {
        adoptDraft()
        refreshBattery()
    }

    private fun adoptDraft() {
        draftStore.peek()?.let { draft ->
            draftStore.clear()
            _uiState.value = _uiState.value.copy(
                prompt = draft.prompt,
                negativePrompt = draft.uc,
                model = draft.model,
                width = draft.width,
                height = draft.height,
                steps = draft.steps,
                scale = draft.scale,
            )
        }
    }

    /** 电池环仅作 UI 展示；真实调度使用仓储内部的最新电量 */
    fun refreshBattery() {
        viewModelScope.launch {
            setState { copy(battery = OpusBatteryState()) }
        }
    }

    fun updatePrompt(value: String) = setState { copy(prompt = value) }

    fun updateNegativePrompt(value: String) = setState { copy(negativePrompt = value) }

    fun selectModel(model: NaiModel) = setState {
        copy(
            model = model,
            transparentBackground = if (!model.supportsTransparency) false else transparentBackground,
        )
    }

    fun selectAspect(preset: AspectPreset) {
        val (w, h) = OpusFreeCalculator.clampResolution(preset.ratioWidth, preset.ratioHeight)
        setState { copy(aspect = preset, width = w, height = h) }
    }

    fun updateSteps(value: Int) = setState { copy(steps = OpusFreeCalculator.clampSteps(value)) }

    fun updateScale(value: Float) = setState { copy(scale = value) }

    fun toggleTransparent(value: Boolean) = setState { copy(transparentBackground = value) }

    fun toggleExploration(value: Boolean) = setState { copy(explorationMode = value) }

    fun generate() {
        if (_uiState.value.isGenerating) return
        val seed = Random.nextLong(0L, 0xFFFFFFFFL)
        val parameters = _uiState.value.parameters(seed)
        val exploration = _uiState.value.explorationMode
        viewModelScope.launch {
            setState { copy(isGenerating = true, errorMessage = null, message = null) }
            when (val outcome = generationRepository.generate(parameters, exploration)) {
                is GenerationOutcome.Success -> setState {
                    copy(
                        isGenerating = false,
                        message = "生成成功，已保存到本地图库",
                        lastDecision = outcome.decision,
                        previewBitmap = decodePngPreview(outcome.previewBytes),
                    )
                }
                is GenerationOutcome.NeedsAnlasConfirmation -> {
                    pendingParameters = outcome.parameters
                    setState { copy(isGenerating = false, needsAnlasConfirmation = true, lastDecision = outcome.decision) }
                }
                is GenerationOutcome.Failure -> setState { copy(isGenerating = false, errorMessage = outcome.message) }
            }
        }
    }

    /** 用户在弹窗中确认扣 Anlas 后继续 */
    fun confirmAnlas() {
        val parameters = pendingParameters ?: return
        viewModelScope.launch {
            setState { copy(needsAnlasConfirmation = false) }
            when (val outcome = generationRepository.generateWithAnlas(parameters)) {
                is GenerationOutcome.Success -> setState {
                    copy(
                        isGenerating = false,
                        message = "生成成功，已保存到本地图库",
                        previewBitmap = decodePngPreview(outcome.previewBytes),
                    )
                }
                is GenerationOutcome.Failure -> setState { copy(isGenerating = false, errorMessage = outcome.message) }
                is GenerationOutcome.NeedsAnlasConfirmation -> setState { copy(isGenerating = false) }
            }
        }
    }

    fun dismissAnlasConfirmation() {
        pendingParameters = null
        setState { copy(needsAnlasConfirmation = false) }
    }

    private inline fun setState(reducer: WorkbenchUiState.() -> WorkbenchUiState) {
        _uiState.value = _uiState.value.reducer()
    }
}
