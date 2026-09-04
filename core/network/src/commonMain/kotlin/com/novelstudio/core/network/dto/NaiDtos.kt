package com.novelstudio.core.network.dto

import com.novelstudio.core.model.SubscriptionTier
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** POST /ai/generate-image 请求体（对齐 V5 官方 payload 规范） */
@Serializable
data class ImageRequestPayload(
    val input: String,
    val model: String,
    val action: String = "generate",
    val parameters: ImageParametersPayload,
)

@Serializable
data class ImageParametersPayload(
    val width: Int,
    val height: Int,
    val scale: Float,
    val sampler: String,
    val steps: Int,
    @SerialName("n_samples") val nSamples: Int,
    val seed: Long,
    @SerialName("ucPreset") val ucPreset: Int,
    @SerialName("qualityToggle") val qualityToggle: Boolean,
    @SerialName("negative_prompt") val negativePrompt: String,
    @SerialName("cfg_rescale") val cfgRescale: Float,
    @SerialName("noise_schedule") val noiseSchedule: String,
    @SerialName("image_format") val imageFormat: String = "png",
    @SerialName("straight_alpha") val straightAlpha: Boolean = false,
    @SerialName("tag_hint_transparent_background") val tagHintTransparentBackground: Boolean = false,
    @SerialName("params_version") val paramsVersion: Int = 3,
    @SerialName("legacy") val legacy: Boolean = false,
    @SerialName("add_original_image") val addOriginalImage: Boolean = true,
    @SerialName("controlnet_strength") val controlnetStrength: Float = 1.0f,
    @SerialName("v4_prompt") val v4Prompt: CharacterConditionPayload? = null,
    @SerialName("v4_negative_prompt") val v4NegativePrompt: CharacterConditionPayload? = null,
    @SerialName("reference_image_multiple") val referenceImageMultiple: List<String>? = null,
    @SerialName("reference_strength_multiple") val referenceStrengthMultiple: List<Float>? = null,
    @SerialName("reference_information_extracted_multiple") val referenceInformationExtractedMultiple: List<Float>? = null,
    val image: String? = null,
    val mask: String? = null,
    val strength: Float? = null,
    val noise: Float? = null,
    val img2img: Img2ImgParamsPayload? = null,
    val stream: String? = null,
    // ControlNet 引导（来自 /ai/generate-controlnet-mask 的 base64 结果）
    @SerialName("controlnet_condition") val controlnetCondition: String? = null,
    // ControlNet 模型 ID（如 "hed"、"midas"）
    @SerialName("controlnet_model") val controlnetModel: String? = null,
    // controlnet_strength 已存在（固定1.0）
    @SerialName("upscaled_enhance") val upscaledEnhance: Boolean = false,
    // SMEA 采样增强（sm=true 是前提，sm_dyn=true 启用动态版本）
    @SerialName("sm") val smea: Boolean = false,
    @SerialName("sm_dyn") val smeaDyn: Boolean = false,
    // Variety+：设为 19.0f 时启用，null 禁用
    @SerialName("skip_cfg_above_sigma") val skipCfgAboveSigma: Float? = null,
    // Decrisper：减少高 scale 下的过曝（覆盖固定 false 的 dynamic_thresholding）
    @SerialName("dynamic_thresholding") val dynamicThresholding: Boolean = false,
    // 无条件引导强度（1.0 为标准值）
    @SerialName("uncond_scale") val uncondScale: Float = 1.0f,
    // 归一化多 Vibe 参考强度
    @SerialName("normalize_reference_strength_multiple") val normalizeReferenceStrengthMultiple: Boolean = false,
    // Inpaint 专用：遮罩区域内部的 img2img 强度
    @SerialName("inpaint_img2img_strength") val inpaintImg2ImgStrength: Float? = null,
)

@Serializable
data class Img2ImgParamsPayload(
    val strength: Float,
    val noise: Float,
    @SerialName("color_correct") val colorCorrect: Boolean = false,
)

@Serializable
data class UpscaleRequestPayload(
    val image: String,
    val model: String,
    @SerialName("declared_blur_sigma") val declaredBlurSigma: Float,
)

/** POST /ai/generate-controlnet-mask 请求体 */
@Serializable
data class GenerateControlNetMaskRequestPayload(
    val image: String,
    val model: String,
    val parameters: ControlNetMaskParametersPayload,
)

@Serializable
data class ControlNetMaskParametersPayload(
    val width: Int,
    val height: Int,
)

@Serializable
data class AugmentImageRequestPayload(
    val image: String,
    val width: Int,
    val height: Int,
    @SerialName("req_type") val requestType: String,
    val prompt: String? = null,
    val defry: Int? = null,
)

@Serializable
data class ImageJsonPayload(
    val image: String,
    val index: Int? = null,
    val seed: Long? = null,
)

@Serializable
data class ImageGenerationJsonResponsePayload(
    val images: List<ImageJsonPayload> = emptyList(),
)

@Serializable
data class TagSuggestionPayload(
    val tag: String,
    val confidence: Float = 0f,
    val count: Int = 0,
)

@Serializable
data class VibeEncodeRequestPayload(
    val image: String,
    val model: String,
    @SerialName("information_extracted") val informationExtracted: Float,
)

@Serializable
data class CharacterConditionPayload(
    val caption: CharacterCaptionPayload,
    @SerialName("use_coords") val useCoords: Boolean,
    @SerialName("use_order") val useOrder: Boolean,
    @SerialName("legacy_uc") val legacyUc: Boolean = false,
)

@Serializable
data class CharacterCaptionPayload(
    @SerialName("base_caption") val baseCaption: String,
    @SerialName("char_captions") val characterCaptions: List<CharacterCaptionEntryPayload>,
)

@Serializable
data class CharacterCaptionEntryPayload(
    @SerialName("char_caption") val characterCaption: String,
    val centers: List<CharacterCenterPayload>,
)

@Serializable
data class CharacterCenterPayload(
    val x: Float,
    val y: Float,
)

/** GET /user/subscription 响应 */
@Serializable
data class SubscriptionDto(
    val tier: Int = 0,
    val active: Boolean = false,
    val usage: UsageLimitDto? = null,
) {
    fun toSubscriptionTier(): SubscriptionTier = when (tier) {
        3 -> SubscriptionTier.OPUS
        2 -> SubscriptionTier.SCROLL
        1 -> SubscriptionTier.TABLET
        0 -> SubscriptionTier.PAPER
        else -> SubscriptionTier.UNKNOWN
    }
}

/** image.novelai.net /user/subscription 中的 V5 Opus 用量状态。 */
@Serializable
data class UsageLimitDto(
    val isNegative: Boolean,
    val percent: Int,
    val timeUntilNextPercent: Int,
)
