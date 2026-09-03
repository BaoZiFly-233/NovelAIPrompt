package com.novelstudio.feature.workbench

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelstudio.core.common.png.PngMetadataParser
import com.novelstudio.core.database.ImageDao
import com.novelstudio.core.database.ImageEntity
import com.novelstudio.core.model.AspectPreset
import com.novelstudio.core.model.DispatchDecision
import com.novelstudio.core.model.GenerationParameters
import com.novelstudio.core.model.ImageRecord
import com.novelstudio.core.model.NaiImageMetadata
import com.novelstudio.core.model.NaiModel
import com.novelstudio.core.model.OpusBatteryState
import com.novelstudio.core.model.PromptDraftStore
import com.novelstudio.core.network.NovelAIApiService
import com.novelstudio.core.network.OpusFreeCalculator
import com.novelstudio.core.network.SmartDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okio.Buffer
import kotlin.random.Random

/**
 * 生成工作台 ViewModel：
 * 提示词/参数编辑 → SmartDispatcher 双轨路由 → NovelAI API → Room 入库。
 */
class WorkbenchViewModel(
    private val api: NovelAIApiService,
    private val imageDao: ImageDao,
    private val draftStore: PromptDraftStore,
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _uiState = MutableStateFlow(WorkbenchUiState())
    val uiState: StateFlow<WorkbenchUiState> = _uiState.asStateFlow()

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

    fun refreshBattery() {
        viewModelScope.launch {
            val battery = runCatching { api.getOpusBatteryState() }
                .getOrDefault(OpusBatteryState())
            _uiState.value = _uiState.value.copy(battery = battery)
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
        val state = _uiState.value
        val parameters = state.parameters(seed)
        viewModelScope.launch {
            val battery = runCatching { api.getOpusBatteryState() }.getOrDefault(state.battery)
            val decision = SmartDispatcher.decide(parameters, battery, state.explorationMode)
            _uiState.value = _uiState.value.copy(battery = battery, lastDecision = decision)
            when (decision) {
                DispatchDecision.USE_V5_BATTERY -> runGeneration(parameters)
                DispatchDecision.FALLBACK_V4_5 -> runGeneration(SmartDispatcher.degradeToV4_5(parameters))
                DispatchDecision.CONFIRM_ANLAS -> setState { copy(needsAnlasConfirmation = true) }
            }
        }
    }

    /** 用户在弹窗中确认扣 Anlas 后继续 */
    fun confirmAnlas() {
        val seed = Random.nextLong(0L, 0xFFFFFFFFL)
        setState { copy(needsAnlasConfirmation = false) }
        runGeneration(_uiState.value.parameters(seed))
    }

    fun dismissAnlasConfirmation() = setState { copy(needsAnlasConfirmation = false) }

    private fun runGeneration(parameters: GenerationParameters) {
        viewModelScope.launch {
            setState { copy(isGenerating = true, errorMessage = null, message = null) }
            runCatching { withContext(Dispatchers.IO) { api.generateImage(parameters) } }
                .onSuccess { bytes -> persistSuccess(parameters, bytes) }
                .onFailure { throwable ->
                    setState { copy(isGenerating = false, errorMessage = throwable.message ?: "生成失败") }
                }
        }
    }

    private suspend fun persistSuccess(parameters: GenerationParameters, bytes: ByteArray) {
        withContext(Dispatchers.IO) {
            val pngInfo = runCatching { PngMetadataParser.parse(Buffer().apply { write(bytes) }) }.getOrNull()
            val id = randomId()
            val metadata = pngInfo?.naiMetadata ?: NaiImageMetadata(
                prompt = parameters.prompt,
                uc = parameters.negativePrompt,
                seed = parameters.seed,
                steps = parameters.steps,
                scale = parameters.scale,
                sampler = parameters.sampler.id,
                width = parameters.width,
                height = parameters.height,
                model = parameters.model.id,
            )
            imageDao.upsert(
                ImageEntity(
                    id = id,
                    filePath = "pending/$id.png",
                    thumbnailPath = "",
                    blurHash = "",
                    prompt = metadata.prompt,
                    uc = metadata.uc,
                    model = metadata.model,
                    seed = metadata.seed,
                    steps = metadata.steps,
                    scale = metadata.scale,
                    sampler = metadata.sampler,
                    width = pngInfo?.width ?: parameters.width,
                    height = pngInfo?.height ?: parameters.height,
                    starRating = ImageRecord.STAR_NEUTRAL,
                    isFavorite = false,
                    hasTransparency = parameters.transparentBackground,
                    rawMetadataJson = json.encodeToString(NaiImageMetadata.serializer(), metadata),
                    createdAt = currentTimeMillis(),
                ),
            )
        }
        setState {
            copy(
                isGenerating = false,
                message = "生成成功，已入库（本地文件管道接入后可在图库查看）",
                previewBitmap = decodePngPreview(bytes),
            )
        }
    }

    private inline fun setState(reducer: WorkbenchUiState.() -> WorkbenchUiState) {
        _uiState.value = _uiState.value.reducer()
    }

    private fun randomId(): String = Random.nextLong(0, Long.MAX_VALUE).toString(36)
}
