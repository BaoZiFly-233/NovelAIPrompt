package com.novelstudio.feature.inpaint

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelstudio.core.common.png.PngMetadataParser
import com.novelstudio.core.data.GenerationOutcome
import com.novelstudio.core.data.GenerationRepository
import com.novelstudio.core.data.ImageRepository
import com.novelstudio.core.data.ImageToolOutcome
import com.novelstudio.core.data.ImageToolRepository
import com.novelstudio.core.model.DirectorTool
import com.novelstudio.core.model.GenerationAction
import com.novelstudio.core.model.GenerationInputImage
import com.novelstudio.core.model.GenerationParameters
import com.novelstudio.core.model.GenerationPreflight
import com.novelstudio.core.model.ImageOperation
import com.novelstudio.core.model.ImageRecord
import com.novelstudio.core.model.ImageToolRequest
import com.novelstudio.core.model.NaiModel
import com.novelstudio.core.model.Sampler
import com.novelstudio.core.storage.ImageFileStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okio.Buffer

data class ImageToolsUiState(
    val parent: ImageRecord? = null,
    val loading: Boolean = true,
    val running: Boolean = false,
    val prompt: String = "",
    val negativePrompt: String = GenerationParameters.DEFAULT_NEGATIVE,
    val strength: Float = 0.5f,
    val noise: Float = 0f,
    val enhanceScale: Float = 2f,
    val blurSigma: Float = 0f,
    val directorPrompt: String = "",
    val defry: Int = 0,
    val confirmation: String? = null,
    val message: String? = null,
    val error: String? = null,
)

class ImageToolsViewModel(
    private val images: ImageRepository,
    private val storage: ImageFileStorage,
    private val generation: GenerationRepository,
    private val tools: ImageToolRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ImageToolsUiState())
    val state: StateFlow<ImageToolsUiState> = mutableState.asStateFlow()

    private var source: GenerationInputImage? = null
    private var pending: PendingRequest? = null

    fun load(imageId: String) {
        viewModelScope.launch {
            mutableState.update { it.copy(loading = true, error = null, message = null) }
            runCatching {
                val parent = requireNotNull(images.getById(imageId)) { "作品不存在" }
                require(parent.trashedAt == null) { "垃圾箱中的作品不能执行图像工具" }
                val bytes = storage.readImage(parent.filePath)
                val input = GenerationInputImage(bytes, parent.width, parent.height, parent.mimeType)
                parent to input
            }.onSuccess { (parent, input) ->
                source = input
                mutableState.update {
                    it.copy(
                        parent = parent,
                        loading = false,
                        prompt = parent.prompt,
                        negativePrompt = parent.uc.ifBlank { GenerationParameters.DEFAULT_NEGATIVE },
                    )
                }
            }.onFailure(::showFailure)
        }
    }

    fun updatePrompt(value: String) = mutableState.update { it.copy(prompt = value) }
    fun updateNegativePrompt(value: String) = mutableState.update { it.copy(negativePrompt = value) }
    fun updateStrength(value: Float) = mutableState.update { it.copy(strength = value.coerceIn(0f, 1f)) }
    fun updateNoise(value: Float) = mutableState.update { it.copy(noise = value.coerceIn(0f, 1f)) }
    fun updateEnhanceScale(value: Float) = mutableState.update { it.copy(enhanceScale = value.coerceIn(1f, 4f)) }
    fun updateBlurSigma(value: Float) = mutableState.update { it.copy(blurSigma = value) }
    fun updateDirectorPrompt(value: String) = mutableState.update { it.copy(directorPrompt = value) }
    fun updateDefry(value: Int) = mutableState.update { it.copy(defry = value.coerceIn(0, 5)) }
    fun dismissConfirmation() { pending = null; mutableState.update { it.copy(confirmation = null) } }
    fun clearNotice() = mutableState.update { it.copy(message = null, error = null) }
    fun showPickerError(message: String) = mutableState.update { it.copy(error = message) }

    fun requestImg2Img() = requestGeneration(ImageOperation.IMG2IMG)
    fun requestEnhance() = requestGeneration(ImageOperation.ENHANCE)

    internal fun requestInpaint(mask: PickedMaskImage) {
        val current = mutableState.value
        val parent = current.parent ?: return
        runCatching {
            require(mask.bytes.isNotEmpty()) { "遮罩不能为空" }
            val info = PngMetadataParser.parse(Buffer().apply { write(mask.bytes) })
            val maskWidth = requireNotNull(info.width) { "遮罩缺少宽度信息" }
            val maskHeight = requireNotNull(info.height) { "遮罩缺少高度信息" }
            require(maskWidth == parent.width && maskHeight == parent.height) {
                "遮罩尺寸 ${maskWidth}×${maskHeight} 与原图 ${parent.width}×${parent.height} 不一致"
            }
            deriveParameters(
                operation = ImageOperation.INPAINT,
                mask = GenerationInputImage(mask.bytes, maskWidth, maskHeight, "image/png"),
            )
        }.onSuccess(::preflightGeneration).onFailure(::showFailure)
    }

    fun requestUpscale() {
        val parent = mutableState.value.parent ?: return
        val input = source ?: return
        preflightTool(
            ImageToolRequest.Upscale(
                parentImageId = parent.id,
                source = input,
                modelId = parent.model,
                declaredBlurSigma = mutableState.value.blurSigma,
            ),
        )
    }

    fun requestDirector(tool: DirectorTool) {
        val parent = mutableState.value.parent ?: return
        val input = source ?: return
        val acceptsPrompt = tool == DirectorTool.COLORIZE || tool == DirectorTool.EMOTION
        preflightTool(
            ImageToolRequest.Director(
                parentImageId = parent.id,
                source = input,
                tool = tool,
                prompt = mutableState.value.directorPrompt.trim().takeIf { acceptsPrompt && it.isNotEmpty() },
                defry = mutableState.value.defry.takeIf { acceptsPrompt },
            ),
        )
    }

    fun confirm() {
        val request = pending ?: return
        pending = null
        mutableState.update { it.copy(confirmation = null, running = true, error = null, message = null) }
        viewModelScope.launch {
            try {
                when (request) {
                    is PendingRequest.Generation -> handleGenerationOutcome(generation.generateWithAnlas(request.parameters))
                    is PendingRequest.Tool -> handleToolOutcome(tools.executeConfirmed(request.request))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Throwable) {
                showFailure(throwable)
            }
        }
    }

    private fun requestGeneration(operation: ImageOperation) {
        runCatching { deriveParameters(operation) }
            .onSuccess(::preflightGeneration)
            .onFailure(::showFailure)
    }

    private fun deriveParameters(
        operation: ImageOperation,
        mask: GenerationInputImage? = null,
    ): GenerationParameters {
        val current = mutableState.value
        val parent = requireNotNull(current.parent) { "尚未载入原图" }
        val input = requireNotNull(source) { "原图文件不可用" }
        val action = when (operation) {
            ImageOperation.INPAINT -> GenerationAction.INFILL
            ImageOperation.IMG2IMG, ImageOperation.ENHANCE -> GenerationAction.IMG2IMG
            else -> error("不支持的派生操作")
        }
        return GenerationParameters(
            prompt = current.prompt,
            negativePrompt = current.negativePrompt,
            model = NaiModel.fromId(parent.model),
            width = parent.width,
            height = parent.height,
            scale = parent.scale.coerceIn(0f, 10f),
            steps = parent.steps.coerceIn(1, 50),
            seed = GenerationParameters.RANDOM_SEED,
            sampler = Sampler.fromId(parent.sampler),
            nSamples = 1,
            action = action,
            operation = operation,
            parentImageId = parent.id,
            sourceImage = input,
            maskImage = mask,
            strength = current.strength,
            noise = current.noise,
            outputScale = if (operation == ImageOperation.ENHANCE) current.enhanceScale else 1f,
            artistStringId = parent.artistStringId,
            promptAssetId = parent.promptAssetId,
        )
    }

    private fun preflightGeneration(parameters: GenerationParameters) {
        mutableState.update { it.copy(running = true, error = null, message = null) }
        viewModelScope.launch {
            when (val result = generation.preflight(parameters)) {
                GenerationPreflight.Free -> handleGenerationOutcome(generation.generate(parameters))
                is GenerationPreflight.RequiresConfirmation -> {
                    pending = PendingRequest.Generation(parameters)
                    mutableState.update { it.copy(running = false, confirmation = result.summary) }
                }
                is GenerationPreflight.Blocked -> mutableState.update { it.copy(running = false, error = result.reason) }
            }
        }
    }

    private fun preflightTool(request: ImageToolRequest) {
        mutableState.update { it.copy(running = true, error = null, message = null) }
        viewModelScope.launch {
            when (val result = tools.preflight(request)) {
                GenerationPreflight.Free -> handleToolOutcome(tools.executeConfirmed(request))
                is GenerationPreflight.RequiresConfirmation -> {
                    pending = PendingRequest.Tool(request)
                    mutableState.update { it.copy(running = false, confirmation = result.summary) }
                }
                is GenerationPreflight.Blocked -> mutableState.update { it.copy(running = false, error = result.reason) }
            }
        }
    }

    private fun handleGenerationOutcome(outcome: GenerationOutcome) {
        when (outcome) {
            is GenerationOutcome.Success -> mutableState.update {
                it.copy(running = false, message = "已创建 ${outcome.records.size} 个派生作品，原图保持不变")
            }
            is GenerationOutcome.NeedsAnlasConfirmation -> {
                pending = PendingRequest.Generation(outcome.parameters)
                mutableState.update { it.copy(running = false, confirmation = outcome.preflight.summary) }
            }
            is GenerationOutcome.Failure -> mutableState.update { it.copy(running = false, error = outcome.message) }
        }
    }

    private fun handleToolOutcome(outcome: ImageToolOutcome) {
        when (outcome) {
            is ImageToolOutcome.Success -> mutableState.update {
                it.copy(running = false, message = "已创建 ${outcome.records.size} 个派生作品，原图保持不变")
            }
            is ImageToolOutcome.Failure -> mutableState.update { it.copy(running = false, error = outcome.message) }
        }
    }

    private fun showFailure(throwable: Throwable) {
        mutableState.update {
            it.copy(loading = false, running = false, error = throwable.message ?: "图像工具操作失败")
        }
    }

    private sealed interface PendingRequest {
        data class Generation(val parameters: GenerationParameters) : PendingRequest
        data class Tool(val request: ImageToolRequest) : PendingRequest
    }
}
