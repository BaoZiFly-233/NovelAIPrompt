package com.novelstudio.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ArtistString(
    val id: String,
    val name: String,
    val positivePrompt: String,
    val negativePrompt: String = "",
    val modelId: String? = null,
    val parameterOverridesJson: String? = null,
    val coverImageId: String? = null,
    val notes: String = "",
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class PromptAsset(
    val id: String,
    val name: String,
    val positivePrompt: String,
    val negativePrompt: String = "",
    val notes: String = "",
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
enum class TagSource {
    PERSONAL,
    OFFICIAL,
    RECENT,
    IMPORTED,
}

@Serializable
data class PersonalTag(
    val id: String,
    val normalizedValue: String,
    val displayValue: String,
    val groupName: String? = null,
    val notes: String = "",
    val isFavorite: Boolean = false,
    val source: TagSource = TagSource.PERSONAL,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class TagSuggestion(
    val tag: String,
    val confidence: Float,
    val count: Int,
)

/**
 * 工作台持久化草稿。资产区段保存快照，删除资产时只清空来源 ID，历史文本不会被改写。
 */
@Serializable
data class WorkbenchDraft(
    val artistStringId: String? = null,
    val promptAssetId: String? = null,
    val artistPositive: String = "",
    val artistNegative: String = "",
    val promptPositive: String = "",
    val promptNegative: String = "",
    val orderedTags: List<String> = emptyList(),
    val freePrompt: String = "",
    val negativePrompt: String = GenerationParameters.DEFAULT_NEGATIVE,
    val model: NaiModel = NaiModel.V5_FULL,
    val width: Int = 1024,
    val height: Int = 1024,
    val steps: Int = 28,
    val scale: Float = 6f,
    val cfgRescale: Float = 0f,
    val sampler: Sampler = Sampler.K_EULER,
    val noiseSchedule: NoiseSchedule = NoiseSchedule.NATIVE,
    val nSamples: Int = 1,
    val qualityToggle: Boolean = true,
    val seed: Long = GenerationParameters.RANDOM_SEED,
    val transparentBackground: Boolean = false,
    val updatedAt: Long = 0L,
) {
    val finalPositive: String
        get() = joinPromptSections(artistPositive, promptPositive, orderedTags.joinToString(", "), freePrompt)

    val finalNegative: String
        get() = joinPromptSections(artistNegative, promptNegative, negativePrompt)
}

fun joinPromptSections(vararg sections: String): String = sections
    .asSequence()
    .map(String::trim)
    .filter(String::isNotEmpty)
    .joinToString(", ")

fun normalizeTag(value: String): String = value
    .trim()
    .replace('_', ' ')
    .split(Regex("\\s+"))
    .filter(String::isNotEmpty)
    .joinToString(" ")
    .lowercase()

@Serializable
enum class GenerationAction(val wireValue: String) {
    GENERATE("generate"),
    IMG2IMG("img2img"),
    INFILL("infill"),
}

@Serializable
enum class ImageOperation {
    GENERATE,
    IMPORT,
    IMG2IMG,
    INPAINT,
    ENHANCE,
    UPSCALE,
    REMOVE_BACKGROUND,
    LINE_ART,
    SKETCH,
    COLORIZE,
    EMOTION,
    DECLUTTER,
}

@Serializable
data class GenerationInputImage(
    val bytes: ByteArray,
    val width: Int,
    val height: Int,
    val mimeType: String,
) {
    init {
        require(bytes.isNotEmpty()) { "图像内容不能为空" }
        require(width > 0 && height > 0) { "图像尺寸必须大于 0" }
        require(mimeType == "image/png" || mimeType == "image/webp") { "只支持 PNG 或 WebP 图像" }
    }
}

@Serializable
sealed interface GenerationPreflight {
    @Serializable
    data object Free : GenerationPreflight

    @Serializable
    data class RequiresConfirmation(val summary: String) : GenerationPreflight

    @Serializable
    data class Blocked(val reason: String) : GenerationPreflight
}

sealed interface GenerationEvent {
    data class Intermediate(
        val imageBytes: ByteArray,
        val mimeType: String,
        val index: Int? = null,
    ) : GenerationEvent

    data class Final(
        val images: List<GeneratedImageBytes>,
    ) : GenerationEvent

    data class Failure(val message: String) : GenerationEvent
}

data class GeneratedImageBytes(
    val bytes: ByteArray,
    val mimeType: String,
    val index: Int? = null,
    val seed: Long? = null,
)

@Serializable
enum class DirectorTool(val requestType: String, val operation: ImageOperation) {
    REMOVE_BACKGROUND("bg-removal", ImageOperation.REMOVE_BACKGROUND),
    REMOVE_BACKGROUND_GENERATED("bg-removal-generated", ImageOperation.REMOVE_BACKGROUND),
    REMOVE_BACKGROUND_BLENDED("bg-removal-blended", ImageOperation.REMOVE_BACKGROUND),
    LINE_ART("lineart", ImageOperation.LINE_ART),
    SKETCH("sketch", ImageOperation.SKETCH),
    COLORIZE("colorize", ImageOperation.COLORIZE),
    EMOTION("emotion", ImageOperation.EMOTION),
    DECLUTTER("declutter", ImageOperation.DECLUTTER),
}

@Serializable
sealed interface ImageToolRequest {
    val parentImageId: String
    val source: GenerationInputImage

    @Serializable
    data class Upscale(
        override val parentImageId: String,
        override val source: GenerationInputImage,
        val modelId: String,
        val declaredBlurSigma: Float = 0f,
    ) : ImageToolRequest

    @Serializable
    data class Director(
        override val parentImageId: String,
        override val source: GenerationInputImage,
        val tool: DirectorTool,
        val prompt: String? = null,
        val defry: Int? = null,
    ) : ImageToolRequest
}
