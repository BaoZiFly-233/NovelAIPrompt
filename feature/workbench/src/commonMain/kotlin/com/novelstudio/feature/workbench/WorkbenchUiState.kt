package com.novelstudio.feature.workbench

import androidx.compose.ui.graphics.ImageBitmap
import com.novelstudio.core.data.GenerationQueueItem
import com.novelstudio.core.model.ArtistString
import com.novelstudio.core.model.AspectPreset
import com.novelstudio.core.model.GenerationParameters
import com.novelstudio.core.model.GenerationPreflight
import com.novelstudio.core.model.NaiModel
import com.novelstudio.core.model.NoiseSchedule
import com.novelstudio.core.model.OpusBatteryState
import com.novelstudio.core.model.PersonalTag
import com.novelstudio.core.model.PromptAsset
import com.novelstudio.core.model.Sampler
import com.novelstudio.core.model.TagSuggestion
import com.novelstudio.core.model.TaskStatus
import com.novelstudio.core.model.V5Character
import com.novelstudio.core.model.VibeReference
import com.novelstudio.core.model.WorkbenchDraft
import com.novelstudio.core.model.joinPromptSections

enum class GenerationMode(val label: String) {
    SINGLE("单张"),
    BATCH_REVIEW("批量审阅"),
    DRAW_UNTIL_LIKED("抽到喜欢为止"),
}

data class WorkbenchUiState(
    val artistStrings: List<ArtistString> = emptyList(),
    val promptAssets: List<PromptAsset> = emptyList(),
    val availableTags: List<PersonalTag> = emptyList(),
    val selectedArtistStringId: String? = null,
    val selectedPromptAssetId: String? = null,
    val artistPositive: String = "",
    val artistNegative: String = "",
    val promptPositive: String = "",
    val promptNegative: String = "",
    val orderedTags: List<String> = emptyList(),
    val freePrompt: String = "",
    val negativePrompt: String = GenerationParameters.DEFAULT_NEGATIVE,
    val tagSuggestions: List<TagSuggestion> = emptyList(),
    val suggestionsOffline: Boolean = false,
    val isSuggestionsLoading: Boolean = false,
    val model: NaiModel = NaiModel.V5_FULL,
    val aspect: AspectPreset = AspectPreset.SQUARE,
    val width: Int = 1024,
    val height: Int = 1024,
    val steps: Int = 28,
    val scale: Float = 6f,
    val cfgRescale: Float = 0f,
    val sampler: Sampler = Sampler.K_EULER,
    val noiseSchedule: NoiseSchedule = NoiseSchedule.NATIVE,
    val nSamples: Int = 2,
    val generationMode: GenerationMode = GenerationMode.SINGLE,
    val qualityToggle: Boolean = true,
    val seedText: String = "",
    val transparentBackground: Boolean = false,
    val characterPrompts: List<V5Character> = emptyList(),
    val selectedCharacterId: String? = null,
    val vibeReferences: List<VibeReference> = emptyList(),
    val isEncodingVibe: Boolean = false,
    val needsVibeEncodingConfirmation: Boolean = false,
    val pendingVibeDisplayName: String? = null,
    val pendingVibeInformationExtracted: Float = 1f,
    // SMEA 采样增强
    val smea: Boolean = false,
    val smeaDyn: Boolean = false,
    // Variety+：生成多样性增强
    val varietyPlus: Boolean = false,
    // Decrisper：减少高 scale 下的过曝
    val decrisper: Boolean = false,
    val isGenerating: Boolean = false,
    val queueItems: List<GenerationQueueItem> = emptyList(),
    val pendingAnlasTaskId: String? = null,
    val battery: OpusBatteryState? = null,
    val isBatteryLoading: Boolean = false,
    val batteryErrorMessage: String? = null,
    val lastPreflight: GenerationPreflight? = null,
    val needsAnlasConfirmation: Boolean = false,
    val anlasConfirmationMessage: String? = null,
    val message: String? = null,
    val errorMessage: String? = null,
    val previewBitmap: ImageBitmap? = null,
) {
    val finalPositive: String
        get() = joinPromptSections(artistPositive, promptPositive, orderedTags.joinToString(", "), freePrompt)
    val finalNegative: String
        get() = joinPromptSections(artistNegative, promptNegative, negativePrompt)
    val batteryLabel: String
        get() = when {
            isBatteryLoading -> "正在读取 V5 用量…"
            battery == null -> "V5 用量未知"
            !battery.isOpus -> "非有效 Opus 订阅"
            battery.isUsageUnavailable -> "V5 用量暂不可用"
            else -> "V5 用量 ${battery.batteryPercent}%"
        }
    val isCharacterCountValid: Boolean get() = characterPrompts.size <= model.maxCharacterPrompts
    val areVibesCompatible: Boolean get() = vibeReferences.all { it.model == model }
    val vibeCompatibilityMessage: String?
        get() = if (areVibesCompatible) null else "有 Vibe 是为其他模型编码的，请切回对应模型或移除后重新编码"
    val totalVibeStrength: Float get() = vibeReferences.sumOf { it.referenceStrength.toDouble() }.toFloat()
    val canGenerate: Boolean
        get() = finalPositive.isNotBlank() && battery != null && !isBatteryLoading && !isEncodingVibe &&
            isCharacterCountValid && areVibesCompatible && seedError == null
    val queuedTaskCount: Int get() = queueItems.count { it.status == TaskStatus.QUEUED }
    val runningTask: GenerationQueueItem? get() = queueItems.firstOrNull { it.status == TaskStatus.RUNNING }
    val waitingAnlasCount: Int get() = queueItems.count { it.status == TaskStatus.WAITING_ANLAS_CONFIRMATION }
    val seedError: String?
        get() = when {
            seedText.isBlank() -> null
            seedText.toLongOrNull() == null -> "Seed 必须是整数"
            seedText.toLong() !in 0L..0xFFFFFFFFL -> "Seed 必须位于 0..4294967295"
            else -> null
        }

    fun parameters(seed: Long): GenerationParameters = GenerationParameters(
        prompt = finalPositive,
        negativePrompt = finalNegative,
        model = model,
        width = width,
        height = height,
        steps = steps,
        scale = scale,
        cfgRescale = cfgRescale,
        sampler = sampler,
        noiseSchedule = noiseSchedule,
        nSamples = if (generationMode == GenerationMode.BATCH_REVIEW) nSamples else 1,
        qualityToggle = qualityToggle,
        transparentBackground = transparentBackground,
        characterPrompts = characterPrompts,
        vibeReferences = vibeReferences,
        seed = seedText.toLongOrNull() ?: seed,
        artistStringId = selectedArtistStringId,
        promptAssetId = selectedPromptAssetId,
        smea = smea,
        smeaDyn = smeaDyn,
        varietyPlus = varietyPlus,
        decrisper = decrisper,
    )

    fun toDraft(updatedAt: Long) = WorkbenchDraft(
        artistStringId = selectedArtistStringId,
        promptAssetId = selectedPromptAssetId,
        artistPositive = artistPositive,
        artistNegative = artistNegative,
        promptPositive = promptPositive,
        promptNegative = promptNegative,
        orderedTags = orderedTags,
        freePrompt = freePrompt,
        negativePrompt = negativePrompt,
        model = model,
        width = width,
        height = height,
        steps = steps,
        scale = scale,
        cfgRescale = cfgRescale,
        sampler = sampler,
        noiseSchedule = noiseSchedule,
        nSamples = nSamples,
        qualityToggle = qualityToggle,
        seed = seedText.toLongOrNull() ?: GenerationParameters.RANDOM_SEED,
        transparentBackground = transparentBackground,
        updatedAt = updatedAt,
    )
}

internal expect fun decodePngPreview(bytes: ByteArray): ImageBitmap?
internal expect fun currentTimeMillis(): Long
