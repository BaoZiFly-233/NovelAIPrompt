package com.novelstudio.core.model

import kotlinx.serialization.Serializable

/** V5 多角色定位画板中的单个角色（最多 22 个，坐标全部归一化到 [0,1]） */
@Serializable
data class V5Character(
    val id: String,
    val prompt: String = "",
    val uc: String = "",
    val centerX: Float = 0.5f,
    val centerY: Float = 0.5f,
    val width: Float = 0.4f,
    val height: Float = 0.8f,
) {
    init {
        require(width in MIN_SIZE..MAX_SIZE) { "角色宽度必须位于 [$MIN_SIZE, $MAX_SIZE]" }
        require(height in MIN_SIZE..MAX_SIZE) { "角色高度必须位于 [$MIN_SIZE, $MAX_SIZE]" }
    }

    val right: Float get() = (centerX + width / 2f).coerceAtMost(1f)
    val bottom: Float get() = (centerY + height / 2f).coerceAtMost(1f)

    companion object {
        /** NovelAI Diffusion V5 官方支持的最大角色数 */
        const val MAX_CHARACTERS = 22
        const val MIN_SIZE = 0.01f
        const val MAX_SIZE = 1.0f
    }
}

/** 一次图像生成的完整参数集（对齐 V5 官方 payload 字段） */
@Serializable
data class GenerationParameters(
    val prompt: String = "",
    val negativePrompt: String = DEFAULT_NEGATIVE,
    val model: NaiModel = NaiModel.V5_FULL,
    val width: Int = 1024,
    val height: Int = 1024,
    val scale: Float = 6.0f,
    val cfgRescale: Float = 0.0f,
    val uncondScale: Float = 1.0f,
    val steps: Int = 28,
    val seed: Long = -1L,
    val sampler: Sampler = Sampler.K_EULER,
    val noiseSchedule: NoiseSchedule = NoiseSchedule.NATIVE,
    val nSamples: Int = 1,
    val ucPreset: Int = 0,
    val qualityToggle: Boolean = true,
    val transparentBackground: Boolean = false,
    val characterPrompts: List<V5Character> = emptyList(),
) {
    init {
        require(characterPrompts.size <= V5Character.MAX_CHARACTERS) {
            "V5 最多支持 ${V5Character.MAX_CHARACTERS} 个独立角色"
        }
    }

    /** 是否命中 Opus 免费额度（像素与步数双重钳位） */
    val isWithinFreeQuota: Boolean
        get() = width.toLong() * height <= 1_048_576L && steps <= 28

    companion object {
        const val DEFAULT_NEGATIVE: String =
            "lowres, bad anatomy, bad hands, text, error, missing fingers, " +
                "extra digit, fewer digits, cropped, worst quality, low quality"
    }
}
