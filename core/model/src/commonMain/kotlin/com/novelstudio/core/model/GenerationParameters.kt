package com.novelstudio.core.model

import kotlinx.serialization.Serializable

@Serializable
data class VibeReference(
    val id: String,
    val displayName: String,
    val encoding: ByteArray,
    val model: NaiModel = NaiModel.V5_FULL,
    val referenceStrength: Float = 0.6f,
    val informationExtracted: Float = 1.0f,
) {
    init {
        require(id.isNotBlank()) { "Vibe 引用 ID 不能为空" }
        require(displayName.isNotBlank()) { "Vibe 名称不能为空" }
        require(encoding.isNotEmpty()) { "Vibe 编码不能为空" }
        require(referenceStrength in 0f..1f) { "Vibe referenceStrength 必须位于 [0, 1]" }
        require(informationExtracted in 0f..1f) { "Vibe informationExtracted 必须位于 [0, 1]" }
    }

    // ByteArray 在 data class 中默认使用引用语义；重写为值语义以确保 state diffing 正确。
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VibeReference) return false
        return id == other.id &&
            displayName == other.displayName &&
            model == other.model &&
            referenceStrength == other.referenceStrength &&
            informationExtracted == other.informationExtracted &&
            encoding.contentEquals(other.encoding)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + displayName.hashCode()
        result = 31 * result + model.hashCode()
        result = 31 * result + referenceStrength.hashCode()
        result = 31 * result + informationExtracted.hashCode()
        result = 31 * result + encoding.contentHashCode()
        return result
    }
}

/** V4+ 多角色定位画板中的单个角色；API 只接收归一化中心点，不接收角色框尺寸。 */
@Serializable
data class V5Character(
    val id: String,
    val prompt: String = "",
    val uc: String = "",
    val centerX: Float = 0.5f,
    val centerY: Float = 0.5f,
) {
    init {
        require(centerX in MIN_COORDINATE..MAX_COORDINATE) { "角色 X 坐标必须位于 [0, 1]" }
        require(centerY in MIN_COORDINATE..MAX_COORDINATE) { "角色 Y 坐标必须位于 [0, 1]" }
    }

    companion object {
        /** NovelAI Diffusion V5 官方支持的最大角色数 */
        const val MAX_CHARACTERS = 22
        const val MIN_COORDINATE = 0f
        const val MAX_COORDINATE = 1f
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
    val steps: Int = 28,
    val seed: Long = -1L,
    val sampler: Sampler = Sampler.K_EULER,
    val noiseSchedule: NoiseSchedule = NoiseSchedule.NATIVE,
    val nSamples: Int = 1,
    val ucPreset: Int = 0,
    val qualityToggle: Boolean = true,
    val transparentBackground: Boolean = false,
    val characterPrompts: List<V5Character> = emptyList(),
    val vibeReferences: List<VibeReference> = emptyList(),
    val action: GenerationAction = GenerationAction.GENERATE,
    val operation: ImageOperation = ImageOperation.GENERATE,
    val parentImageId: String? = null,
    val sourceImage: GenerationInputImage? = null,
    val maskImage: GenerationInputImage? = null,
    val strength: Float = 0.5f,
    val noise: Float = 0f,
    val outputScale: Float = 1f,
    val artistStringId: String? = null,
    val promptAssetId: String? = null,
    // SMEA 采样增强：大尺寸图像质量提升（sm=true 是前提，sm_dyn 提供更强的动态版本）
    val smea: Boolean = false,
    val smeaDyn: Boolean = false,
    // Variety+：skip_cfg_above_sigma=19 时增加生成多样性
    val varietyPlus: Boolean = false,
    // Decrisper：降低高 scale 下的过曝效果（dynamic_thresholding wire 名）
    val decrisper: Boolean = false,
    // 无条件引导强度（通常保持 1.0，细粒度调整用）
    val uncondScale: Float = 1.0f,
    // Inpaint 专用：遮罩区域内部的 img2img 强度
    val inpaintImg2ImgStrength: Float = 1.0f,
    // 归一化多 Vibe 参考强度（防止超过 1.0 时效果过饱和）
    val normalizeReferenceStrengthMultiple: Boolean = false,
    // ControlNet 引导：生成后的 mask base64（来自 /ai/generate-controlnet-mask）
    val controlnetCondition: String? = null,
    // ControlNet 模型（HED/MIDAS 等）
    val controlnetModel: ControlNetModel? = null,
) {
    init {
        require(width >= MIN_DIMENSION && width % DIMENSION_STEP == 0) { "宽度必须是至少 64 的 64 倍数" }
        require(height >= MIN_DIMENSION && height % DIMENSION_STEP == 0) { "高度必须是至少 64 的 64 倍数" }
        require(steps in MIN_STEPS..MAX_STEPS) { "步数必须位于 1..50" }
        require(scale in MIN_GUIDANCE..MAX_GUIDANCE) { "提示词引导必须位于 0..10" }
        require(cfgRescale in MIN_CFG_RESCALE..MAX_CFG_RESCALE) { "引导重缩放必须位于 0..1" }
        require(seed == RANDOM_SEED || seed in MIN_SEED..MAX_SEED) { "Seed 必须为 -1 或位于 0..4294967295" }
        require(nSamples in 1..MAX_SAMPLES) { "每次生成张数必须位于 1..6；具体上限还取决于分辨率" }
        require(characterPrompts.size <= model.maxCharacterPrompts) {
            "${model.displayName} 最多支持 ${model.maxCharacterPrompts} 个独立角色"
        }
        require(vibeReferences.size <= MAX_VIBE_REFERENCES) { "最多支持 $MAX_VIBE_REFERENCES 个 Vibe 引用" }
        require(vibeReferences.all { it.model == model }) { "Vibe 编码模型必须与生成模型一致" }
        require(strength in 0f..1f) { "Strength 必须位于 [0, 1]" }
        require(noise in 0f..1f) { "Noise 必须位于 [0, 1]" }
        require(outputScale >= 1f) { "输出缩放不能小于 1" }
        when (action) {
            GenerationAction.GENERATE -> {
                require(sourceImage == null && maskImage == null) { "普通生成不能携带源图或遮罩" }
            }
            GenerationAction.IMG2IMG -> {
                requireNotNull(sourceImage) { "Img2Img/Enhance 必须携带源图" }
                require(maskImage == null) { "Img2Img/Enhance 不能携带遮罩" }
                if (operation == ImageOperation.ENHANCE) {
                    require(nSamples == 1) { "Enhance 固定为单张结果" }
                }
            }
            GenerationAction.INFILL -> {
                requireNotNull(model.inpaintingModelId) { "${model.displayName} 的 Inpaint 能力尚未确认，禁止提交" }
                val source = requireNotNull(sourceImage) { "Inpaint 必须携带源图" }
                val mask = requireNotNull(maskImage) { "Inpaint 必须携带非空遮罩" }
                require(source.width == mask.width && source.height == mask.height) { "源图与遮罩尺寸必须一致" }
                require(width == source.width && height == source.height) { "Inpaint 请求尺寸必须与源图一致" }
                require(operation == ImageOperation.INPAINT) { "Infill action 只能用于 Inpaint" }
            }
        }
        require((parentImageId != null) == (operation != ImageOperation.GENERATE)) {
            "派生操作必须绑定父作品，普通生成不能绑定父作品"
        }
    }

    /** 是否命中 Opus 免费额度（像素与步数双重钳位） */
    val isWithinFreeQuota: Boolean
        get() = width.toLong() * height <= 1_048_576L && steps <= 28 && nSamples == 1 && vibeReferences.size <= 4

    companion object {
        const val DEFAULT_NEGATIVE: String =
            "lowres, bad anatomy, bad hands, text, error, missing fingers, " +
                "extra digit, fewer digits, cropped, worst quality, low quality"
        const val MAX_VIBE_REFERENCES = 16
        const val MAX_SAMPLES = 6
        const val DIMENSION_STEP = 64
        const val MIN_DIMENSION = 64
        const val MIN_STEPS = 1
        const val MAX_STEPS = 50
        const val MIN_GUIDANCE = 0f
        const val MAX_GUIDANCE = 10f
        const val MIN_CFG_RESCALE = 0f
        const val MAX_CFG_RESCALE = 1f
        const val RANDOM_SEED = -1L
        const val MIN_SEED = 0L
        const val MAX_SEED = 0xFFFFFFFFL
    }
}
