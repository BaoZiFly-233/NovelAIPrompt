package com.novelstudio.core.network.dto

import com.novelstudio.core.model.NaiModel
import com.novelstudio.core.model.NoiseSchedule
import com.novelstudio.core.model.Sampler
import com.novelstudio.core.model.SubscriptionTier
import com.novelstudio.core.model.V5Character
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
    @SerialName("transparent_background") val transparentBackground: Boolean,
    @SerialName("cfg_rescale") val cfgRescale: Float,
    @SerialName("uncond_scale") val uncondScale: Float,
    @SerialName("noise_schedule") val noiseSchedule: String,
    @SerialName("params_version") val paramsVersion: Int = 3,
    @SerialName("legacy") val legacy: Boolean = false,
    @SerialName("add_original_image") val addOriginalImage: Boolean = true,
    @SerialName("controlnet_strength") val controlnetStrength: Float = 1.0f,
    @SerialName("dynamic_thresholding") val dynamicThresholding: Boolean = false,
    @SerialName("characterPrompts") val characterPrompts: List<CharacterPromptPayload> = emptyList(),
)

@Serializable
data class CharacterPromptPayload(
    val prompt: String,
    val uc: String,
    @SerialName("center_x") val centerX: Float,
    @SerialName("center_y") val centerY: Float,
    val width: Float,
    val height: Float,
)

/** GET /user/subscription 响应 */
@Serializable
data class SubscriptionDto(
    val tier: Int = 0,
    val active: Boolean = false,
    val anlas: Int? = null,
) {
    fun toSubscriptionTier(): SubscriptionTier = when (tier) {
        3 -> SubscriptionTier.OPUS
        2 -> SubscriptionTier.SCROLL
        1 -> SubscriptionTier.TABLET
        0 -> SubscriptionTier.PAPER
        else -> SubscriptionTier.UNKNOWN
    }
}
